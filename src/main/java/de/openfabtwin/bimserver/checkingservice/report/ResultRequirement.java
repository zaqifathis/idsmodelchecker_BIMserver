package de.openfabtwin.bimserver.checkingservice.report;

import java.util.List;

public class ResultRequirement {
    private String facetType;
    private String label;
    private String value;
    private String description;
    private boolean status;
    private List<ResultEntity> failedEntities;
    private List<ResultEntity> passedEntities;
    private int totalApplicableEntities;
    private int total_pass;
    private int total_fail;

}
