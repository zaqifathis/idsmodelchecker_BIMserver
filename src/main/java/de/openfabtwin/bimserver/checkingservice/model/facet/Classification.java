package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.ClassificationResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EReference;

import java.util.*;

public class Classification extends Facet {
    private final Value system;
    private final Value value;
    private final String uri;
    private final Cardinality cardinality;
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

    @Override
    public Result matches(IdEObject element) {
       Set<IdEObject> leafRefs = getLeafClassificationReferences(element);
       Set<IdEObject> refs = new LinkedHashSet<>(leafRefs);
       for (IdEObject leaf : leafRefs) refs.addAll(getInheritedReferences(leaf));

       boolean isPass = !refs.isEmpty();
       Map<String, Object> reason = new HashMap<>();

       if (!isPass) {
           if (cardinality == Cardinality.OPTIONAL) {
               return new ClassificationResult(true, null);
           }
           reason = Map.of("type", "NOVALUE");

       }

       if (cardinality == Cardinality.PROHIBITED) {
            return new ClassificationResult(false, Map.of("type", "PROHIBITED"));
       }

       return new ClassificationResult(isPass, reason);
    }

    // Inherited references: follow ReferencedSource upward while it is also an IfcClassificationReference.
    private Set<IdEObject> getInheritedReferences(IdEObject ref) {
        Set<IdEObject> results = new LinkedHashSet<>();
        IdEObject current = ref;
        for (int guard = 0; guard < 50; guard++) { // small guard against cycles
            IdEObject src = getRef(current, "ReferencedSource"); // SELECT in IFC: can be IfcClassification or IfcClassificationReference
            if (src == null) break;
            if (!"IfcClassificationReference".equals(src.eClass().getName())) break;
            // parent reference
            if (!results.add(src)) break;
            current = src;
        }

        return results;
    }

    private IdEObject getRef(IdEObject obj, String feature) {
        if (obj == null) return null;
        var f = obj.eClass().getEStructuralFeature(feature);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return (v instanceof IdEObject r) ? r : null;
    }

    //Leaf references attached to the element via IfcRelAssociatesClassification.
    private Set<IdEObject> getLeafClassificationReferences(IdEObject element) {
        Set<IdEObject> results = new LinkedHashSet<>();
        var hasAssocF = element.eClass().getEStructuralFeature("HasAssociations");
        if (hasAssocF == null) return results;

        List<IdEObject> rels = (List<IdEObject>) element.eGet(hasAssocF);
        if (rels == null) return results;

        for (IdEObject rel : rels) {
            if (!"IfcRelAssociatesClassification".equals(rel.eClass().getName())) continue;
            var rcF = rel.eClass().getEStructuralFeature("RelatingClassification");
            if (rcF == null) continue;
            Object rcObj = rel.eGet(rcF);
            if (!(rcObj instanceof IdEObject rc)) continue;
            if ("IfcClassificationReference".equals(rc.eClass().getName())) {
                results.add(rc);
            }
        }
        return results;
    }
}
