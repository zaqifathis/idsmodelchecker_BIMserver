package de.openfabtwin.bimserver.checkingservice;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class IdsData {
    Logger LOGGER = LoggerFactory.getLogger(IdsData.class);
    private final String url;

    public IdsData(String url) {
        this.url = url;
    }

    public void fetchAndValidate() throws IOException, InterruptedException {
        Schema schema = getSchema();
        Validator validator = schema.newValidator();

        try (InputStream ids = fetchFile()) {
        }
    }

    private Schema getSchema() {
        final String schemaPath = "schema/ids.xsd";
        URL xsdUrl = IdsData.class.getClassLoader().getResource(schemaPath);
        if (xsdUrl == null) {
            throw new RuntimeException("ids.xsd not found in classpath: " + schemaPath);
        }

        try (InputStream in = xsdUrl.openStream()) {
            byte[] xsdBytes = in.readAllBytes();

            // print a quick preview so you know it's really loaded
            LOGGER.info("Loaded XSD from: {}", xsdUrl.toExternalForm());
            LOGGER.info("XSD preview: {}", previewUtf8(xsdBytes, 1000));

            return null;

        } catch (IOException e) {
            throw new RuntimeException("Failed to load/parse IDS schema from classpath", e);
        }
    }

    private static String previewUtf8(byte[] bytes, int maxChars) {
        String s = new String(bytes, 0, Math.min(bytes.length, maxChars), java.nio.charset.StandardCharsets.UTF_8);
        return s.replaceAll("\\s+", " "); // single-line preview
    }

    private InputStream fetchFile() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET().build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch file: HTTP " + response.statusCode());
        }

        InputStream body = response.body();
        if (body == null) throw new RuntimeException("Input stream is null");
        return body;
    }
}
