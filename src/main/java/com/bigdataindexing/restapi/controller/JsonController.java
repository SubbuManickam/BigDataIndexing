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

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping(value = "/api/json")
public class JsonController {
    @Autowired
    RedisService redisService;
    @Autowired
    JsonValidatorService jsonValidatorService;
    @Autowired
    ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity createPlan(@RequestBody Plan plan) {
        Set<String> errors = new HashSet<>();
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Initialization Error");
        }
        if(!jsonValidatorService.validate(jsonPayload, errors)) {
            return ResponseEntity.badRequest().body(errors);
        }
        if(redisService.exists(plan.getObjectId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Plan ID already exists");
        }
        redisService.save(plan.getObjectId(), jsonPayload);
        String etag = redisService.generateETag(jsonPayload);
        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", etag);
        return new ResponseEntity<>(plan, headers, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity getPlan(@PathVariable String id, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        String jsonPayload = redisService.get(id);
        if (jsonPayload == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Plan not found");
        }

        String etag = redisService.generateETag(jsonPayload);
        if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }

        Plan plan;
        try {
            plan = objectMapper.readValue(jsonPayload, Plan.class);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading plan");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("ETag", etag);
        return new ResponseEntity<>(plan, headers, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity deletePlan(@PathVariable String id) {
        if (!redisService.exists(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Plan not found");
        }
        redisService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
