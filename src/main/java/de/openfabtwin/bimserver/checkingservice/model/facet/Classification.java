package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Classification extends Facet {
    private final Value system;
    private final Value value;
    private final String uri;
    private final Cardinality cardinality;
    private final String instructions;

    public Classification(Value system, Value value, String uri, String cardinality, String instructions){
        this.system = system;
        this.value = value;
        this.uri = uri;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if(system != null && value == null) {
            this.applicability_templates = "Data classified using " + system.extract();
            this.requirement_templates = "Shall be classified using " + system.extract();
            this.prohibited_templates = "Shall not be classified using " + system.extract();
        } else if (system == null && value != null){
            this.applicability_templates = "Data classified as " + value.extract();
            this.requirement_templates = "Shall be classified as " + value.extract();
            this.prohibited_templates = "Shall not be classified as " + value.extract();
        } else if (system != null && value != null){
            this.applicability_templates = "Data having a " + system.extract() + " reference of " + value.extract();
            this.requirement_templates = "Shall have a " + system.extract() + " reference of " + value.extract();
            this.prohibited_templates = "Shall not have a " + system.extract() + " reference of " + value.extract();
        }
    }



    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        EClass racClass = (EClass) model.getPackageMetaData().getEClassifier("IfcRelAssociatesClassification");
        if (racClass == null) return results;

        for (IdEObject rel : model.getAll(racClass)) {
            List<IdEObject> related = (List<IdEObject>) rel.eGet(racClass.getEStructuralFeature("RelatedObjects"));
            if(related == null) continue;
            for (IdEObject obj : related) {
                if(seen.add(obj.getOid())) results.add(obj);
            }
        }
        return results;
    }

    @Override
    public Result matches(IdEObject element) {

        return null;
    }
}
