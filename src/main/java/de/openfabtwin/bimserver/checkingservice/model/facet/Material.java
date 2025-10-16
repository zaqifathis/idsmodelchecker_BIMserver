package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Material extends Facet {

    public Material(Value value, String uri, String cardinality, String instructions) {}

    @Override
    public FacetType getType(){return FacetType.MATERIAL; }

    @Override
    public List<IdEObject> filter(IfcModelInterface models, List<IdEObject> elements) {
        return null;
    }
}
