package de.openfabtwin.bimserver.checkingservice.report;

import de.openfabtwin.bimserver.checkingservice.model.Ids;
import de.openfabtwin.bimserver.checkingservice.model.Specification;
import de.openfabtwin.bimserver.checkingservice.model.facet.Facet;
import de.openfabtwin.bimserver.checkingservice.model.facet.FacetFailure;

import java.util.Map;

public class TextReport extends Reporter {

    private final StringBuilder text = new StringBuilder();
    public TextReport(Ids ids) {
        super(ids);
    }

    @Override
    public void report() {
        print("IDS Report");
        print("==========");
        print("");

        super.ids.getInfo().entrySet().stream()
                .filter(e -> e.getValue() != null)
                .forEach(e -> print(e.getKey() + ": " + e.getValue()));

        print("");
        for (Specification spec : super.ids.getSpecifications()) reportSpecification(spec);
    }

    private void reportSpecification(Specification spec) {
        if (spec.getStatus()) print("[PASS] ", "");
        else if (!spec.getStatus()) print("[FAIL] ", "");
        else print("[UNTESTED] ", "");

        int total = spec.getApplicable_entities().size();
        int total_success = total - spec.getFailed_entities().size();

        if (total == 0 || total_success == 0) print("No applicable entities", "");
        else print(String.valueOf((total_success/total)), "");

        if(!"0".equals(spec.getMinOccurs())) print(" | ", "");
        print(spec.getName());

        print(" ".repeat(4) + "Applies to:");

        for (Facet applicability : spec.getApplicability()) {
            print(" ".repeat(8) + applicability.to_string("applicability", null, null));
        }

        print(" ".repeat(4) + "Requirements:");
        for (Facet requirement : spec.getRequirements()) {
            print(" ".repeat(8) + requirement.to_string("requirement", spec, requirement));
            for (FacetFailure failure : requirement.getFailures()) {
                print(" ".repeat(12), "");
                reportReason(failure);
            }
        }

    }

    private void reportReason(FacetFailure failure) {
        print(failure.getReason(), "");
        print(" - oid: " + failure.getElement().getOid());
    }

    private void print(String line) {
        text.append(line).append("\n");
    }

    public void print(String txt, String end) {
        text.append(txt).append(end);
    }

    public String to_string() {
        return text.toString();
    }
}
