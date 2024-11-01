package com.bigdataindexing.restapi.service;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class JsonValidatorService {
    private final Schema schema;

    public JsonValidatorService() {
        String schemaJson = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "planCostShares": {
                      "type": "object",
                      "properties": {
                        "deductible": { "type": "number" },
                        "_org": { "type": "string" },
                        "copay": { "type": "number" },
                        "objectId": { "type": "string" },
                        "objectType": { "type": "string" }
                      },
                      "required": ["deductible", "_org", "copay", "objectId", "objectType"]
                    },
                    "linkedPlanServices": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "linkedService": {
                            "type": "object",
                            "properties": {
                              "_org": { "type": "string" },
                              "objectId": { "type": "string" },
                              "objectType": { "type": "string" },
                              "name": { "type": "string" }
                            },
                            "required": ["_org", "objectId", "objectType", "name"]
                          },
                          "planserviceCostShares": {
                            "type": "object",
                            "properties": {
                              "deductible": { "type": "number" },
                              "_org": { "type": "string" },
                              "copay": { "type": "number" },
                              "objectId": { "type": "string" },
                              "objectType": { "type": "string" }
                            },
                            "required": ["deductible", "_org", "copay", "objectId", "objectType"]
                          },
                          "_org": { "type": "string" },
                          "objectId": { "type": "string" },
                          "objectType": { "type": "string" }
                        },
                        "required": ["linkedService", "planserviceCostShares", "_org", "objectId", "objectType"]
                      }
                    },
                    "_org": { "type": "string" },
                    "objectId": { "type": "string" },
                    "objectType": { "type": "string" },
                    "planType": { "type": "string" },
                    "creationDate": { "type": "string" }
                  },
                  "required": ["planCostShares", "linkedPlanServices", "_org", "objectId", "objectType", "planType", "creationDate"]
                }""";
        JSONObject rawSchema = new JSONObject(new JSONTokener(schemaJson));
        this.schema = SchemaLoader.load(rawSchema);
    }

    public boolean validate(String jsonPayload, Set<String> errors) {
        try {
            JSONObject jsonObject = new JSONObject(jsonPayload);
            schema.validate(jsonObject);
            return true;
        } catch (ValidationException e) {
            List<String> errorMessages = e.getAllMessages();
            errorMessages.forEach(System.out::println);
            errors.add(e.getMessage());
            return false;
        }
    }
}
