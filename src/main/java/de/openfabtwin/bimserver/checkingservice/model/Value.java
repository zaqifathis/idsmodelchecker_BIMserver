package de.openfabtwin.bimserver.checkingservice.model;

sealed public interface Value permits SimpleValue, RestrictionValue {}

