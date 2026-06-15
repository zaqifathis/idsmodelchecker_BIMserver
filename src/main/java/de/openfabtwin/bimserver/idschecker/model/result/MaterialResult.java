package de.openfabtwin.bimserver.idschecker.model.result;

import java.util.Map;

public class MaterialResult extends Result {

    public MaterialResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NOVALUE" -> "The entity has no material";
            case "VALUE" -> "The material names and categories of \"" + reason.get("actual") + "\" do not match the requirement";
            case "PROHIBITED" -> "The material should not have met the requirement";
            default -> "";
        };
    }
}
