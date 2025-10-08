package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Specification;
import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Attribute extends Facet {
    private final String name;
    private final ValueOrRestriction value;
    private Specification.Cardinality cardinality;
    private final String instructions;

    public Attribute(String name, ValueOrRestriction value, String cardinality, String instructions){
        this.name = name;
        this.value = value;
        this.cardinality = Specification.cardinalityFromString(cardinality);
        this.instructions = instructions;

        setMinMaxOccurs(this.cardinality);
    }

    @Override
    public FacetType getType() {return FacetType.ATTRIBUTE; }

    @Override
    public List<IdEObject> filter(IfcModelInterface elements, String minOccurs, String maxOccurs) {
        return null;
    }

}
