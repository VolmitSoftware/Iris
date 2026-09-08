package art.arcane.iris.core.pregenerator;

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

public class PregenWaitPolicyContractTest {
    @Test
    public void noPregenSourceSleepPollsWhileWaitingForCapacity() throws IOException {
        List<String> offenders = new ArrayList<>();
        Path pregenRoot = sourceRoot().resolve("art/arcane/iris/core/pregenerator");
        try (Stream<Path> sources = Files.walk(pregenRoot)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source);
                if (text.contains("Thread.sleep(") || text.contains("J.sleep(")) {
                    offenders.add(source.getFileName().toString());
                }
            }
        }

        assertEquals("sleep polling on the pregen path: " + offenders, List.of(), offenders);
    }

    @Test
    public void mantleBackpressureWaitsOnAProgressSignalWithABoundedTimeout() throws IOException {
        String backpressure = read("art/arcane/iris/core/pregenerator/PregenMantleBackpressure.java");

        assertTrue(backpressure.contains("progressed.await(waitMs, TimeUnit.MILLISECONDS)"));
        assertTrue(method(backpressure, "public void enforceMantleBudget()").contains("awaitProgress()"));
        assertTrue(method(backpressure, "public void awaitHeapHeadroom()").contains("awaitProgress()"));
    }

    @Test
    public void chunkCompletionSignalsBothTheAdmissionGateAndTheBackpressureWait() throws IOException {
        String finished = method(read("art/arcane/iris/core/pregenerator/methods/AsyncPregenMethod.java"),
                "private void markFinished(boolean success)");

        assertTrue(finished, finished.contains("admission.release()"));
        assertTrue(finished, finished.contains("backpressure.signalProgress()"));
    }

    @Test
    public void pauseResumeAndStopSignalThePregenGateInsteadOfLettingItPoll() throws IOException {
        String pregenerator = read("art/arcane/iris/core/pregenerator/IrisPregenerator.java");

        assertTrue(method(pregenerator, "public void pause()").contains("signalState()"));
        assertTrue(method(pregenerator, "public void resume()").contains("signalState()"));
        assertTrue(method(pregenerator, "public void close()").contains("signalState()"));
        assertTrue(method(pregenerator, "private void visitRegion(").contains("awaitStateChange(GATE_WAIT_MS)"));
        assertTrue(method(pregenerator, "private void reclaimTectonicPlates(").contains("awaitStateChange(RECLAIM_WAIT_MS)"));
    }

    @Test
    public void aFinishedPregenReleasesTheBurstParallelismItRaised() throws IOException {
        String shutdown = method(read("art/arcane/iris/core/pregenerator/IrisPregenerator.java"), "private void shutdown()");

        assertTrue(shutdown, shutdown.contains("shutdownStep(\"parallelism\", PregenPerformanceProfile::restore)"));
    }

    private static Path sourceRoot() throws IOException {
        Path current;
        try {
            current = Path.of(PregenWaitPolicyContractTest.class.getProtectionDomain()
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
