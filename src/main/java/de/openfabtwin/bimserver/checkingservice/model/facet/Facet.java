package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Specification.Cardinality;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public abstract class Facet {
    protected Cardinality cardinality = Cardinality.REQUIRED;
    protected Boolean status = null;
    protected String applicabilityTemplate;
    protected String requirementTemplate;
    protected String prohibitedTemplate;
    protected String minOccurs = "1";
    protected String maxOccurs = "unbounded";

    public enum FacetType {ENTITY, ATTRIBUTE, CLASSIFICATION, PROPERTY, PARTOF, MATERIAL}

    public void setMinMaxOccurs(Cardinality cardinality) {
        this.cardinality = cardinality;
        switch (cardinality) {
            case REQUIRED -> {
                this.minOccurs = "1";
                this.maxOccurs = "unbounded";
            }
            case OPTIONAL -> {
                this.minOccurs = "0";
                this.maxOccurs = "unbounded";
            }
            case PROHIBITED -> {
                this.minOccurs = "0";
                this.maxOccurs = "0";
            }
            default -> {
                this.minOccurs = "1";
                this.maxOccurs = "unbounded";
            }
        }
    }

    public abstract FacetType getType();
    public abstract List<IdEObject> filter(IfcModelInterface elements, String minOccurs, String maxOccurs);
}


