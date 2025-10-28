package de.openfabtwin.bimserver.checkingservice.model;

import de.openfabtwin.bimserver.checkingservice.model.facet.Entity;
import de.openfabtwin.bimserver.checkingservice.model.facet.Facet;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

    private final List<IdEObject> applicable_entities = new ArrayList<>();
    private final List<IdEObject> passed_entities   = new ArrayList<>();
    private final List<IdEObject> failed_entities   = new ArrayList<>();
    private Boolean status = null;
    private Boolean is_ifc_version_supported = null;

    private boolean check_ifc_version(SProject project) {
        String projectSchema = project.getSchema().toUpperCase();
        if (projectSchema.equals("IFC2X3TC1")) projectSchema = "IFC2X3";
        this.is_ifc_version_supported = ifcVersion.contains(ifcVersionFromString(projectSchema));
        return this.is_ifc_version_supported;
    }

    public void validate(SProject project, IfcModelInterface model) {
        if(!check_ifc_version(project)) return;

        // Applicability
        if (this.applicability.isEmpty()) return;
        Facet facet = this.applicability.stream().filter(f -> f instanceof Entity)
                .findFirst()
                .orElse(this.applicability.get(0));

        List<IdEObject> elements = facet.filter(model);

        for (IdEObject element : elements) {
            boolean isApplicable = true;
            for (Facet f : this.applicability) {
                if (f == facet) continue;
                if (!f.matches(element).isPass()) {
                    isApplicable = false;
                    break;
                }
            }
            if (!isApplicable) continue;
            this.applicable_entities.add(element);
            for(Facet f : this.requirements) {
                Result result = f.matches(element);
                boolean is_pass = result.isPass();
                if (!"0".equals(this.maxOccurs)) { //required or optional
                    if (is_pass) {
                        this.passed_entities.add(element);
                        f.addPassedEntities(element);
                    } else {
                        this.failed_entities.add(element);
                        f.addFailures(element, result.to_String());
                    }
                } else { //prohibited
                    if (is_pass) {
                        this.failed_entities.add(element);
                        f.addFailures(element, result.to_String());
                    } else {
                        this.passed_entities.add(element);
                        f.addPassedEntities(element);
                    }
                }
            }

            this.status = true;
            for (Facet f : this.requirements){
                f.setStatus(f.getFailures().isEmpty());
                if (!f.isStatus()) this.status = false;
            }

            if (!"0".equals(this.minOccurs)) { //required specification
                if (this.applicable_entities.isEmpty()) {
                    this.status = false;
                    for (Facet f : this.requirements) {
                        f.setStatus(false);
                    }
                }
            } else if ("0".equals(this.maxOccurs)) { //prohibited specification
                if (!this.applicable_entities.isEmpty() && this.requirements.isEmpty()) this.status = false;
            }
        }
        LOGGER.info("Specification '{}' validated. Status: {}", this.name, this.status ? "PASS" : "FAIL");

    }


    public String getName() { return name; }
    public List<IfcVersion> getIfcVersion() { return ifcVersion; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public List<Facet> getApplicability() { return applicability; }
    public List<Facet> getRequirements() { return requirements; }
    public List<IdEObject> getApplicable_entities() { return applicable_entities; }
    public List<IdEObject> getPassed_entities() { return passed_entities; }
    public List<IdEObject> getFailed_entities() { return failed_entities; }
    public boolean getStatus() { return status; }
    public boolean getIs_ifc_version_supported() { return is_ifc_version_supported; }
    public String getMinOccurs() { return minOccurs; }
    public String getMaxOccurs() { return maxOccurs; }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
    }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setDescription(String description) { this.description = description; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setMinOccurs(String minOccurs) { this.minOccurs = minOccurs; }
    public void setMaxOccurs(String maxOccurs) { this.maxOccurs = maxOccurs; }

    public enum IfcVersion {IFC2X3, IFC4, IFC4X3_ADD2 }

    public static IfcVersion ifcVersionFromString(String s) {
        return switch (s.trim().toUpperCase()) {
            case "IFC2X3" -> IFC2X3;
            case "IFC4" -> IFC4;
//            case "IFC4X3_ADD2" -> IFC4X3_ADD2;
            default -> throw new IllegalArgumentException("Only accept IFC2X3TC1 and IFC4. IFC version: " + s);
        };
    }

}



