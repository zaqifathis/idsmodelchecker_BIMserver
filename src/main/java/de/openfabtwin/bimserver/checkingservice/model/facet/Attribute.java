package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.SimpleValue;
import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

public class Attribute extends Facet {
    private final Value name;
    private final Value value;
    private Cardinality cardinality;
    private final String instructions;

    public Attribute(Value name, Value value, String cardinality, String instructions){
        this.name = name;
        this.value = value;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (value == null) {
            this.applicability_templates = "Data where the " + name.extract() + " is provided";
            this.requirement_templates = "The " + name.extract() + " shall be provided";
            this.prohibited_templates = "The " + name.extract() + " shall not be provided";
        } else {
            this.applicability_templates = "Data where the " + name + " is " + value.extract();
            this.requirement_templates = "The " + name + " shall be " + value.extract();
            this.prohibited_templates = "The " + name + " shall not be " + value.extract();
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> candidates = new ArrayList<>();
        var meta = model.getPackageMetaData();
        var epkg = meta.getEPackage();

        Set<Long> seen = new HashSet<>();
        for (EClassifier c : epkg.getEClassifiers()) {
            if (!(c instanceof EClass ec)) continue;

            boolean declaresMatch = ec.getEAttributes()
                    .stream()
                    .map((EAttribute f) -> f.getName())
                    .anyMatch(attrName  -> name.matches(attrName ));

            if (declaresMatch) {
                for (IdEObject e : model.getAllWithSubTypes(ec)) {
                    if (seen.add(e.getOid())) candidates.add(e);
                }
            }
        }
        if (this.value == null) return candidates;

        //if it has value, name always SimpleValue
        List<IdEObject> result = new ArrayList<>();
        String attrName = name.extract();

        for (IdEObject candidate : candidates) {
            Object attrValue = candidate.eGet(candidate.eClass().getEStructuralFeature(attrName));
            if (attrValue != null) {
                String attrValueStr = attrValue.toString();
                if (value.matches(attrValueStr)) {
                    result.add(candidate);
                }
            }
        }

        return result;
    }

    @Override
    public Result matches(IdEObject element) {

        return null;
    }

}
