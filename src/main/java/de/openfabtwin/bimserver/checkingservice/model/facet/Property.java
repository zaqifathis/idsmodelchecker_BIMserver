package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.ValueOrRestriction;

public record Property(String pset, String baseName, ValueOrRestriction value, String dataType, String uri, String cardinality, String instructions) implements Facet {}
