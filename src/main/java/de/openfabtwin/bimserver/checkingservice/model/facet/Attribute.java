package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;

public record Attribute(String name, ValueOrRestriction value, String cardinality, String instructions) implements Facet {}
