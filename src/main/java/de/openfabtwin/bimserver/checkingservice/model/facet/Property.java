package de.openfabtwin.bimserver.checkingservice.model.facet;

import de.openfabtwin.bimserver.checkingservice.model.Value;
import de.openfabtwin.bimserver.checkingservice.model.result.PropertyResult;
import de.openfabtwin.bimserver.checkingservice.model.result.Result;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;

import java.util.*;
import java.util.function.Consumer;

public class Property extends Facet {
    private final Value pset;
    private final Value baseName;
    private final Value value;
    private final String dataType;
    private final String uri;
    private final String instructions;

    public Property(Value pset, Value baseName, Value value, String dataType, String uri, String cardinality, String instructions){
        this.pset = pset;
        this.baseName = baseName;
        this.value = value;
        this.dataType = dataType;
        this.uri = uri;
        this.cardinality = cardinalityFromString(cardinality);
        this.instructions = instructions;

        if (value == null) {
            this.applicability_templates = "Elements with " + baseName.extract() + " data in the dataset " + pset.extract();
            this.requirement_templates = baseName.extract() + " data shall be provided in the dataset " + pset.extract();
            this.prohibited_templates = baseName.extract() + " data shall not be provided in the dataset " + pset.extract();
        } else {
            this.applicability_templates = "Elements with " + baseName.extract() + " data of " + value.extract() + " in the dataset " + pset.extract();
            this.requirement_templates = baseName.extract() + " data shall be " + value.extract() + " and in the dataset " + pset.extract();
            this.prohibited_templates = baseName.extract() + " data shall not be " + value.extract() + " and in the dataset " + pset.extract();
        }
    }

    @Override
    public List<IdEObject> filter(IfcModelInterface model) {
        List<IdEObject> results = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        var meta = model.getPackageMetaData();
        EPackage epkg = meta.getEPackage();

        Consumer<String> addAllByName = (typeName) -> {
            EClassifier c = epkg.getEClassifier(typeName);
            if (c instanceof EClass ec) {
                for (IdEObject inst : model.getAllWithSubTypes(ec)) {
                    if (seen.add(inst.getOid())) results.add(inst);
                }
            }
        };

        String schema = meta.getSchema().name().toUpperCase();
        if (schema.contains("IFC2X3")) {
            addAllByName.accept("IfcObjectDefinition");
        } else {
            addAllByName.accept("IfcObjectDefinition");
            addAllByName.accept("IfcMaterialDefinition");
            addAllByName.accept("IfcProfileDef");
        }

        return results;
    }

    @Override
    public Result matches(IdEObject element) {

        boolean isPass = false;
        Map<String, Object> reason = new HashMap<>();

        return new PropertyResult(isPass, reason);
    }


}
