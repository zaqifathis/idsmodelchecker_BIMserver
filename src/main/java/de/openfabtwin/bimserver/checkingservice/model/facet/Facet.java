package de.openfabtwin.bimserver.checkingservice.model.facet;
import de.openfabtwin.bimserver.checkingservice.model.RestrictionValue;
import de.openfabtwin.bimserver.checkingservice.model.SimpleValue;
import de.openfabtwin.bimserver.checkingservice.model.Specification.Cardinality;
import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.ArrayList;
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

    protected static List<String> extractValue(Value name) {
        if (name instanceof SimpleValue sv) return List.of(sv.value());
        if (name instanceof RestrictionValue rv) return rv.enums();
        return List.of();
    }

    public List<IdEObject> filter(IfcModelInterface model, List<IdEObject> elements) {
        if (elements != null && !elements.isEmpty()) {
            List<IdEObject> candidate = new ArrayList<>();
            for (IdEObject el : elements){
               if (matches(model,el)) candidate.add(el);
            }
            return candidate;
        }
        return discover(model);
    }
    protected abstract List<IdEObject> discover(IfcModelInterface model);
    protected abstract boolean matches(IfcModelInterface models, IdEObject element);
    public abstract FacetType getType();

}


