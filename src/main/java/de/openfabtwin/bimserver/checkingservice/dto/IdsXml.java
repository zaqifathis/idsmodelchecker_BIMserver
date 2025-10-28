package de.openfabtwin.bimserver.checkingservice.dto;

import javax.xml.bind.annotation.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@XmlRootElement(name = "ids")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdsXml {

    private InfoXml info;
    private SpecificationsXml specifications;

    public InfoXml getInfo() { return info; }
    public SpecificationsXml getSpecifications() { return specifications; }

    // ---------- <info> ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"title","copyright","version","description","author","date","purpose","milestone"})
    public static class InfoXml {
        private String title;
        private String copyright;
        private String version;
        private String description;
        private String author;

        @XmlSchemaType(name = "date")
        private LocalDate date;

        private String purpose;
        private String milestone;

        public String getTitle() { return title; }
        public String getCopyright() { return copyright; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public LocalDate getDate() { return date; }
        public String getPurpose() { return purpose; }
        public String getMilestone() { return milestone; }
    }

    // ---------- <specifications> ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SpecificationsXml {
        @XmlElement(name = "specification")
        private List<SpecificationXml> specification = new ArrayList<>();
        public List<SpecificationXml> getSpecification() { return specification; }
    }

    // ---------- <specification> (ids:specificationType) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"applicability", "requirements"})
    public static class SpecificationXml {
        private ApplicabilityXml applicability;     // required
        private RequirementsXml
                requirements;       // optional

        @XmlAttribute(name = "name", required = true)
        public String name;

        // xs:list → @XmlList List<String>
        @XmlAttribute(name = "ifcVersion", required = true)
        @XmlList
        public List<String> ifcVersion = new ArrayList<>();

        @XmlAttribute public String identifier;
        @XmlAttribute public String description;
        @XmlAttribute public String instructions;

        public ApplicabilityXml getApplicability() { return applicability; }
        public RequirementsXml  getRequirements()  { return requirements; }
    }

    // ---------- ids:applicabilityType ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ApplicabilityXml {
        @XmlElement(name = "entity")        public EntityXml entity;                     // 0..1
        @XmlElement(name = "partOf")        public List<PartOfXml> partOf        = new ArrayList<>();
        @XmlElement(name = "classification")public List<ClassificationXml> classification = new ArrayList<>();
        @XmlElement(name = "attribute")     public List<AttributeXml> attribute   = new ArrayList<>();
        @XmlElement(name = "property")      public List<PropertyXml> property     = new ArrayList<>();
        @XmlElement(name = "material")      public List<MaterialXml> material     = new ArrayList<>();

        // xs:occurs attributeGroup → carry as strings (e.g., "0", "1", "unbounded")
        @XmlAttribute public String minOccurs;
        @XmlAttribute public String maxOccurs;
    }

    // ---------- ids:requirementsType (+ extension on <requirements> adds @description) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RequirementsXml {
        @XmlElement(name = "entity")        public List<EntityXml> entity;                     // 0..1 (extended with @instructions)
        @XmlElement(name = "partOf")        public List<PartOfXml> partOf        = new ArrayList<>();
        @XmlElement(name = "classification")public List<ClassificationXml> classification = new ArrayList<>();
        @XmlElement(name = "attribute")     public List<AttributeXml> attribute   = new ArrayList<>();
        @XmlElement(name = "property")      public List<PropertyXml> property     = new ArrayList<>();
        @XmlElement(name = "material")      public List<MaterialXml> material     = new ArrayList<>();

        // extension on the <requirements> element itself:
        @XmlAttribute(name = "description")
        public String description;
    }

    // ---------- ids:entityType (+ requirements extension allows @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class EntityXml {
        @XmlElement(name = "name")
        public IdsValueXml name;           // required
        @XmlElement(name = "predefinedType")
        public IdsValueXml predefinedType; // optional

        // Only used under <requirements> (extension), keep optional so applicability still works
        @XmlAttribute(name = "instructions")
        public String instructions;
    }

    // ---------- ids:partOfType (+ requirements extension: @cardinality, @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PartOfXml {
        @XmlElement(name = "entity") public EntityXml entity;   // required

        // base type:
        @XmlAttribute public String relation;                   // ids:relations

        // requirements extension:
        @XmlAttribute public String cardinality;                // ids:simpleCardinality
        @XmlAttribute public String instructions;
    }

    // ---------- ids:classificationType (+ req: @uri, @cardinality, @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ClassificationXml {
        @XmlElement(name = "value")  public IdsValueXml value;   // optional
        @XmlElement(name = "system") public IdsValueXml system;  // required

        @XmlAttribute public String uri;            // anyURI
        @XmlAttribute public String cardinality;    // ids:conditionalCardinality
        @XmlAttribute public String instructions;
    }

    // ---------- ids:attributeType (+ req: @cardinality, @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class AttributeXml {
        @XmlElement(name = "name")  public IdsValueXml name;     // required
        @XmlElement(name = "value") public IdsValueXml value;    // optional

        @XmlAttribute public String cardinality;    // ids:conditionalCardinality
        @XmlAttribute public String instructions;
    }

    // ---------- ids:propertyType (+ req: @uri, @cardinality, @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PropertyXml {
        @XmlElement(name = "propertySet") public IdsValueXml propertySet; // required
        @XmlElement(name = "baseName")    public IdsValueXml baseName;    // required
        @XmlElement(name = "value")       public IdsValueXml value;       // optional

        @XmlAttribute public String dataType;       // ids:upperCaseName
        @XmlAttribute public String uri;            // anyURI
        @XmlAttribute public String cardinality;    // ids:conditionalCardinality
        @XmlAttribute public String instructions;
    }

    // ---------- ids:materialType (+ req: @uri, @cardinality, @instructions) ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MaterialXml {
        @XmlElement(name = "value") public IdsValueXml value;    // optional

        @XmlAttribute public String uri;            // anyURI
        @XmlAttribute public String cardinality;    // ids:conditionalCardinality
        @XmlAttribute public String instructions;
    }

    // ---------- ids:idsValue = <simpleValue> | xs:restriction ----------
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class IdsValueXml {
        @XmlElement(name = "simpleValue") public String simpleValue;

        // NOTE: restriction lives in the XML Schema namespace
        @XmlElement(name = "restriction", namespace = "http://www.w3.org/2001/XMLSchema")
        public RestrictionXml restriction;
    }

    //TODO: add Bounds and Length
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RestrictionXml {
        @XmlAttribute public String base; // often omitted in IDS

        @XmlElement(name = "enumeration", namespace = "http://www.w3.org/2001/XMLSchema")
        public List<EnumFacetXml> enumeration = new ArrayList<>();

        @XmlElement(name = "pattern", namespace = "http://www.w3.org/2001/XMLSchema")
        public PatternFacetXml pattern;

        @XmlElement(name = "minInclusive", namespace = "http://www.w3.org/2001/XMLSchema")
        public BoundFacetXml minInclusive;

        @XmlElement(name = "maxInclusive", namespace = "http://www.w3.org/2001/XMLSchema")
        public BoundFacetXml maxInclusive;

        @XmlElement(name = "minExclusive", namespace = "http://www.w3.org/2001/XMLSchema")
        public BoundFacetXml minExclusive;

        @XmlElement(name = "maxExclusive", namespace = "http://www.w3.org/2001/XMLSchema")
        public BoundFacetXml maxExclusive;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class EnumFacetXml {
        @XmlAttribute(name = "value") public String value;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PatternFacetXml {
        @XmlAttribute(name = "value") public String value;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BoundFacetXml {
        @XmlAttribute(name = "value")
        public String value;
    }


}
