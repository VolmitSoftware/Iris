package art.arcane.iris.engine;

import art.arcane.iris.engine.object.runtime.IrisEngineData;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureBootstrapLifecycleContractTest {
    @Test
    public void closeWaitsBeforeLifecycleMutationAndGenerationSeal() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");
        int closeStart = source.indexOf("void close()");
        int closeEnd = source.indexOf("void cleanupFailedConstruction", closeStart);
        String close = source.substring(closeStart, closeEnd);

        assertBefore(close,
                "engine.awaitNativeStructureBootstrap(\"close\")",
                "engine.lifecycleState = LifecycleState.CLOSING");
        assertBefore(close,
                "engine.awaitNativeStructureBootstrap(\"close\")",
                "sealAndAwait(\"close\"");
    }

    @Test
    public void fullAndComplexHotloadWaitBeforeLifecycleMutationAndGenerationSeal() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineHotloader.java")).replace("\r\n", "\n");
        int complexStart = source.indexOf("void hotloadComplex()");
        int fullStart = source.indexOf("void hotloadSilently()", complexStart);
        String complex = source.substring(complexStart, fullStart);
        int fullEnd = source.indexOf("private void broadcastStudioHotload", fullStart);
        String full = source.substring(fullStart, fullEnd);

        assertBefore(complex,
                "engine.awaitNativeStructureBootstrap(\"complex hotload\")",
                "engine.lifecycleState = LifecycleState.HOTLOADING");
        assertBefore(complex,
                "engine.awaitNativeStructureBootstrap(\"complex hotload\")",
                "engine.sealForTransition(\"complex hotload\"");
        assertBefore(full,
                "engine.awaitNativeStructureBootstrap(\"hotload\")",
                "engine.lifecycleState = LifecycleState.HOTLOADING");
        assertBefore(full,
                "engine.awaitNativeStructureBootstrap(\"hotload\")",
                "engine.sealForTransition(\"hotload\"");
    }

    @Test
    public void ringBootstrapStartsUnderTheSameLifecycleLock() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/IrisEngine.java")).replace("\r\n", "\n");
        int start = source.indexOf("public CompletableFuture<Void> startNativeStructureBootstrap(");
        int end = source.indexOf("void awaitNativeStructureBootstrap", start);
        String method = source.substring(start, end);

        assertBefore(method, "synchronized (lifecycleLock)",
                "nativeStructureBootstrapBarrier.start(claim, starter)");
        assertBefore(method, "requireRunning(\"prepare native structure placements\")",
                "nativeStructureBootstrapBarrier.start(claim, starter)");
        assertBefore(method, "nativeStructureBootstrapBarrier.start(claim, starter)",
                "activation.run()");
    }

    @Test
    public void studioVolumeQueriesStayOutsideCachesUntilTheBootstrapGateReleases() throws IOException {
        String engineSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/IrisEngine.java")).replace("\r\n", "\n");
        int queryStart = engineSource.indexOf(
                "public KList<NativeStructureVolume> getNativeStructureVolumes(");
        int queryEnd = engineSource.indexOf(
                "public void setNativeStructureVolumeQueriesEnabled", queryStart);
        String query = engineSource.substring(queryStart, queryEnd);
        int setterEnd = engineSource.indexOf("public IrisEngineData getEngineData()", queryEnd);
        String setter = engineSource.substring(queryEnd, setterEnd);
        String generatorSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/platform/BukkitChunkGenerator.java")).replace("\r\n", "\n");
        int releaseStart = generatorSource.indexOf("public void endStudioEntryBootstrap()");
        int releaseEnd = generatorSource.indexOf(
                "public boolean isStudioEntryBootstrapActive()", releaseStart);
        String release = generatorSource.substring(releaseStart, releaseEnd);

        assertTrue(engineSource.contains(
                "new AtomicBoolean(!requiredMode.studio())"));
        assertBefore(query,
                "!nativeStructureVolumeQueriesEnabled.get()",
                "nativeStructureVolumeMemo.volumes(");
        assertBefore(setter,
                "nativeStructureVolumeMemo.clear()",
                "nativeStructureVolumeQueriesEnabled.set(true)");
        assertBefore(release,
                "irisEngine.setNativeStructureVolumeQueriesEnabled(shouldGenerateNativeStructures(",
                "studioEntryBootstrapActive.set(false)");
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue("Missing source contract token: " + first, firstIndex >= 0);
        assertTrue("Missing source contract token: " + second, secondIndex >= 0);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }
}
