package de.openfabtwin.bimserver.checkingservice.model.facet;


import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Entity extends Facet {
    private final String name;
    private final String predefinedType;
    private final String instructions;


    public Entity(String name, String predefinedType, String instructions) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
        this.predefinedType = (predefinedType == null || predefinedType.isBlank()) ? null : predefinedType;
        this.instructions = (instructions == null || instructions.isBlank()) ? null : instructions;
        this.applicabilityTemplate = predefinedType != null ? "All " + name + " data of type " + predefinedType : "All " + name + " data";
        this.requirementTemplate = predefinedType != null ? "Shall be " + name + " data of type " + predefinedType : "Shall be " + name + " data";
        this.prohibitedTemplate = predefinedType != null ? "Shall not be " + name + " data of type " + predefinedType : "Shall not be " + name + " data";
    }

    public String getName() { return name; }
    public String getPredefinedType() { return predefinedType; }
    public String getInstructions() { return instructions; }
    public String getApplicabilityTemplate() {return this.applicabilityTemplate; }
    public String getRequirementTemplate() {return this.requirementTemplate; }
    public String getProhibitedTemplate() {return this.prohibitedTemplate; }

    @Override
    public List<IdEObject> filter(IfcModelInterface elements, String minOccurs, String maxOccurs) {
        if (this.predefinedType == null) {
            for (IdEObject element : elements) {

            }
        }
        return null;
    }

    @Override
    public FacetType getType(){return FacetType.ENTITY; }


}
