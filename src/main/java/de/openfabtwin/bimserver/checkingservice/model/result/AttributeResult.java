package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class AttributeResult extends Result {

    public AttributeResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        if (reason.get("type") == "NOVALUE")
            return "The required attribute did not exist";
        else if (reason.get("type") == "FALSEY")
            return "The attribute value" + reason.get("actual") + " is empty";
        else if (reason.get("type") == "INVALID")
            return "An invalid attribute name was specified in the IDS";
        else if (reason.get("type") == "VALUE")
            return "The attribute value " + reason.get("actual") +" does not match the requirement";
        else if (reason.get("type") == "PROHIBITED")
            return "The attribute value should not have met the requirement";
        return "";
    }
}
