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
        Map<String, Map<String, Object>> psets;

        // 1. Retrieve relevant property sets
        if (propertySet instanceof SimpleValue) {
            Map<String, Object> one = getPset(element);
            psets = (one == null) ? Map.of() : Map.of(propertySet.extract(), one);
        } else {
            Map<String, Map<String, Object>> all = getPsets(element);
            Map<String, Map<String, Object>> filtered = new LinkedHashMap<>();
            for (var e : all.entrySet()) {
                if (propertySet.matches(e.getKey())) filtered.put(e.getKey(), e.getValue());
            }
            psets = filtered;
        }

        boolean isPass = !psets.isEmpty();
        Map<String, Object> reason = new HashMap<>();

        if (!isPass) {
            if(cardinality == OPTIONAL) return new PropertyResult(true, reason);
            reason = Map.of("type", "NOPSET");
        } else {

            // 2a. Iterate over Psets
            outer:
            for (var entry : psets.entrySet()) {
                String psetName = entry.getKey();
                Map<String, Object> psetProps = entry.getValue();
                Map<String, Object> chosen = new LinkedHashMap<>();

                if(baseName instanceof SimpleValue sv){
                    String bn = sv.extract();
                    Object propVal = psetProps.get(bn);
                    if (!isLogicalUnknown(psetProps, bn, propVal) && isNonEmptyValue(propVal)) {
                        chosen.put(bn, propVal);
                    }
                } else {
                    for (var kv: psetProps.entrySet()) {
                        String k = kv.getKey();
                        if ("_entity".equals(k)) continue;
                        if( baseName.matches(k)) {
                            Object v = kv.getValue();
                            if (v != null && !"".equals(v)) chosen.put(k, v);
                        }
                    }
                }

                //2b. If no properties found for this Pset
                if (chosen.isEmpty()) {
                    if (cardinality == OPTIONAL) return new PropertyResult(true, reason);
                    reason = Map.of("type", "NOVALUE");
                    break;
                }

                //2c. Datatype checks
                IdEObject carrier = (IdEObject) psetProps.get("_entity");
                if (carrier == null) {
                    isPass = false;
                    reason = Map.of("type", "NOVALUE");
                    break;
                }

                boolean supported = true;
                for (Object propObj : getProperties(carrier)) {
                    if (!(propObj instanceof IdEObject propEntity)) continue;
                    String propName = getString(propEntity, "Name");
                    if (propName == null || !chosen.containsKey(propName)) continue;
                    String t = propEntity.eClass().getName();

                    if ("IfcPropertySingleValue".equals(t)) {
                        IdEObject nominal = getObject(propEntity, "NominalValue");
                        String actualType = (nominal != null) ? nominal.eClass().getName() : null;

                        if (dataType != null && actualType != null &&
                                !dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else if (t.startsWith("IfcQuantity") || "IfcPhysicalSimpleQuantity".equals(t)) {
                        Object numeric = tryGet(propEntity, "LengthValue","AreaValue","VolumeValue","CountValue","WeightValue","TimeValue");
                        if (numeric == null) {
                            var f3 = propEntity.eClass().getEStructuralFeature(3);
                            if (f3 != null) numeric = propEntity.eGet(f3);
                        }
                        String actualType = inferQuantityDataType(propEntity.eClass().getName(), numeric);
                        if (dataType != null && actualType != null && !dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else if ("IfcPropertyEnumeratedValue".equals(t)) {
                        @SuppressWarnings("unchecked")
                        List<Object> enumVals = (List<Object>) getList(propEntity, "EnumerationValues");
                        if (enumVals == null || enumVals.isEmpty()) {
                            isPass = false;
                            reason = Map.of("type","NOVALUE");
                            break outer;
                        }
                        String actualType = unwrapTypeName(enumVals.get(0));
                        if (dataType != null && actualType != null &&
                                !dataType.equalsIgnoreCase(actualType)) {
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else if ("IfcPropertyListValue".equals(t)) {
                        @SuppressWarnings("unchecked")
                        List<Object> listVals = (List<Object>) getList(propEntity, "ListValues");
                        if (listVals == null || listVals.isEmpty()) {
                            isPass = false;
                            reason = Map.of("type","NOVALUE");
                            break outer;
                        }
                        String actualType = unwrapTypeName(listVals.get(0));
                        if (dataType != null && actualType != null &&
                                !dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else if ("IfcPropertyBoundedValue".equals(t)) {
                        String actualType = null;
                        for (String a : new String[]{"UpperBoundValue","LowerBoundValue","SetPointValue"}) {
                            Object raw = propEntity.eGet(propEntity.eClass().getEStructuralFeature(a));
                            if (raw instanceof IdEObject ve && actualType == null) {
                                actualType = ve.eClass().getName();
                            }
                        }
                        if (dataType != null && actualType != null && !dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else if ("IfcPropertyTableValue".equals(t)){
                        @SuppressWarnings("unchecked")
                        List<Object> defining = (List<Object>) getList(propEntity, "DefiningValues");
                        @SuppressWarnings("unchecked")
                        List<Object> defined  = (List<Object>) getList(propEntity, "DefinedValues");

                        if ((defining == null || defining.isEmpty()) && (defined == null || defined.isEmpty())) {
                            isPass = false;
                            reason = Map.of("type","NOVALUE");
                            break outer;
                        }

                        String actualType = null;
                        if (defining != null && !defining.isEmpty()) actualType = unwrapTypeName(defining.get(0));
                        else if (defined != null && !defined.isEmpty()) actualType = unwrapTypeName(defined.get(0));

                        if (dataType != null && actualType != null && !dataType.equalsIgnoreCase(actualType)) {
                            isPass = false;
                            reason = Map.of("type","DATATYPE","actual", actualType, "dataType", dataType);
                            break outer;
                        }
                    } else {
                        supported = false;
                    }
                }

                if (!supported) {
                    isPass = false;
                    reason = Map.of("type", "NOVALUE");
                    break;
                }

                // 2d. Value constraint (self.value)
                if (this.value != null) {
                    for (Object actual : chosen.values()) {
                        if (!compareValues(actual, this.value)) {
                            isPass = false;
                            reason = Map.of("type", "VALUE", "actual", actual);
                            break outer;
                        }
                    }
                }
            }
        }

        if (cardinality == PROHIBITED) {
            return new PropertyResult(!isPass, Map.of("type", "PROHIBITED"));
        }

        return new PropertyResult(isPass, reason);
    }

    private boolean compareValues(Object actual, Object expected) {
        if (actual == null || expected == null) return false;

        // If actual is a list, pass if ANY element matches
        if (actual instanceof List<?> list) {
            for (Object a : list) if (compareValues(a, expected)) return true;
            return false;
        }

        if (expected instanceof String exp) {
            if (actual instanceof Number n && exp.matches("[-+]?\\d*\\.?\\d+")) {
                try {
                    double ev = Double.parseDouble(exp);
                    return Math.abs(ev - n.doubleValue()) <= 1e-6;
                } catch (NumberFormatException ignored) { /* fall through */ }
            }
            return exp.equals(String.valueOf(actual));
        }
        return Objects.equals(actual, expected);
    }

    private String inferQuantityDataType(String quantityClassName, Object numeric) {
        if (quantityClassName == null) return null;
        if (quantityClassName.startsWith("IfcQuantityLength"))  return "IfcLengthMeasure";
        if (quantityClassName.startsWith("IfcQuantityArea"))    return "IfcAreaMeasure";
        if (quantityClassName.startsWith("IfcQuantityVolume"))  return "IfcVolumeMeasure";
        if (quantityClassName.startsWith("IfcQuantityCount"))   return "IfcCountMeasure";
        if (quantityClassName.startsWith("IfcQuantityWeight"))  return "IfcMassMeasure";
        if (quantityClassName.startsWith("IfcQuantityTime"))    return "IfcTimeMeasure";
        if (numeric instanceof Number) return numeric.getClass().getSimpleName();
        return null;
    }

    private boolean isLogicalUnknown(Map<String,Object> psetMap, String base, Object propVal) {
        if (propVal instanceof Enum<?> en) {
            String n = en.name();
            if ("UNKNOWN".equalsIgnoreCase(n) || "UNDEFINED".equalsIgnoreCase(n)) return true;
        }
        if (propVal != null) {
            String s = String.valueOf(propVal).trim();
            if ("UNKNOWN".equalsIgnoreCase(s) || "UNDEFINED".equalsIgnoreCase(s)) {
                return true;
            }
        }

        IdEObject carrier = (IdEObject) psetMap.get("_entity");
        if (carrier == null) return false;
        for (Object po : getProperties(carrier)) {
            if (!(po instanceof IdEObject p)) continue;
            if (!Objects.equals(getString(p, "Name"), base)) continue;
            if ("IfcPropertySingleValue".equals(p.eClass().getName())) {
                IdEObject nominal = getObject(p, "NominalValue");
                if (nominal == null) return false;

                if (!"IfcLogical".equals(nominal.eClass().getName())) return false;

                Object wrapped = tryGet(nominal, "wrappedValue");
                if (wrapped instanceof Enum<?> en) {
                    String n = en.name();
                    return "UNKNOWN".equalsIgnoreCase(n) || "UNDEFINED".equalsIgnoreCase(n);
                }
                if (wrapped != null) {
                    String s = String.valueOf(wrapped).trim();
                    return "UNKNOWN".equalsIgnoreCase(s) || "UNDEFINED".equalsIgnoreCase(s);
                }
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<IdEObject> getProperties(IdEObject pset) {
        if (pset == null) return List.of();

        String name = pset.eClass().getName();
        if ("IfcPropertySet".equals(name)) {
            return (List<IdEObject>) getList(pset, "HasProperties");
        } else if ("IfcElementQuantity".equals(name)) {
            return (List<IdEObject>) getList(pset, "Quantities");
        } else if ("IfcMaterialProperties".equals(name) || "IfcProfileProperties".equals(name)) {
            return (List<IdEObject>) getList(pset, "Properties");
        } else if ("IfcPreDefinedPropertySet".equals(name)) {
            // For predefined property sets, fallback to pseudo-objects from attributes
            List<IdEObject> pseudo = new ArrayList<>();
            for (var f : pset.eClass().getEAllStructuralFeatures()) {
                Object v = pset.eGet(f);
                if (!(v instanceof IdEObject)) {
                    pseudo.add(createPseudoProperty(f.getName(), v));
                }
            }
            return pseudo;
        }
        return List.of();
    }

    private IdEObject createPseudoProperty(String name, Object value) {
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPset(IdEObject element) {
        if (element == null || propertySet == null) return null;

        // (1) Direct Psets on the element
        List<IdEObject> rels = (List<IdEObject>) getList(element,"IsDefinedBy");
        if (rels != null) {
            for (IdEObject rel : rels) {
                if (!"IfcRelDefinesByProperties".equals(rel.eClass().getName())) continue;

                IdEObject pdef = getObject(rel, "RelatingPropertyDefinition");
                if (pdef == null || !"IfcPropertySet".equals(pdef.eClass().getName())) continue;

                String name = getString(pdef, "Name");
                if (name != null && propertySet.matches(name)) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("_entity", pdef);
                    List<IdEObject> props = (List<IdEObject>)getList(pdef, "HasProperties");
                    if (props != null) {
                        for (IdEObject prop : props) {
                            String pn = getString(prop, "Name");
                            if (pn == null) continue;
                            map.put(pn, extractPropertyValue(prop));
                        }
                    }
                    return map;
                }
            }
        }

        // (2) Psets from the element’s Type
        List<IdEObject> typeRels = (List<IdEObject>) getList(element, "IsTypedBy");
        if (typeRels != null) {
            for (IdEObject r : typeRels) {
                if (!"IfcRelDefinesByType".equals(r.eClass().getName())) continue;

                IdEObject typeObj = getObject(r, "RelatingType");
                if (typeObj == null) continue;

                List<IdEObject> psets = (List<IdEObject>) getList(typeObj, "HasPropertySets");
                if (psets == null) continue;

                for (IdEObject ps : psets) {
                    if (!"IfcPropertySet".equals(ps.eClass().getName())) continue;
                    String name = getString(ps, "Name");
                    if (name != null && propertySet.matches(name)) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("_entity", ps);
                        List<IdEObject> props = (List<IdEObject>) getList(ps, "HasProperties");
                        if (props != null) {
                            for (IdEObject prop : props) {
                                String pn = getString(prop, "Name");
                                if (pn == null) continue;
                                map.put(pn, extractPropertyValue(prop));
                            }
                        }
                        return map;
                    }
                }
            }
        }

        // (3) Materials & Profiles (IFC4+)
        final Map<String, Map<String, Object>> tmp = new LinkedHashMap<>();
        forEachMaterialDefinitionAndProfiles(element, carrier -> mergeCarrierPsets(tmp, carrier));
        return tmp.get(propertySet.extract());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getPsets(IdEObject element) {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        if (element == null) return results;

        // (1) Element → RelDefinesByProperties
        var isDefinedByF = element.eClass().getEStructuralFeature("IsDefinedBy");
        if (isDefinedByF != null) {
            List<IdEObject> rels = (List<IdEObject>) element.eGet(isDefinedByF);
            if (rels != null) for (IdEObject rel : rels) {
                if (!"IfcRelDefinesByProperties".equals(rel.eClass().getName())) continue;
                IdEObject pdef = getObject(rel, "RelatingPropertyDefinition");
                if (pdef == null) continue;
                String t = pdef.eClass().getName();
                if ("IfcPropertySet".equals(t)) {
                    mergePropertySet(results, pdef);
                } else if ("IfcElementQuantity".equals(t)) {
                    mergeElementQuantity(results, pdef);
                }
            }
        }

        // (2) Type → RelDefinesByType
        var isTypedByF = element.eClass().getEStructuralFeature("IsTypedBy");
        if (isTypedByF != null) {
            List<IdEObject> typeRels = (List<IdEObject>) element.eGet(isTypedByF);
            if (typeRels != null) for (IdEObject rel : typeRels) {
                if (!"IfcRelDefinesByType".equals(rel.eClass().getName())) continue;
                IdEObject typeObj = getObject(rel, "RelatingType");
                if (typeObj == null) continue;

                var hasPsetsF = typeObj.eClass().getEStructuralFeature("HasPropertySets");
                if (hasPsetsF == null) continue;
                List<IdEObject> psets = (List<IdEObject>) typeObj.eGet(hasPsetsF);
                if (psets == null) continue;

                for (IdEObject ps : psets) {
                    String t = ps.eClass().getName();
                    if ("IfcPropertySet".equals(t)) {
                        mergePropertySet(results, ps);
                    } else if ("IfcElementQuantity".equals(t)) {
                        mergeElementQuantity(results, ps);
                    }
                }
            }
        }

        // (3) Materials & Profiles
        forEachMaterialDefinitionAndProfiles(element, carrier -> mergeCarrierPsets(results, carrier));
        return results;
    }

    @SuppressWarnings("unchecked")
    private void forEachMaterialDefinitionAndProfiles(IdEObject element, Consumer<IdEObject> sink) {
        List<IdEObject> assocs = (List<IdEObject>) getList(element, "HasAssociations");
        if (assocs == null) return;

        for (IdEObject rel : assocs) {
            if (!"IfcRelAssociatesMaterial".equals(rel.eClass().getName())) continue;
            IdEObject matSel = getObject(rel, "RelatingMaterial"); // IfcMaterialSelect
            if (matSel == null) continue;
            forEachMaterialDefinition(matSel, sink);
            forEachProfilesViaMaterialSelect(matSel, sink);
        }
    }

    @SuppressWarnings("unchecked")
    private void forEachMaterialDefinition(IdEObject matSelect, Consumer<IdEObject> sink) {
        if (matSelect == null) return;
        String t = matSelect.eClass().getName();
        switch (t) {
            case "IfcMaterial" -> sink.accept(matSelect);
            case "IfcMaterialList" -> {
                List<IdEObject> mats = (List<IdEObject>) getList(matSelect, "Materials");
                if (mats != null) for (IdEObject m : mats) sink.accept(m);
            }
            case "IfcMaterialLayer" -> {
                IdEObject m = getObject(matSelect, "Material");
                if (m != null) sink.accept(m);
            }
            case "IfcMaterialLayerSet" -> {
                List<IdEObject> layers = (List<IdEObject>) getList(matSelect,"MaterialLayers");
                if (layers != null) for (IdEObject lyr : layers) forEachMaterialDefinition(lyr, sink);
            }
            case "IfcMaterialLayerSetUsage" -> {
                IdEObject ls = getObject(matSelect, "ForLayerSet");
                if (ls != null) forEachMaterialDefinition(ls, sink);
            }
            case "IfcMaterialConstituent" -> {
                IdEObject m = getObject(matSelect, "Material");
                if (m != null) sink.accept(m);
            }
            case "IfcMaterialConstituentSet" -> {
                List<IdEObject> consts = (List<IdEObject>) getList(matSelect,"Constituents");
                if (consts != null) for (IdEObject c : consts) forEachMaterialDefinition(c, sink);
            }
            case "IfcMaterialProfile" -> {
                IdEObject m = getObject(matSelect, "Material");
                if (m != null) sink.accept(m);
            }
            case "IfcMaterialProfileSet" -> {
                List<IdEObject> profiles = (List<IdEObject>) getList(matSelect,"MaterialProfiles");
                if (profiles != null) for (IdEObject mp : profiles) forEachMaterialDefinition(mp, sink);
            }
            case "IfcMaterialProfileSetUsage" -> {
                IdEObject mps = getObject(matSelect, "ForProfileSet");
                if (mps != null) forEachMaterialDefinition(mps, sink);
            }
            default -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void forEachProfilesViaMaterialSelect(IdEObject matSelect, Consumer<IdEObject> sink) {
        if (matSelect == null) return;
        String t = matSelect.eClass().getName();
        if ("IfcMaterialProfile".equals(t)) {
            IdEObject prof = getObject(matSelect, "Profile");
            if (prof != null) sink.accept(prof);
        } else if ("IfcMaterialProfileSet".equals(t)) {
            List<IdEObject> mps = (List<IdEObject>) getList(matSelect, "MaterialProfiles");
            if (mps != null) for (IdEObject mp : mps) {
                IdEObject prof = getObject(mp, "Profile");
                if (prof != null) sink.accept(prof);
            }
        } else if ("IfcMaterialProfileSetUsage".equals(t)) {
            IdEObject mps = getObject(matSelect, "ForProfileSet");
            if (mps != null) forEachProfilesViaMaterialSelect(mps, sink);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeCarrierPsets(Map<String, Map<String, Object>> out, IdEObject carrier) {
        if (carrier == null) return;
        List<IdEObject> mprops = (List<IdEObject>) getList(carrier, "HasProperties");
        if (mprops != null)
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
    private void mergePropertySet(Map<String, Map<String, Object>> results, IdEObject pset) {
        String name = getString(pset, "Name");
        if (name == null) return;
        Map<String, Object> props = results.computeIfAbsent(name, k -> new LinkedHashMap<>());
        props.put("_entity", pset);

        List<IdEObject> list = (List<IdEObject>) getList(pset, "HasProperties");
        if (list == null) return;

        for (IdEObject prop : list) {
            String pname = getString(prop, "Name");
            if (pname == null) continue;
            props.put(pname, extractPropertyValue(prop));
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeElementQuantity(Map<String, Map<String, Object>> results, IdEObject eq) {
        String name = getString(eq, "Name");
        if (name == null) return;
        Map<String, Object> props = results.computeIfAbsent(name, k -> new LinkedHashMap<>());
        props.put("_entity", eq);

        List<IdEObject> quants = (List<IdEObject>) getList(eq, "Quantities");
        if (quants == null) return;
        for (IdEObject q : quants) {
            String qn = getString(q, "Name");
            if (qn == null) continue;

            Object num = tryGet(q, "LengthValue","AreaValue","VolumeValue","CountValue","WeightValue","TimeValue");
            if (num == null) {
                var f3 = q.eClass().getEStructuralFeature(3);
                if (f3 != null) num = q.eGet(f3);
            }
            if (num != null) props.put(qn, unwrapIfValue(num));
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeExtendedProperties(Map<String, Map<String, Object>> out, IdEObject ext) {
        String name = getString(ext, "Name");
        if (name == null) return;
        Map<String, Object> bag = out.computeIfAbsent(name, k -> new LinkedHashMap<>());
        bag.put("_entity", ext);

        List<IdEObject> props = (List<IdEObject>) getList(ext, "Properties");
        if (props == null) return;
        for (IdEObject p : props) {
            String pn = getString(p, "Name");
            if (pn == null) continue;
            Object v = p.eGet(p.eClass().getEStructuralFeature("NominalValue"));
            if (v instanceof IdEObject ve) v = unwrapIfcValue(ve);
            bag.put(pn, v);
        }
    }

    @SuppressWarnings("unchecked")
    private Object extractPropertyValue(IdEObject prop) {
        String type = prop.eClass().getName();

        if ("IfcPropertySingleValue".equals(type)) {
            IdEObject nominal = getObject(prop, "NominalValue");
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
                IdEObject raw = getObject(prop, a);
                if (raw != null) vals.add(unwrapIfcValue(raw));
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

    //---------- HELPER --------------------

    private String unwrapTypeName(Object v) {
        if (v == null) return null;
        if (v instanceof IdEObject e) {
            return e.eClass().getName(); // e.g. IfcLabel, IfcLengthMeasure, etc.
        }
        return v.getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
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
        Object wf = tryGet(ifcValue, "wrappedValue");
        if (wf != null) return wf;
        return ifcValue.toString();
    }

    private static boolean isNonEmptyValue(Object v) {
        if (v == null) return false;
        if (v instanceof CharSequence cs) return cs.length() > 0;
        if (v instanceof List<?> l) return !l.isEmpty();

        // Handle BIMserver Tristate/IfcLogical unknown values as empty
        if (v instanceof Enum<?> en) {
            String n = en.name();
            if ("UNKNOWN".equalsIgnoreCase(n) || "UNDEFINED".equalsIgnoreCase(n)) return false;
        }
        String s = String.valueOf(v).trim();
        if ("UNKNOWN".equalsIgnoreCase(s) || "UNDEFINED".equalsIgnoreCase(s)) {
            return false;
        }

        return true;
    }

}
