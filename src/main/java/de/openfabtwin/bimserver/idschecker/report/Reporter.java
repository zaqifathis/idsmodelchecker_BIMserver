package de.openfabtwin.bimserver.idschecker.report;


import de.openfabtwin.bimserver.idschecker.model.Ids;

public abstract class Reporter {
    protected final Ids ids;

    public Reporter(Ids ids) {
        this.ids = ids;
    }

    public abstract void report();


}
