package de.openfabtwin.bimserver.idschecker.model.result;

import java.util.List;
import java.util.Map;

public class PropertyResult extends Result {
    public PropertyResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NOPSET" -> "The required property set does not exist";
            case "NOVALUE" -> "The property set does not contain the required property";
            case "DATATYPE"-> "The property's data type \"" + reason.get("actual") + "\" does not match the required data type of \""  + reason.get("dataType") + "\"";
            case "VALUE" -> {
                Object actual = reason.get("actual");
                if (actual instanceof List<?> list) {
                    if (list.size() == 1)
                        yield "The property value \"" + list.get(0) + "\" does not match the requirements";
                    else
                        yield "The property values \"" + list + "\" do not match the requirements";
                }
                yield "The property value \"" + actual + "\" does not match the requirements";
            }
            case "PROHIBITED" -> "The property should not have met the requirement";
            default -> "";
        };
    }
}
