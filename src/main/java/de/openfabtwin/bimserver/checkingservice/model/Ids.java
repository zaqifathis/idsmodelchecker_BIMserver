package de.openfabtwin.bimserver.checkingservice.model;

import org.bimserver.interfaces.objects.SProject;

import java.util.*;

public class Ids {
    private final Map<String, Object> info = new LinkedHashMap<>();
    private final List<Specification> specifications = new ArrayList<>();

    public Map<String, Object> getInfo() { return info; }
    public List<Specification> getSpecifications() { return specifications; }

    public String validate(StringBuilder report, SProject project) {

        //check IFC version
        for (Specification spec : specifications) {
            report.append(spec.getName()).append("\n");

            spec.check_ifc_version(project);
            if(spec.is_ifc_version_supported == Boolean.TRUE) {
                // TODO: check applicability and requirements


            } else if (spec.is_ifc_version_supported == Boolean.FALSE) {
                report.append("status      : FAIL\n");
                report.append("message     : Specification only for IFC version ").append(spec.getIfcVersion()).append(", model is: ").append(project.getSchema()).append("\n");
            }
        }

        return report.toString();
    }

}


