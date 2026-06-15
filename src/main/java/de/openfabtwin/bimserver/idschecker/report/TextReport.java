package de.openfabtwin.bimserver.idschecker.report;

import de.openfabtwin.bimserver.idschecker.model.Ids;
import de.openfabtwin.bimserver.idschecker.model.Specification;
import de.openfabtwin.bimserver.idschecker.model.facet.Facet;
import de.openfabtwin.bimserver.idschecker.model.facet.FacetFailure;
import org.bimserver.emf.IdEObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import java.util.List;

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

        print("Summary");
        print("-------");
        getSummary(super.ids.getSpecifications());
        print("");
        print("");

        for (Specification spec : super.ids.getSpecifications()) reportSpecification(spec);
    }

    private void getSummary(List<Specification> specifications) {
        int total_passed = 0;
        int total_failed = 0;

        for (Specification spec : specifications) {
            if (spec.getStatus()) total_passed++;
            else total_failed++;
        }
        if (total_passed > 0 && total_failed == 0) print ("[PASS]", "");
        else if (total_failed > 0) print ("[FAIL]", "");

        print("Specifications passed: " + total_passed + "/" + specifications.size());

    }

    private void reportSpecification(Specification spec) {
        if (spec.getStatus()) print("[PASS] ", "");
        else if (!spec.getStatus()) print("[FAIL] ", "");
        else print("[UNTESTED] ", "");

        int total = spec.getApplicable_entities().size();
        int total_Check = spec.getPassed_entities().size() + spec.getFailed_entities().size();
        print("Check passed: " + spec.getPassed_entities().size() + "/" + total_Check, " | ");
        print("Total elements: " + total);
        print(spec.getName());

        print(" ".repeat(4) + "Applies to:");
        int ac = 1;
        for (Facet applicability : spec.getApplicability()) {
            print(" ".repeat(8) + ac + "." + " ".repeat(2) + applicability.to_string("applicability", null, null));
            ac++;
        }

        print(" ".repeat(4) + "Requirements:");
        int rc = 1;
        for (Facet requirement : spec.getRequirements()) {
            print(" ".repeat(8) + rc + "." + " ".repeat(2) + requirement.to_string("requirement", spec, requirement));
            for(IdEObject passed : requirement.getPassedEntities()) {
                print(" ".repeat(16) + "[P] ", "");
                getElementInfo(passed);
            }
            for (FacetFailure failure : requirement.getFailures()) {
                print(" ".repeat(16) + "[F] ", "");
                reportReason(failure);
            }
            rc++;
        }
        print("");
    }

    private void reportReason(FacetFailure failure) {
        print(failure.getReason(), " | ");
        getElementInfo(failure.getElement());
    }

    private void getElementInfo(IdEObject element) {
        String name = String.valueOf(element.eGet(element.eClass().getEStructuralFeature("Name")));
        String type = element.eClass().getName();
        String guid = "-";
        EStructuralFeature guidFeature = element.eClass().getEStructuralFeature("GlobalId");
        if (guidFeature != null) {
            Object g = element.eGet(guidFeature);
            if (g != null) guid = g.toString();
        }
        print("Class: " + type + " | Name: " + name + " | GUID: " + guid);
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
