package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class EntityResult extends Result {

    public EntityResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        if (reason.get("type") == "NAME") return "the entity class " + reason.get("actual") + " does not meet the required IFC class";
        else if (reason.get("type") == "PREDEFINEDTYPE") return "The predefined type " + reason.get("actual") + " does not meet the required type";
        return "";
    }
}
