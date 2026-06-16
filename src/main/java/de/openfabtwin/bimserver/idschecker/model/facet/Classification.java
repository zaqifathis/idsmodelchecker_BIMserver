package de.openfabtwin.bimserver.idschecker.model.facet;

import de.openfabtwin.bimserver.idschecker.model.Value;
import de.openfabtwin.bimserver.idschecker.model.result.ClassificationResult;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;

import java.util.*;

import static de.openfabtwin.bimserver.idschecker.model.facet.Facet.Cardinality.*;

public class Classification extends Facet {

    private final Value system;
    private final Value value;
    private final String uri;
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

        EClass relAssClass = (EClass) model.getPackageMetaData().getEClassifier("IfcRelAssociatesClassification");
        if (relAssClass == null) return results;

        for (IdEObject rel : model.getAll(relAssClass)) {
            List<IdEObject> related = (List<IdEObject>) rel.eGet(relAssClass.getEStructuralFeature("RelatedObjects"));
            if(related == null) continue;
            for (IdEObject obj : related) {
                if(seen.add(obj.getOid())) results.add(obj);
            }
        }
        return results;
    }

    /** One classification association: a system (IfcClassification name) and an optional value. */
    private record Assoc(String system, String value) {}

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {
       List<Assoc> assocs = gather(element);

       boolean isPass = !assocs.isEmpty();
       Map<String, Object> reason = new HashMap<>();

       // --- No classification at all ---
       if (!isPass) {
           if (cardinality == OPTIONAL) {
               return new ClassificationResult(true, null);
           }
           reason = Map.of("type", "NOVALUE");
       }

       // --- only check value when this.value is set ---
       if (isPass && this.value != null) {
            List<String> actualValues = new ArrayList<>();
            boolean anyMatch = false;
            for (Assoc a : assocs) {
                if (a.value() != null) {
                    actualValues.add(a.value());
                    if (this.value.matches(a.value())) anyMatch = true;
                }
            }
            isPass = anyMatch;
            if (!isPass) reason = Map.of("type", "VALUE", "actual", actualValues);
       }

       // --- only check system when this.system is set ---
       if (isPass && this.system != null) {
           List<String> actualSystems = new ArrayList<>();
           boolean sysMatch = false;
           for (Assoc a : assocs) {
               if (a.system() != null) {
                   actualSystems.add(a.system());
                   if (system.matches(a.system())) sysMatch = true;
               }
           }
           isPass = sysMatch;
           if (!isPass) reason = Map.of("type", "SYSTEM", "actual", actualSystems);
       }

       // PROHIBITED returns !isPass, not always false ---
       if (cardinality == PROHIBITED) {
            return new ClassificationResult(!isPass, Map.of("type", "PROHIBITED"));
       }

       return new ClassificationResult(isPass, reason);
    }

    /**
     * Gather classification associations for the element: its own (occurrence) associations plus
     * those inherited from its defining type(s). Per the spec, an occurrence overrides the type's
     * classification per <b>system</b>, so a type association is dropped if the occurrence already
     * has an association in the same system.
     */
    private List<Assoc> gather(IdEObject element) {
        List<Assoc> occ = associationsOf(element);
        Set<String> occSystems = new HashSet<>();
        for (Assoc a : occ) if (a.system() != null) occSystems.add(a.system());

        List<Assoc> all = new ArrayList<>(occ);
        for (IdEObject type : definingTypes(element)) {
            for (Assoc a : associationsOf(type)) {
                if (a.system() == null || !occSystems.contains(a.system())) all.add(a);
            }
        }
        return all;
    }

    /** Associations directly on an object: rooted (HasAssociations) + non-rooted (HasExternalReferences). */
    private List<Assoc> associationsOf(IdEObject obj) {
        List<Assoc> out = new ArrayList<>();

        List<?> rels = getList(obj, "HasAssociations");
        if (rels != null) {
            for (Object o : rels) {
                if (!(o instanceof IdEObject rel)) continue;
                if (!"IfcRelAssociatesClassification".equals(rel.eClass().getName())) continue;
                addRelating(out, getIdEObject(rel, "RelatingClassification"));
            }
        }

        // Non-rooted resources (IfcMaterial, IfcProfileDef, ...) carry classification via
        // IfcExternalReferenceRelationship, exposed on the resource as the HasExternalReferences inverse.
        List<?> exts = getList(obj, "HasExternalReferences");
        if (exts != null) {
            for (Object o : exts) {
                if (!(o instanceof IdEObject rel)) continue;
                if (!"IfcExternalReferenceRelationship".equals(rel.eClass().getName())) continue;
                addRelating(out, getIdEObject(rel, "RelatingReference"));
            }
        }
        return out;
    }

    private void addRelating(List<Assoc> out, IdEObject relating) {
        if (relating == null) return;
        String t = relating.eClass().getName();
        if ("IfcClassificationReference".equals(t)) {
            String system = systemNameOf(relating);
            addRef(out, relating, system);
            for (IdEObject parent : getInheritedReferences(relating)) addRef(out, parent, system);
        } else if ("IfcClassification".equals(t)) {
            // A classification associated directly (no reference) is a system with no value.
            out.add(new Assoc(getString(relating, "Name"), null));
        }
    }

    private void addRef(List<Assoc> out, IdEObject ref, String system) {
        String id = getString(ref, "Identification");
        String ir = getString(ref, "ItemReference");
        out.add(new Assoc(system, id != null ? id : ir));
    }

    private String systemNameOf(IdEObject ref) {
        IdEObject cls = getClassificationOfReference(ref);
        return cls != null ? getString(cls, "Name") : null;
    }

    private List<IdEObject> definingTypes(IdEObject element) {
        List<IdEObject> types = new ArrayList<>();
        List<?> rels = getList(element, "IsTypedBy");
        if (rels != null) {
            for (Object o : rels) {
                if (!(o instanceof IdEObject rel)) continue;
                if (!"IfcRelDefinesByType".equals(rel.eClass().getName())) continue;
                IdEObject t = getIdEObject(rel, "RelatingType");
                if (t != null) types.add(t);
            }
        }
        return types;
    }

    // Inherited references: follow ReferencedSource upward while it is also an IfcClassificationReference.
    private Set<IdEObject> getInheritedReferences(IdEObject ref) {
        Set<IdEObject> results = new LinkedHashSet<>();
        IdEObject current = ref;
        for (int guard = 0; guard < 50; guard++) { // small guard against cycles
            IdEObject src = getIdEObject(current, "ReferencedSource");
            if (src == null) break;
            if (!"IfcClassificationReference".equals(src.eClass().getName())) break;
            // parent reference
            if (!results.add(src)) break;
            current = src;
        }

        return results;
    }

    private IdEObject getClassificationOfReference(IdEObject ref) {
        if(ref == null) return null;

        IdEObject src = getIdEObject(ref, "ReferencedSource");
        if (src == null) return null;

        String t = src.eClass().getName();
        if(t.equals("IfcClassification")) return src;

        IdEObject cur = src;
        for (int guard = 0; guard < 50; guard++) { // small guard against cycles
            if ("IfcClassification".equals(cur.eClass().getName())) return cur;
            cur = getIdEObject(cur, "ReferencedSource");
        }
        return null;
    }

}
