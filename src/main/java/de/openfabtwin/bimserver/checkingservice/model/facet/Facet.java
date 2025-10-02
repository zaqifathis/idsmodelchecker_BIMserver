package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Specification.Cardinality;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public abstract class Facet {
    protected Cardinality cardinality = Cardinality.REQUIRED;
    protected Boolean status = null;
    protected String applicabilityTemplate;
    protected String requirementTemplate;
    protected String prohibitedTemplate;

    public enum FacetType {ENTITY, ATTRIBUTE, CLASSIFICATION, PROPERTY, PARTOF, MATERIAL}

    public abstract FacetType getType();
    public abstract List<IfcModelInterface> filter(IfcModelInterface elements);
}


