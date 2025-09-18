package de.openfabtwin.bimserver.checkingservice;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

public class IdsData {

    Logger LOGGER = LoggerFactory.getLogger(IdsData.class);

    private final String url;
    private static final String IDS_NS = "http://standards.buildingsmart.org/IDS";

    public IdsData(String url) {
        this.url = url;
    }

    public void fetchAndValidate() throws IOException, InterruptedException, SAXException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            LOGGER.error("Failed to fetch IDS file: HTTP " + response.statusCode());
            throw new RuntimeException("Failed to fetch IDS file: HTTP " + response.statusCode());
        }
        
        try (InputStream idsStream = response.body()) {
            if (idsStream == null) {
                throw new RuntimeException("IDS stream is null");
            }
            Source ids = new StreamSource(idsStream);
            Schema schema = getSchema();

        }

    }

    private Schema getSchema() {

        final String schemaPath = "schema/ids.xsd";
        URL xsdUrl = IdsData.class.getClassLoader().getResource(schemaPath);
        if (xsdUrl == null) {
            throw new RuntimeException("ids.xsd not found in classpath: " + schemaPath);
        }

        return null;
    }

    private void validateIds(Schema schema,Source ids) throws SAXException {

    }

}
