package de.openfabtwin.bimserver.checkingservice.report;


import de.openfabtwin.bimserver.checkingservice.model.Ids;

public abstract class Reporter {
    private final Ids ids;

    public Reporter(Ids ids) {
        this.ids = ids;
    }

    public abstract void report();

    public String to_string() {
        return "";
    }

}
