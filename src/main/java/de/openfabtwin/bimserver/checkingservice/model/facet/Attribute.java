package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Attribute extends Facet {

    public Attribute(String name, ValueOrRestriction value, String cardinality, String instructions){

    }

    @Override
    public FacetType getType() {return FacetType.ATTRIBUTE; }

    @Override
    public List<IfcModelInterface> filter(IfcModelInterface elements) {
        return null;
    }
}
