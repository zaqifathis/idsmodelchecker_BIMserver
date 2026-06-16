package de.openfabtwin.bimserver.idschecker.model.facet;

import de.openfabtwin.bimserver.idschecker.model.Value;
import de.openfabtwin.bimserver.idschecker.model.result.MaterialResult;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

import static de.openfabtwin.bimserver.idschecker.model.facet.Facet.Cardinality.*;

public class Material extends Facet {

    private final Value value;
    private final String uri;
    private final String instructions;

    public Material(Value value, String uri, String cardinality, String instructions) {
        this.value = value;
        this.uri = uri;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (value == null) {
            this.applicability_templates = "All data with a material";
            this.requirement_templates   = "Shall have a material";
            this.prohibited_templates    = "Shall not have a material";
        } else {
            this.applicability_templates = "All data with a " + value.extract() + " material";
            this.requirement_templates   = "Shall have a material of " + value.extract();
            this.prohibited_templates    = "Shall not have a material of " + value.extract();
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        EClassifier c = model.getPackageMetaData().getEPackage().getEClassifier("IfcObjectDefinition");
        if (c instanceof EClass ec) {
            for (IdEObject inst : model.getAllWithSubTypes(ec)) {
                if (seen.add(inst.getOid())) results.add(inst);
            }
        }
        return results;
    }

    @Override
    public Result matches(IfcModelInterface model, IdEObject element) {

        IdEObject material = getMaterial(element);

        boolean isPass = material != null;
        Map<String, Object> reason = null;

        if (!isPass) {
            if (cardinality == OPTIONAL) return new MaterialResult(true, null);
            reason = Map.of("type", "NOVALUE");
        }

        if (isPass && this.value != null) {
            Set<String> values = collectMaterialValues(material);
            isPass = values.stream().anyMatch(v -> v != null && this.value.matches(v));
            if (!isPass) {
                reason = Map.of("type", "VALUE", "actual", new ArrayList<>(values));
            }
        }

        if (cardinality == PROHIBITED) {
            return new MaterialResult(!isPass, Map.of("type", "PROHIBITED"));
        }
        return new MaterialResult(isPass, reason);
    }

    private IdEObject getMaterial(IdEObject element) {
        // Direct association on the occurrence...
        IdEObject direct = materialFrom(element);
        if (direct != null) return direct;
        // ...otherwise inherit from the defining type (spec: occurrences inherit type materials).
        for (IdEObject type : definingTypes(element)) {
            IdEObject inherited = materialFrom(type);
            if (inherited != null) return inherited;
        }
        return null;
    }

    private IdEObject materialFrom(IdEObject obj) {
        List<?> rels = getList(obj, "HasAssociations");
        if (rels == null) return null;

        for (Object o : rels) {
            if (!(o instanceof IdEObject rel)) continue;
            if (!"IfcRelAssociatesMaterial".equals(rel.eClass().getName())) continue;

            IdEObject materialSelect = getIdEObject(rel, "RelatingMaterial");
            if (materialSelect == null) continue;

            return skipUsage(materialSelect);
        }
        return null;
    }

    private List<IdEObject> definingTypes(IdEObject element) {
        List<IdEObject> types = new ArrayList<>();
        List<?> rels = getList(element, "IsTypedBy");
        if (rels != null) {
            for (Object o : rels) {
                if (!(o instanceof IdEObject rel)) continue;
                if (!"IfcRelDefinesByType".equals(rel.eClass().getName())) continue;
                IdEObject t = getIdEObject(rel, "RelatingType");
                if (t != null) types.add(t);
            }
        }
        return types;
    }

    private IdEObject skipUsage(IdEObject mat) {
        if (mat == null) return null;
        String type = mat.eClass().getName();
        if ("IfcMaterialLayerSetUsage".equals(type)) {
            IdEObject inner = getIdEObject(mat, "ForLayerSet");
            return inner != null ? inner : mat;
        }
        if ("IfcMaterialProfileSetUsage".equals(type)) {
            IdEObject inner = getIdEObject(mat, "ForProfileSet");
            return inner != null ? inner : mat;
        }
        return mat;
    }

    private Set<String> collectMaterialValues(IdEObject material) {
        Set<String> values = new LinkedHashSet<>();
        String type = material.eClass().getName();

        switch (type) {
            case "IfcMaterial" -> {
                addStr(values, getString(material, "Name"));
                addStr(values, getString(material, "Category"));
            }
            case "IfcMaterialList" -> {
                List<?> mats = getList(material, "Materials");
                if (mats != null) {
                    for (Object o : mats) {
                        if (!(o instanceof IdEObject mat)) continue;
                        addStr(values, getString(mat, "Name"));
                        addStr(values, getString(mat, "Category"));
                    }
                }
            }
            case "IfcMaterialLayerSet" -> {
                addStr(values, getString(material, "LayerSetName"));
                List<?> layers = getList(material, "MaterialLayers");
                if (layers != null) {
                    for (Object o : layers) {
                        if (!(o instanceof IdEObject layer)) continue;
                        addStr(values, getString(layer, "Name"));
                        addStr(values, getString(layer, "Category"));
                        IdEObject mat = getIdEObject(layer, "Material");
                        if (mat != null) {
                            addStr(values, getString(mat, "Name"));
                            addStr(values, getString(mat, "Category"));
                        }
                    }
                }
            }
            case "IfcMaterialProfileSet" -> {
                addStr(values, getString(material, "Name"));
                List<?> profiles = getList(material, "MaterialProfiles");
                if (profiles != null) {
                    for (Object o : profiles) {
                        if (!(o instanceof IdEObject profile)) continue;
                        addStr(values, getString(profile, "Name"));
                        addStr(values, getString(profile, "Category"));
                        IdEObject mat = getIdEObject(profile, "Material");
                        if (mat != null) {
                            addStr(values, getString(mat, "Name"));
                            addStr(values, getString(mat, "Category"));
                        }
                    }
                }
            }
            case "IfcMaterialConstituentSet" -> {
                addStr(values, getString(material, "Name"));
                List<?> constituents = getList(material, "MaterialConstituents");
                if (constituents != null) {
                    for (Object o : constituents) {
                        if (!(o instanceof IdEObject constituent)) continue;
                        addStr(values, getString(constituent, "Name"));
                        addStr(values, getString(constituent, "Category"));
                        IdEObject mat = getIdEObject(constituent, "Material");
                        if (mat != null) {
                            addStr(values, getString(mat, "Name"));
                            addStr(values, getString(mat, "Category"));
                        }
                    }
                }
            }
        }
        return values;
    }

    private void addStr(Set<String> set, String value) {
        if (value != null) set.add(value);
    }

}
