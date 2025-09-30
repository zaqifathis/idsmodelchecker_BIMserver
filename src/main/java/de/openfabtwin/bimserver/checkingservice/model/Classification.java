package de.openfabtwin.bimserver.checkingservice.model;

public record Classification(String system, ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
