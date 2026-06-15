package de.openfabtwin.bimserver.checkingservice.model;

import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.math.BigDecimal;

import static de.openfabtwin.bimserver.checkingservice.model.RestrictionValue.XsdBase.*;

public record RestrictionValue(XsdBase base, List<String> enums, String pattern, String minInclusive, String maxInclusive, String minExclusive, String maxExclusive) implements Value {
    public enum XsdBase { STRING, INTEGER, DOUBLE, BOOLEAN }

    public static XsdBase xsdBaseFromString(String s) {
        if (s.endsWith(":string") || s.equals("xs:string")) return STRING;
        if (s.endsWith(":integer") || s.equals("xs:integer") || s.endsWith(":int") || s.equals("xs:int")) return XsdBase.INTEGER;
        if (s.endsWith(":double") || s.equals("xs:double")) return DOUBLE;
        if (s.endsWith(":boolean") || s.equals("xs:boolean")) return BOOLEAN;
        return null;
    }

    @Override
    public boolean matches (String candidate) {
        if (candidate == null) return false;
        candidate = candidate.trim();

        // Check enum first
        if (enums != null && !enums.isEmpty()) {
            return enumMatches(candidate);
        }

        // Check pattern
        if (pattern != null) {
            Pattern p = Pattern.compile(pattern);
            if (!p.matcher(candidate).matches()) return false;
        }

        // Check bounds only for numeric types
        return switch (base) {
            case INTEGER, DOUBLE -> checkNumeric(candidate);
            case BOOLEAN -> candidate.equals("true") || candidate.equals("false");
            case STRING -> true; //never bound
        };
    }

    private boolean checkNumeric(String candidate) {
        try {
            BigDecimal value = new BigDecimal(candidate);
            if (minInclusive != null && value.compareTo(new BigDecimal(minInclusive)) < 0) return false;
            if (maxInclusive != null && value.compareTo(new BigDecimal(maxInclusive)) > 0) return false;
            if (minExclusive != null && value.compareTo(new BigDecimal(minExclusive)) <= 0) return false;
            if (maxExclusive != null && value.compareTo(new BigDecimal(maxExclusive)) >= 0) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean enumMatches(String candidate) {
        switch (base) {
            case STRING:
                return enums.contains(candidate);

            case BOOLEAN: {
                String c = candidate.trim();
                for (String e : enums) {
                    if (e != null && e.equals(c)) return true;
                }
                return false;
            }

            case INTEGER: {
                BigDecimal cand = Value.parseBigDecimal(candidate);
                if (cand == null || cand.stripTrailingZeros().scale() > 0) return false; // not an integer
                for (String e : enums) {
                    BigDecimal ev = Value.parseBigDecimal(e);
                    if (ev != null && ev.stripTrailingZeros().scale() == 0 && cand.compareTo(ev) == 0) {
                        return true;
                    }
                }
                return false;
            }

            case DOUBLE: {
                BigDecimal cand = Value.parseBigDecimal(candidate);
                if (cand == null) return false;
                for (String e : enums) {
                    BigDecimal ev = Value.parseBigDecimal(e);
                    if (ev != null && cand.compareTo(ev) == 0) return true;
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public String extract() {
        StringBuilder sb = new StringBuilder();

        if (enums != null && !enums.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ");
            for (String e : enums) joiner.add(e);
            sb.append("enums: [").append(joiner).append("]");
        }

        if (pattern != null && !pattern.isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("pattern: /").append(pattern).append("/");
        }

        if (minInclusive != null || maxInclusive != null ||
                minExclusive != null || maxExclusive != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("bounds: ");
            sb.append(formatBound("≥", minInclusive));
            sb.append(formatBound(">", minExclusive));
            sb.append(formatBound("≤", maxInclusive));
            sb.append(formatBound("<", maxExclusive));
        }

        if (sb.isEmpty()) sb.append(base);
        return sb.toString();
    }

    private static String formatBound(String symbol, String val) {
        return val == null ? "" : symbol + val + " ";
    }


}


