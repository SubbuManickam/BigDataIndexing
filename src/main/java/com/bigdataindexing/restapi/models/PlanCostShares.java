package com.bigdataindexing.restapi.models;

import lombok.Data;

@Data
public class PlanCostShares {
    Integer deductible;
    String _org;
    Integer copay;
    String objectId;
    String objectType;
}
