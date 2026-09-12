package art.arcane.iris.world.pregen;

import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PregenSilentFailureContractTest {
    @Test
    public void noPregenSourceSwallowsAThrowableWithoutSayingAnything() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path pregenRoot = sourceRoot().resolve("art/arcane/iris/world/pregen");
        try (Stream<Path> sources = Files.walk(pregenRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                if (text.contains("catch (Throwable ignored)") || text.contains("catch (Exception ignored)")) {
                    offenders.add(source.getFileName().toString());
                }
            }
        }

        assertEquals("silent catches on the pregen path: " + offenders, List.of(), offenders);
    }

    @Test
    public void mantleCleanupFailureIsReportedWithItsStackTrace() throws IOException {
        String cleanup = method(read("art/arcane/iris/world/pregen/AsyncPregenMethod.java"),
                "private void cleanupMantleChunksCoveredBy(");

        assertTrue(cleanup, cleanup.contains("IrisLogging.reportError("));
        assertTrue(cleanup, cleanup.contains("catch (Throwable e)"));
    }

    @Test
    public void aStrandedPluginChunkTicketIsReportedWithItsStackTrace() throws IOException {
        String unload = method(read("art/arcane/iris/world/pregen/AsyncPregenMethod.java"),
                "private void unloadChunkSafely(");

        assertTrue(unload, unload.contains("removePluginChunkTicket"));
        assertEquals(unload, 2, countOccurrences(unload, "IrisLogging.reportError("));
    }

    @Test
    public void capabilityProbesLogThroughTheNamedDebugHelperRatherThanReportingErrors() throws IOException {
        String async = read("art/arcane/iris/world/pregen/AsyncPregenMethod.java");

        assertTrue(method(async, "private int resolveWorkerPoolThreads()").contains("PregenDiagnostics.probeFailed("));
        assertTrue(method(async, "private int evictionWindow()").contains("PregenDiagnostics.probeFailed("));
        assertTrue(method(async, "private Engine resolveMetricsEngine()").contains("PregenDiagnostics.probeFailed("));
    }

    @Test
    public void theFoliaSubmitFallbackNamesTheFailureItIsFallingBackFrom() throws IOException {
        String executor = method(read("art/arcane/iris/world/pregen/AsyncPregenMethod.java"),
                "private class FoliaRegionExecutor");

        assertTrue(executor, executor.contains("PregenDiagnostics.probeFailed("));
        assertTrue(executor, executor.contains("falling back to a region task"));
    }

    @Test
    public void backpressureProbesAnnounceWhyTheyFellBack() throws IOException {
        String backpressure = read("art/arcane/iris/world/pregen/PregenMantleBackpressure.java");

        assertTrue(method(backpressure, "private boolean isCancelled()").contains("PregenDiagnostics.probeFailed("));
        assertTrue(method(backpressure, "private Mantle resolveMantle()").contains("PregenDiagnostics.probeFailed("));
    }

    @Test
    public void medievalEngineResolutionAnnouncesWhyItFellBack() throws IOException {
        String medieval = read("art/arcane/iris/world/pregen/MedievalPregenMethod.java");

        assertTrue(method(medieval, "private Engine resolveEngine()").contains("PregenDiagnostics.probeFailed("));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static Path sourceRoot() throws IOException {
        Path current;
        try {
            current = Path.of(PregenSilentFailureContractTest.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Unable to resolve the core source root", e);
        }
        while (current != null && !Files.isDirectory(current.resolve("src/main/java"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Unable to locate the core source root");
        }
        return current.resolve("src/main/java");
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(sourceRoot().resolve(relativePath));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing source contract signature: " + signature, start >= 0);
        int openBrace = source.indexOf('{', start);
        assertTrue("Missing source contract method body: " + signature, openBrace >= 0);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unclosed source contract method: " + signature);
    }
}
