package de.openfabtwin.bimserver.checkingservice.model;

import java.util.List;

// Value that can be a simple string or a restriction
sealed public interface Value permits SimpleValue, RestrictionValue {}

record SimpleValue(String value) implements Value {}
record RestrictionValue(List<String> enums) implements Value {} //TODO:add pattern