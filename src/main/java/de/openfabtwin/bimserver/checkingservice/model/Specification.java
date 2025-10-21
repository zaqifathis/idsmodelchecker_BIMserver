package de.openfabtwin.bimserver.checkingservice.model;

import de.openfabtwin.bimserver.checkingservice.model.facet.Entity;
import de.openfabtwin.bimserver.checkingservice.model.facet.Facet;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static de.openfabtwin.bimserver.checkingservice.model.Specification.Cardinality.*;
import static de.openfabtwin.bimserver.checkingservice.model.Specification.IfcVersion.*;

public class Specification {
    Logger LOGGER = LoggerFactory.getLogger(Specification.class);

    private String name = "Unnamed";
    private final List<IfcVersion> ifcVersion = new ArrayList<>();
    private String identifier, description, instructions;
    private String minOccurs;
    private String maxOccurs;
    private final List<Facet> applicability = new ArrayList<>();
    private final List<Facet> requirements  = new ArrayList<>();

    private List<IdEObject> applicable_entities = new ArrayList<>();
    private List<IdEObject> passed_entities   = new ArrayList<>();
    private List<IdEObject> failed_entities   = new ArrayList<>();
    private Boolean status = null; // null=not checked, true=passed, false=failed
    private Boolean is_ifc_version_supported = null; // null=not checked, true=supported, false=not supported
    private Cardinality cardinality;


    public void reset_status(){
        this.status = null;
        this.applicable_entities.clear();
        this.passed_entities.clear();
        this.failed_entities.clear();
        this.is_ifc_version_supported = null;
    }

    private void check_ifc_version(SProject project) {
        String projectSchema = project.getSchema().toUpperCase();
        if (projectSchema.equals("IFC2X3TC1")) projectSchema = "IFC2X3";
        if (ifcVersion.contains(ifcVersionFromString(projectSchema))) {
            this.is_ifc_version_supported = Boolean.TRUE;
        } else {
            this.is_ifc_version_supported = Boolean.FALSE;
        }
    }

    public void validate(SProject project, IfcModelInterface model) {
        check_ifc_version(project);
        if (this.is_ifc_version_supported == Boolean.FALSE) return;

        // Applicability
        List<Facet> applicability = getApplicability();
        if (applicability.isEmpty()) return;

        Facet facet = applicability.stream().filter(f -> f instanceof Entity)
                .findFirst()
                .orElse(applicability.get(0));

        List<IdEObject> applicable = facet.filter(model, null);

        for (Facet f : applicability){
            if (f == facet) continue;
            applicable = f.filter(model, applicable);
            if(applicable.isEmpty()) break;
        }

        this.applicable_entities = applicable;

        // Requirements

    }


    public String getName() { return name; }
    public List<IfcVersion> getIfcVersion() { return ifcVersion; }
    public String getIdentifier() { return identifier; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public String getMinOccurs() { return minOccurs; }
    public String getMaxOccurs() { return maxOccurs; }
    public List<Facet> getApplicability() { return applicability; }
    public List<Facet> getRequirements() { return requirements; }
    public List<IdEObject> getApplicable_entities() { return applicable_entities; }
    public List<IdEObject> getPassed_entities() { return passed_entities; }
    public List<IdEObject> getFailed_entities() { return failed_entities; }
    public Boolean getStatus() { return status; }
    public Boolean getIs_ifc_version_supported() { return is_ifc_version_supported; }
    public Cardinality getCardinality() { return cardinality; }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
    }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setDescription(String description) { this.description = description; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setMinOccurs(String minOccurs) { this.minOccurs = minOccurs; }
    public void setMaxOccurs(String maxOccurs) { this.maxOccurs = maxOccurs; }
    public void setCardinality(String cardinality) { this.cardinality = cardinalityFromString(cardinality); }
    public void setApplicable_entities(List<IdEObject> applicable_entities) { this.applicable_entities = applicable_entities; }
    public void setPassed_entities(List<IdEObject> passed_entities){this.passed_entities = passed_entities; }
    public void setFailed_entities(List<IdEObject> failed_entities) {this.failed_entities = failed_entities; }

    public enum IfcVersion {IFC2X3, IFC4, IFC4X3_ADD2 }
    public enum Cardinality {REQUIRED, OPTIONAL, PROHIBITED}

    public static IfcVersion ifcVersionFromString(String s) {
        return switch (s.trim().toUpperCase()) {
            case "IFC2X3" -> IFC2X3;
            case "IFC4" -> IFC4;
//            case "IFC4X3_ADD2" -> IFC4X3_ADD2;
            default -> throw new IllegalArgumentException("Only accept IFC2X3TC1 and IFC4. IFC version: " + s);
        };
    }

    public static Cardinality cardinalityFromString(String s) {
        if (s == null || s.isBlank()) {
            return REQUIRED;
        }
        return switch (s.trim().toLowerCase()) {
            case "required" -> REQUIRED;
            case "optional" -> OPTIONAL;
            case "prohibited" -> PROHIBITED;
            default -> throw new IllegalArgumentException("Unknown cardinality: " + s);
        };
    }


}



