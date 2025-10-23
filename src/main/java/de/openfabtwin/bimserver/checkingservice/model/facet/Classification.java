package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Classification extends Facet {

    public Classification(Value system, Value value, String uri, String cardinality, String instructions){

    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        return List.of();
    }

    @Override
    public Result matches(IdEObject element) {
        return null;
    }
}
