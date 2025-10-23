package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;

import java.util.List;

public class Attribute extends Facet {
    private final Value name;
    private final Value value;
    private Cardinality cardinality;
    private final String instructions;
    private final String applicability_templates;
    private final String requirement_templates;
    private final String prohibited_templates;

    public Attribute(Value name, Value value, String cardinality, String instructions){
        this.name = name;
        this.value = value;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;
        this.applicability_templates = extractValue(value, false).isEmpty() ?
                "Data where the " + name + " is provided" :
                "Data where the " + name + " is " + extractValue(value, false);
        this.requirement_templates = extractValue(value, false).isEmpty() ?
                "The " + name + " shall be provided" :
                "The " + name + " shall be " + extractValue(value, false);
        this.prohibited_templates = extractValue(value, false).isEmpty() ?
                "The " + name + " shall not be provided" :
                "The " + name + " shall not be " + extractValue(value, false);
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        return List.of();
    }

    @Override
    public Result matches(IdEObject element) {return null;}


}
