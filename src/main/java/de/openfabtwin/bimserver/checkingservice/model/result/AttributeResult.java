package de.openfabtwin.bimserver.checkingservice.model.result;

import java.util.Map;

public class AttributeResult extends Result {

    public AttributeResult(boolean isPass, Map<String, Object> reason) {
        super(isPass, reason);
    }

    @Override
    public String to_String() {
        return "Attribute result: " + (isPass ? "Pass" : "Fail") + ". Reason: " + reason;
    }
}
