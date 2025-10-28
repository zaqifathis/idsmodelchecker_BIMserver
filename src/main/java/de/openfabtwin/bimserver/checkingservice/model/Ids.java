package de.openfabtwin.bimserver.checkingservice.model;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SProject;

import java.util.*;

public class Ids {
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


