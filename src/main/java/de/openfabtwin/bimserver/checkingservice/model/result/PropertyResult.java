package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class PropertyResult extends Result {
    public PropertyResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        if (reason.get("type") == "NOPSET")
            return "The required property set does not exist";
        if (reason.get("type") == "NOVALUE")
            return "The property set does not contain the required property";
        if (reason.get("type") == "DATATYPE")
            return "The property's data type " + reason.get("actual") + " does not match the required data type of" + reason.get("dataType");
        if (reason.get("type") == "VALUE")
            return "";
        if (reason.get("type") == "PROHIBITED")
            return "The property should not have met the requirement";
        return "";
    }
}
