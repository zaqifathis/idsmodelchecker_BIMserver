package de.openfabtwin.bimserver.checkingservice;

import org.bimserver.interfaces.objects.SObjectType;
import org.bimserver.models.store.*;
import org.bimserver.plugins.SchemaName;
import org.bimserver.plugins.services.AbstractAddExtendedDataService;
import org.bimserver.plugins.services.BimServerClientInterface;

import java.io.InputStream;

public class IdsModelChecking extends AbstractAddExtendedDataService {

    public IdsModelChecking() {
        super(SchemaName.UNSTRUCTURED_UTF8_TEXT_1_0.name());
    }

    @Override
    public ObjectDefinition getUserSettingsDefinition() {
        ObjectDefinition objectDefinition = StoreFactory.eINSTANCE.createObjectDefinition();

        ParameterDefinition parameter = StoreFactory.eINSTANCE.createParameterDefinition();
        parameter.setIdentifier("IdsFile");
        parameter.setDescription("URL to the IDS file to be used for checking.");
        parameter.setName("IDS File");

        PrimitiveDefinition type = StoreFactory.eINSTANCE.createPrimitiveDefinition();
        type.setType(PrimitiveEnum.STRING);
        parameter.setType(type);
        StringType defaultValue = StoreFactory.eINSTANCE.createStringType();
        defaultValue.setValue("");
        parameter.setDefaultValue(defaultValue);

        objectDefinition.getParameters().add(parameter);
        return objectDefinition;
    }

    @Override
    public void newRevision(RunningService runningService, BimServerClientInterface bimServerClientInterface, long poid, long roid, String userToken, long soid, SObjectType settings) throws Exception {

        // ids validation
        final String URL_IDS = runningService.getPluginConfiguration().getString("IdsFile");

        if (URL_IDS == null || URL_IDS.isEmpty()) {
            String report = "No IDS file URL provided.";
            addExtendedData(report.getBytes(), "result.txt", "IdsCheckerReport_Test", "text/plain", bimServerClientInterface, roid);
            return;
        }
        else if (!URL_IDS.toLowerCase().endsWith(".ids")) {
            String report = "The provided IDS file URL does not point to a valid XML or IDS file.";
            addExtendedData(report.getBytes(), "result.txt", "IdsCheckerReport_Test", "text/plain", bimServerClientInterface, roid);
            return;
        }

        byte[] ids = new Ids(URL_IDS).fetchAndValidate();


        // ifc model

        // ids model checking

        // report generation
        String report = "IDS model checking is not yet implemented.";
        addExtendedData(report.getBytes(), "result.txt", "IDS Model Checker Report", "text/plain", bimServerClientInterface, roid);
    }



    @Override
    public ProgressType getProgressType() {
        return ProgressType.UNKNOWN;
    }


}
