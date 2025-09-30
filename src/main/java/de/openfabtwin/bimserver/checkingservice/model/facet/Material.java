package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;

public record Material(ValueOrRestriction value, String uri, String cardinality, String instructions) implements Facet {}
