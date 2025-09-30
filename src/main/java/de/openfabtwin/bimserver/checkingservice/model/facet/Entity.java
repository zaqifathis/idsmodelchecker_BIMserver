package de.openfabtwin.bimserver.checkingservice.model.facet;


import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Entity implements Facet {
    private String name;
    private String predefinedType;
    private String instructions;

    public Entity(String name, String predefinedType, String instructions) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
        this.predefinedType = (predefinedType == null || predefinedType.isBlank()) ? null : predefinedType;
        this.instructions = (instructions == null || instructions.isBlank()) ? null : instructions;
    }

    public String getName() { return name; }
    public String getPredefinedType() { return predefinedType; }
    public String getInstructions() { return instructions; }

    public List<IfcModelInterface> filter (IfcModelInterface elements) {

        return null;
    }

    public String getApplicabilityTemplate() {
        if (predefinedType != null) {
            return "All " + name + " data of type " + predefinedType;
        } else {
            return "All " + name + " data";
        }
    }

    public String getRequirementTemplate() {
        if (predefinedType != null) {
            return "Shall be " + name + " data of type " + predefinedType;
        } else {
            return "Shall be " + name + " data";
        }
    }

    public String getProhibitedTemplate() {
        if (predefinedType != null) {
            return "Shall not be " + name + " data of type " + predefinedType;
        } else {
            return "Shall not be " + name + " data";
        }
    }


}
