package de.openfabtwin.bimserver.checkingservice.model;

import java.util.*;

public class Ids {
    private final Map<String, Object> info = new LinkedHashMap<>();
    private final List<Specification> specifications = new ArrayList<>();

    public Map<String, Object> getInfo() { return info; }
    public List<Specification> getSpecifications() { return specifications; }
}


