package de.openfabtwin.bimserver.idschecker;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives the IDS Model Checker plugin against the official buildingSMART facet corpora
 * (entity, attribute, classification, property, material, partof) and emits a single HTML report.
 *
 * <p>Each case is an {@code .ids}/{@code .ifc} pair whose base-name prefix encodes the expected
 * outcome: {@code pass-} &rarr; {@code [PASS]}, {@code fail-} &rarr; {@code [FAIL]},
 * {@code invalid-} &rarr; not {@code [PASS]}. This run is <b>report-only</b>: a mismatch is
 * recorded (red row in the report) but does NOT fail the build.
 *
 * <p>Network is used only once at startup to download the corpus (proxy-aware); at check time the
 * IDS is served from a loopback HTTP server and the IFC is read from a local temp file. Run
 * {@code mvn -DskipTests package} first so the plugin is on the build output.
 */
@RunWith(Parameterized.class)
public class IdsTestCasesTest extends IdsFacetTestBase {

    /** Facets to exercise; each has a committed {@code testcases-<facet>.list} resource. */
    private static final String[] FACETS =
            {"entity", "attribute", "classification", "property", "material", "partof"};

    /** Where the HTML dashboard is written. */
    private static final Path REPORT = Paths.get("target", "ids-test-report.html");

    private static final List<HtmlReport.CaseResult> RESULTS =
            Collections.synchronizedList(new ArrayList<>());

    private final String facet;
    private final String baseName;
    private final String expected; // pass | fail | invalid

    public IdsTestCasesTest(String facet, String baseName, String expected) {
        this.facet = facet;
        this.baseName = baseName;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{0}/{1}")
    public static Collection<Object[]> cases() {
        List<Object[]> params = new ArrayList<>();
        for (String facet : FACETS) {
            for (String base : listFacet(facet)) {
                params.add(new Object[]{facet, base, expectedOf(base)});
            }
        }
        return params;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        try {
            HtmlReport.write(REPORT, RESULTS);
            System.out.println("IDS test report written to " + REPORT.toAbsolutePath());
        } finally {
            stopServer();
        }
    }

    @Test
    public void facetCase() {
        String fileName = baseName + ".ids";
        long start = System.nanoTime();
        String result;
        boolean ok;
        try {
            String report = runCase(facet, baseName);
            boolean pass = report.contains("[PASS]");
            boolean fail = report.contains("[FAIL]");
            result = pass ? "PASS" : (fail ? "FAIL" : "UNKNOWN");
            ok = switch (expected) {
                case "pass" -> pass;
                case "fail" -> fail;
                case "invalid" -> !pass; // a parse/validation error counts below as ERROR (also non-pass)
                default -> false;
            };
        } catch (Exception e) {
            result = "ERROR";
            ok = "invalid".equals(expected); // erroring on an invalid case is an acceptable non-pass
        }
        long timeMs = (System.nanoTime() - start) / 1_000_000;
        RESULTS.add(new HtmlReport.CaseResult(facet, fileName, expected, result, timeMs, ok));
        // Report-only: never fail the build on a mismatch; the HTML carries the status.
    }

    private static String expectedOf(String baseName) {
        int dash = baseName.indexOf('-');
        return dash > 0 ? baseName.substring(0, dash) : "pass";
    }

    private static Set<String> listFacet(String facet) {
        Set<String> bases = new LinkedHashSet<>();
        String resource = "testcases-" + facet + ".list";
        try (InputStream in = IdsTestCasesTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " not found on test classpath");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                bases.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return bases;
    }
}
