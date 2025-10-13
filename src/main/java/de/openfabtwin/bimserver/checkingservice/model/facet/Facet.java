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
    protected String minOccursReq = "1";
    protected String maxOccursReq = "unbounded";

    public enum FacetType {ENTITY, ATTRIBUTE, CLASSIFICATION, PROPERTY, PARTOF, MATERIAL}

    public void setMinMaxOccursReq(Cardinality cardinality) {
        this.cardinality = cardinality;
        switch (cardinality) {
            case REQUIRED -> {
                this.minOccursReq = "1";
                this.maxOccursReq = "unbounded";
            }
            case OPTIONAL -> {
                this.minOccursReq = "0";
                this.maxOccursReq = "unbounded";
            }
            case PROHIBITED -> {
                this.minOccursReq = "0";
                this.maxOccursReq = "0";
            }
        }
    }

    public abstract FacetType getType();
    public abstract List<IdEObject> filter(IfcModelInterface models, List<IdEObject> elements);
}


