package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.PartOfResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

import static de.openfabtwin.bimserver.checkingservice.model.facet.Facet.Cardinality.*;

public class PartOf extends Facet {

    private final Value name;
    private final Value predefinedType;
    private final String relation;
    private final String instructions;

    public PartOf(Value name, Value predefinedType, String relation, String cardinality, String instructions){
        this.name = name;
        this.predefinedType = predefinedType;
        this.relation = relation;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (name != null && predefinedType != null) {
            this.requirement_templates = "An element must have an " + relation + " relationship with an " + name.extract() + " of predefined type " + predefinedType.extract();
        }
        else if (name != null){
            this.applicability_templates = "An element with an " + relation + " relationship with an " + name.extract();
            this.requirement_templates = "An element must have an " + relation + " relationship with an " + name.extract();
            this.prohibited_templates = "An element must not have an " + relation + " relationship with an " + name.extract();
        } else {
            this.applicability_templates = "An element with an " + relation + " relationship";
            this.requirement_templates = "An element must have an " + relation + " relationship";
            this.prohibited_templates = "An element must not have an " + relation + " relationship";
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) { //lazy
        List<IdEObject> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        var meta = model.getPackageMetaData();
        var epkg = meta.getEPackage();
        for (EClassifier c : epkg.getEClassifiers()) {
            if (c instanceof EClass ec) {
                for (IdEObject e : model.getAllWithSubTypes(ec)) {
                    if(seen.add(e.getOid())) candidates.add(e);
                }
            }
        }
        return candidates;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {

        boolean isPass = false;
        Map<String, Object> reason = null;

        if (relation == null) {
            // generic parent walk
            List<String> ancestors = new ArrayList<>();
            IdEObject parent = getParent(element); // implement: aggregate → nest → container (first found)
            while (parent != null) {
                String t = parent.eClass().getName().toUpperCase();
                if (predefinedType != null) {
                    String pdt = getPredefinedType(parent); // feature "PredefinedType", if available
                    ancestors.add(pdt != null ? t + "." + pdt : t);
                    if (t.equals(name) && predefinedType.equals(pdt)) { isPass = true; break; }
                } else {
                    ancestors.add(t);
                    if (t.equals(name)) { isPass = true; break; }
                }
                parent = getParent(parent);
            }
            if (!isPass) reason = Map.of("type","ENTITY","actual", ancestors);
        }
        else if (relation.equals("IFCRELAGGREGATES")) {
            IdEObject agg = getAggregateParent(element); // Relates→RelatingObject via IfcRelAggregates
            if (agg == null) { return endWithNOVALUE(cardinality); }
            if (name == null) { isPass = true; }
            else {
                List<String> ancestors = new ArrayList<>();
                while (agg != null) {
                    String t = agg.eClass().getName().toUpperCase();
                    if (predefinedType != null) {
                        String pdt = getPredefinedType(agg);
                        ancestors.add(pdt != null ? t + "." + pdt : t);
                        if (t.equals(name) && predefinedType.equals(pdt)) { isPass = true; break; }
                    } else {
                        ancestors.add(t);
                        if (t.equals(name)) { isPass = true; break; }
                    }
                    agg = getAggregateParent(agg);
                }
                if (!isPass) reason = Map.of("type","ENTITY","actual", ancestors);
            }
        }
        else if (relation.equals("IFCRELASSIGNSTOGROUP")) {
            IdEObject group = getAssignedGroup(element); // scan HasAssignments for IfcRelAssignsToGroup → RelatingGroup
            if (group == null) { return endWithNOVALUE(cardinality); }
            isPass = checkDirectTarget(group, name.extract(), predefinedType.extract());
            if (!isPass) reason = mkEntityOrPredefReason(group, name.extract(), predefinedType.extract());

        }
        else if (relation.equals("IFCRELCONTAINEDINSPATIALSTRUCTURE")) {
            IdEObject container = getSpatialContainer(element); // IfcRelContainedInSpatialStructure
            if (container == null) { return endWithNOVALUE(cardinality); }
            isPass = checkDirectTarget(container, name.extract(), predefinedType.extract());
            if (!isPass) reason = mkEntityOrPredefReason(container, name.extract(), predefinedType.extract());

        }
        else if (relation.equals("IFCRELNESTS")) {
            IdEObject nest = getNestParent(element); // IfcRelNests
            if (nest == null) { return endWithNOVALUE(cardinality); }
            if (name == null) { isPass = true; }
            else {
                List<String> ancestors = new ArrayList<>();
                while (nest != null) {
                    String t = nest.eClass().getName().toUpperCase();
                    if (predefinedType != null) {
                        String pdt = getPredefinedType(nest);
                        ancestors.add(pdt != null ? t + "." + pdt : t);
                        if (name.matches(t) && predefinedType.matches(pdt)) { isPass = true; break; }
                    } else {
                        ancestors.add(t);
                        if (t.equals(name)) { isPass = true; break; }
                    }
                    nest = getNestParent(element);
                }
                if (!isPass) reason = Map.of("type","ENTITY","actual", ancestors);
            }
        }
        else if (relation.equals("IFCRELVOIDSELEMENT IFCRELFILLSELEMENT")) {
            IdEObject buildingElement = null;
            if (isType(element, "IfcOpeningElement")) {
                buildingElement = getVoidedElement(element); // IfcRelVoidsElement.RelatingBuildingElement
            } else {
                IdEObject opening = getFilledVoid(element);  // IfcRelFillsElement where this inst is RelatingBuildingElement → returns opening
                if (opening != null) buildingElement = getVoidedElement(opening);
            }
            if (buildingElement == null) { return endWithNOVALUE(cardinality); }
            isPass = checkDirectTarget(buildingElement, name.extract(), predefinedType.extract());
            if (!isPass) reason = mkEntityOrPredefReason(buildingElement, name.extract(), predefinedType.extract());
        }

        if (cardinality == PROHIBITED) {
            return new PartOfResult(!isPass, Map.of("type", "PROHIBITED"));
        }

        return new PartOfResult(isPass, reason);
    }

    @SuppressWarnings("unchecked")
    private IdEObject getFilledVoid(IdEObject buildingElement) {
        if (buildingElement == null) return null;

        // Find an IfcRelFillsElement where this is the RelatingBuildingElement
        List<IdEObject> fills = (List<IdEObject>) getList(buildingElement, "HasFillings");
        if (fills != null) {
            for (IdEObject rel : fills) {
                if (isType(rel, "IfcRelFillsElement")) {
                    IdEObject opening = getIdEObject(rel, "RelatedOpeningElement");
                    if (opening != null) return opening;
                }
            }
        }

        // Fallback (some IFC2x3 variants store this inversely)
        List<IdEObject> rels = (List<IdEObject>) getList(buildingElement, "FillsVoids");
        if (rels != null) {
            for (IdEObject rel : rels) {
                if (isType(rel, "IfcRelFillsElement")) {
                    IdEObject opening = getIdEObject(rel, "RelatedOpeningElement");
                    if (opening != null) return opening;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private IdEObject getVoidedElement(IdEObject opening) {
        if (opening == null) return null;

        // The inverse relation: VoidsElements or HasOpenings depending on direction
        List<IdEObject> voids = (List<IdEObject>) getList(opening, "VoidsElements");
        if (voids != null) {
            for (IdEObject rel : voids) {
                if (isType(rel, "IfcRelVoidsElement")) {
                    IdEObject building = getIdEObject(rel, "RelatingBuildingElement");
                    if (building != null) return building;
                }
            }
        }

        // Fallback (some implementations store it inversely as HasOpenings)
        List<IdEObject> openings = (List<IdEObject>) getList(opening, "HasOpenings");
        if (openings != null) {
            for (IdEObject rel : openings) {
                if (isType(rel, "IfcRelVoidsElement")) {
                    IdEObject building = getIdEObject(rel, "RelatingBuildingElement");
                    if (building != null) return building;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private IdEObject getNestParent(IdEObject element) {
        List<IdEObject> decomposes = (List<IdEObject>) getList(element, "Decomposes");
        if (decomposes != null) {
            for (IdEObject rel : decomposes) {
                if (isType(rel, "IfcRelNests")) {
                    IdEObject parent = getIdEObject(rel, "RelatingObject");
                    if (parent != null) return parent;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private IdEObject getSpatialContainer(IdEObject element) {
        List<IdEObject> contained = (List<IdEObject>) getList(element, "ContainedInStructure");
        if (contained != null) {
            for (IdEObject rel : contained) {
                if (isType(rel, "IfcRelContainedInSpatialStructure")) {
                    IdEObject parent = getIdEObject(rel, "RelatingStructure");
                    if (parent != null) return parent;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private IdEObject getAssignedGroup(IdEObject inst) {
        if (inst == null) return null;

        List<IdEObject> assigns = (List<IdEObject>) getList(inst, "HasAssignments");
        if (assigns == null) return null;

        for (IdEObject rel : assigns) {
            String relName = rel.eClass().getName();
            if ("IfcRelAssignsToGroup".equals(relName) || "IfcRelAssignsToGroupByFactor".equals(relName)) {
                IdEObject group = getIdEObject(rel, "RelatingGroup"); // -> IfcGroup (or subtype e.g. IfcZone, IfcSystem)
                if (group != null) return group; // return the first match
            }
        }
        return null;
    }

    private IdEObject getAggregateParent(IdEObject element) {
        List<IdEObject> decomposes = (List<IdEObject>) getList(element, "Decomposes");
        if (decomposes != null) {
            for (IdEObject rel : decomposes) {
                if (isType(rel, "IfcRelAggregates")) {
                    IdEObject parent = getIdEObject(rel, "RelatingObject");
                    if (parent != null) return parent;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private IdEObject getParent(IdEObject inst) {
        if (inst == null) return null;

        getAggregateParent(inst); // 1) IfcRelAggregates
        getNestParent(inst); // 2) IfcRelNests
        getSpatialContainer(inst); // 3) IfcRelContainedInSpatialStructure

        // 4) IfcRelConnectsElements (optional)
        List<IdEObject> connected = (List<IdEObject>) getList(inst, "ConnectedTo");
        if (connected != null) {
            for (IdEObject rel : connected) {
                if (isType(rel, "IfcRelConnectsElements")) {
                    IdEObject parent = getIdEObject(rel, "RelatingElement");
                    if (parent != null) return parent;
                }
            }
        }

        return null;
    }

    //--------helper-------------

    private boolean isType(IdEObject e, String name) {
        return e != null && name.equalsIgnoreCase(e.eClass().getName());
    }

    private String getPredefinedType(IdEObject e) {
        var f = e.eClass().getEStructuralFeature("PredefinedType");
        if (f == null) return null;
        Object v = e.eGet(f);
        return (v == null) ? null : v.toString(); // already upper-ish for enums
    }

    private PartOfResult endWithNOVALUE(Cardinality c) {
        if (c == PROHIBITED) return new PartOfResult(false, Map.of("type","PROHIBITED"));
        return new PartOfResult(false, Map.of("type","NOVALUE"));
    }

    private boolean checkDirectTarget(IdEObject target, String name, String predefinedType) {
        if (name == null || name.isBlank()) return true;
        String t = target.eClass().getName().toUpperCase();
        if (!t.equals(name)) return false;
        if (predefinedType == null) return true;
        String pdt = getPredefinedType(target);
        return predefinedType.equals(pdt);
    }

    private Map<String,Object> mkEntityOrPredefReason(IdEObject target, String name, String predefinedType) {
        String t = target.eClass().getName().toUpperCase();
        if (predefinedType != null) {
            String pdt = getPredefinedType(target);
            if (!Objects.equals(predefinedType, pdt)) {
                return Map.of("type","PREDEFINEDTYPE", "actual", pdt);
            }
        }
        return Map.of("type","ENTITY", "actual", t);
    }


}
