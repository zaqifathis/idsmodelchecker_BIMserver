package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.AttributeResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.lang.reflect.Array;
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
            this.applicability_templates = "Data where the " + name.extract() + " is " + value.extract();
            this.requirement_templates = "The " + name.extract() + " shall be " + value.extract();
            this.prohibited_templates = "The " + name.extract() + " shall not be " + value.extract();
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

        List<EAttribute> features = new ArrayList<>();
        for (EAttribute a : element.eClass().getEAllAttributes()) {
            if (name.matches(a.getName())) {
                features.add(a);
            }
        }

        return switch (this.cardinality) {
            case REQUIRED    -> evalRequired(element, features);
            case OPTIONAL    -> evalOptional(element, features);
            case PROHIBITED  -> evalProhibited(element, features);
        };
    }

    private Result evalProhibited(IdEObject element, List<EAttribute> attributes) {
        if(attributes.isEmpty()) {
            return pass();
        } else {
            for (EAttribute attr: attributes) {
                Object raw = element.eGet(attr);
                if(isPresent(raw, attr)) return fail("PROHIBITED");
            }
            return fail("PROHIBITED");
        }
    }

    private Result evalOptional(IdEObject element, List<EAttribute> attributes) {
        if(attributes.isEmpty()) return pass();

        for (EAttribute attr: attributes) {
            Object raw = element.eGet(attr);
            if(!isPresent(raw, attr)) continue;
            if (!isActualValue(raw)) return fail(Map.of(
                    "type", "FALSEY",
                    "actual", raw.toString()
            ));
            if (this.value != null && !value.matches(raw.toString().trim())) {
                return fail(Map.of(
                        "type", "VALUE",
                        "actual", raw.toString()
                ));
            }
        }
        return pass();
    }

    private Result evalRequired(IdEObject element, List<EAttribute> attributes) {
        if (attributes.isEmpty()) return fail("NOVALUE");

        for(EAttribute attr : attributes) {
            Object raw = element.eGet(attr);
            if(!isPresent(raw, attr)) return fail("NOVALUE");
            if (!isActualValue(raw)) return fail(Map.of(
                    "type", "FALSEY",
                    "actual", raw.toString()
            ));
            if (this.value != null && !value.matches(raw.toString().trim())) {
                return fail(Map.of(
                        "type", "VALUE",
                        "actual", raw.toString()
                ));
            }
        }
        return pass();
    }

    private boolean isPresent(Object raw, EAttribute attr) {
        if (raw == null) return false;

        if(attr.isMany()) {
            List<Object> list = (List<Object>) raw;
            return !list.isEmpty();
        }
        return true;
    }

    private boolean isActualValue(Object raw) {
        if (raw instanceof IdEObject) return false;
        if (raw instanceof CharSequence cs) {
            return !cs.toString().trim().isEmpty();
        }
        if (raw instanceof Enum<?> en) {
            return !"UNKNOWN".equalsIgnoreCase(en.name());
        }
        if (raw instanceof Collection<?> col) {
            return !col.isEmpty();
        }
        if (raw.getClass().isArray()) {
            return Array.getLength(raw) > 0;
        }
        return true;
    }

    private static AttributeResult pass() { return new AttributeResult(true, null); }
    private static AttributeResult fail(String type) { return new AttributeResult(false, Map.of("type", type)); }
    private static AttributeResult fail(Map<String, Object> reason) {
        return new AttributeResult(false, reason);
    }

}
