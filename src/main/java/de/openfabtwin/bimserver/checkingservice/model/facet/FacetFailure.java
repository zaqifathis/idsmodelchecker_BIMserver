package de.openfabtwin.bimserver.checkingservice.model.facet;

import org.bimserver.emf.IdEObject;

public class FacetFailure {
    protected final IdEObject element;
    protected final String reason;

    protected FacetFailure (IdEObject element, String reason) {
        this.element = element;
        this.reason = reason;
    }

    public IdEObject getElement() {
        return element;
    }

    public String getReason() {
        return reason;
    }
}
