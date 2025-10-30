package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class ClassificationResult extends Result {

    public ClassificationResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        if (reason.get("type") == "NOVALUE") return "The entity has no classification";
        else if (reason.get("type") == "VALUE") return "The references " + reason.get("actual") + " do not match the requirements";
        else if (reason.get("type") == "SYSTEM") return "The systems " + reason.get("actual") + " do not match the requirements";
        else if (reason.get("type") == "PROHIBITED ") return "The classification should not have met the requirement";
        return "";
    }
}
