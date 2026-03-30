package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class PartOfResult extends Result {

    public PartOfResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NOVALUE" -> "The entity has no relationship";
            case "ENTITY" -> "The entity has a relationship with incorrect entities: \"" + reason.get("actual") + "\"";
            case "PREDEFINEDTYPE"-> "The entity has a relationship with incorrect predefined type: \"" + reason.get("actual") + "\"";
            case "PROHIBITED" -> "The relationship should not have met the requirement";
            default -> "";
        };
    }
}
