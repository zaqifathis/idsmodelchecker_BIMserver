package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.AttributeResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

public class Attribute extends Facet {
    Logger LOGGER = LoggerFactory.getLogger(Attribute.class);

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

            for (EStructuralFeature eAttr : ec.getEAllStructuralFeatures()) {
                if (!name.matches(eAttr.getName())) continue;

                if (eAttr instanceof EReference ref) { // keep only forward features
                    if (ref.isDerived()) continue;
                }

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

        List<EStructuralFeature> features = new ArrayList<>();

        for (EStructuralFeature f : element.eClass().getEAllStructuralFeatures()) {
            if (!name.matches(f.getName())) continue;

            if (f instanceof EReference ref) { // keep only forward features
                if (ref.isDerived()) continue;
            }
            features.add(f);
        }

        return switch (this.cardinality) {
            case REQUIRED    -> evalRequired(element, features);
            case OPTIONAL    -> evalOptional(element, features);
            case PROHIBITED  -> evalProhibited(element, features);
        };
    }

    private Result evalProhibited(IdEObject element, List<EStructuralFeature> attributes) {
        if(attributes.isEmpty()) {
            return pass();
        } else {
            for (EStructuralFeature attr: attributes) {
                Object raw = element.eGet(attr);
                if(isPresent(raw, attr)) return fail("PROHIBITED");
            }
            return fail("PROHIBITED");
        }
    }

    private Result evalOptional(IdEObject element, List<EStructuralFeature> attributes) {
        if(attributes.isEmpty()) return pass();

        for (EStructuralFeature attr: attributes) {
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

    private Result evalRequired(IdEObject element, List<EStructuralFeature> attributes) {
        if (attributes.isEmpty()) return fail("NOVALUE");

        for(EStructuralFeature attr : attributes) {
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

    private boolean isPresent(Object raw, EStructuralFeature attr) {
        if (raw == null) return false;

        if(attr.isMany()) {
            List<Object> list = (List<Object>) raw;
            return !list.isEmpty();
        }
        return true;
    }

    private boolean isActualValue(Object raw) {
        if (raw instanceof IdEObject) return true;

        if (raw instanceof CharSequence cs) {
            String s = cs.toString().trim();
            if (s.isEmpty()) return false;
            return true;
        }

        if (raw instanceof Enum<?> en) {
            String n = en.name();
            if ("UNKNOWN".equalsIgnoreCase(n) || "UNDEFINED".equalsIgnoreCase(n)) return false;
            return true;
        }

        try {
            var getName = raw.getClass().getMethod("getName");
            Object n = getName.invoke(raw);
            if (n instanceof String s && (s.equalsIgnoreCase("UNKNOWN") || s.equalsIgnoreCase("UNDEFINED")))
                return false;
        } catch (Exception ignored) { }

        String str = raw.toString().trim();
        if (str.equalsIgnoreCase("UNKNOWN") || str.equalsIgnoreCase("UNDEFINED")) return false;
        if (raw instanceof Collection<?> col) return !col.isEmpty();
        if (raw.getClass().isArray()) return Array.getLength(raw) > 0;
        return true;
    }

    private static AttributeResult pass() { return new AttributeResult(true, null); }
    private static AttributeResult fail(String type) { return new AttributeResult(false, Map.of("type", type)); }
    private static AttributeResult fail(Map<String, Object> reason) {
        return new AttributeResult(false, reason);
    }

}
