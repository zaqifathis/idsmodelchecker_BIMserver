package de.openfabtwin.bimserver.checkingservice.model;

import java.util.List;

// Value that can be a simple string or a restriction
sealed public interface ValueOrRestriction permits SimpleValue, RestrictionValue {}

record SimpleValue(String value) implements ValueOrRestriction {}
record RestrictionValue(List<String> enums, List<String> patterns) implements ValueOrRestriction {}