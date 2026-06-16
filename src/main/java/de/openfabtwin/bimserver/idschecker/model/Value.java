package de.openfabtwin.bimserver.idschecker.model;

import java.math.BigDecimal;

sealed public interface Value permits SimpleValue, RestrictionValue {

    boolean matches(String candidate);

    String extract();

    static BigDecimal parseBigDecimal(String s) {
        if (s == null) return null;
        s = s.trim();
        // IFC/EXPRESS reals may be written with a trailing dot ("42."); BigDecimal rejects that.
        if (s.endsWith(".")) s = s + "0";
        try { return new BigDecimal(s); }
        catch (NumberFormatException ex) { return null; }
    }
}

