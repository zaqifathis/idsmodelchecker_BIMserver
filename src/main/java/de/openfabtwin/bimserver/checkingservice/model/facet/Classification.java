package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;

public record Classification(String system, ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
