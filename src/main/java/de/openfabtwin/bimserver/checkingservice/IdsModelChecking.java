package de.openfabtwin.bimserver.checkingservice;

import de.openfabtwin.bimserver.checkingservice.model.Ids;
import de.openfabtwin.bimserver.checkingservice.model.IdsMapper;
import de.openfabtwin.bimserver.checkingservice.report.Reporter;
import de.openfabtwin.bimserver.checkingservice.report.Results;
import org.bimserver.emf.IdEObject;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.interfaces.objects.SObjectType;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.store.*;
import org.bimserver.plugins.SchemaName;
import org.bimserver.plugins.services.AbstractAddExtendedDataService;
import org.bimserver.plugins.services.BimServerClientInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdsModelChecking extends AbstractAddExtendedDataService {
    Logger LOGGER = LoggerFactory.getLogger(IdsModelChecking.class);

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

        final String URL_IDS = runningService.getPluginConfiguration().getString("IdsFile");

        if (URL_IDS == null || URL_IDS.isEmpty() || !URL_IDS.toLowerCase().endsWith(".ids")) {
            String report = "Missing or invalid IDS URL.";
            addExtendedData(report.getBytes(), "result.txt", "IdsCheckerReport_Test", "text/plain", bimServerClientInterface, roid);
            return;
        }

        Ids ids = IdsMapper.read(URL_IDS);
        LOGGER.info("Using IDS: " + ids.getSpecifications().size() + "spec size, " + ids.getSpecifications().get(0).getApplicability().size() + " applicability facets, " +
                ids.getSpecifications().get(0).getRequirements().size() + " requirement facets.");

        SProject project = bimServerClientInterface.getServiceInterface().getProjectByPoid(poid);
        IfcModelInterface elements = bimServerClientInterface.getModel(project, roid, true, false);
        LOGGER.info("Model has " + elements.size() + " elements.");
        for (IdEObject element : elements) {
            LOGGER.debug("Element: " + element.getClass().getSimpleName() + " - " + element.getOid() + " - " + element.toString());
        }

        Results results = ids.validate(project, elements);
        Reporter reporter = new Reporter(results);
        String txtReport = reporter.txtReport();

        addExtendedData(txtReport.getBytes(), "result.txt", "OFT: IDS Model Checker Report", "text/plain", bimServerClientInterface, roid);
    }

    @Override
    public ProgressType getProgressType() {
        return ProgressType.UNKNOWN;
    }


}
