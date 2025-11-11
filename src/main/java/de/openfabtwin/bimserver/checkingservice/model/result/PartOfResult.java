package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class PartOfResult extends Result {
    public PartOfResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        if (reason.get("type") == "NOVALUE")
            return "The entity has no relationship";
        else if (reason.get("type") == "ENTITY")
            return "The entity has a relationship with incorrect entities: " + reason.get("actual");
        else if (reason.get("type") == "PREDEFINEDTYPE")
            return "The entity has a relationship with incorrect predefined type: " + reason.get("actual");
        else if (reason.get("type") == "PROHIBITED")
            return "The relationship should not have met the requirement";
        return "";
    }
}
