package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Property extends Facet {

    public Property(Value pset, Value baseName, Value value, String dataType, String uri, String cardinality, String instructions){}

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        return List.of();
    }

    @Override
    public Result matches(IdEObject element) {
        return null;
    }


}
