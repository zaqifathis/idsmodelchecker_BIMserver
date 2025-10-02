package de.openfabtwin.bimserver.checkingservice.model;

import de.openfabtwin.bimserver.checkingservice.IdsModelChecking;
import de.openfabtwin.bimserver.checkingservice.model.facet.Facet;
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

        for (Specification spec : specifications) {
            spec.reset_status();
            spec.check_ifc_version(project);
            if(spec.getIs_ifc_version_supported() == Boolean.TRUE) {

                // applicability
                if (!spec.getApplicability().isEmpty()) {
                    List<Facet> facets = spec.getApplicability();
                    for (Facet facet: facets) {
                        spec.setApplicable_entities(facet.filter(model, spec.getMinOccurs(), spec.getMaxOccurs()));
                    }
                }

                // requirement


            }
            ResultSpecification resultSpec = new ResultSpecification(spec);
            results.getSpecifications().add(resultSpec);
        }

        return results;
    }

}


