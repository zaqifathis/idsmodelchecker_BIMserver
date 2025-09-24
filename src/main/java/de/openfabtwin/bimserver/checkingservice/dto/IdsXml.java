package de.openfabtwin.bimserver.checkingservice.dto;

import javax.xml.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@XmlRootElement(name = "ids")
@XmlAccessorType(XmlAccessType.FIELD)
public class IdsXml {
    private InfoXml info;
    private SpecificationsXml specifications;

    public InfoXml getInfo() {return info;}
    public void setInfo(InfoXml info) {this.info = info;}

    public SpecificationsXml getSpecifications() {return specifications;}
    public void setSpecifications(SpecificationsXml specifications) {this.specifications = specifications;}

    // --- Nested classes --- //

    @XmlAccessorType(XmlAccessType.FIELD)
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

        public String getTitle() { return title != null ? title : "Untitled"; }
        public String getCopyright() { return copyright; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public String getAuthor() { return author; }
        public LocalDate getDate() { return date; }
        public String getPurpose() { return purpose; }
        public String getMilestone() { return milestone; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SpecificationsXml {
        @XmlElement(name = "specification")
        private List<SpecificationXml> specification = new ArrayList<>();

        public List<SpecificationXml> getSpecification() { return specification; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SpecificationXml {
        @XmlAttribute public String name;
        @XmlAttribute(name="ifcVersion") public String ifcVersion;
        private ApplicabilityXml applicability;
        private RequirementsXml requirements;

        public ApplicabilityXml getApplicability() { return applicability; }
        public RequirementsXml getRequirements() { return requirements; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ApplicabilityXml {
        @XmlElement(name="entity")
        public List<EntityXml> entity = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RequirementsXml {
        @XmlElement(name="entity")
        public List<EntityXml> entity = new ArrayList<>();
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class EntityXml {
        public NameXml name;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NameXml {
        @XmlElement(name="simpleValue")
        public String simpleValue;
    }

}

