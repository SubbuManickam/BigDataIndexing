package com.bigdataindexing.restapi.models;

import lombok.Data;

import java.util.List;

@Data
public class Plan {
    PlanCostShares planCostShares;
    List<LinkedPlanServices> linkedPlanServices;
    String _org;
    String objectId;
    String objectType;
    String planType;
    String creationDate;
}
