package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class EntityResult extends Result {

    public EntityResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NAME" -> "The entity class \"" + reason.get("actual") + "\" does not meet the required IFC class";
            case "PREDEFINEDTYPE" -> "The predefined type \"" + reason.get("actual") + "\" does not meet the required type";
            default -> "";
        };
    }
}
