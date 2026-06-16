package de.openfabtwin.bimserver.idschecker;

import org.apache.commons.io.FileUtils;
import org.bimserver.BimServer;
import org.bimserver.BimServerConfig;
import org.bimserver.EmbeddedWebServer;
import org.bimserver.LocalDevPluginLoader;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SExtendedData;
import org.bimserver.interfaces.objects.SExtendedDataSchema;
import org.bimserver.interfaces.objects.SInternalServicePluginConfiguration;
import org.bimserver.interfaces.objects.SLongCheckinActionState;
import org.bimserver.interfaces.objects.SObjectType;
import org.bimserver.interfaces.objects.SParameter;
import org.bimserver.interfaces.objects.SProfileDescriptor;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SRenderEnginePluginConfiguration;
import org.bimserver.interfaces.objects.SService;
import org.bimserver.interfaces.objects.SServiceDescriptor;
import org.bimserver.interfaces.objects.SStringType;
import org.bimserver.models.log.AccessMethod;
import org.bimserver.resources.ClasspathResourceFetcher;
import org.bimserver.webservices.ServiceMap;
import org.bimserver.webservices.authorization.SystemAuthorization;

import com.sun.net.httpserver.HttpServer;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Embedded-BIMserver lifecycle + helpers shared by the IDS facet integration tests.
 *
 * <p>Mirrors the proven pattern from bauinformatik/levelout
 * (AllTests.java + CheckingServiceTest.java), adapted for the IDS Model Checker plugin which
 * reads its IDS-file URL from a single global plugin configuration ("IdsFile" parameter).
 *
 * <p>The IDS Model Checker plugin is loaded from THIS project's own build output via
 * {@link LocalDevPluginLoader} (run {@code mvn -DskipTests package} first). To instead load the
 * installed artifact, point the loader at:
 *   C:\\Users\\ezahi597\\.m2\\repository\\org\\openfabtwin\\IdsModelPlugins\\1.0.1\\IdsModelPlugins-1.0.1.jar
 */
public abstract class EntityFacetTestBase {

    protected static final int PORT = 7010;
    protected static final String SITE = "http://localhost:" + PORT;
    protected static final String MAVEN_REPO = "https://repo1.maven.org/maven2/";
    protected static final String REPORT_SCHEMA = "UNSTRUCTURED_UTF8_TEXT_1_0";

    /** Plugin/service name fragment used to locate the IDS Model Checker among registered services. */
    protected static final String SERVICE_NAME_FRAGMENT = "Ids";

    /** When true also install ifcopenshellplugin; otherwise use the NOP render engine. */
    protected static final boolean ONLINE = Boolean.getBoolean("ids.test.online");

    /** buildingSMART entity test corpus (raw download base). The only GitHub access, at startup/lazily. */
    protected static final String RAW_BASE =
            "https://raw.githubusercontent.com/buildingSMART/IDS/refs/heads/development/"
                    + "Documentation/ImplementersDocumentation/TestCases/entity/";

    private static final HttpClient HTTP = buildHttpClient();

    /** Proxy-aware client: corporate proxies (env http(s)_proxy or -Dhttp.proxyHost) block plain Java otherwise. */
    private static HttpClient buildHttpClient() {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS);
        ProxySelector proxy = proxyFromEnv();
        if (proxy != null) {
            b.proxy(proxy);
        }
        return b.build();
    }

    private static ProxySelector proxyFromEnv() {
        String p = firstNonBlank(
                System.getenv("https_proxy"), System.getenv("HTTPS_PROXY"),
                System.getenv("http_proxy"), System.getenv("HTTP_PROXY"));
        if (p == null) {
            return ProxySelector.getDefault(); // honors -Dhttp.proxyHost / -Dhttps.proxyHost if set
        }
        if (!p.contains("://")) {
            p = "http://" + p;
        }
        URI u = URI.create(p);
        int port = u.getPort() != -1 ? u.getPort() : 3128;
        return ProxySelector.of(new InetSocketAddress(u.getHost(), port));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    protected static Path homePath;
    protected static BimServer bimServer;
    protected static String adminToken;
    protected static long internalServiceOid = -1;
    protected static long reportSchemaOid = -1;

    /** Local temp dir holding downloaded .ids/.ifc files, served over a local HTTP server. */
    protected static Path corpusDir;
    /** Tiny loopback HTTP server so the plugin fetches IDS files locally (no check-time GitHub traffic). */
    protected static HttpServer localServer;
    protected static int localPort = -1;
    /** Empty dir used as the embedded web server's static-content base (so Jetty starts and 7010 binds). */
    protected static Path webResourceBase;

    protected static void startServer() throws Exception {
        homePath = Paths.get("tmptestdata", "home-" + Long.toHexString(new Random().nextLong()));
        if (Files.exists(homePath)) {
            FileUtils.deleteDirectory(homePath.toFile());
        }

        bimServer = createBimServer(homePath);
        bimServer.start();

        // Bootstrap admin + base settings via the internal system authorization.
        ServiceMap boot = bimServer.getServiceFactory()
                .get(new SystemAuthorization(1, TimeUnit.HOURS), AccessMethod.INTERNAL);
        boot.getAdminInterface().setup(SITE, "Test Name", "Test Description", "noicon",
                "Administrator", "admin@localhost", "admin");
        boot.getSettingsInterface().setCacheOutputFiles(false);
        boot.getSettingsInterface().setPluginStrictVersionChecking(false);
        boot.getSettingsInterface().setSiteAddress(SITE);

        adminToken = bimServer.getServiceFactory().get(AccessMethod.INTERNAL)
                .getAuthInterface().login("admin@localhost", "admin");
        ServiceMap services = bimServer.getServiceFactory().get(adminToken, AccessMethod.INTERNAL);

        // Required plugin bundles (resolved from local .m2 cache / Maven Central).
        services.getPluginInterface().installPluginBundle(MAVEN_REPO, "org.opensourcebim", "ifcplugins", null, null);
        services.getPluginInterface().installPluginBundle(MAVEN_REPO, "org.opensourcebim", "binaryserializers", null, null);
        services.getPluginInterface().installPluginBundle(MAVEN_REPO, "org.opensourcebim", "bimviews", null, null);
        if (ONLINE) {
            services.getPluginInterface().installPluginBundle(MAVEN_REPO, "org.opensourcebim", "ifcopenshellplugin", null, null);
        } else {
            Optional<SRenderEnginePluginConfiguration> nop = services.getPluginInterface()
                    .getAllRenderEngines(true).stream()
                    .filter(re -> re.getName().equals("NOP Render Engine")).findFirst();
            if (nop.isPresent()) {
                services.getPluginInterface().setDefaultRenderEngine(nop.get().getOid());
            }
        }

        // Report output schema used by the IDS plugin (AbstractAddExtendedDataService).
        // Fresh home dir each run, so it never pre-exists; add unconditionally (levelout style).
        SExtendedDataSchema s = new SExtendedDataSchema();
        s.setName(REPORT_SCHEMA);
        s.setContentType("text/plain");
        s.setUrl("");
        s.setDescription("Generic text");
        services.getServiceInterface().addExtendedDataSchema(s);
        reportSchemaOid = services.getServiceInterface().getExtendedDataSchemaByName(REPORT_SCHEMA).getOid();

        // Load the IDS Model Checker plugin from this project's build output and register services.
        LocalDevPluginLoader.loadPlugins(bimServer.getPluginBundleManager(), new Path[]{Path.of(".")});
        bimServer.activateServices();

        internalServiceOid = locateIdsInternalService(services);

        startLocalCorpusServer();
    }

    /** Serve the downloaded corpus over loopback so the IDS plugin never touches GitHub at check time. */
    private static void startLocalCorpusServer() throws Exception {
        corpusDir = Files.createTempDirectory("ids-entity-corpus-");
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        localServer.createContext("/", exchange -> {
            String name = exchange.getRequestURI().getPath().replaceFirst("^/+", "");
            Path file = corpusDir.resolve(name);
            if (!Files.exists(file) || !file.normalize().startsWith(corpusDir)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        localServer.start();
        localPort = localServer.getAddress().getPort();
    }

    protected static void stopServer() throws Exception {
        if (localServer != null) {
            localServer.stop(0);
        }
        if (bimServer != null) {
            bimServer.stop();
        }
        if (corpusDir != null && Files.exists(corpusDir)) {
            FileUtils.deleteDirectory(corpusDir.toFile());
        }
        if (homePath != null && Files.exists(homePath)) {
            FileUtils.deleteDirectory(homePath.toFile());
        }
        if (webResourceBase != null && Files.exists(webResourceBase)) {
            FileUtils.deleteDirectory(webResourceBase.toFile());
        }
    }

    private static long locateIdsInternalService(ServiceMap services) throws Exception {
        List<SInternalServicePluginConfiguration> all =
                services.getPluginInterface().getAllInternalServices(false);
        Optional<SInternalServicePluginConfiguration> ids = all.stream()
                .filter(c -> c.getName() != null && c.getName().contains(SERVICE_NAME_FRAGMENT))
                .findFirst();
        if (ids.isEmpty()) {
            StringBuilder sb = new StringBuilder("IDS internal service not found. Registered: ");
            for (SInternalServicePluginConfiguration c : all) sb.append("[").append(c.getName()).append("] ");
            throw new IllegalStateException(sb.toString());
        }
        return ids.get().getOid();
    }

    /**
     * Run one entity test case end-to-end and return the plugin's report text.
     *
     * <p>The {@code .ids} and {@code .ifc} are downloaded once into the local corpus dir (with
     * retry), the IDS is served to the plugin from the loopback HTTP server, and the IFC is
     * checked in from the local file. No GitHub traffic at check time.
     *
     * @param baseName case base name, e.g. {@code pass-a_matching_entity_should_pass}
     */
    protected String runCase(String baseName) throws Exception {
        Path idsFile = ensureDownloaded(baseName + ".ids");
        Path ifcFile = ensureDownloaded(baseName + ".ifc");
        String idsLocalUrl = "http://127.0.0.1:" + localPort + "/" + idsFile.getFileName();

        ServiceMap services = bimServer.getServiceFactory().get(adminToken, AccessMethod.INTERNAL);

        // 1. Point the global plugin config at this case's (local) IDS URL, then re-register.
        SObjectType settings = services.getPluginInterface().getPluginSettings(internalServiceOid);
        if (settings == null) {
            settings = new SObjectType();
        }
        setStringParameter(settings, "IdsFile", idsLocalUrl);
        services.getPluginInterface().setPluginSettings(internalServiceOid, settings);
        bimServer.activateServices();

        // 2. Fresh project + attach the (freshly registered) IDS service to it.
        String projectName = "test-" + Long.toHexString(new Random().nextLong());
        SProject project = services.getServiceInterface().addProject(projectName, "IFC4");
        attachIdsService(services, project.getOid());

        // 3. Check in the IFC from the local temp file (no URLDataSource / openConnection).
        SDeserializerPluginConfiguration deserializer =
                services.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
        DataHandler ifcData = new DataHandler(new FileDataSource(ifcFile.toFile()));
        String ifcName = ifcFile.getFileName().toString();
        long length = Files.size(ifcFile);
        SLongCheckinActionState checkin = services.getServiceInterface().checkinSync(
                project.getOid(), "checkin", deserializer.getOid(), length, ifcName, ifcData, false);
        if (checkin.getRoid() == -1) {
            throw new IllegalStateException("Checkin failed for " + ifcName);
        }

        // 4. Wait for the service to run, then read the last report of the revision.
        project = services.getServiceInterface().getProjectByPoid(project.getOid());
        long roid = project.getLastRevisionId();
        return waitForReport(services, roid);
    }

    private void attachIdsService(ServiceMap services, long poid) throws Exception {
        SServiceDescriptor descriptor = services.getServiceInterface().getAllLocalServiceDescriptors().stream()
                .filter(d -> d.getName() != null && d.getName().contains(SERVICE_NAME_FRAGMENT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("IDS local service descriptor not found"));
        SProfileDescriptor profile =
                services.getServiceInterface().getAllLocalProfiles(descriptor.getIdentifier()).get(0);
        SExtendedDataSchema writeSchema =
                services.getServiceInterface().getExtendedDataSchemaByName(descriptor.getWriteExtendedData());

        SService service = new SService();
        service.setName(descriptor.getName() + new Random().nextInt(100000)); // must be unique in project
        service.setProviderName(descriptor.getProviderName());
        service.setServiceName(descriptor.getName());
        service.setServiceIdentifier(descriptor.getIdentifier());
        service.setUrl(descriptor.getUrl());
        service.setToken(descriptor.getToken());
        service.setNotificationProtocol(descriptor.getNotificationProtocol());
        service.setDescription(descriptor.getDescription());
        service.setTrigger(descriptor.getTrigger());
        service.setProfileIdentifier(profile.getIdentifier());
        service.setProfileName(profile.getName());
        service.setProfileDescription(profile.getDescription());
        service.setProfilePublic(profile.isPublicProfile());
        service.setReadRevision(descriptor.isReadRevision());
        service.setWriteRevisionId(-1);
        service.setWriteExtendedDataId(writeSchema.getOid());

        services.getServiceInterface().addLocalServiceToProject(
                poid, service, Long.valueOf(service.getProfileIdentifier()));
    }

    private String waitForReport(ServiceMap services, long roid) throws Exception {
        // The service runs asynchronously on checkin; poll for its extended-data report.
        for (int attempt = 0; attempt < 30; attempt++) {
            SExtendedData ed =
                    services.getServiceInterface().getLastExtendedDataOfRevisionAndSchema(roid, reportSchemaOid);
            if (ed != null) {
                byte[] data = services.getServiceInterface().getFile(ed.getFileId()).getData();
                return new String(data, java.nio.charset.StandardCharsets.UTF_8);
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("No IDS report produced for revision " + roid);
    }

    /** Download a corpus file from GitHub once (idempotent), with retry + backoff. */
    private static synchronized Path ensureDownloaded(String fileName) throws Exception {
        Path target = corpusDir.resolve(fileName);
        if (Files.exists(target) && Files.size(target) > 0) {
            return target;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_BASE + fileName))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/octet-stream, */*")
                .GET().build();
        Exception last = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                HttpResponse<byte[]> resp = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                    Files.write(target, resp.body());
                    return target;
                }
                last = new IllegalStateException("HTTP " + resp.statusCode() + " for " + fileName);
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1000L * attempt); // 1s, 2s, 3s backoff
        }
        throw new IllegalStateException("Failed to download " + fileName + " after retries", last);
    }

    private static void setStringParameter(SObjectType settings, String identifier, String value) {
        for (SParameter p : settings.getParameters()) {
            if (identifier.equals(p.getIdentifier())) {
                SStringType st = new SStringType();
                st.setValue(value);
                p.setValue(st);
                return;
            }
        }
        SParameter p = new SParameter();
        p.setIdentifier(identifier);
        p.setName(identifier);
        SStringType st = new SStringType();
        st.setValue(value);
        p.setValue(st);
        settings.getParameters().add(p);
    }

    private static BimServer createBimServer(Path home) throws Exception {
        BimServerConfig config = new BimServerConfig();
        config.setHomeDir(home);
        config.setStartEmbeddedWebServer(true);
        config.setPort(PORT);
        config.setResourceFetcher(new ClasspathResourceFetcher());
        config.setClassPath(System.getProperty("java.class.path"));
        BimServer server = new BimServer(config);
        // EmbeddedWebServer defaults its static-content base to "<cwd>/www"; in BIMserver 1.6.0
        // (Jetty ee8) a missing base dir aborts the web server start, so port 7010 never binds and
        // internal services fail their loopback JSON callback ("Connection refused"). Point the base
        // at a real (empty) dir so the server comes up.
        EmbeddedWebServer webServer = new EmbeddedWebServer(server, null, false);
        webResourceBase = Files.createTempDirectory("ids-www-");
        webServer.setResourceBase(webResourceBase.toAbsolutePath().toString());
        server.setEmbeddedWebServer(webServer);
        return server;
    }
}
