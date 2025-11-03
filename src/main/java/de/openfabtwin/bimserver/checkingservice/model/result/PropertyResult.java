package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class PropertyResult extends Result {
    public PropertyResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return "";
    }
}
