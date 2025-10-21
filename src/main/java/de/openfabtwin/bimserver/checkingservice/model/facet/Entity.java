package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Entity extends Facet {
    Logger LOGGER = LoggerFactory.getLogger(Entity.class);

    private final Value name;
    private final Value predefinedType;
    private final String instructions;

    public Entity(Value name, Value predefinedType, String instructions) {
        this.name = name;
        this.predefinedType = predefinedType;
        this.instructions = instructions;
    }

    @Override
    protected List<IdEObject> discover(IfcModelInterface model) {
        List<String> entitiesName = extractValue(name);
        var meta = model.getPackageMetaData();
        Map<String, EClass> upperIndex = buildUpperIndex(meta.getEPackage());

        List<IdEObject> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (String entName : entitiesName) {
            EClass eClass = upperIndex.get(entName);
            if (eClass == null) {
                throw new IllegalArgumentException("Entity name not valid in schema: " + entName);
            }
            for (IdEObject e : model.getAllWithSubTypes(eClass)) {
                if (seen.add(e.getOid())) candidates.add(e);
            }
        }
        return predefinedFilter(candidates);
    }

    private static Map<String, EClass> buildUpperIndex(EPackage pkg) {
        Map<String, EClass> index = new HashMap<>();
        for (EClassifier c : pkg.getEClassifiers()) {
            if (c instanceof EClass ec) {
                index.put(ec.getName().toUpperCase(Locale.ROOT), ec);
            }
        }
        return index;
    }

    private List<IdEObject> predefinedFilter(List<IdEObject> candidates) {
        if (predefinedType == null) return candidates;

        String val;
        List<String> predefinedTypes = extractValue(predefinedType);
        List<IdEObject> filtered = new ArrayList<>();

        for (IdEObject cd : candidates) {
            List<?> typedBy = asList(cd, "IsTypedBy");
            if (typedBy != null && !typedBy.isEmpty()) {
                for (Object relObj : typedBy) {
                    if (!(relObj instanceof IdEObject rel)) continue;

                    String relClass = rel.eClass().getName();
                    if (!"IfcRelDefinesByType".equals(relClass)) continue;

                    IdEObject type = asObject(rel, "RelatingType");
                    if (type == null) continue;

                    String pt = asString(type, "PredefinedType");
                    if (eq(pt, "USERDEFINED")){
                        val = asString(type, "ElementType");
                        if(eq(val,predefinedTypes)) filtered.add(cd); break;
                    } else if (eq(pt,predefinedTypes)) {
                        filtered.add(cd); break;
                    } else {
                        if (objType(cd,predefinedTypes)) filtered.add(cd);
                    }
                }
            } else {
                if (objType(cd,predefinedTypes)) filtered.add(cd);
            }
        }
        return filtered;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private static boolean eq(String a, List<String> bs) {
        if (a == null || bs == null || bs.isEmpty()) return false;
        for (String b: bs) {
            if (a.equals(b)) return true;
        }
        return false;
    }

    private boolean objType (IdEObject obj,List<String> predefinedTypes) {
        String pdef = asString(obj,"PredefinedType");
        if (eq(pdef, "USERDEFINED")) {
            String val = asString(obj,"ObjectType");
            if (eq(val, predefinedTypes)) return true;
        } //6th check
        return eq(pdef, predefinedTypes);
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
    public Value getPredefinedType() { return predefinedType; }
    public String getInstructions() { return instructions; }

    @Override
    public FacetType getType(){return FacetType.ENTITY; }

}
