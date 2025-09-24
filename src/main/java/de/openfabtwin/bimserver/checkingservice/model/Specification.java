package de.openfabtwin.bimserver.checkingservice.model;

import org.bimserver.emf.IdEObject;

import java.util.ArrayList;
import java.util.List;

public class Specification {
    String name = "Unnamed";
    List<IfcVersion> ifcVersion = new ArrayList<>();
    String identifier, description, instructions;
    String minOccurs;
    String maxOccurs;
    List<Facet> applicability = new ArrayList<>();
    List<Facet> requirements  = new ArrayList<>();

    List<IdEObject> applicable_entities = new ArrayList<>();
    List<IdEObject> passed_entities   = new ArrayList<>();
    List<IdEObject> failed_entities   = new ArrayList<>();
    Boolean status = null; // null=not checked, true=passed, false=failed
    Boolean is_ifc_version_supported = null; // null=not checked, true=supported, false=not supported

    public String getName() { return name; }
    public List<IfcVersion> getIfcVersion() { return ifcVersion; }
    public String getIdentifier() { return identifier; }
    public String getDescription() { return description; }
    public String getInstructions() { return instructions; }
    public String getMinOccurs() { return minOccurs; }
    public String getMaxOccurs() { return maxOccurs; }
    public List<Facet> getApplicability() { return applicability; }
    public List<Facet> getRequirements() { return requirements; }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Unnamed" : name;
    }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setDescription(String description) { this.description = description; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setMinOccurs(String minOccurs) { this.minOccurs = minOccurs; }
    public void setMaxOccurs(String maxOccurs) { this.maxOccurs = maxOccurs; }
}


interface Facet {}
record EntityFacet(String name, String predefinedType, String instructions) implements Facet {}
record PropertyFacet(String pset, String baseName, ValueOrRestriction value, String dataType, String uri, String cardinality, String instructions) implements Facet {}
record AttributeFacet(String name, ValueOrRestriction value, String cardinality, String instructions) implements Facet {}
record ClassificationFacet(String system, ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
record MaterialFacet(ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
record PartOfFacet(String name, String predefinedType, String relation, String cardinality, String instructions) implements Facet {}
// Value that can be a simple string or a restriction
sealed interface ValueOrRestriction permits SimpleValue, RestrictionValue {}
record SimpleValue(String value) implements ValueOrRestriction {}
record RestrictionValue(List<String> enums, List<String> patterns) implements ValueOrRestriction {}


enum IfcVersion {
    IFC2X3,
    IFC4,
    IFC4X3_ADD2;

    public static IfcVersion fromString(String s) {
        return switch (s.trim().toUpperCase()) {
            case "IFC2X3" -> IFC2X3;
            case "IFC4" -> IFC4;
            case "IFC4X3_ADD2" -> IFC4X3_ADD2;
            default -> throw new IllegalArgumentException("Unknown IFC version: " + s);
        };
    }
}