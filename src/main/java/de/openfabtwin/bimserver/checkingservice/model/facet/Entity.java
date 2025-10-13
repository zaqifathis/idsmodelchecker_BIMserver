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
        List<IdEObject> applicableEntities = new ArrayList<>();
        for (IdEObject entity : models) {
            String entityName = entity.eClass().getName();
            if (entityName.equalsIgnoreCase(this.name)) {
                List<IdEObject> subTypes = models.getAllWithSubTypes(models.getPackageMetaData().getEClass(entityName));
                List<IdEObject> candidates = subTypes.isEmpty()? List.of(entity) : subTypes;

                //check predefinedType
                if (this.predefinedType != null && this.predefinedType.matches("^[A-Z_]+$")) {
                    for (IdEObject cd : candidates) {
                        Object val = cd.eGet(cd.eClass().getEStructuralFeature("PredefinedType"));
                        if (val != null && Objects.equals(val.toString(), this.predefinedType)) {
                            applicableEntities.add(cd);
                        }
                    }
                }
                applicableEntities.addAll(candidates);
            }
        }
        return applicableEntities;
    }

    @Override
    public FacetType getType(){return FacetType.ENTITY; }


}
