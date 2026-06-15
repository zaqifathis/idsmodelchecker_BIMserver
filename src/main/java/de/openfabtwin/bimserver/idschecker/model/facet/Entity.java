package de.openfabtwin.bimserver.idschecker.model.facet;

import de.openfabtwin.bimserver.idschecker.model.Value;
import de.openfabtwin.bimserver.idschecker.model.result.EntityResult;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

public class Entity extends Facet {

    private final Value name;
    private final Value predefinedType;
    private final String instructions;
//    private String actualPredefVal = "";


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
        String[] unused = {""};
        for (IdEObject candidate : candidates) {
            if(predefinedFilter(candidate, unused)) result.add(candidate);
        }
        return result;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {
        String entName = element.eClass().getName().toUpperCase(Locale.ROOT);
        boolean isPass = name != null && name.matches(entName);

        Map<String, Object> reason = null;

        if (!isPass) {
            String schema = model.getPackageMetaData().getSchema().name().toUpperCase();
            String nameStr = name != null ? name.extract().toUpperCase() : "";
            if (schema.contains("IFC2X3") && !nameStr.endsWith("TYPE")) {
                IdEObject elementType = getElementType(element);
                if (elementType != null) {
                    String typeName = elementType.eClass().getName().toUpperCase(Locale.ROOT);
                    if (typeName.equals(nameStr + "TYPE")) {
                        isPass = true;
                    }
                }
            }
            if (!isPass) {
                reason = Map.of("type", "NAME", "actual", entName);
            }
        }

        if (isPass && this.predefinedType != null) {
            String[] actualOut = {""};
            isPass = predefinedFilter(element, actualOut);
            if (!isPass) {
                reason = Map.of(
                        "type", "PREDEFINEDTYPE",
                        "actual", actualOut[0]
                );
            }
        }
        return new EntityResult(isPass, reason);
    }

    private boolean predefinedFilter(IdEObject candidate, String[] actualOut) {
        List<?> typedBy = getList(candidate, "IsTypedBy");

        if (typedBy != null && !typedBy.isEmpty()) {
            for (Object relObj : typedBy) {
                if (!(relObj instanceof IdEObject rel)) continue;

                String relClass = rel.eClass().getName();
                if (!"IfcRelDefinesByType".equals(relClass)) continue;

                IdEObject type = getIdEObject(rel, "RelatingType");
                if (type == null) continue;

                String pt = getString(type, "PredefinedType");
                if (eq(pt, "USERDEFINED")){
                    String val = getString(type, "ElementType");
                    actualOut[0] = val != null ? val : "";
                    if(predefinedType.matches(val)) return true;
                } else if (predefinedType.matches(pt)) {
                    actualOut[0] = pt != null ? pt : "";
                    return true;
                } else {
                    if (objType(candidate, actualOut)) return true;
                }
            }
        } else {
            return objType(candidate, actualOut);
        }
        return false;
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private boolean objType (IdEObject obj, String[] actualOut) {
        String pdef = getString(obj,"PredefinedType");
        if (eq(pdef, "USERDEFINED")) {
            String val = getString(obj,"ObjectType");
            actualOut[0] = val != null ? val : "";
            return predefinedType.matches(val);
        } //6th check
        actualOut[0] = pdef != null ? pdef : "";
        return predefinedType.matches(pdef);
    }

    @SuppressWarnings("unchecked")
    private IdEObject getElementType(IdEObject element) {
        // IFC4+: IsTypedBy → RelatingType
        List<?> typedBy = getList(element, "IsTypedBy");
        if (typedBy != null) {
            for (Object relObj : typedBy) {
                if (!(relObj instanceof IdEObject rel)) continue;
                if (!"IfcRelDefinesByType".equals(rel.eClass().getName())) continue;
                IdEObject type = getIdEObject(rel, "RelatingType");
                if (type != null) return type;
            }
        }

        // IFC2X3: IsDefinedBy → RelatingType (IfcRelDefinesByType also exists in 2x3)
        List<?> definedBy = getList(element, "IsDefinedBy");
        if (definedBy != null) {
            for (Object relObj : definedBy) {
                if (!(relObj instanceof IdEObject rel)) continue;
                if (!"IfcRelDefinesByType".equals(rel.eClass().getName())) continue;
                IdEObject type = getIdEObject(rel, "RelatingType");
                if (type != null) return type;
            }
        }
        return null;
    }

}
