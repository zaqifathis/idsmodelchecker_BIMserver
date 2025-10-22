package de.openfabtwin.bimserver.checkingservice.report;

import de.openfabtwin.bimserver.checkingservice.model.Ids;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Results {
    private String title;
    private Date date;
    private String fileName;
    private boolean status;
    private List<ResultSpecification> specifications;
    private int totalSpecifications;
    private int totalSpecifications_passed;
    private int totalSpecifications_failed;
    private int totalRequirements;
    private int totalRequirements_passed;
    private int totalRequirements_failed;
    private int totalChecks;
    private int totalChecks_passed;
    private int totalChecks_failed;

    public Results(Ids ids) {

    }

    public String getTitle() { return title; }
    public Date getDate() { return date; }
    public String getFileName() { return fileName; }
    public boolean isStatus() { return status; }
    public List<ResultSpecification> getSpecifications() { return specifications; }
    public int getTotalSpecifications() {
        return totalSpecifications;
    }
    public int getTotalSpecifications_passed() {
        return totalSpecifications_passed;
    }
    public int getTotalSpecifications_failed() {
        return totalSpecifications_failed;
    }
    public int getTotalRequirements() {
        return totalRequirements;
    }
    public int getTotalRequirements_passed() {
        return totalRequirements_passed;
    }
    public int getTotalRequirements_failed() {
        return totalRequirements_failed;
    }
    public int getTotalChecks() {
        return totalChecks;
    }
    public int getTotalChecks_passed() {
        return totalChecks_passed;
    }
    public int getTotalChecks_failed() {
        return totalChecks_failed;
    }
}
