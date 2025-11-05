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
import java.util.function.Consumer;

import static java.util.Collections.emptyList;

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

        if (propertySet instanceof SimpleValue) {
            Map<String, Object> one = getPset(element);
            psets = (one == null) ? Map.of() : Map.of(propertySet.extract(), one);
        } else {
//            Map<String, Map<String, Object>> all = getPsets(element);
//            Map<String, Map<String, Object>> filtered = new LinkedHashMap<>();
//            for (var e : all.entrySet()) {
//                if (propertySet.matches(e.getKey())) filtered.put(e.getKey(), e.getValue());
//            }
//            psets = filtered;
        }

        boolean isPass = false;
        Map<String, Object> reason = new HashMap<>();

        return new PropertyResult(isPass, reason);
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
                    map.put("id", pdef.getOid());

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
        var isTypedByF = element.eClass().getEStructuralFeature("IsTypedBy");
        if (isTypedByF != null) {
            List<IdEObject> typeRels = (List<IdEObject>) element.eGet(isTypedByF);
            if (typeRels != null) {
                for (IdEObject r : typeRels) {
                    if (!"IfcRelDefinesByType".equals(r.eClass().getName())) continue;
                    IdEObject typeObj = getObject(r, "RelatingType");
                    if (typeObj == null) continue;

                    var hasPsetsF = typeObj.eClass().getEStructuralFeature("HasPropertySets");
                    if (hasPsetsF == null) continue;
                    List<IdEObject> psets = (List<IdEObject>) typeObj.eGet(hasPsetsF);
                    if (psets == null) continue;

                    for (IdEObject ps : psets) {
                        if (!"IfcPropertySet".equals(ps.eClass().getName())) continue;
                        String name = getString(ps, "Name");
                        if (name != null && propertySet.matches(name)) {
                            Map<String, Object> map = new LinkedHashMap<>();
                            map.put("id", ps.getOid());
                            var hasPropsF2 = ps.eClass().getEStructuralFeature("HasProperties");
                            if (hasPropsF2 != null) {
                                List<IdEObject> props = (List<IdEObject>) ps.eGet(hasPropsF2);
                                if (props != null) {
                                    for (IdEObject prop : props) {
                                        String pn = getString(prop, "Name");
                                        if (pn == null) continue;
                                        map.put(pn, extractPropertyValue(prop));
                                    }
                                }
                            }
                            return map;
                        }
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
    private Object extractPropertyValue(IdEObject prop) {
        String type = prop.eClass().getName();
        List<Object> vals = new ArrayList<>();

        if ("IfcPropertySingleValue".equals(type)) {
            IdEObject nominal = getObject(prop, "NominalValue");
            vals.add(unwrapIfcValue(nominal));
        } else if ("IfcPropertyListValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"ListValues");
            vals.addAll(unwrapList(list));
        } else if ("IfcPropertyEnumeratedValue".equals(type)) {
            List<Object> list = (List<Object>) getList(prop,"EnumerationValues");
            vals.addAll(unwrapList(list));
        } else if ("IfcPropertyBoundedValue".equals(type)) {
            for (String a : new String[]{"UpperBoundValue", "LowerBoundValue", "SetPointValue"}) {
                IdEObject raw = getObject(prop, a);
                if (raw != null) vals.add(unwrapIfcValue(raw));
            }
        } else if ("IfcPropertyTableValue".equals(type)) {
            List<Object> def = (List<Object>) prop.eGet(prop.eClass().getEStructuralFeature("DefiningValues"));
            List<Object> ded = (List<Object>) prop.eGet(prop.eClass().getEStructuralFeature("DefinedValues"));
            if (def != null) for (Object v : def) vals.add(unwrapIfValue(v));
            if (ded != null) for (Object v : ded) vals.add(unwrapIfValue(v));
        }
        return vals;
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
        var wf = ifcValue.eClass().getEStructuralFeature("wrappedValue");
        if (wf != null) return ifcValue.eGet(wf);
        return ifcValue.toString();
    }

}
