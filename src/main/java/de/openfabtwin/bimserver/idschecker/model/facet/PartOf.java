package de.openfabtwin.bimserver.idschecker.model.facet;

import de.openfabtwin.bimserver.idschecker.model.Value;
import de.openfabtwin.bimserver.idschecker.model.result.PartOfResult;
import de.openfabtwin.bimserver.idschecker.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;

import java.util.*;

import static de.openfabtwin.bimserver.idschecker.model.facet.Facet.Cardinality.*;

public class PartOf extends Facet {

    private final Value name;
    private final Value predefinedType;
    private final String relation;
    private final String instructions;

    public PartOf(Value name, Value predefinedType, String relation, String cardinality, String instructions){
        this.name = name;
        this.predefinedType = predefinedType;
        this.relation = (relation != null) ? relation.toUpperCase(Locale.ROOT).trim() : null;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        String rel = (relation != null) ? relation : "?";
        if (name != null && predefinedType != null) {
            this.applicability_templates = "An element with an " + rel + " relationship with an " + name.extract();
            this.requirement_templates   = "An element must have an " + rel + " relationship with an " + name.extract() + " of predefined type " + predefinedType.extract();
            this.prohibited_templates    = "An element must not have an " + rel + " relationship with an " + name.extract();
        } else if (name != null) {
            this.applicability_templates = "An element with an " + rel + " relationship with an " + name.extract();
            this.requirement_templates   = "An element must have an " + rel + " relationship with an " + name.extract();
            this.prohibited_templates    = "An element must not have an " + rel + " relationship with an " + name.extract();
        } else {
            this.applicability_templates = "An element with an " + rel + " relationship";
            this.requirement_templates   = "An element must have an " + rel + " relationship";
            this.prohibited_templates    = "An element must not have an " + rel + " relationship";
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
        boolean isPass;
        Map<String, Object> reason = null;

        if (relation == null || relation.isBlank()) {
            isPass = false;
            List<String> ancestors = new ArrayList<>();
            IdEObject parent = getParent(element);
            while (parent != null) {
                String parentName = parent.eClass().getName().toUpperCase(Locale.ROOT);
                ancestors.add(parentName);
                if (name != null && name.matches(parentName)) {
                    if (predefinedType != null) {
                        String[] actualOut = {""};
                        isPass = matchesPredefinedType(parent, actualOut);
                        if (isPass) ancestors.set(ancestors.size() - 1, parentName + "." + actualOut[0]);
                    } else {
                        isPass = true;
                    }
                    break;
                }
                parent = getParent(parent);
            }
            if (!isPass) reason = Map.of("type", "ENTITY", "actual", ancestors);

        } else {
            switch (relation) {

                case "IFCRELAGGREGATES" -> {
                    IdEObject aggregate = getAggregate(element);
                    isPass = aggregate != null;
                    if (!isPass) {
                        reason = Map.of("type", "NOVALUE");
                    } else if (name != null) {
                        isPass = false;
                        List<String> ancestors = new ArrayList<>();
                        while (aggregate != null) {
                            String aggName = aggregate.eClass().getName().toUpperCase(Locale.ROOT);
                            ancestors.add(aggName);
                            if (name.matches(aggName)) {
                                if (predefinedType != null) {
                                    String[] actualOut = {""};
                                    isPass = matchesPredefinedType(aggregate, actualOut);
                                    if (isPass) ancestors.set(ancestors.size() - 1, aggName + "." + actualOut[0]);
                                } else {
                                    isPass = true;
                                }
                                break;
                            }
                            aggregate = getAggregate(aggregate);
                        }
                        if (!isPass) reason = Map.of("type", "ENTITY", "actual", ancestors);
                    }
                }

                case "IFCRELASSIGNSTOGROUP" -> {
                    IdEObject group = getGroup(element);
                    isPass = group != null;
                    if (!isPass) {
                        reason = Map.of("type", "NOVALUE");
                    } else if (name != null) {
                        String groupName = group.eClass().getName().toUpperCase(Locale.ROOT);
                        if (!name.matches(groupName)) {
                            isPass = false;
                            reason = Map.of("type", "ENTITY", "actual", groupName);
                        } else if (predefinedType != null) {
                            String[] actualOut = {""};
                            isPass = matchesPredefinedType(group, actualOut);
                            if (!isPass) reason = Map.of("type", "PREDEFINEDTYPE", "actual", actualOut[0]);
                        }
                    }
                }

                case "IFCRELCONTAINEDINSPATIALSTRUCTURE" -> {
                    IdEObject container = getContainer(element);
                    isPass = container != null;
                    if (!isPass) {
                        reason = Map.of("type", "NOVALUE");
                    } else if (name != null) {
                        String containerName = container.eClass().getName().toUpperCase(Locale.ROOT);
                        if (!name.matches(containerName)) {
                            isPass = false;
                            reason = Map.of("type", "ENTITY", "actual", containerName);
                        } else if (predefinedType != null) {
                            String[] actualOut = {""};
                            isPass = matchesPredefinedType(container, actualOut);
                            if (!isPass) reason = Map.of("type", "PREDEFINEDTYPE", "actual", actualOut[0]);
                        }
                    }
                }

                case "IFCRELNESTS" -> {
                    IdEObject nest = getNest(element);
                    isPass = nest != null;
                    if (!isPass) {
                        reason = Map.of("type", "NOVALUE");
                    } else if (name != null) {
                        isPass = false;
                        List<String> ancestors = new ArrayList<>();
                        while (nest != null) {
                            String nestName = nest.eClass().getName().toUpperCase(Locale.ROOT);
                            ancestors.add(nestName);
                            if (name.matches(nestName)) {
                                if (predefinedType != null) {
                                    String[] actualOut = {""};
                                    isPass = matchesPredefinedType(nest, actualOut);
                                    if (isPass) ancestors.set(ancestors.size() - 1, nestName + "." + actualOut[0]);
                                } else {
                                    isPass = true;
                                }
                                break;
                            }
                            nest = getNest(nest);
                        }
                        if (!isPass) reason = Map.of("type", "ENTITY", "actual", ancestors);
                    }
                }

                case "IFCRELVOIDSELEMENT IFCRELFILLSELEMENT" -> {
                    IdEObject buildingElement = null;
                    if ("IfcOpeningElement".equals(element.eClass().getName())) {
                        buildingElement = getVoidedElement(element);
                    } else {
                        IdEObject opening = getFilledVoid(element);
                        if (opening != null) buildingElement = getVoidedElement(opening);
                    }
                    isPass = buildingElement != null;
                    if (!isPass) {
                        reason = Map.of("type", "NOVALUE");
                    } else if (name != null) {
                        String beName = buildingElement.eClass().getName().toUpperCase(Locale.ROOT);
                        if (!name.matches(beName)) {
                            isPass = false;
                            reason = Map.of("type", "ENTITY", "actual", beName);
                        } else if (predefinedType != null) {
                            String[] actualOut = {""};
                            isPass = matchesPredefinedType(buildingElement, actualOut);
                            if (!isPass) reason = Map.of("type", "PREDEFINEDTYPE", "actual", actualOut[0]);
                        }
                    }
                }

                default -> {
                    isPass = false;
                    reason = Map.of("type", "NOVALUE");
                }
            }
        }

        if (cardinality == PROHIBITED) {
            return new PartOfResult(!isPass, Map.of("type", "PROHIBITED"));
        }
        return new PartOfResult(isPass, reason);
    }

    private IdEObject getParent(IdEObject element) {
        List<?> decomposes = getList(element, "Decomposes");
        if (decomposes != null) {
            for (Object o : decomposes) {
                if (!(o instanceof IdEObject rel)) continue;
                if ("IfcRelAggregates".equals(rel.eClass().getName())) {
                    IdEObject parent = getIdEObject(rel, "RelatingObject");
                    if (parent != null) return parent;
                }
            }
        }
        List<?> nests = getList(element, "Nests");
        if (nests != null) {
            for (Object o : nests) {
                if (!(o instanceof IdEObject rel)) continue;
                if ("IfcRelNests".equals(rel.eClass().getName())) {
                    IdEObject parent = getIdEObject(rel, "RelatingObject");
                    if (parent != null) return parent;
                }
            }
        }
        List<?> assignments = getList(element, "HasAssignments");
        if (assignments != null) {
            for (Object o : assignments) {
                if (!(o instanceof IdEObject rel)) continue;
                if ("IfcRelAssignsToGroup".equals(rel.eClass().getName())) {
                    IdEObject group = getIdEObject(rel, "RelatingGroup");
                    if (group != null) return group;
                }
            }
        }
        return null;
    }

    private IdEObject getAggregate(IdEObject element) {
        List<?> decomposes = getList(element, "Decomposes");
        if (decomposes == null) return null;
        for (Object o : decomposes) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelAggregates".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingObject");
            }
        }
        return null;
    }

    private IdEObject getContainer(IdEObject element) {
        List<?> contained = getList(element, "ContainedInStructure");
        if (contained == null) return null;
        for (Object o : contained) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelContainedInSpatialStructure".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingStructure");
            }
        }
        return null;
    }

    private IdEObject getNest(IdEObject element) {
        List<?> nests = getList(element, "Nests");
        if (nests == null) return null;
        for (Object o : nests) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelNests".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingObject");
            }
        }
        return null;
    }

    private IdEObject getGroup(IdEObject element) {
        List<?> assignments = getList(element, "HasAssignments");
        if (assignments == null) return null;
        for (Object o : assignments) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelAssignsToGroup".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingGroup");
            }
        }
        return null;
    }

    private IdEObject getVoidedElement(IdEObject opening) {
        List<?> voids = getList(opening, "VoidsElements");
        if (voids == null) return null;
        for (Object o : voids) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelVoidsElement".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingBuildingElement");
            }
        }
        return null;
    }

    private IdEObject getFilledVoid(IdEObject element) {
        List<?> fills = getList(element, "FillsVoids");
        if (fills == null) return null;
        for (Object o : fills) {
            if (!(o instanceof IdEObject rel)) continue;
            if ("IfcRelFillsElement".equals(rel.eClass().getName())) {
                return getIdEObject(rel, "RelatingOpeningElement");
            }
        }
        return null;
    }

    private boolean matchesPredefinedType(IdEObject element, String[] actualOut) {
        String pdef = getString(element, "PredefinedType");
        if ("USERDEFINED".equals(pdef)) {
            String val = getString(element, "ObjectType");
            actualOut[0] = val != null ? val : "";
            return predefinedType != null && predefinedType.matches(val);
        }
        actualOut[0] = pdef != null ? pdef : "";
        return predefinedType != null && predefinedType.matches(pdef);
    }

}
