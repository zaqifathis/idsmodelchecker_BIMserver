package de.openfabtwin.bimserver.checkingservice.model;

public record PartOf(String name, String predefinedType, String relation, String cardinality, String instructions) implements Facet {}
