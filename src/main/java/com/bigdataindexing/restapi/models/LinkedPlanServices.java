package com.bigdataindexing.restapi.models;

import lombok.Data;

@Data
public class LinkedPlanServices {
    LinkedService linkedService;
    PlanserviceCostShares planserviceCostShares;
    String _org;
    String objectId;
    String objectType;
}
