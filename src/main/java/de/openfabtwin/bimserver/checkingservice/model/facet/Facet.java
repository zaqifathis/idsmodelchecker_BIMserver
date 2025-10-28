package de.openfabtwin.bimserver.checkingservice.model.facet;
import de.openfabtwin.bimserver.checkingservice.model.Specification;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.*;

import static de.openfabtwin.bimserver.checkingservice.model.facet.Facet.Cardinality.*;

public abstract class Facet {
    protected Cardinality cardinality = REQUIRED;
    protected Boolean status = null;
    protected Set<IdEObject> passedEntities = new HashSet<>();
    protected List<FacetFailure> failures = new ArrayList<>();
    protected String applicability_templates;
    protected String requirement_templates;
    protected String prohibited_templates;

    public enum Cardinality {REQUIRED, OPTIONAL, PROHIBITED}

    protected static String joinValues(List<String> values) {
        return String.join(", ", values);
    }

    public abstract List<IdEObject> filter(IfcModelInterface model);
    public abstract Result matches(IdEObject element);

    public static Cardinality cardinalityFromString(String s) {
        if (s == null || s.isBlank()) {
            return null;
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
                            (requirement != null && cardinality == Cardinality.PROHIBITED);

            String template = isProhibited ? this.prohibited_templates : this.requirement_templates;

            if (requirement != null && cardinality == Cardinality.OPTIONAL) {
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
}


