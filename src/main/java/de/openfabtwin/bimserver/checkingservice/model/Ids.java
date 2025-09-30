package de.openfabtwin.bimserver.checkingservice.model;

import de.openfabtwin.bimserver.checkingservice.IdsModelChecking;
import de.openfabtwin.bimserver.checkingservice.report.ResultSpecification;
import de.openfabtwin.bimserver.checkingservice.report.Results;
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

    public Results validate(SProject project, IfcModelInterface model) {
        final Results results = new Results(this);

        //check IFC version
        for (Specification spec : specifications) {
            spec.reset_status();
            spec.check_ifc_version(project);
            if(spec.getIs_ifc_version_supported() == Boolean.TRUE) {
                // TODO: check applicability and requirements


            }
            ResultSpecification resultSpec = new ResultSpecification(spec);
            results.getSpecifications().add(resultSpec);
        }

        return results;
    }

}


