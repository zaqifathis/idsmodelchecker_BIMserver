package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.EntityResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;

import java.util.*;

public class Entity extends Facet {

    private final List<String> name;
    private final List<String> predefinedType;
    private final String instructions;


    public Entity(Value name, Value predefinedType, String instructions) {
        this.name = extractValue(name, true);
        this.predefinedType = extractValue(predefinedType, false);
        this.instructions = instructions;

        boolean hasPredefined = !this.predefinedType.isEmpty();
        String namePart = joinValues(this.name);
        String predefinedPart = joinValues(this.predefinedType);

        if (hasPredefined) {
            this.applicability_templates = "All " + namePart + " data of type " + predefinedPart;
            this.requirement_templates   = "Shall be " + namePart + " data of type " + predefinedPart;
            this.prohibited_templates    = "Shall not be " + namePart + " data of type " + predefinedPart;
        } else {
            this.applicability_templates = "All " + namePart + " data";
            this.requirement_templates   = "Shall be " + namePart + " data";
            this.prohibited_templates    = "Shall not be " + namePart + " data";
        }
    }

    @Override
    public Result matches(IdEObject element) {
        String entName = element.eClass().getName().toUpperCase(Locale.ROOT);

        Map<String, Object> reason = null;
        boolean isPass = this.name.contains(entName);

        if (!isPass) {
            reason = Map.of(
                    "type", "NAME",
                    "actual",entName
            );
        }
        if (isPass && this.predefinedType != null) {
            isPass = predefinedFilter(element);

            if (!isPass) {
                reason = Map.of(
                        "type", "PREDEFINEDTYPE",
                        "actual", this.predefinedType
                );
            }
        }
        return new EntityResult(isPass, reason);
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> candidates = new ArrayList<>();
        if (this.name.isEmpty()) return candidates;

        var meta = model.getPackageMetaData();
        Map<String, EClass> upperIndex = buildUpperIndex(meta.getEPackage());

        Set<Long> seen = new HashSet<>();
        for (String entName : this.name) {
            EClass eClass = upperIndex.get(entName);
            if (eClass == null) {
                continue;
            }
            for (IdEObject e : model.getAllWithSubTypes(eClass)) {
                if (seen.add(e.getOid())) candidates.add(e);
            }
        }

        if (this.predefinedType == null) return candidates;
        List<IdEObject> result = new ArrayList<>();
        for (IdEObject candidate : candidates) {
            if(predefinedFilter(candidate)) result.add(candidate);
        }
        return result;
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

    private boolean predefinedFilter(IdEObject candidate) {
        String val;
        List<?> typedBy = asList(candidate, "IsTypedBy");
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
                    if(eq(val,this.predefinedType)) return true;
                } else if (eq(pt,this.predefinedType)) {
                    return true;
                } else {
                    if (objType(candidate,this.predefinedType)) return true;
                }
            }
        } else {
            return objType(candidate, this.predefinedType);
        }
        return false;
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

}
