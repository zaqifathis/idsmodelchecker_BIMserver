package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Property extends Facet {

    public Property(String pset, String baseName, ValueOrRestriction value, String dataType, String uri, String cardinality, String instructions){}

    @Override
    public FacetType getType(){return FacetType.PROPERTY; }

    @Override
    public List<IdEObject> filter(IfcModelInterface elements, String minOccurs, String maxOccurs) {
        return null;
    }
}
