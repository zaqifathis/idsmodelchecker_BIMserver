package de.openfabtwin.bimserver.idschecker.model.result;

import java.util.Map;

public class AttributeResult extends Result {

    public AttributeResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NOVALUE" -> "The required attribute did not exist";
            case "FALSEY" -> "The attribute value \"" + reason.get("actual") + "\" is empty";
            case "INVALID" -> "An invalid attribute name was specified in the IDS";
            case "VALUE" -> "The attribute value \"" + reason.get("actual") + "\" does not match the requirement";
            case "PROHIBITED" -> "The attribute value should not have met the requirement";
            default  -> "";
        };
    }
}
