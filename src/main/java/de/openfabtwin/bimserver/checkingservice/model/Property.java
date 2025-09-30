package de.openfabtwin.bimserver.checkingservice.model;

public record Property(String pset, String baseName, ValueOrRestriction value, String dataType, String uri, String cardinality, String instructions) implements Facet {}
