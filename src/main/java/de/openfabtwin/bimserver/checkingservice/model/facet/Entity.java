package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.EntityResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

public class Entity extends Facet {

    private final Value name;
    private final Value predefinedType;
    private final String instructions;
    private String actualPredefVal = "";


    public Entity(Value name, Value predefinedType, String instructions) {
        this.name = name;
        this.predefinedType = predefinedType;
        this.instructions = instructions;

        if (predefinedType == null) {
            this.applicability_templates = "All " + name.extract() + " data";
            this.requirement_templates   = "Shall be " + name.extract() + " data";
            this.prohibited_templates    = "Shall not be " + name.extract() + " data";
        } else {
            this.applicability_templates = "All " + name.extract() + " data of type " + predefinedType.extract();
            this.requirement_templates   = "Shall be " + name.extract() + " data of type " + predefinedType.extract();
            this.prohibited_templates    = "Shall not be " + name.extract() + " data of type " + predefinedType.extract();
        }
    }

    @Override
    public Result matches(IdEObject element) {
        String entName = element.eClass().getName().toUpperCase(Locale.ROOT);
        boolean isPass = name != null && name.matches(entName);

        Map<String, Object> reason = null;
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
                        "actual", actualPredefVal
                );
            }
        }
        return new EntityResult(isPass, reason);
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> candidates = new ArrayList<>();
        var meta = model.getPackageMetaData();
        var epkg = meta.getEPackage();
        Set<Long> seen = new HashSet<>();
        for (EClassifier c : epkg.getEClassifiers()) {
            if (c instanceof EClass ec) {
                String className = ec.getName().toUpperCase(Locale.ROOT);
                if (name.matches(className)) {
                    for (IdEObject e : model.getAllWithSubTypes(ec)) {
                        if(seen.add(e.getOid())) candidates.add(e);
                    }
                }
            }
        }
        if (this.predefinedType == null) return candidates;

        List<IdEObject> result = new ArrayList<>();
        for (IdEObject candidate : candidates) {
            if(predefinedFilter(candidate)) result.add(candidate);
        }
        return result;
    }

    private boolean predefinedFilter(IdEObject candidate) {
        String val;
        List<?> typedBy = getList(candidate, "IsTypedBy");
        if (typedBy != null && !typedBy.isEmpty()) {
            for (Object relObj : typedBy) {
                if (!(relObj instanceof IdEObject rel)) continue;

                String relClass = rel.eClass().getName();
                if (!"IfcRelDefinesByType".equals(relClass)) continue;

                IdEObject type = getObject(rel, "RelatingType");
                if (type == null) continue;

                String pt = getString(type, "PredefinedType");
                if (eq(pt, "USERDEFINED")){
                    val = getString(type, "ElementType");
                    actualPredefVal = val;
                    if(predefinedType.matches(val)) return true;
                } else if (predefinedType.matches(pt)) {
                    return true;
                } else {
                    if (objType(candidate)) return true;
                }
            }
        } else {
            return objType(candidate);
        }
        return false;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private boolean objType (IdEObject obj) {
        String pdef = getString(obj,"PredefinedType");
        if (eq(pdef, "USERDEFINED")) {
            String val = getString(obj,"ObjectType");
            actualPredefVal = val;
            if (predefinedType.matches(val)) return true;
        } //6th check
        actualPredefVal = pdef;
        return predefinedType.matches(pdef);
    }

}
