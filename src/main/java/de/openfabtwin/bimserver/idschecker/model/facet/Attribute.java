package de.openfabtwin.bimserver.idschecker.model.facet;

import de.openfabtwin.bimserver.idschecker.model.SimpleValue;
import de.openfabtwin.bimserver.idschecker.model.Value;
import de.openfabtwin.bimserver.idschecker.model.result.AttributeResult;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.*;

import java.lang.reflect.Array;
import java.util.*;

public class Attribute extends Facet {

    private final Value name;
    private final Value value;
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

                if (isUncheckable(eAttr, meta)) continue; // derived / inverse attributes cannot be checked

                for (IdEObject inst : model.getAllWithSubTypes(ec)) {
                    Object raw = inst.eGet(eAttr);
                    if (raw != null) {
                        if (!seen.add(inst.getOid())) candidates.add(inst);
                    }
                }
                break;
            }
        }
        return candidates;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {

        List<EStructuralFeature> features = new ArrayList<>();
        var meta = model.getPackageMetaData();

        for (EStructuralFeature f : element.eClass().getEAllStructuralFeatures()) {
            if (!name.matches(f.getName())) continue;

            if (isUncheckable(f, meta)) continue; // derived / inverse attributes cannot be checked
            features.add(f);
        }

        return switch (this.cardinality) {
            case REQUIRED    -> evalRequired(element, features);
            case OPTIONAL    -> evalOptional(element, features);
            case PROHIBITED  -> evalProhibited(element, features);
        };
    }

    private Result evalProhibited(IdEObject element, List<EStructuralFeature> attributes) {
        // PROHIBITED name "-"   → the attribute must not be present at all.
        // PROHIBITED name value → the attribute must not hold that value (null/absent is allowed).
        for (EStructuralFeature attr : attributes) {
            Object raw = element.eGet(attr);
            if (!isPresent(raw, attr)) continue;
            Object val = unwrap(raw);
            if (this.value == null) {
                return fail("PROHIBITED"); // presence itself is prohibited
            }
            if (val instanceof IdEObject) continue; // entity ref has no scalar value to match
            String s = matchString(val);
            if (s != null && value.matches(s)) return fail("PROHIBITED");
        }
        return pass();
    }

    private Result evalOptional(IdEObject element, List<EStructuralFeature> attributes) {
        // OPTIONAL: if a matching attribute is present it must satisfy the value. When the name is a
        // restriction matching several attributes, ANY satisfying match passes ("match any result").
        if (attributes.isEmpty()) return pass();

        boolean anyPresent = false;
        Map<String, Object> lastReason = null;
        for (EStructuralFeature attr : attributes) {
            Object raw = element.eGet(attr);
            if (!isPresent(raw, attr)) continue;
            anyPresent = true;

            Object val = unwrap(raw);
            if (!isActualValue(val)) { lastReason = Map.of("type", "FALSEY", "actual", String.valueOf(val)); continue; }

            if (val instanceof IdEObject) {
                if (this.value == null) return pass();
                lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val));
                continue;
            }
            if (this.value == null) return pass();
            // An integer-typed attribute cannot match an IDS value that is not an integer literal
            // (e.g. "42.0" against an IfcInteger) — such a requirement can never be satisfied.
            if (integerTypeMismatch(attr)) { lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val)); continue; }
            String s = matchString(val);
            if (s != null && value.matches(s)) return pass();
            lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val));
        }
        if (!anyPresent) return pass();
        return fail(lastReason != null ? lastReason : Map.of("type", "VALUE"));
    }

    private Result evalRequired(IdEObject element, List<EStructuralFeature> attributes) {
        // REQUIRED: at least one matching attribute must be present and satisfy the value. When the
        // name is a restriction matching several attributes, ANY satisfying match passes.
        if (attributes.isEmpty()) return fail("NOVALUE");

        Map<String, Object> lastReason = Map.of("type", "NOVALUE");
        for (EStructuralFeature attr : attributes) {
            Object raw = element.eGet(attr);
            if (!isPresent(raw, attr)) { lastReason = Map.of("type", "NOVALUE"); continue; }

            Object val = unwrap(raw);
            if (!isActualValue(val)) { lastReason = Map.of("type", "FALSEY", "actual", String.valueOf(val)); continue; }

            if (val instanceof IdEObject) {
                if (this.value == null) return pass();
                lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val));
                continue;
            }
            if (this.value == null) return pass();
            // An integer-typed attribute cannot match an IDS value that is not an integer literal
            // (e.g. "42.0" against an IfcInteger) — such a requirement can never be satisfied.
            if (integerTypeMismatch(attr)) { lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val)); continue; }
            String s = matchString(val);
            if (s != null && value.matches(s)) return pass();
            lastReason = Map.of("type", "VALUE", "actual", String.valueOf(val));
        }
        return fail(lastReason);
    }

    @SuppressWarnings("unchecked")
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

    /** Derived and inverse attributes cannot be checked. */
    private static boolean isUncheckable(EStructuralFeature f, org.bimserver.emf.PackageMetaData meta) {
        if (f.isDerived() || f.isTransient() || f.isVolatile()) return true;        // derived
        return f instanceof EReference ref && meta != null && meta.isInverse(ref);  // inverse
    }

    private static boolean isIntegerTyped(EStructuralFeature attr) {
        if (!(attr instanceof EAttribute)) return false;
        EClassifier t = attr.getEType();
        if (t == null) return false;
        String cn = t.getInstanceClassName();
        return "int".equals(cn) || "long".equals(cn) || "short".equals(cn)
                || "java.lang.Integer".equals(cn) || "java.lang.Long".equals(cn)
                || "java.lang.Short".equals(cn) || "java.math.BigInteger".equals(cn);
    }

    private boolean integerTypeMismatch(EStructuralFeature attr) {
        return this.value instanceof SimpleValue sv
                && isIntegerTyped(attr)
                && !sv.extract().trim().matches("[-+]?\\d+");
    }

    private static AttributeResult pass() { return new AttributeResult(true, null); }
    private static AttributeResult fail(String type) { return new AttributeResult(false, Map.of("type", type)); }
    private static AttributeResult fail(Map<String, Object> reason) {
        return new AttributeResult(false, reason);
    }

}
