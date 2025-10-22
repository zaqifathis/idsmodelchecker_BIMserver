package de.openfabtwin.bimserver.checkingservice.model;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Ids {
    Logger LOGGER = LoggerFactory.getLogger(Ids.class);

    private final Map<String, Object> info = new LinkedHashMap<>();
    private final List<Specification> specifications = new ArrayList<>();

    public Map<String, Object> getInfo() { return info; }
    public List<Specification> getSpecifications() { return specifications; }

    public void validate(SProject project, IfcModelInterface model) {

        for (Specification spec : specifications) {
            spec.validate(project, model);
        }
    }

}


