package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.PackageMetaData;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.*;

public class Attribute extends Facet {
    private final List<String> name;
    private final List<String> value;
    private Cardinality cardinality;
    private final String instructions;
    private final String applicability_templates;
    private final String requirement_templates;
    private final String prohibited_templates;

    public Attribute(Value name, Value value, String cardinality, String instructions){
        this.name = extractValue(name, false);
        this.value = extractValue(value, false);
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        boolean hasValue = !this.value.isEmpty();
        String namePart = joinValues(this.name);
        String valuePart = joinValues(this.value);

        if (hasValue) {
            this.applicability_templates = "Data where the " + namePart + " is provided";
            this.requirement_templates =  "The " + namePart + " shall be provided";
            this.prohibited_templates =  "The " + namePart + " shall not be provided";
        } else {
            this.applicability_templates =  "Data where the " + namePart + " is " + valuePart;
            this.requirement_templates =  "The " + namePart + " shall be " + valuePart;
            this.prohibited_templates = "The " + namePart + " shall not be " + valuePart;
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        if (this.name == null || this.name.isEmpty()) return List.of();

        PackageMetaData meta = model.getPackageMetaData();
        EPackage ePkg = meta.getEPackage();

        List<IdEObject> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (String attrName : this.name) {
            List<EClass> definingClasses = new ArrayList<>();
            for (EClassifier classifier : ePkg.getEClassifiers()) {
                if (!(classifier instanceof EClass eClass)) continue;

                EStructuralFeature feature = eClass.getEStructuralFeature(attrName);
                if (feature == null) continue;

                if (feature.getEContainingClass() == eClass) {
                    definingClasses.add(eClass);
                }
            }

            for (EClass eClass : definingClasses) {
                List<IdEObject> instances = model.getAllWithSubTypes(eClass);
                if (instances.isEmpty()) continue;

                for (IdEObject instance : instances) {
                    EStructuralFeature f = instance.eClass().getEStructuralFeature(attrName);
                    if (f == null) continue;
                    Object val = instance.eGet(f);

                    boolean isEmpty =
                            (val == null) ||
                            (val instanceof String str && str.isBlank()) ||
                            (val instanceof Collection<?> coll && coll.isEmpty());

                    if (!isEmpty && seen.add(instance.getOid())) {
                        result.add(instance);
                    }
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
