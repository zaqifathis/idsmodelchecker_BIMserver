package de.openfabtwin.bimserver.checkingservice.report;

import de.openfabtwin.bimserver.checkingservice.model.Specification;

import java.util.List;

public class ResultSpecification {
    private final String name;
    private final String description;
    private final String identifier;
    private final String instructions;
    private final Boolean status;
    private final Boolean is_ifc_version;
    private final int total_applicable;
    private final int total_applicable_passed;
    private final int total_applicable_failed;
    private int total_checks;
    private int total_checks_passed;
    private int total_checks_failed;
    private String cardinality;
    private List<String> applicability;
    private List<ResultRequirement> requirements;

    public ResultSpecification(Specification specification) {
        this.name = specification.getName();
        this.description = specification.getDescription();
        this.identifier = specification.getIdentifier();
        this.instructions = specification.getInstructions();
        this.status = specification.getStatus();
        this.is_ifc_version = specification.getIs_ifc_version_supported();
        this.total_applicable = specification.getApplicable_entities().size();
        this.total_applicable_passed = specification.getPassed_entities().size();
        this.total_applicable_failed = specification.getFailed_entities().size();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIdentifier() { return identifier; }
    public String getInstructions() { return instructions; }
    public Boolean getStatus() { return status; }
    public Boolean getIs_ifc_version() { return is_ifc_version; }
    public int getTotal_applicable() { return total_applicable; }
    public int getTotal_applicable_passed() { return total_applicable_passed; }
    public int getTotal_applicable_failed() { return total_applicable_failed; }
    public int getTotal_checks() { return total_checks; }
    public int getTotal_checks_passed() { return total_checks_passed; }
    public int getTotal_checks_failed() { return total_checks_failed; }
    public String getCardinality() { return cardinality; }
    public List<String> getApplicability() { return applicability; }
    public List<ResultRequirement> getRequirements() { return requirements; }
}
