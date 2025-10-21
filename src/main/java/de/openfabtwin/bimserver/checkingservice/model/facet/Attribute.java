package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Specification;
import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Attribute extends Facet {
    private final Value name;
    private final Value value;
    private Specification.Cardinality cardinality;
    private final String instructions;

    public Attribute(Value name, Value value, String cardinality, String instructions){
        this.name = name;
        this.value = value;
        this.cardinality = Specification.cardinalityFromString(cardinality);
        this.instructions = instructions;

        setMinMaxOccursReq(this.cardinality);
    }

    @Override
    public FacetType getType() {return FacetType.ATTRIBUTE; }

    @Override
    protected List<IdEObject> discover(IfcModelInterface model) {
        return List.of();
    }

    @Override
    protected boolean matches(IfcModelInterface models, IdEObject element) {
        return false;
    }


}
