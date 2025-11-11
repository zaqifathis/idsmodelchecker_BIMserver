package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PartOf extends Facet {

    private final Value name;
    private final Value predefinedType;
    private final String relation;
    private final String instructions;

    public PartOf(Value name, Value predefinedType, String relation, String cardinality, String instructions){
        this.name = name;
        this.predefinedType = predefinedType;
        this.relation = relation;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (name != null && predefinedType != null) {
            this.requirement_templates = "An element must have an " + relation + " relationship with an " + name.extract() + " of predefined type " + predefinedType.extract();
        }
        else if (name != null){
            this.applicability_templates = "An element with an " + relation + " relationship with an " + name.extract();
            this.requirement_templates = "An element must have an " + relation + " relationship with an " + name.extract();
            this.prohibited_templates = "An element must not have an " + relation + " relationship with an " + name.extract();
        } else {
            this.applicability_templates = "An element with an " + relation + " relationship";
            this.requirement_templates = "An element must have an " + relation + " relationship";
            this.prohibited_templates = "An element must not have an " + relation + " relationship";
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) { //lazy
        List<IdEObject> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        var meta = model.getPackageMetaData();
        var epkg = meta.getEPackage();
        for (EClassifier c : epkg.getEClassifiers()) {
            if (c instanceof EClass ec) {
                for (IdEObject e : model.getAllWithSubTypes(ec)) {
                    if(seen.add(e.getOid())) candidates.add(e);
                }
            }
        }
        return candidates;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {

        return null;
    }


}
