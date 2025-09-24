package de.openfabtwin.bimserver.checkingservice;

import de.openfabtwin.bimserver.checkingservice.dto.IdsXml;
import de.openfabtwin.bimserver.checkingservice.model.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static de.openfabtwin.bimserver.checkingservice.model.Mappers.*;


public class IdsMapper {
    static Logger LOGGER = LoggerFactory.getLogger(IdsMapper.class);
    private static final JAXBContext IDS_CTX = initCtx();

    private static JAXBContext initCtx() {
        try { return JAXBContext.newInstance(IdsXml.class); }
        catch (JAXBException e) { throw new ExceptionInInitializerError(e); }
    }

    public IdsMapper() {}

    public static Ids read(String url) throws Exception {
        byte[] bytes = fetchURL(url);
        Schema schema = getSchema();
        validate(bytes, schema);
        IdsXml dto = unmarshal(bytes, schema);
        return toDomain(dto);
    }

    static Ids toDomain(IdsXml idsXml) {
        Ids ids = new Ids();
        if (idsXml.getInfo() != null) {
            ids.getInfo().put("title", idsXml.getInfo().getTitle());
            ids.getInfo().put("description", idsXml.getInfo().getDescription());
            ids.getInfo().put("copyright",   idsXml.getInfo().getCopyright());
            ids.getInfo().put("version",     idsXml.getInfo().getVersion());
            ids.getInfo().put("author",      idsXml.getInfo().getAuthor());
            ids.getInfo().put("date",        idsXml.getInfo().getDate());      // String or LocalDate per your DTO
            ids.getInfo().put("purpose",     idsXml.getInfo().getPurpose());
            ids.getInfo().put("milestone",   idsXml.getInfo().getMilestone());
        }

        if (idsXml.getSpecifications() != null) {
            for (IdsXml.SpecificationXml sx : idsXml.getSpecifications().getSpecification()) {
                ids.getSpecifications().add(mapSpec(sx));
            }
        }
        return ids;
    }


    private static IdsXml unmarshal(byte[] data, Schema schema) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)){
            Unmarshaller um = IDS_CTX.createUnmarshaller();
            if (schema != null) um.setSchema(schema);
            return (IdsXml) um.unmarshal(bais);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private static void validate(byte[] data, Schema schema) throws IOException, SAXException {
        Validator validator = schema.newValidator();
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            validator.validate(new StreamSource(in));
            LOGGER.info("IDS file is valid.");
        }
    }

    private static Schema getSchema() {
        final String CLASSPATH_ROOT = "schema/";
        final String IDS_XSD = CLASSPATH_ROOT + "ids.xsd";
        final String XML_XSD = CLASSPATH_ROOT + "xml.xsd";
        final String XMLSCHEMA_DTD = CLASSPATH_ROOT + "XMLschema.dtd";
        final String XMLSCHEMA_XSD = CLASSPATH_ROOT + "XMLSchema.xsd";
        final String DATATYPES_DTD = CLASSPATH_ROOT + "datatypes.dtd";

        URL idsXsd = Ids.class.getClassLoader().getResource(IDS_XSD);

        if (idsXsd == null) {
            throw new RuntimeException("ids.xsd not found in classpath: " + IDS_XSD);
        }

        try (InputStream in = idsXsd.openStream()) {
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            try {
                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
//                sf.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            } catch (IllegalArgumentException ignored) {}

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

    private static byte[] fetchURL(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET().build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body() == null)
            throw new RuntimeException("Failed to fetch IDS: HTTP " + response.statusCode());
        return response.body();
    }
}


