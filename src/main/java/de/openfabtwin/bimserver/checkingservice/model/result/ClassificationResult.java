package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class ClassificationResult extends Result {

    public ClassificationResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return switch (reasonType()) {
            case "NOVALUE" -> "The entity has no classification";
            case "VALUE" -> "The references \"" + reason.get("actual") + "\" do not match the requirements";
            case "SYSTEM"  -> "The systems \"" + reason.get("actual") + "\" do not match the requirements";
            case "PROHIBITED" -> "The classification should not have met the requirement";
            default -> "";
        };
    }
}
