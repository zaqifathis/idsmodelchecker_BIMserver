package de.openfabtwin.bimserver.idschecker;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Drives the IDS Model Checker plugin against the official buildingSMART entity test corpus.
 *
 * <p>Each case is an {@code .ids}/{@code .ifc} pair sharing a base name whose prefix encodes the
 * expected outcome:
 * <ul>
 *   <li>{@code pass-}    &rarr; report contains {@code [PASS]}</li>
 *   <li>{@code fail-}    &rarr; report contains {@code [FAIL]}</li>
 *   <li>{@code invalid-} &rarr; report does NOT contain {@code [PASS]} (or processing errored)</li>
 * </ul>
 *
 * <p>Network is used only once at startup to download the corpus (proxy-aware); at check time the
 * IDS is served from a local loopback HTTP server and the IFC is read from a local temp file. Run
 * {@code mvn -DskipTests package} first so the plugin is on the build output for
 * {@code LocalDevPluginLoader}.
 */
@RunWith(Parameterized.class)
public class EntityFacetTest extends EntityFacetTestBase {

    private final String baseName;
    private final String expected; // pass | fail | invalid

    public EntityFacetTest(String baseName, String expected) {
        this.baseName = baseName;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{1}: {0}")
    public static Collection<Object[]> cases() {
        List<Object[]> params = new ArrayList<>();
        for (String base : discoverCaseBaseNames()) {
            params.add(new Object[]{base, expectedOf(base)});
        }
        return params;
    }

    @BeforeClass
    public static void setUp() throws Exception {
        startServer();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        stopServer();
    }

    @Test
    public void entityCase() throws Exception {
        String report;
        try {
            report = runCase(baseName);
        } catch (Exception e) {
            if ("invalid".equals(expected)) {
                return; // an error processing an invalid case is an acceptable non-pass outcome
            }
            throw e;
        }

        System.out.println("===== " + baseName + " (" + expected + ") =====");
        System.out.println(report);

        boolean pass = report.contains("[PASS]");
        boolean fail = report.contains("[FAIL]");
        switch (expected) {
            case "pass":
                Assert.assertTrue("expected [PASS] for " + baseName + "\n" + report, pass);
                break;
            case "fail":
                Assert.assertTrue("expected [FAIL] for " + baseName + "\n" + report, fail);
                break;
            case "invalid":
                Assert.assertFalse("expected non-[PASS] for " + baseName + "\n" + report, pass);
                break;
            default:
                Assert.fail("unknown expected outcome: " + expected);
        }
    }

    private static String expectedOf(String baseName) {
        int dash = baseName.indexOf('-');
        return dash > 0 ? baseName.substring(0, dash) : "pass";
    }

    /**
     * Case base names come from the committed {@code testcases-entity.list} resource (deterministic,
     * no network/proxy needed for discovery). The list mirrors the buildingSMART entity directory:
     *   https://github.com/buildingSMART/IDS/tree/development/Documentation/ImplementersDocumentation/TestCases/entity
     * To refresh it, re-list that directory (e.g. the GitHub contents API
     * {@code api.github.com/repos/buildingSMART/IDS/contents/.../entity?ref=development}) and write
     * the {@code .ids} base names into the resource file. The actual {@code .ids}/{@code .ifc} bytes
     * are still downloaded at runtime from raw.githubusercontent.com (see {@code RAW_BASE}).
     */
    private static Set<String> discoverCaseBaseNames() {
        return listViaResource();
    }

    private static Set<String> listViaResource() {
        Set<String> bases = new LinkedHashSet<>();
        try (InputStream in = EntityFacetTest.class.getClassLoader()
                .getResourceAsStream("testcases-entity.list")) {
            if (in == null) {
                throw new IllegalStateException("testcases-entity.list not found on test classpath");
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
