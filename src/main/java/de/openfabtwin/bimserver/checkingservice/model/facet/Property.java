package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.SimpleValue;
import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.PropertyResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static de.openfabtwin.bimserver.checkingservice.model.facet.Facet.Cardinality.*;

public class Property extends Facet {

    private final Value propertySet;
    private final Value baseName;
    private final Value value;
    private final String dataType;
    private final String uri;
    private final String instructions;

    public Property(Value propertySet, Value baseName, Value value, String dataType, String uri, String cardinality, String instructions){
        this.propertySet = propertySet;
        this.baseName = baseName;
        this.value = value;
        this.dataType = dataType;
        this.uri = uri;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (value == null) {
            this.applicability_templates = "Elements with " + baseName.extract() + " data in the dataset " + propertySet.extract();
            this.requirement_templates = baseName.extract() + " data shall be provided in the dataset " + propertySet.extract();
            this.prohibited_templates = baseName.extract() + " data shall not be provided in the dataset " + propertySet.extract();
        } else {
            this.applicability_templates = "Elements with " + baseName.extract() + " data of " + value.extract() + " in the dataset " + propertySet.extract();
            this.requirement_templates = baseName.extract() + " data shall be " + value.extract() + " and in the dataset " + propertySet.extract();
            this.prohibited_templates = baseName.extract() + " data shall not be " + value.extract() + " and in the dataset " + propertySet.extract();
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        var meta = model.getPackageMetaData();
        EPackage epkg = meta.getEPackage();

        Consumer<String> addAllByName = (typeName) -> {
            EClassifier c = epkg.getEClassifier(typeName);
            if (c instanceof EClass ec) {
                for (IdEObject inst : model.getAllWithSubTypes(ec)) {
                    if (seen.add(inst.getOid())) results.add(inst);
                }
            }
        };

        String schema = meta.getSchema().name().toUpperCase();
        if (schema.contains("IFC2X3")) {
            addAllByName.accept("IfcObjectDefinition");
        } else {
            addAllByName.accept("IfcObjectDefinition");
            addAllByName.accept("IfcMaterialDefinition");
            addAllByName.accept("IfcProfileDef");
        }

        return results;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {

        // 1. get propertySet
        Map<String, Map<String,Object>> psets = getPropertySets(element);

        boolean isPass = !psets.isEmpty();
        Map<String, Object> reason = new HashMap<>();

        if (!isPass) {
            if(cardinality == OPTIONAL) return new PropertyResult(true, reason);
            reason = Map.of("type", "NOPSET");
        }

        if (isPass) {

            for (var psetEntry : psets.entrySet()) {
                String psetName = psetEntry.getKey();
                Map<String, Object> psetProps = psetEntry.getValue();

                // --- 1) collect properties by baseName ---
                Map<String, Object> collected = new LinkedHashMap<>();

                if (baseName instanceof SimpleValue sv) {
                    String bn = sv.extract();
                    Object propVal = psetProps.get(bn);
                    if (propVal != null && !"".equals(propVal)) {
                        if (!isLogicalUnknownForProperty(psetProps, propVal)) {
                            collected.put(bn, propVal);
                        }
                    }
                } else { // regex / pattern
                    for (var e : psetProps.entrySet()) {
                        String nm = e.getKey();
                        if ("_entity".equals(nm)) continue;
                        if (baseName.matches(nm)) {
                            Object v = e.getValue();
                            if (v != null && !"".equals(v) && !isLogicalUnknownForProperty(psetProps,v)) {
                                collected.put(nm, v);
                            }
                        }
                    }
                }

                // --- 2) if no properties found for this pset ---
                if (collected.isEmpty()) {
                    if (cardinality == OPTIONAL) return new PropertyResult(true, reason);
                    isPass = false;
                    reason = Map.of("type", "NOVALUE");
                    break;
                }

                // --- 3) datatype checks (against the IFC property entity) ---
                if (dataType != null && !dataType.isBlank()) {
                    IdEObject psetEntity = (IdEObject) psetProps.get("_entity");
                    if (psetEntity == null) {
                        isPass = false;
                        reason = Map.of("type", "NOVALUE");
                        break;
                    }

                    List<IdEObject> propEntities = getProperties(psetEntity);
                    boolean supported = true;

                    for (IdEObject propEntity : propEntities) {
                        String propName = getString(propEntity, "Name");
                        if (propName == null || !collected.containsKey(propName)) continue;

                        String actualType = actualDataTypeForProperty(propEntity);
                        if (actualType == null) continue; // some predefined etc., skip type check

                        if (!dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type", "DATATYPE", "actual", actualType, "dataType", dataType);
                            break;
                        }
                    }
                    if (!isPass) break;
                    if (!supported) {
                        isPass = false;
                        reason = Map.of("type", "NOVALUE");
                        break;
                    }
                }

                // --- 4) value checks (self.value) ---
                if (this.value != null) {
                    for (Object actual : collected.values()) {
                        if (!compareActualAgainstFacetValue(actual, this.value)) {
                            isPass = false;
                            reason = Map.of("type", "VALUE", "actual", actual);
                            break;
                        }
                    }
                    if (!isPass) break;
                }

                // if one Pset passes, continue to check the rest; overall pass if none fails
            }
        }

        if (cardinality == PROHIBITED) {
            return new PropertyResult(!isPass, Map.of("type", "PROHIBITED"));
        }

        return new PropertyResult(isPass, reason);
    }



    @SuppressWarnings("unchecked")
    private Map<String, Map<String,Object>> getPropertySets(IdEObject element) {
        if (element == null) return new LinkedHashMap<>();
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();

        // (1) From element TYPE (RelDefinesByType)
        List<IdEObject> typeRels = (List<IdEObject>) getList(element, "IsTypedBy");
        if (typeRels != null) {
            for (IdEObject rel : typeRels) {
                if (!isType(rel, "IfcRelDefinesByType")) continue;
                IdEObject typeObj = getIdEObject(rel, "RelatingType");
                if (typeObj == null) continue;

                List<IdEObject> hasPsets = (List<IdEObject>) getList(typeObj, "HasPropertySets");
                if (hasPsets == null) continue;
                for (IdEObject pset : hasPsets) {
                    Map<String,Map<String, Object>> result = extractPset(pset);
                    if (!result.isEmpty()) {
                        mergeExtract(results, result);
                    }
                }
            }
        }

        // (2) Direct on the element (RelDefinesByProperties)
        List<IdEObject> rels = (List<IdEObject>) getList(element,"IsDefinedBy");
        if (rels != null) {
            for (IdEObject rel : rels) {
                if (!isType(rel, "IfcRelDefinesByProperties")) continue;
                List<IdEObject> pdefs = (List<IdEObject>) getList(rel, "RelatingPropertyDefinition");
                if (pdefs == null) continue;

                for (IdEObject pdef : pdefs) {
                    if (isType(pdef,"IfcPropertySetDefinitionSet")) {
                        List<IdEObject> defs = (List<IdEObject>) getList(pdef, "PropertySetDefinitions");
                        if (defs != null) for (IdEObject d : defs) mergeExtract(results, extractPset(d));
                    } else {
                        Map<String,Map<String, Object>> result = extractPset(pdef);
                        if (!result.isEmpty()) {
                            mergeExtract(results, result);
                        }
                    }
                }
            }
        }

        // (3) Materials & Profiles
        mergeExtract(results, collectMaterialAndProfilePsets(element));

        return results;
    }

    private Map<String, Map<String, Object>> extractPset(IdEObject pdef) {
        if (isType(pdef, "IfcPropertySet")) {
            String name = getString(pdef, "Name");
            if (name != null && propertySet.matches(name)) {
                return Map.of(name,extractBaseValueMap(pdef, "IfcPropertySet"));
            }
        } else if (isType(pdef,"IfcElementQuantity")) {
            String name = getString(pdef, "Name");
            if (name != null && propertySet.matches(name)) {
                return Map.of(name,extractBaseValueMap(pdef, "IfcElementQuantity"));
            }
        } else {
            boolean isPredefined = isType(pdef,"IfcPreDefinedPropertySet")
                    || pdef.eClass().getEAllSuperTypes().stream()
                    .anyMatch(s -> "IfcPreDefinedPropertySet".equals(s.getName()));
            if (isPredefined) {
                String name = getString(pdef, "Name");
                if (name != null && propertySet.matches(name)) {
                    return Map.of(name, extractPredefPropertySetMap(pdef));
                }
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<IdEObject> getProperties(IdEObject pset) {
        if (pset == null) return List.of();

        String name = pset.eClass().getName();
        if ("IfcPropertySet".equals(name)) {
            return (List<IdEObject>) getList(pset, "HasProperties");
        } else if ("IfcElementQuantity".equals(name)) {
            return (List<IdEObject>) getList(pset, "Quantities");
        }
        return List.of();
    }

    private Map<String, Object> extractBaseValueMap(IdEObject obj, String psetType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_entity", obj);

        List<IdEObject> props = getProperties(obj);

        if (props != null) {
            for (IdEObject prop : props) {
                String bn = getString(prop, "Name");
                if (bn != null) {
                    if (psetType.equals("IfcPropertySet")) map.put(bn, extractValue(prop));
                    if (psetType.equals("IfcElementQuantity")) map.put(bn, extractQuantityValue(prop));
                }
            }
        }
        return map;
    }

    private Object extractQuantityValue(IdEObject prop) {
        Object raw = getObject(prop, "VolumeValue", "AreaValue", "WeightValue", "LengthValue", "TimeValue", "CountValue");
        return unwrapIfValue(raw);
    }

    private Map<String, Object> extractPredefPropertySetMap(IdEObject obj) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_entity", obj);
        for (EStructuralFeature f : obj.eClass().getEAllStructuralFeatures()) {
            String fn = f.getName();
            if (baseName.matches(fn)) {
                Object v = obj.eGet(f);
                map.put(fn, unwrapIfValue(v));
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Object extractValue(IdEObject prop) {
        String type = prop.eClass().getName();

        if ("IfcPropertySingleValue".equals(type)) {
            IdEObject nominal = getIdEObject(prop, "NominalValue");
            return unwrapIfcValue(nominal);
        }
        else if ("IfcPropertyListValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"ListValues");
            return unwrapList(list);
        }
        else if ("IfcPropertyEnumeratedValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"EnumerationValues");
            return unwrapList(list);
        }
        else if ("IfcPropertyBoundedValue".equals(type)) {
            List<Object> vals = new ArrayList<>();
            for (String a : new String[]{"UpperBoundValue", "LowerBoundValue", "SetPointValue"}) {
                Object raw = getObject(prop, a);
                if (raw != null) vals.add(unwrapIfValue(raw));
            }
            return vals;
        }
        else if ("IfcPropertyTableValue".equals(type)) {
            List<Object> vals = new ArrayList<>();
            List<Object> def = (List<Object>) getList(prop,"DefiningValues");
            List<Object> ded = (List<Object>) getList(prop,"DefinedValues");
            if (def != null) for (Object v : def) vals.add(unwrapIfValue(v));
            if (ded != null) for (Object v : ded) vals.add(unwrapIfValue(v));
            return vals;
        }
        return null;
    }

    // Material and profile check
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> collectMaterialAndProfilePsets(IdEObject element) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (element == null) return out;

        List<IdEObject> assocs = (List<IdEObject>) getList(element, "HasAssociations");
        if (assocs == null) return out;

        for (IdEObject rel : assocs) {
            if (!isType(rel, "IfcRelAssociatesMaterial")) continue;

            IdEObject matSel = getIdEObject(rel, "RelatingMaterial");
            if (matSel == null) continue;

            // 4a) Traverse materials
            for (IdEObject carrier : expandMaterialSelectToMaterials(matSel)) {
                mergeCarrierPsets(out, carrier);
            }
            // 4b) Traverse profiles
            for (IdEObject carrier : expandMaterialSelectToProfiles(matSel)) {
                mergeCarrierPsets(out, carrier);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<IdEObject> expandMaterialSelectToMaterials(IdEObject matSelect) {
        List<IdEObject> carriers = new ArrayList<>();
        if (matSelect == null) return carriers;
        String t = matSelect.eClass().getName();

        switch (t) {
            case "IfcMaterial" -> carriers.add(matSelect);
            case "IfcMaterialList" -> {
                List<IdEObject> mats = (List<IdEObject>) getList(matSelect, "Materials");
                if (mats != null) carriers.addAll(mats);
            }
            case "IfcMaterialLayer" -> addIfNotNull(carriers, getIdEObject(matSelect, "Material"));
            case "IfcMaterialLayerSet" -> {
                List<IdEObject> layers = (List<IdEObject>) getList(matSelect, "MaterialLayers");
                if (layers != null) for (IdEObject lyr : layers)
                    carriers.addAll(expandMaterialSelectToMaterials(lyr));
            }
            case "IfcMaterialLayerSetUsage" -> carriers.addAll(
                    expandMaterialSelectToMaterials(getIdEObject(matSelect, "ForLayerSet")));
            case "IfcMaterialConstituent" -> addIfNotNull(carriers, getIdEObject(matSelect, "Material"));
            case "IfcMaterialConstituentSet" -> {
                List<IdEObject> consts = (List<IdEObject>) getList(matSelect, "Constituents");
                if (consts != null) for (IdEObject c : consts)
                    carriers.addAll(expandMaterialSelectToMaterials(c));
            }
            case "IfcMaterialProfile" -> addIfNotNull(carriers, getIdEObject(matSelect, "Material"));
            case "IfcMaterialProfileSet" -> {
                List<IdEObject> profiles = (List<IdEObject>) getList(matSelect, "MaterialProfiles");
                if (profiles != null) for (IdEObject mp : profiles)
                    carriers.addAll(expandMaterialSelectToMaterials(mp));
            }
            case "IfcMaterialProfileSetUsage" -> carriers.addAll(
                    expandMaterialSelectToMaterials(getIdEObject(matSelect, "ForProfileSet")));
            default -> {}
        }
        return carriers;
    }

    @SuppressWarnings("unchecked")
    private List<IdEObject> expandMaterialSelectToProfiles(IdEObject matSelect) {
        List<IdEObject> carriers = new ArrayList<>();
        if (matSelect == null) return carriers;
        String t = matSelect.eClass().getName();

        if ("IfcMaterialProfile".equals(t)) {
            addIfNotNull(carriers, getIdEObject(matSelect, "Profile"));
        } else if ("IfcMaterialProfileSet".equals(t)) {
            List<IdEObject> mps = (List<IdEObject>) getList(matSelect, "MaterialProfiles");
            if (mps != null) for (IdEObject mp : mps)
                addIfNotNull(carriers, getIdEObject(mp, "Profile"));
        } else if ("IfcMaterialProfileSetUsage".equals(t)) {
            IdEObject mps = getIdEObject(matSelect, "ForProfileSet");
            if (mps != null) carriers.addAll(expandMaterialSelectToProfiles(mps));
        }
        return carriers;
    }

    @SuppressWarnings("unchecked")
    private void mergeCarrierPsets(Map<String, Map<String, Object>> out, IdEObject carrier) {
        if (carrier == null) return;
        List<IdEObject> mprops = (List<IdEObject>) getList(carrier, "HasProperties");
        if (mprops == null) return;

        for (IdEObject mp : mprops) {
            String t = mp.eClass().getName();
            if ("IfcExtendedProperties".equals(t)) {
                mergeExtendedProperties(out, mp);
            } else if ("IfcPropertySet".equals(t)) {
                mergePropertySet(out, mp);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergePropertySet(Map<String, Map<String, Object>> out, IdEObject pset) {
        if (pset == null) return;

        String name = getString(pset, "Name");
        if (name == null) return;

        Map<String, Object> props = out.computeIfAbsent(name, k -> new LinkedHashMap<>());
        props.put("_entity", pset);

        List<IdEObject> list = (List<IdEObject>) getList(pset, "HasProperties");
        if (list == null) return;

        for (IdEObject prop : list) {
            String pname = getString(prop, "Name");
            if (pname == null) continue;

            Object val = extractValue(prop);
            if (val != null) props.put(pname, val);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeExtendedProperties(Map<String, Map<String, Object>> out, IdEObject ext) {
        if (ext == null) return;

        String name = getString(ext, "Name");
        if (name == null) return;

        Map<String, Object> bag = out.computeIfAbsent(name, k -> new LinkedHashMap<>());
        bag.put("_entity", ext);

        List<IdEObject> props = (List<IdEObject>) getList(ext, "Properties");
        if (props == null) return;

        for (IdEObject p : props) {
            String pn = getString(p, "Name");
            if (pn == null) continue;

            Object v = getObject(p, "NominalValue");
            if (v instanceof IdEObject ve) v = unwrapIfcValue(ve);

            if (v != null) bag.put(pn, v);
        }
    }

    private void mergeExtract(Map<String, Map<String, Object>> into, Map<String, Map<String, Object>> x) {
        if (x == null || x.isEmpty()) return;
        for (var e : x.entrySet()) {
            into.merge(e.getKey(), e.getValue(), (oldMap, newMap) -> {
                oldMap.putAll(newMap);
                return oldMap;
            });
        }
    }

    //--------helper--------------

    private void addIfNotNull(List<IdEObject> list, IdEObject e) { if (e != null) list.add(e); }

    private boolean isType(IdEObject obj, String name) {
        return name.equals(obj.eClass().getName());
    }

    private List<Object> unwrapList(List<Object> raw) {
        if (raw == null) return Collections.emptyList();
        List<Object> out = new ArrayList<>(raw.size());
        for (Object o : raw) out.add(unwrapIfValue(o));
        return out;
    }

    private Object unwrapIfValue(Object v) {
        if (v instanceof IdEObject e) return unwrapIfcValue(e);
        return v;
    }

    private Object unwrapIfcValue(IdEObject ifcValue) {
        if (ifcValue == null) return null;
        Object wf = getObject(ifcValue, "wrappedValue");
        if (wf != null) return wf;
        return ifcValue.toString();
    }


    //---------------

    @SuppressWarnings("unchecked")
    private boolean isLogicalUnknownForProperty(Map<String,Object> psetMap, Object propVal) {
        // treat only "UNKNOWN"/"UNDEFINED" as unknown; everything else passes through
        String s = String.valueOf(propVal);
        if (!"UNKNOWN".equalsIgnoreCase(s) && !"UNDEFINED".equalsIgnoreCase(s)) return false;

        IdEObject psetEntity = (IdEObject) psetMap.get("_entity");
        if (psetEntity == null) return false;

        for (IdEObject p : getProperties(psetEntity)) {
            String nm = getString(p, "Name");
            if(!baseName.matches(nm)) continue;

            String cls = p.eClass().getName();
            if ("IfcPropertySingleValue".equals(cls)) {
                IdEObject nominal = getIdEObject(p, "NominalValue");
                if (nominal != null && "IfcLogical".equals(nominal.eClass().getName())) {
                    return true;
                }
            }
            // Other types don’t use UNKNOWN convention → treat as actual value
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private String actualDataTypeForProperty(IdEObject propEntity) {
        String t = propEntity.eClass().getName();

        if ("IfcPropertySingleValue".equals(t)) {
            IdEObject nominal = getIdEObject(propEntity, "NominalValue");
            return (nominal != null) ? nominal.eClass().getName() : null;
        }

        if ("IfcPropertyEnumeratedValue".equals(t)) {
            List<Object> list = (List<Object>) getList(propEntity, "EnumerationValues");
            if (list != null && !list.isEmpty()) {
                Object v = list.get(0);
                return (v instanceof IdEObject e) ? e.eClass().getName() : typeFromPrimitive(v);
            }
            return null;
        }

        if ("IfcPropertyListValue".equals(t)) {
            List<Object> list = (List<Object>) getList(propEntity, "ListValues");
            if (list != null && !list.isEmpty()) {
                Object v = list.get(0);
                return (v instanceof IdEObject e) ? e.eClass().getName() : typeFromPrimitive(v);
            }
            return null;
        }

        if ("IfcPropertyBoundedValue".equals(t)) {
            for (String a : new String[]{"UpperBoundValue","LowerBoundValue","SetPointValue"}) {
                Object raw = getObject(propEntity, a);
                if (raw instanceof IdEObject e) return e.eClass().getName();
            }
            return null;
        }

        if ("IfcPropertyTableValue".equals(t)) {
            List<Object> def = (List<Object>) getList(propEntity,"DefiningValues");
            List<Object> ded = (List<Object>) getList(propEntity,"DefinedValues");
            Object v = (def != null && !def.isEmpty()) ? def.get(0) : (ded != null && !ded.isEmpty() ? ded.get(0) : null);
            if (v instanceof IdEObject e) return e.eClass().getName();
            return typeFromPrimitive(v);
        }

        // Quantities: derive by class name → IfcLengthMeasure, IfcAreaMeasure, etc.
        if (t.startsWith("IfcQuantity") || "IfcPhysicalSimpleQuantity".equals(t)) {
            if (t.startsWith("IfcQuantityLength"))  return "IfcLengthMeasure";
            if (t.startsWith("IfcQuantityArea"))    return "IfcAreaMeasure";
            if (t.startsWith("IfcQuantityVolume"))  return "IfcVolumeMeasure";
            if (t.startsWith("IfcQuantityCount"))   return "IfcCountMeasure";
            if (t.startsWith("IfcQuantityWeight"))  return "IfcMassMeasure";
            if (t.startsWith("IfcQuantityTime"))    return "IfcTimeMeasure";
            return null;
        }

        return null; // predefined sets would be handled differently; often skip dtype
    }

    private String typeFromPrimitive(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return v.getClass().getSimpleName();
        if (v instanceof Boolean) return "Boolean";
        return "String";
    }

    @SuppressWarnings("unchecked")
    private boolean compareActualAgainstFacetValue(Object actual, Value expected) {
        if (actual == null || expected == null) return false;

        // If actual is a list → ANY match
        if (actual instanceof List<?> list) {
            for (Object a : list) if (compareActualAgainstFacetValue(a, expected)) return true;
            return false;
        }

        // SimpleValue → exact string / numeric-eq
        if (expected instanceof SimpleValue sv) {
            String exp = sv.extract();
            if (actual instanceof Number n && isNumeric(exp)) {
                try {
                    double ev = Double.parseDouble(exp);
                    return Math.abs(ev - n.doubleValue()) <= 1e-6;
                } catch (NumberFormatException ignore) { /* fallthrough */ }
            }

            String s = String.valueOf(actual);
            if (s.equals("TRUE") || s.equals("FALSE")) {
                s = s.toLowerCase();
            }
            return expected.matches(s);
        }

        // RestrictionValue → use its .matches(String)
        String s = String.valueOf(actual);
        if (s.equals("TRUE") || s.equals("FALSE")) {
            s = s.toLowerCase();
        }
        return expected.matches(s);
    }

    private boolean isNumeric(String s) {
        return s != null && s.matches("[-+]?\\d*\\.?\\d+");
    }

    //-----------CONVERT TO SI UNIT------------

}



