package de.openfabtwin.bimserver.checkingservice.model.facet;

public record PartOf(String name, String predefinedType, String relation, String cardinality, String instructions) implements Facet {}
