package de.openfabtwin.bimserver.checkingservice.model.facet;


import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Entity extends Facet {
    Logger LOGGER = LoggerFactory.getLogger(Entity.class);

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
    public List<IdEObject> filter(IfcModelInterface models, List<IdEObject> elements) {
        var meta = models.getPackageMetaData();
        var eClass = meta.getEClass(this.name);
        if(eClass == null) throw new IllegalArgumentException("Entity name is not valid: " + this.name);

        List<IdEObject> candidates = models.getAllWithSubTypes(eClass);
        if (candidates.isEmpty()) throw new IllegalArgumentException("No elements found for entity: " + this.name);

        //check predefinedType
        if (this.predefinedType == null) return candidates;
        return resolvePredefinedType(candidates);
    }

    private List<IdEObject> resolvePredefinedType(List<IdEObject> candidates) {

        if (!this.predefinedType.matches("^[A-Z_]+$")) {
            throw new IllegalArgumentException("PredefinedType is not valid: " + this.predefinedType);
        }

        List<IdEObject> filtered = new ArrayList<>();
        for (IdEObject cd : candidates) {
            Object val = cd.eGet(cd.eClass().getEStructuralFeature("PredefinedType"));
            if(val != null && val.toString().equals("USERDEFINED")) {
                if (this.name.endsWith("TYPE")) {
                    Object value = cd.eGet(cd.eClass().getEStructuralFeature("ElementType"));
                    if (Objects.equals(value.toString(), this.predefinedType)) {
                        filtered.add(cd);
                    }
                } else {
                    Object value = cd.eGet(cd.eClass().getEStructuralFeature("ObjectType"));
                    if (Objects.equals(value.toString(), this.predefinedType)) {
                        filtered.add(cd);
                    }
                }
            }
            else if (val != null && val.toString().equals(this.predefinedType)) {
                filtered.add(cd);
            }
        }

        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("No elements found for entity " + this.name + " with PredefinedType " + this.predefinedType);
        }
        return filtered;
    }

    @Override
    public FacetType getType(){return FacetType.ENTITY; }


}
