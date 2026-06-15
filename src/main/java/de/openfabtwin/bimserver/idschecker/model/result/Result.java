package de.openfabtwin.bimserver.idschecker.model.result;

import java.util.Map;

public abstract class Result {
    protected final boolean isPass;
    protected final Map<String, Object> reason;

    protected Result(boolean isPass, Map<String, Object> reason) {
        this.isPass = isPass;
        this.reason = reason;
    }

    public boolean isPass() {
        return isPass;
    }

    public abstract String to_String();

    protected String reasonType() {
        if (reason == null) return "";
        Object t = reason.get("type");
        return t instanceof String s ? s : "";
    }
}
