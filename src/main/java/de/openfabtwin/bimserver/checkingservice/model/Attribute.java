package de.openfabtwin.bimserver.checkingservice.model;

public record Attribute(String name, ValueOrRestriction value, String cardinality, String instructions) implements Facet {}
