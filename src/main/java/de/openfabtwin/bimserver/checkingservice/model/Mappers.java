package de.openfabtwin.bimserver.checkingservice.model;

import de.openfabtwin.bimserver.checkingservice.dto.IdsXml;
import de.openfabtwin.bimserver.checkingservice.model.facet.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Mappers {

    public static Specification mapSpec(IdsXml.SpecificationXml spXml) {
        Specification s = new Specification();

        // attributes
        if (spXml.name != null && !spXml.name.isBlank()) s.setName(spXml.name);
        if (spXml.ifcVersion != null) {
            for (String v : spXml.ifcVersion) {
                s.getIfcVersion().add(Specification.ifcVersionFromString(v));

            }
        }

        s.setIdentifier(spXml.identifier);
        s.setDescription(spXml.description);
        s.setInstructions(spXml.instructions);

        // applicability (required in XSD)
        if (spXml.getApplicability() != null) {
            var ax = spXml.getApplicability();
            s.setMinOccurs(Objects.requireNonNullElse(ax.minOccurs, "1"));
            s.setMaxOccurs(ax.maxOccurs);

            if (ax.entity != null) s.getApplicability().add(mapEntity(ax.entity));
            for (IdsXml.PartOfXml po : ax.partOf) s.getApplicability().add(mapPartOf(po));
            for (IdsXml.ClassificationXml cx : ax.classification) s.getApplicability().add(mapClassification(cx));
            for (IdsXml.AttributeXml at : ax.attribute) s.getApplicability().add(mapAttribute(at));
            for (IdsXml.PropertyXml px : ax.property) s.getApplicability().add(mapProperty(px));
            for (IdsXml.MaterialXml mx : ax.material) s.getApplicability().add(mapMaterial(mx));
        }

        // requirements (optional)
        if (spXml.getRequirements() != null) {
            var rx = spXml.getRequirements();

            if (rx.entity != null) {
                for (IdsXml.EntityXml e : rx.entity) {
                    s.getRequirements().add(mapEntity(e));
                }
            }

            for (IdsXml.PartOfXml po : rx.partOf) {
                s.getRequirements().add(mapPartOf(po));
                s.setCardinality(po.cardinality);
            }
            for (IdsXml.ClassificationXml cx : rx.classification) {
                s.getRequirements().add(mapClassification(cx));
                s.setCardinality(cx.cardinality);
            }
            for (IdsXml.AttributeXml at : rx.attribute) {
                s.getRequirements().add(mapAttribute(at));
                s.setCardinality(at.cardinality);
            }
            for (IdsXml.PropertyXml px : rx.property) {
                s.getRequirements().add(mapProperty(px));
                s.setCardinality(px.cardinality);
            }
            for (IdsXml.MaterialXml mx : rx.material) {
                s.getRequirements().add(mapMaterial(mx));
                s.setCardinality(mx.cardinality);
            }

            if (rx.description != null && (s.getDescription() == null || s.getDescription().isBlank())) {
                s.setDescription(rx.description);
            }
        }

        return s;
    }

    public static Entity mapEntity(IdsXml.EntityXml e) {
        return new Entity(
                value(e.name),
                value(e.predefinedType),
                e.instructions
        );
    }

    public static PartOf mapPartOf(IdsXml.PartOfXml po) {
        return new PartOf(
                value(po.entity.name),
                value(po.entity.predefinedType),
                po.relation,
                po.cardinality,
                po.instructions
        );
    }

    public static Classification mapClassification(IdsXml.ClassificationXml c) {
        return new Classification(
                value(c.value),
                value(c.system),
                c.uri,
                c.cardinality,
                c.instructions
        );
    }

    public static Attribute mapAttribute(IdsXml.AttributeXml a) {
        return new Attribute(
                value(a.name),
                value(a.value),
                a.cardinality,
                a.instructions
        );
    }

    public static Property mapProperty(IdsXml.PropertyXml p) {
        return new Property(
                value(p.propertySet),
                value(p.baseName),
                value(p.value),
                up(p.dataType), p.uri,
                p.cardinality,
                p.instructions
        );
    }

    public static Material mapMaterial(IdsXml.MaterialXml m) {
        return new Material(
                value(m.value),
                m.uri,
                m.cardinality,
                m.instructions
        );
    }


    //helpers for value/restriction
    private static String up(String s) { return s == null ? null : s.trim().toUpperCase(); }
    private static String defCard(String c) { return (c == null || c.isBlank()) ? "required" : c; }

//    private static String text(IdsXml.IdsValueXml v) {
//        if (v == null) return null;
//        if (v.simpleValue != null && !v.simpleValue.isBlank()) return v.simpleValue;
//        return null;
//    }

    private static Value value(IdsXml.IdsValueXml v) {
        if (v == null) return null;
        if (v.simpleValue != null) return new SimpleValue(v.simpleValue);

        if (v.restriction != null) {
            List<String> enums = new ArrayList<>();
            if (v.restriction.enumeration != null) for (IdsXml.EnumFacetXml e : v.restriction.enumeration) enums.add(e.value);
            //String pattern = v.restriction.pattern.value != null ? v.restriction.pattern.value : null; TODO: next add pattern
            return new RestrictionValue(enums);
        }
        return null;
    }


}
