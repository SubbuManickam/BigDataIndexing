package com.bigdataindexing.restapi.controller;

import com.bigdataindexing.restapi.models.Plan;
import com.bigdataindexing.restapi.service.JsonValidatorService;
import com.bigdataindexing.restapi.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;

import java.util.*;
import java.util.List;

@RestController
@RequestMapping(value = "/v1/api/json")
public class JsonController {
    @Autowired
    RedisService redisService;
    @Autowired
    JsonValidatorService jsonValidatorService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private Jedis jedis;

    @PostMapping
    public ResponseEntity createPlan(@RequestBody String jsonPayload) {
        Set<String> errors = new HashSet<>();
        Map<String,String> hash = new HashMap<>();
        Plan plan;
        try {
            plan = objectMapper.readValue(jsonPayload, Plan.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Initialization Error");
        }
        if(!jsonValidatorService.validate(jsonPayload, errors)) {
            return ResponseEntity.badRequest().body(errors);
        }
        if(jedis.exists("plan:" + plan.getObjectId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Plan ID already exists");
        }
        hash.put(plan.getObjectId(), jsonPayload);
        jedis.hmset("plan:" + plan.getObjectId(), hash);

        String etag = redisService.generateETag(jsonPayload);
        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", etag);
        return new ResponseEntity<>(plan, headers, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity getAllPlans() {
        List<Plan> allPlans = new ArrayList<>();

        try {
            String cursor = "0";
            do {
                var scanResult = jedis.scan(cursor, new ScanParams().match("plan:*"));
                cursor = scanResult.getCursor();
                List<String> planKeys = scanResult.getResult();

                for (String planKey : planKeys) {
                    Map<String, String> planJson = jedis.hgetAll(planKey);

                    if (!planJson.isEmpty()) {
                        Plan plan = objectMapper.readValue(planJson.get(planKey.substring(5)), Plan.class);
                        allPlans.add(plan);
                    }
                }
            } while (!cursor.equals("0"));
            return new ResponseEntity<>(allPlans, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading plans");
        }
    }
    @GetMapping("/{id}")
    public ResponseEntity getPlan(@PathVariable String id, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        try {
            Map<String, String> planData = jedis.hgetAll("plan:" + id);
            if (planData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Plan not found");
            }

            String etag = redisService.generateETag(planData.get(id));
            if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            }

            Plan plan = objectMapper.readValue(planData.get(id), Plan.class);
            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", etag);
            return new ResponseEntity<>(plan, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading plan");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deletePlan(@PathVariable String id) {
        try {
            if (!jedis.exists("plan:" + id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Plan not found");
            }
            jedis.del("plan:" + id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting plan");
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updatePlan(@PathVariable String id, @RequestBody Map<String, Object> patchData, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        try {
            Set<String> errors = new HashSet<>();

            Map<String, String> existingData = jedis.hgetAll("plan:" + id);
            if (existingData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Plan not found");
            }

            String currentETag = redisService.generateETag(existingData.get(id));
            if (ifMatch != null && !ifMatch.equals(currentETag)) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body("ETag mismatch. Plan has been changed.");
            }

            Map<String, Object> existingPlanMap = objectMapper.readValue(existingData.get(id), Map.class);
            appendPatchData(existingPlanMap, patchData);

            Plan updatedPlan = objectMapper.convertValue(existingPlanMap, Plan.class);
            String updatedJsonPayload = objectMapper.writeValueAsString(updatedPlan);

            Map<String,String> hash = new HashMap<>();
            hash.put(id, updatedJsonPayload);
            jedis.hset("plan:" + id, hash);

            String etag = redisService.generateETag(updatedJsonPayload);
            HttpHeaders headers = new HttpHeaders();
            headers.set("ETag", etag);
            return new ResponseEntity<>(updatedPlan, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating plan");
        }
    }

    private void appendPatchData(Map<String, Object> existingData, Map<String, Object> patchData) {
        try {
            for (Map.Entry<String, Object> entry : patchData.entrySet()) {
                String key = entry.getKey();
                Object patchValue = entry.getValue();

                if (existingData.containsKey(key) && existingData.get(key) instanceof List && patchValue instanceof List) {
                    List<Object> existingList = (List<Object>) existingData.get(key);
                    List<Object> patchList = (List<Object>) patchValue;
                    existingList.addAll(patchList);
                }

                else if (existingData.containsKey(key) && existingData.get(key) instanceof Map && patchValue instanceof Map) {
                    Map<String, Object> existingMap = (Map<String, Object>) existingData.get(key);
                    Map<String, Object> patchMap = (Map<String, Object>) patchValue;
                    appendPatchData(existingMap, patchMap);
                }

                else {
                    existingData.put(key, patchValue);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
