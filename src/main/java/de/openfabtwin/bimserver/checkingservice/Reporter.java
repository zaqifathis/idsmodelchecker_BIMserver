package de.openfabtwin.bimserver.checkingservice;

import de.openfabtwin.bimserver.checkingservice.model.Ids;

public class Reporter {
    private final Ids ids;
    private StringBuilder txt = new StringBuilder();

    public Reporter(Ids ids) {
        this.ids = ids;
    }

    StringBuilder generateReport() {
        txt.append("IDS Report\n");
        txt.append(ids.getInfo().get("filename")).append("\n");
        txt.append("==========\n\n");

        txt.append("Info:\n");
        ids.getInfo().forEach((k, v) -> {
            if (!"filename".equalsIgnoreCase(k)) {   // skip filename
                String value = (v == null) ? "-" : v.toString();
                txt.append(" - ").append(k).append(": ").append(value).append("\n");
            }
        });
        txt.append("\n");
        txt.append("--------------------------------------\n");
        txt.append("\n");

        txt.append("Specifications:\n");
        txt.append("\n");

        return txt;
    }
}
