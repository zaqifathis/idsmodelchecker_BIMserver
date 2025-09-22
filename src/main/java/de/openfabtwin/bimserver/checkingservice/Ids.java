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
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class Ids {
    Logger LOGGER = LoggerFactory.getLogger(Ids.class);
    private final String url;
    private byte[] idsContent = null;

    Ids(String url) {
        this.url = url;
    }

    public byte[] fetchAndValidate() throws IOException, InterruptedException {

        try (InputStream ids = fetchFile()) {
            Schema schema = getSchema();
            Validator validator = schema.newValidator();
            try {
                validator.validate(new StreamSource(ids));
                idsContent = fetchFile().readAllBytes();
                LOGGER.info("IDS file is valid.");
            } catch (SAXException e) {
                LOGGER.error("IDS file is NOT valid: {}", e.getMessage());
            }
        }
        return idsContent;
    }

    private Schema getSchema() {
        final String CLASSPATH_ROOT = "schema/";
        final String IDS_XSD = CLASSPATH_ROOT + "ids.xsd";
        final String XML_XSD = CLASSPATH_ROOT + "xml.xsd";
        final String XMLSCHEMA_DTD = CLASSPATH_ROOT + "XMLschema.dtd";
        final String XMLSCHEMA_XSD = CLASSPATH_ROOT + "XMLSchema.xsd";
        final String DATATYPES_DTD = CLASSPATH_ROOT + "datatypes.dtd";
        final String XMLSCHEMA_INSTANCE = CLASSPATH_ROOT + "XMLSchema-instance.xml";

        URL idsXsd = Ids.class.getClassLoader().getResource(IDS_XSD);

        if (idsXsd == null) {
            throw new RuntimeException("ids.xsd not found in classpath: " + IDS_XSD);
        }

        try (InputStream in = idsXsd.openStream()) {
            LOGGER.info("Loaded XSD from: {}", idsXsd.toExternalForm());

            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
//            try {
//                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
//                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
//            } catch (IllegalArgumentException ignored) {}

            // Resolve imports/includes from classpath
            sf.setResourceResolver(new LSResourceResolver() {
                final DOMImplementationLS impl = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");

                private LSInput fromClassPath(String cpPath, String originalSystemId, String publicId) {
                    InputStream is = Ids.class.getClassLoader().getResourceAsStream(cpPath);
                    if(is == null) return null;
                    LSInput input = impl.createLSInput();
                    input.setPublicId(publicId);
                    input.setSystemId(originalSystemId);
                    input.setByteStream(is);
                    return input;
                }

                @Override
                public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
                    if (systemId == null) return null;

                    String sys = systemId.replace("http://www.w3.org/", "https://www.w3.org/");
                    if ("https://www.w3.org/2001/xml.xsd".equals(sys)) {
                        return fromClassPath(XML_XSD, systemId, publicId);
                    }
                    if ("https://www.w3.org/2001/XMLSchema.xsd".equals(sys)) {
                        return fromClassPath(XMLSCHEMA_XSD, systemId, publicId);
                    }
                    if ("http://www.w3.org/2001/XMLSchema-instance".equals(systemId)
                            || "https://www.w3.org/2001/XMLSchema-instance".equals(systemId.replace("http://","https://"))) {
                        String stub =
                                "<?xml version=\"1.0\"?>" +
                                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" " +
                                        "           targetNamespace=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                                        "           elementFormDefault=\"qualified\" attributeFormDefault=\"unqualified\"/>";
                        LSInput in = impl.createLSInput();
                        in.setSystemId(systemId);
                        in.setStringData(stub);
                        return in;
                    }
                    if (sys.endsWith("XMLSchema.dtd")) {
                        return fromClassPath(XMLSCHEMA_DTD, systemId, publicId);
                    }
                    if (sys.endsWith("datatypes.dtd")) {
                        return fromClassPath(DATATYPES_DTD, systemId, publicId);
                    }
                    if (!sys.contains("://")) {
                        String candidate = CLASSPATH_ROOT + sys.replaceFirst("^/+", "");
                        LSInput input = fromClassPath(candidate, systemId, publicId);
                        if (input != null) return input;
                    }
                    if (sys.startsWith("schema/")) {
                        return fromClassPath(sys, systemId, publicId);
                    }
                    return null;
                }
            });

            StreamSource schemaSource = new StreamSource(in);
            schemaSource.setSystemId("classpath:" + IDS_XSD);
            return sf.newSchema(schemaSource);

        } catch (IOException | SAXException e) {
            throw new RuntimeException("Failed to load/parse schema from classpath", e);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void previewUtf8(byte[] bytes, int maxChars) {
        String s = new String(bytes, 0, Math.min(bytes.length, maxChars), java.nio.charset.StandardCharsets.UTF_8);
        LOGGER.info("Preview: " + s);
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

