package de.openfabtwin.bimserver.checkingservice.model.facet;
import de.openfabtwin.bimserver.checkingservice.model.RestrictionValue;
import de.openfabtwin.bimserver.checkingservice.model.SimpleValue;
import de.openfabtwin.bimserver.checkingservice.model.Value;
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

    public enum FacetType {ENTITY, ATTRIBUTE, CLASSIFICATION, PROPERTY, PARTOF, MATERIAL}
    public enum Cardinality {REQUIRED, OPTIONAL, PROHIBITED}

    protected static List<String> extractValue(Value name, boolean checkUpperCase) {
        List<String> result = new ArrayList<>();
        if (name instanceof SimpleValue sv) result = List.of(sv.value());
        else if (name instanceof RestrictionValue rv) result = rv.enums();

        if (checkUpperCase) {
            boolean allUppercase = result.stream()
                    .filter(Objects::nonNull)
                    .allMatch(s -> s.equals(s.toUpperCase()));
            if (!allUppercase) return List.of();
        }
        return result;
    }

    public abstract List<IdEObject> filter(IfcModelInterface model);
    public abstract Result matches(IdEObject element);
    public abstract FacetType getType();

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

    public void to_string() {}

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


