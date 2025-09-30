package de.openfabtwin.bimserver.checkingservice.model;

public record Material(ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
