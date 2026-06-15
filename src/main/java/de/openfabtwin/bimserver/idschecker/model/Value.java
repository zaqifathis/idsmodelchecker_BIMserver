package de.openfabtwin.bimserver.checkingservice.model;

import java.math.BigDecimal;

sealed public interface Value permits SimpleValue, RestrictionValue {

    boolean matches(String candidate);

    String extract();

    static BigDecimal parseBigDecimal(String s) {
        if (s == null) return null;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
}

