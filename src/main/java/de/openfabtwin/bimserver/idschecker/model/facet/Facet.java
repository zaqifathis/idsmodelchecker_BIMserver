package de.openfabtwin.bimserver.idschecker.model.facet;
import de.openfabtwin.bimserver.idschecker.model.Specification;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static de.openfabtwin.bimserver.idschecker.model.facet.Facet.Cardinality.*;

public abstract class Facet {
    Logger LOGGER = LoggerFactory.getLogger(Facet.class);

    protected Cardinality cardinality = REQUIRED;
    protected Boolean status = null;
    protected List<IdEObject> passedEntities = new ArrayList<>();
    protected List<FacetFailure> failures = new ArrayList<>();
    protected String applicability_templates;
    protected String requirement_templates;
    protected String prohibited_templates;

    public enum Cardinality {REQUIRED, OPTIONAL, PROHIBITED}

    public abstract List<IdEObject> filter(IfcModelInterface model);
    public abstract Result matches(IfcModelInterface model, IdEObject element);

    public static Cardinality cardinalityFromString(String s) {
        if (s == null || s.isBlank()) {
            return REQUIRED;
        }
        return switch (s.trim().toLowerCase()) {
            case "required" -> REQUIRED;
            case "optional" -> OPTIONAL;
            case "prohibited" -> PROHIBITED;
            default -> null;
        };
    }

    public String to_string(String type, Specification specification, Facet requirement) {
        if ("applicability".equals(type)) {
            return this.applicability_templates;
        }

        if ("requirement".equals(type)) {
            boolean isProhibited =
                    (specification != null && "0".equals(specification.getMaxOccurs())) ||
                            (requirement != null && requirement.cardinality == Cardinality.PROHIBITED);
            String template = isProhibited ? this.prohibited_templates : this.requirement_templates;

            if (requirement != null && requirement.cardinality == Cardinality.OPTIONAL) {
                template = template.replace("Shall", "May")
                        .replace("shall", "may")
                        .replace("must", "may");
            }
            return template;
        }

        return "This facet cannot be interpreted";
    }

    public void addPassedEntities(IdEObject element) {
        this.passedEntities.add(element);
    }

    public List<IdEObject> getPassedEntities() {
        return this.passedEntities;
    }

    public void addFailures(IdEObject element, String reason) {
        this.failures.add(new FacetFailure(element, reason));
    }

    public void setStatus(boolean bool) {
        this.status = bool;
    }

    public List<FacetFailure> getFailures () {
        return this.failures;
    }

    public boolean isStatus(){
        return this.status;
    }

    // ---- helper ----

    public static String getString(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        return s;
    }

    public static IdEObject getIdEObject(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return (v instanceof IdEObject) ? (IdEObject) v : null;
    }

    public Object getObject(IdEObject obj, String... features) {
        for (String f : features) {
            var sf = obj.eClass().getEStructuralFeature(f);
            if (sf != null) {
                Object v = obj.eGet(sf);
                if (v != null) return v;
            }
        }
        return null;
    }

    public static List<?> getList(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        if (v instanceof List<?>)  return (List<?>)v;
        if (v instanceof IdEObject) return List.of((IdEObject)v);
        return null;
    }

    /**
     * Unwrap an IFC value object to its underlying primitive. IFC wraps primitives in defined types
     * (IfcLabel, IfcBoolean, IfcInteger, IfcReal, ...) and SELECT types (IfcValue, ...), each exposing
     * a {@code wrappedValue} feature. Recurses through nested wrappers. If the object is a genuine
     * entity reference (no {@code wrappedValue}), the entity itself is returned; non-EMF values pass
     * through unchanged.
     */
    public static Object unwrap(Object v) {
        int guard = 0;
        while (v instanceof IdEObject e && guard++ < 16) {
            var f = e.eClass().getEStructuralFeature("wrappedValue");
            if (f == null) return e;       // genuine entity reference, not a value wrapper
            v = e.eGet(f);
            if (v == null) return null;
        }
        return v;
    }

    /**
     * Canonical string form of an (unwrapped) IFC value for matching against IDS values. Booleans
     * and IFC boolean/logical tokens are normalised to lowercase {@code true}/{@code false}; returns
     * {@code null} for null and for genuine entity references (which have no scalar form).
     */
    public static String matchString(Object v) {
        Object u = unwrap(v);
        if (u == null || u instanceof IdEObject) return null;
        if (u instanceof Boolean b) return b ? "true" : "false";
        String s = u.toString().trim();
        if (s.equalsIgnoreCase("true")  || s.equals(".T.") || s.equals("T")) return "true";
        if (s.equalsIgnoreCase("false") || s.equals(".F.") || s.equals("F")) return "false";
        return s;
    }
}


