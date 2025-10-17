package de.openfabtwin.bimserver.checkingservice.model.facet;


import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Entity extends Facet {
    Logger LOGGER = LoggerFactory.getLogger(Entity.class);

    private final Value name;
    private final String predefinedType;
    private final String instructions;

    public Entity(Value name, String predefinedType, String instructions) {
        this.name = name;
        this.predefinedType = predefinedType;
        this.instructions = instructions;
        this.applicabilityTemplate = predefinedType != null ? "All " + name + " data of type " + predefinedType : "All " + name + " data";
        this.requirementTemplate = predefinedType != null ? "Shall be " + name + " data of type " + predefinedType : "Shall be " + name + " data";
        this.prohibitedTemplate = predefinedType != null ? "Shall not be " + name + " data of type " + predefinedType : "Shall not be " + name + " data";
    }

    @Override
    protected List<IdEObject> discover(IfcModelInterface models) {
        List<String> entitiesName = extractNames(name);
        List<IdEObject> candidates = new ArrayList<>();
        var meta = models.getPackageMetaData();
        for (String name : entitiesName) {
            var eClass = meta.getEClass(name);
            if(eClass == null) throw new IllegalArgumentException("Entity name is not valid: " + this.name);
            candidates.addAll(models.getAllWithSubTypes(eClass));
        }
        return predefinedFilter(candidates);
    }

    private List<IdEObject> predefinedFilter(List<IdEObject> candidates) {
        if (predefinedType == null) return candidates;
        String val;
        List<IdEObject> filtered = new ArrayList<>();

        for (IdEObject cd : candidates) {
            List<?> typedBy = asList(cd, "IsTypedBy");
            if (typedBy != null && !typedBy.isEmpty()) {
                for (Object relObj : typedBy) {
                    if (!(relObj instanceof IdEObject rel)) continue;

                    String relClass = rel.eClass().getName();
                    if (!"IFCRELDEFINESBYTYPE".equals(relClass)) continue;

                    IdEObject type = asObject(rel, "RelatingType");
                    if (type == null) continue;

                    String pt = asString(type, "PredefinedType");
                    if (eq(pt, "USERDEFINED")){
                        val = asString(type, "ElementType");
                        if(eq(val,predefinedType)) filtered.add(cd); break;
                    } else if (eq(pt,predefinedType)) {
                        filtered.add(cd); break;
                    } else {
                        if (objType(cd)) filtered.add(cd);
                    }
                }
            } else {
                if (objType(cd)) filtered.add(cd);
            }
        }
        return filtered;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private boolean objType (IdEObject obj) {
        String pdef = asString(obj,"PredefinedType");
        if (eq(pdef, "USERDEFINED")) {
            String val = asString(obj,"ObjectType");
            if (eq(val, predefinedType)) return true;
        } //6th check
        return eq(pdef, predefinedType);
    }

    private static String asString(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return normalizeEnumLike(v);
    }

    private static String normalizeEnumLike(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        if ("UNSET".equalsIgnoreCase(s)) return null;
        return s;
    }

    private static IdEObject asObject(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return (v instanceof IdEObject) ? (IdEObject) v : null;
    }

    private static List<?> asList(IdEObject obj, String featName) {
        var f = obj.eClass().getEStructuralFeature(featName);
        if (f == null) return null;
        Object v = obj.eGet(f);
        return (v instanceof List<?>) ? (List<?>) v : null;
    }


    @Override
    protected boolean matches(IfcModelInterface models, IdEObject element) {
        return false;
    }

    public Value getName() { return name; }
    public String getPredefinedType() { return predefinedType; }
    public String getInstructions() { return instructions; }
    public String getApplicabilityTemplate() {return this.applicabilityTemplate; }
    public String getRequirementTemplate() {return this.requirementTemplate; }
    public String getProhibitedTemplate() {return this.prohibitedTemplate; }

    @Override
    public FacetType getType(){return FacetType.ENTITY; }

}
