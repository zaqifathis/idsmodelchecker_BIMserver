package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class PartOf extends Facet {

    public PartOf(Value name, Value predefinedType, String relation, String cardinality, String instructions){}

    @Override
    public FacetType getType(){return FacetType.PARTOF; }

    @Override
    protected List<IdEObject> discover(IfcModelInterface model) {
        return List.of();
    }

    @Override
    protected boolean matches(IfcModelInterface models, IdEObject element) {
        return false;
    }


}
