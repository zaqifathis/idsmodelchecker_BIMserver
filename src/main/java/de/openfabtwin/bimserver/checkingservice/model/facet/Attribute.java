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

            for (EAttribute eAttr : ec.getEAttributes()) {
                if (!name.matches(eAttr.getName())) continue;
                for (IdEObject inst : model.getAllWithSubTypes(ec)) {
                    Object raw = inst.eGet(eAttr);
                    if (raw != null) {
                        if (!seen.add(inst.getOid())) candidates.add(inst);
                    }
                }
                break;
            }
        }
        // future: consider check value as well
        return candidates;
    }

    @Override
    public Result matches(IdEObject element) {

        return null;
    }

}
