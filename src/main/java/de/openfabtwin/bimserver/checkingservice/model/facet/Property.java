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

import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

import static de.openfabtwin.bimserver.checkingservice.model.facet.Facet.Cardinality.*;
import static org.apache.commons.lang3.math.NumberUtils.toDouble;

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
        Map<String, Map<String,Object>> psets = getPropertySets(model, element);

        boolean isPass = !psets.isEmpty();
        Map<String, Object> reason = new HashMap<>();

        if (!isPass) {
            if(cardinality == OPTIONAL) return new PropertyResult(true, reason);
            reason = Map.of("type", "NOPSET");
        }

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
            } else { //pattern
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

            // --- 3) datatype checks ---
            if (dataType != null && !dataType.isBlank()) {
                IdEObject psetEntity = (IdEObject) psetProps.get("_entity");
                if (psetEntity == null) {
                    isPass = false;
                    reason = Map.of("type", "NOVALUE");
                    break;
                }

                List<IdEObject> propEntities = getProperties(psetEntity);

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

        }

        if (cardinality == PROHIBITED) {
            return new PropertyResult(!isPass, Map.of("type", "PROHIBITED"));
        }

        if (!isPass) {
            if (cardinality == OPTIONAL) return new PropertyResult(true, reason);
            return new PropertyResult(false, (reason != null ? reason : Map.of("type", "NOVALUE")));
        }

        return new PropertyResult(isPass, reason);
    }



    @SuppressWarnings("unchecked")
    private Map<String, Map<String,Object>> getPropertySets(IfcModelInterface model, IdEObject element) {
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
                    Map<String,Map<String, Object>> result = extractPset(model, pset);
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
                        if (defs != null) for (IdEObject d : defs) mergeExtract(results, extractPset(model, d));
                    } else {
                        Map<String,Map<String, Object>> result = extractPset(model, pdef);
                        if (!result.isEmpty()) {
                            mergeExtract(results, result);
                        }
                    }
                }
            }
        }

        // (3) Materials & Profiles
        mergeExtract(results, collectMaterialAndProfilePsets(model, element));

        return results;
    }

    private Map<String, Map<String, Object>> extractPset(IfcModelInterface model, IdEObject pdef) {
        if (isType(pdef, "IfcPropertySet")) {
            String name = getString(pdef, "Name");
            if (name != null && propertySet.matches(name)) {
                return Map.of(name,extractBaseValueMap(model, pdef, "IfcPropertySet"));
            }
        } else if (isType(pdef,"IfcElementQuantity")) {
            String name = getString(pdef, "Name");
            if (name != null && propertySet.matches(name)) {
                return Map.of(name,extractBaseValueMap(model, pdef, "IfcElementQuantity"));
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

    private Map<String, Object> extractBaseValueMap(IfcModelInterface model, IdEObject obj, String psetType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_entity", obj);

        List<IdEObject> props = getProperties(obj);

        if (props != null) {
            for (IdEObject prop : props) {
                String bn = getString(prop, "Name");
                if (bn != null) {
                    if (psetType.equals("IfcPropertySet")) map.put(bn, extractValue(model, prop));
                    if (psetType.equals("IfcElementQuantity")) map.put(bn, extractQuantityValue(model, prop));
                }
            }
        }
        return map;
    }

    private Object extractQuantityValue(IfcModelInterface model, IdEObject prop) {Object raw = getObject(prop, "VolumeValue","AreaValue","WeightValue","LengthValue","TimeValue","CountValue");
        raw = unwrapIfValue(raw);

        String qc = prop.eClass().getName();
        String measure = null;
        if (qc.startsWith("IfcQuantityLength"))  measure = "IfcLengthMeasure";
        else if (qc.startsWith("IfcQuantityArea"))    measure = "IfcAreaMeasure";
        else if (qc.startsWith("IfcQuantityVolume"))  measure = "IfcVolumeMeasure";
        else if (qc.startsWith("IfcQuantityTime"))    measure = "IfcTimeMeasure";
        else if (qc.startsWith("IfcQuantityWeight"))  measure = "IfcMassMeasure";
        else if (qc.startsWith("IfcQuantityCount"))   measure = "IfcCountMeasure";

        IdEObject unit = unitFor(model, prop, prop, measure);
        return toSI(raw, unit);
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
    private Object extractValue(IfcModelInterface model, IdEObject prop) {
        String type = prop.eClass().getName();

        if ("IfcPropertySingleValue".equals(type)) {
            IdEObject nominal = getIdEObject(prop, "NominalValue");
            Object raw = unwrapIfcValue(nominal);
            String measure = (nominal != null) ? nominal.eClass().getName() : null;
            IdEObject unit = unitFor(model, prop, prop, measure);
            return toSI(raw, unit);
        }
        else if ("IfcPropertyListValue".equals(type)) {
            List<Object> lst = (List<Object>) getList(prop,"ListValues");
            if (lst == null || lst.isEmpty()) return List.of();
            String measure = (lst.get(0) instanceof IdEObject e) ? e.eClass().getName() : null;
            List<Object> flat = unwrapList(lst);
            IdEObject unit = unitFor(model, prop, prop, measure);
            return toSIList(flat, unit);
        }
        else if ("IfcPropertyEnumeratedValue".equals(type)) {
            List<Object> lst = (List<Object>) getList(prop,"EnumerationValues");
            if (lst == null || lst.isEmpty()) return List.of();
            String measure = (lst.get(0) instanceof IdEObject e) ? e.eClass().getName() : null;
            List<Object> flat = unwrapList(lst);
            IdEObject unit = unitFor(model, prop, prop, measure);
            return toSIList(flat, unit);
        }
        else if ("IfcPropertyBoundedValue".equals(type)) {
            List<Object> vals = new ArrayList<>();
            String measure = null;
            for (String a : new String[]{"UpperBoundValue","LowerBoundValue","SetPointValue"}) {
                IdEObject rawIfc = getIdEObject(prop, a);
                if (rawIfc != null) {
                    if (measure == null) measure = rawIfc.eClass().getName();
                    vals.add(unwrapIfcValue(rawIfc));
                }
            }
            IdEObject unit = unitFor(model, prop, prop, measure);
            return toSIList(vals, unit);
        }
        else if ("IfcPropertyTableValue".equals(type)) {
            List<Object> def = (List<Object>) getList(prop,"DefiningValues");
            List<Object> ded = (List<Object>) getList(prop,"DefinedValues");
            List<Object> out = new ArrayList<>();
            if (def != null && !def.isEmpty()) {
                List<Object> defFlat = unwrapList(def);
                IdEObject du = getIdEObject(prop, "DefiningUnit");
                out.addAll(toSIList(defFlat, du));
            }
            if (ded != null && !ded.isEmpty()) {
                List<Object> dedFlat = unwrapList(ded);
                IdEObject du2 = getIdEObject(prop, "DefinedUnit");
                out.addAll(toSIList(dedFlat, du2));
            }
            return out;
        }
        return null;
    }

    // Material and profile check
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> collectMaterialAndProfilePsets(IfcModelInterface model, IdEObject element) {
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
                mergeCarrierPsets(out, carrier, model);
            }
            // 4b) Traverse profiles
            for (IdEObject carrier : expandMaterialSelectToProfiles(matSel)) {
                mergeCarrierPsets(out, carrier, model);
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
    private void mergeCarrierPsets(Map<String, Map<String, Object>> out, IdEObject carrier, IfcModelInterface model) {
        if (carrier == null) return;
        List<IdEObject> mprops = (List<IdEObject>) getList(carrier, "HasProperties");
        if (mprops == null) return;

        for (IdEObject mp : mprops) {
            String t = mp.eClass().getName();
            if ("IfcExtendedProperties".equals(t)) {
                mergeExtendedProperties(out, mp);
            } else if ("IfcPropertySet".equals(t)) {
                mergePropertySet(out, mp, model);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void mergePropertySet(Map<String, Map<String, Object>> out, IdEObject pset, IfcModelInterface model) {
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

            Object val = extractValue(model, prop);
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

            if (list.size() == 2 && expected instanceof SimpleValue sv && isNumeric(sv.extract())) {
                Double lower = toDouble(list.get(0));
                Double upper = toDouble(list.get(1));
                Double exp   = safeParseDouble(sv.extract());
                if (lower != null && upper != null && exp != null) {
                    double lo = Math.min(lower, upper);
                    double hi = Math.max(lower, upper);
                    if (exp >= lo - 1e-9 && exp <= hi + 1e-9) return true;
                }
            }
            return false;
        }

        // SimpleValue → exact string or numeric equality
        if (expected instanceof SimpleValue sv) {
            String exp = sv.extract();
            if (actual instanceof Number n && isNumeric(exp)) {
                Double ev = safeParseDouble(exp);
                if (ev != null) return Math.abs(ev - n.doubleValue()) <= 1e-6;
            }
            String s = String.valueOf(actual);
            if ("TRUE".equals(s) || "FALSE".equals(s)) s = s.toLowerCase();
            return expected.matches(s);
        }

        // RestrictionValue → use its .matches(String)
        String s = String.valueOf(actual);
        if ("TRUE".equals(s) || "FALSE".equals(s)) s = s.toLowerCase();
        return expected.matches(s);
    }

    private boolean isNumeric(String s) {
        return s != null && s.matches("[-+]?\\d*\\.?\\d+");
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) return safeParseDouble(s);
        return null;
    }
    private static Double safeParseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    //-----------CONVERT TO SI UNIT------------

    private static final Map<String, Integer> PREFIX_POW10 = Map.ofEntries(
            Map.entry("KILO", 3),
            Map.entry("HECTO", 2),
            Map.entry("DECA", 1),
            Map.entry("DECI", -1),
            Map.entry("CENTI", -2),
            Map.entry("MILLI", -3),
            Map.entry("MICRO", -6),
            Map.entry("NANO", -9)
    );

    // Use this from your extractValue / extractQuantityValue
    private IdEObject unitFor(IfcModelInterface model,
                              IdEObject propOrQuantity,
                              IdEObject carrierForProjectLookup,
                              String measureTypeName) {
        IdEObject u = getIdEObject(propOrQuantity, "Unit"); // local override on the property/quantity
        if (u != null) return u;
        return unitFromProject(model, measureTypeName);
    }

    @SuppressWarnings("unchecked")
    private IdEObject unitFromProject(IfcModelInterface model, String measureTypeName) {
        if (model == null) return null;
        String ut = unitTypeFromMeasure(measureTypeName);
        if (ut == null) return null;

        IdEObject project = findAnyProject(model);
        if (project == null) return null;

        IdEObject ua = getIdEObject(project, "UnitsInContext"); // IfcUnitAssignment
        if (ua == null) return null;

        List<IdEObject> units = (List<IdEObject>) getList(ua, "Units");
        if (units == null) return null;

        for (IdEObject u : units) {
            if ("IfcSIUnit".equals(u.eClass().getName()) && ut.equals(getString(u, "UnitType"))) {
                return u;
            }
            // TODO: IfcConversionBasedUnit / IfcDerivedUnit support if needed
        }
        return null;
    }

    private IdEObject findAnyProject(IfcModelInterface model) {
        var meta = model.getPackageMetaData();
        var epkg = meta.getEPackage();
        var cls  = epkg.getEClassifier("IfcProject");
        if (cls instanceof EClass projClass) {
            for (IdEObject e : model.getAllWithSubTypes(projClass)) {
                return e; // first is fine
            }
        }
        return null;
    }

    private String unitTypeFromMeasure(String m) {
        if (m == null) return null;
        switch (m) {
            case "IfcLengthMeasure":  return "LENGTHUNIT";
            case "IfcAreaMeasure":    return "AREAUNIT";
            case "IfcVolumeMeasure":  return "VOLUMEUNIT";
            case "IfcTimeMeasure":    return "TIMEUNIT";
            case "IfcMassMeasure":    return "MASSUNIT";
            // IfcCountMeasure typically has no unit; return null.
            case "IfcCountMeasure":   return null;
            default: return null;
        }
    }

    private IdEObject unitFor(IdEObject propOrQuantity, IfcModelInterface carrierForProjectLookup, String measureTypeName) {
        IdEObject u = getIdEObject(propOrQuantity, "Unit");  // present for SingleValue, BoundedValue, Quantities (optional)
        if (u != null) return u;
        return unitFromProject(carrierForProjectLookup, measureTypeName);
    }

    private Double unitToSIFactor(IdEObject ifcUnit) {
        if (ifcUnit == null) return null;
        String cls = ifcUnit.eClass().getName();

        if ("IfcSIUnit".equals(cls)) {
            String unitType = getString(ifcUnit, "UnitType"); // LENGTHUNIT/AREAUNIT/...
            String name     = getString(ifcUnit, "Name");     // e.g. METRE, SQUARE_METRE, SECOND, GRAM
            String prefix   = getString(ifcUnit, "Prefix");   // e.g. MILLI, CENTI, null

            int pow = (prefix != null && PREFIX_POW10.containsKey(prefix)) ? PREFIX_POW10.get(prefix) : 0;
            double f = Math.pow(10.0, pow);

            // MASS in IFC base name is GRAM; SI base is kg → 1 g = 1e-3 kg
            if ("GRAM".equals(name)) f *= 1e-3;

            // Prefix affects area/volume multiplicatively
            if ("SQUARE_METRE".equals(name)) f = f * f;
            else if ("CUBIC_METRE".equals(name)) f = f * f * f;

            return f;
        }

        // TODO: IfcConversionBasedUnit / IfcDerivedUnit handling if you encounter them
        return null;
    }

    /** Convert a single Number to SI using an IFC unit; non-numbers or null unit are returned as-is. */
    private Object toSI(Object v, IdEObject unit) {
        if (!(v instanceof Number) || unit == null) return v;
        Double f = unitToSIFactor(unit);
        if (f == null) return v;
        return ((Number) v).doubleValue() * f;
    }

    /** Convert a list of Numbers to SI (keeps non-number entries unchanged). */
    private List<Object> toSIList(List<Object> src, IdEObject unit) {
        if (src == null || src.isEmpty()) return src;
        List<Object> out = new ArrayList<>(src.size());
        for (Object o : src) out.add(toSI(o, unit));
        return out;
    }

}



