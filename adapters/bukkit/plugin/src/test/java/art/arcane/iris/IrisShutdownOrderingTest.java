package art.arcane.iris;

import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisShutdownOrderingTest {
    @Test
    public void drainWorldGenerators_closesGeneratorsWithoutEagerMantleSave() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String drain = section(source, "private void drainWorldGenerators", "private void setupPapi");

        assertTrue("Shutdown must close each Iris generator", drain.contains("generator.closeAsync()"));
        assertFalse("Shutdown must not close Mantle plates before generation drains", drain.contains("saveAllNow()"));
    }

    @Test
    public void serverStop_defersRuntimeTeardownUntilPaperShutdownBoundary() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String onDisable = section(source, "public void onDisable()", "public void onPreUnload");
        String quiesce = section(source, "private void quiesceRuntimeForServerShutdown", "private void startPostStopFinisher");
        String shutdownHook = section(source, "private void runShutdownHook()", "private void finishRuntimeAfterServerShutdown");
        String boundaryFinish = section(source, "private void finishRuntimeAfterServerShutdown", "private boolean awaitServerShutdownBoundary");
        String boundaryAwait = section(source, "private boolean awaitServerShutdownBoundary", "private void finishDeferredRuntimeTeardown");
        String finisher = section(source, "private void startPostStopFinisher()", "private void runShutdownHook");
        String generatorSource = Files.readString(Path.of(System.getProperty("iris.bukkitChunkGeneratorSource")));
        String generatorQuiesce = section(generatorSource,
                "public void quiesceForServerShutdown()", "public boolean isStudio()");

        assertOrdered(onDisable,
                "startupBoundaryRestart.get()",
                "teardownRuntime(\"startup-boundary-restart\", 30L)",
                "else if (serverStopping)",
                "quiesceRuntimeForServerShutdown(\"onDisable\")",
                "startPostStopFinisher()",
                "} else {",
                "teardownRuntime(\"onDisable\", 30L)");
        assertOrdered(shutdownHook,
                "startupBoundaryRestart.get()",
                "finishDeferredRuntimeTeardown(\"startup-boundary-restart-hook\", 30L)",
                "return;",
                "finishRuntimeAfterServerShutdown(\"shutdown-hook\")");
        assertOrdered(finisher,
                "finishRuntimeAfterServerShutdown(\"post-server-stop\")",
                "finisher.setDaemon(false)",
                "finisher.start()");
        assertOrdered(boundaryFinish,
                "awaitServerShutdownBoundary()",
                "return;",
                "finishDeferredRuntimeTeardown(reason, 30L)");
        assertTrue("A missing shutdown boundary must retain the runtime",
                boundaryAwait.contains("if (!INMS.isBound()) {\n            return false;"));
        assertFalse("Runtime cleanup must wait for the shutdown boundary, not the server thread",
                source.contains("serverThread.join()") || source.contains("serverLifecycleThread"));
        assertTrue("Server-stop quiescence must leave queued Paper generation admitted",
                quiesce.contains("generator.quiesceForServerShutdown()"));
        assertOrdered(quiesce,
                "jigsawStudioService.quiesceForServerShutdown()",
                "PregeneratorJob.shutdownAndWait",
                "generator.quiesceForServerShutdown()");
        assertOrdered(onDisable,
                "quiesceRuntimeForServerShutdown(\"onDisable\")",
                "super.onDisable()");
        assertTrue("Server-stop quiescence must keep queued generation running its mantle stages",
                quiesce.contains("IrisToolbelt.beginWorldMaintenance(world, reason);"));
        assertTrue("Disable must wait for a replacement that is staging before it tears anything down",
                onDisable.indexOf("pendingWorldReplacements.awaitStagingForShutdown()") >= 0
                        && onDisable.indexOf("pendingWorldReplacements.awaitStagingForShutdown()")
                        < onDisable.indexOf("teardownPapi()"));
        assertFalse("Server-stop quiescence must not begin generator close before Paper's boundary",
                generatorQuiesce.contains("closing = true"));
        assertFalse("Server-stop quiescence must not dispatch generator close before Paper's boundary",
                generatorQuiesce.contains("closeAsync()"));
    }

    @Test
    public void deferredTeardown_keepsGeneratorClosingServicesBehindGeneratorDrain() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String engineService = Files.readString(Path.of(System.getProperty("iris.engineSvcSource")));
        String studioService = Files.readString(Path.of(System.getProperty("iris.studioSvcSource")));
        String generatorSource = Files.readString(Path.of(System.getProperty("iris.bukkitChunkGeneratorSource")));
        String teardown = section(source, "private void teardownRuntime", "private void quiesceRuntimeForServerShutdown");
        String engineDisable = section(engineService, "public void onDisable()", "public void engineStatus");
        String studioDisable = section(studioService, "public void onDisable()", "public IrisDimension installIntoWorld");
        String generatorClose = section(generatorSource,
                "public void close()", "public CompletableFuture<Void> closeAsync()");
        String generatorCloseAsync = section(generatorSource,
                "public CompletableFuture<Void> closeAsync()", "public void quiesceForServerShutdown()");

        assertOrdered(teardown,
                "drainWorldGenerators(reason, timeoutSeconds)",
                "service.onDisable()",
                "MultiBurst.burst::close",
                "MultiBurst.ioBurst::close");
        assertTrue("Engine service must retain its generator close behind deferred service teardown",
                engineDisable.contains("startClose("));
        assertTrue("Studio service must retain its generator close behind deferred service teardown",
                studioDisable.contains("generator.close()"));
        assertTrue("Service-level generator close must delegate to the shared idempotent close future",
                generatorClose.contains("closeAsync()"));
        assertTrue("Repeated post-boundary closes must return the already-published close future",
                generatorCloseAsync.contains("return existing;"));
        assertFalse("A completed generator close must never be re-dispatched",
                generatorCloseAsync.contains("!existing.isDone()"));
    }

    @Test
    public void preUnload_doesNotDrainGeneratorsDuringServerStop() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.startupSource")));
        String preUnload = section(source, "public void onPreUnload", "private void drainOnce");

        assertOrdered(preUnload,
                "IrisToolbelt.isServerStopping()",
                "quiesceRuntimeForServerShutdown",
                "startPostStopFinisher()",
                "return;",
                "drainOnce(");
    }

    @Test
    public void jigsawStudio_quiescesOnceBeforeDeferredServiceTeardown() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("iris.jigsawStudioSvcSource")));
        String enable = section(source, "public void onEnable()", "public void onDisable()");
        String disable = section(source, "public void onDisable()", "public void quiesceForServerShutdown()");
        String quiesce = section(source, "public void quiesceForServerShutdown()", "public void register(");
        String register = section(source, "public void register(", "public void activationCommitted(");
        String activation = section(source, "public void activationCommitted(", "public void markChunkGenerated(");
        String chunkGenerated = section(source, "public void markChunkGenerated(", "private void markChunkAvailable(");

        assertOrdered(enable, "disableStarted.set(false)", "enabled = true");
        assertTrue("Deferred service teardown must reuse the idempotent early disable",
                disable.contains("quiesceForServerShutdown();"));
        assertOrdered(quiesce,
                "disableStarted.compareAndSet(false, true)",
                "finalizeAllJigsawTileWatches()",
                "drainAutosavesBeforeDisable()",
                "enabled = false",
                "activeMenuController.closeAll()",
                "previewRenderer.removeAll()",
                "studios.clear()");
        assertTrue("Registration must reject work after shutdown begins",
                occurrences(register, "!enabled || disableStarted.get()") >= 2);
        assertTrue("Activation must reject work after shutdown begins",
                activation.contains("!enabled || disableStarted.get()"));
        assertTrue("Chunk-generation callbacks must reject work after shutdown begins",
                occurrences(chunkGenerated, "!enabled || disableStarted.get()") >= 2);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue("Missing source section starting with " + startMarker, start >= 0);
        assertTrue("Missing source section ending with " + endMarker, end > start);
        return source.substring(start, end);
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertTrue("Missing source marker " + marker, current >= 0);
            assertTrue("Source marker is out of order: " + marker, current > previous);
            previous = current;
        }
    }

    private static int occurrences(String source, String marker) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(marker, offset)) >= 0) {
            count++;
            offset += marker.length();
        }
        return count;
    }
}
