package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Classification extends Facet {

    public Classification(Value system, Value value, String uri, String cardinality, String instructions){

    }

    @Override
    public FacetType getType(){return FacetType.CLASSIFICATION; }

    @Override
    protected List<IdEObject> discover(IfcModelInterface model) {
        return List.of();
    }

    @Override
    protected boolean matches(IfcModelInterface models, IdEObject element) {
        return false;
    }
}
