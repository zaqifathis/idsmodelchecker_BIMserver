package de.openfabtwin.bimserver.checkingservice.model;

import java.util.List;

//TODO:add pattern
public record RestrictionValue(List<String> enums) implements Value {}
