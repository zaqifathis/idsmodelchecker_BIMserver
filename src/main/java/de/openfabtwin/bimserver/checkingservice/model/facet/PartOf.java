package de.openfabtwin.bimserver.checkingservice.model.facet;

import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class PartOf extends Facet {

    public PartOf(String name, String predefinedType, String relation, String cardinality, String instructions){}

    @Override
    public FacetType getType(){return FacetType.PARTOF; }

    @Override
    public List<IdEObject> filter(IfcModelInterface elements, String minOccurs, String maxOccurs) {
        return null;
    }
}
