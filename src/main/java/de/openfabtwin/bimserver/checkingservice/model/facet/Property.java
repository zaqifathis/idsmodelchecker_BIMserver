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
    public Result matches(IdEObject element) {

        // 1. get propertySet
        // should store: Pset, <"_entity", IdEObject ,"basename", bn, "datatype", dt, "value", val>
        Map<String, Map<String,Object>> psets = getPropertySets(element);

        boolean isPass = !psets.isEmpty();
        Map<String, Object> reason = new HashMap<>();

        if (!isPass) {
            if(cardinality == OPTIONAL) return new PropertyResult(true, reason);
            reason = Map.of("type", "NOPSET");
        }

        if (isPass) {
            Map<String, Map<String, Object>> props = new LinkedHashMap<>();

            outer:
            for (var psetEntry : psets.entrySet()) {
                String psetName = psetEntry.getKey();
                Map<String, Object> psetProps = psetEntry.getValue();
                Map<String, Object> collected = new LinkedHashMap<>();
                props.put(psetName, collected);

                // check empty basemap
                for (var prop : psetProps.entrySet()) {
                    String nm = prop.getKey();


                }

                // check datatype

                // check value
            }
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

    private Map<String, Object> extractBaseValueMap(IdEObject obj, String psetType) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("_entity", obj);

        List<IdEObject> props = switch (psetType) {
            case "IfcPropertySet" -> (List<IdEObject>) getList(obj, "HasProperties");
            case "IfcElementQuantity" -> (List<IdEObject>) getList(obj, "Quantities");
            default -> null;
        };

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
        Object nominal = getObject(prop, "VolumeValue", "AreaValue", "WeightValue", "LengthValue", "TimeValue", "CountValue");
        return unwrapIfValue(nominal);
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
        } else if ("IfcPropertyListValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"ListValues");
            return unwrapList(list);
        } else if ("IfcPropertyEnumeratedValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"EnumerationValues");
            return unwrapList(list);
        } else if ("IfcPropertyBoundedValue".equals(type)) {
            List<Object> vals = new ArrayList<>();
            for (String a : new String[]{"UpperBoundValue", "LowerBoundValue", "SetPointValue"}) {
                Object raw = getObject(prop, a);
                if (raw != null) vals.add(unwrapIfValue(raw));
            }
            return vals;
        } else if ("IfcPropertyTableValue".equals(type)) {
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
        Object type = ifcValue.eClass().getName();
        Object wf = getObject(ifcValue, "wrappedValue");
        if (wf != null) return wf;
        return ifcValue.toString();
    }

}
