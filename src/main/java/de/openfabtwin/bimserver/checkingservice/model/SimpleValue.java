package de.openfabtwin.bimserver.checkingservice.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record SimpleValue(String value) implements Value {
    public enum Type { BOOLEAN, INTEGER, DOUBLE, STRING }

    private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?");

    public Type type() {
        if (value == null) return Type.STRING;

        String trimmed = value.trim();
        if (trimmed.equals("true") || trimmed.equals("false")) {
            return Type.BOOLEAN;
        } else if (INTEGER_PATTERN.matcher(trimmed).matches()) {
            return Type.INTEGER;
        } else if (DOUBLE_PATTERN.matcher(trimmed).matches()) {
            return Type.DOUBLE;
        } else {
            return Type.STRING;
        }
    }

    @Override
    public boolean matches(String candidate) {
        if (candidate == null) return false;

        String c = candidate.trim();
        return switch (type()) {
            case BOOLEAN -> equalsBoolean(value, c);
            case INTEGER -> equalsAsBigDecimal(value, c, true);
            case DOUBLE  -> equalsAsBigDecimal(value, c, false);
            case STRING  -> c.equals(value);
        };
    }

    @Override
    public String extract() {
        return value == null ? "" : value.trim();
    }

    private static boolean equalsBoolean(String a, String b) {
        return ("true".equals(a) && "true".equals(b)) ||
                ("false".equals(a) && "false".equals(b));
    }

    private static boolean equalsAsBigDecimal(String a, String b, boolean integerOnly) {
        BigDecimal da = Value.parseBigDecimal(a);
        BigDecimal db = Value.parseBigDecimal(b);
        if (da == null || db == null) return false;

        if (integerOnly) {
            if (da.stripTrailingZeros().scale() > 0) return false;
            if (db.stripTrailingZeros().scale() > 0) return false;
        }
        return da.compareTo(db) == 0;
    }
}
