package art.arcane.iris.engine;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EngineRuntimePublicationContractTest {
    @Test
    public void worldManagerStartsOnlyAfterTheRuntimeSessionIsReady() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineRuntimeBuilder.java")).replace("\r\n", "\n");
        int publishStart = source.indexOf("void publishRuntime(");
        int publishEnd = source.indexOf("private void scheduleRuntimeTasks", publishStart);
        String publish = source.substring(publishStart, publishEnd);

        assertBefore(publish, "engine.runtime = next", "next.worldManager().start()");
        assertBefore(publish, "activateNextSession()", "next.worldManager().start()");
        assertBefore(publish, "engine.lifecycleState = LifecycleState.RUNNING", "next.worldManager().start()");
        assertBefore(publish, "engine.getClosing().set(false)", "next.worldManager().start()");
        assertBefore(publish, "openBackgroundTaskAdmission()", "next.worldManager().start()");
    }

    @Test
    public void failedManagerStartClosesAdmissionBeforeRuntimeCleanup() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineRuntimeBuilder.java")).replace("\r\n", "\n");
        int publishStart = source.indexOf("void publishRuntime(");
        int publishEnd = source.indexOf("private void scheduleRuntimeTasks", publishStart);
        String publish = source.substring(publishStart, publishEnd);

        assertBefore(publish, "engine.getClosing().set(true)", "cleanupFailure = engine.shutdownSequence.closeRuntime(next, null)");
        assertBefore(publish, "closeBackgroundTaskAdmission()", "cleanupFailure = engine.shutdownSequence.closeRuntime(next, null)");
        assertBefore(publish, "sealAndAwait(", "cleanupFailure = engine.shutdownSequence.closeRuntime(next, null)");
    }

    @Test
    public void dimensionStackRegistrationRunsAfterHostStaticObjects() throws IOException {
        String builderSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineRuntimeBuilder.java")).replace("\r\n", "\n");
        int buildStart = builderSource.indexOf("private EngineRuntime buildRuntime(");
        int buildEnd = builderSource.indexOf("GenerationRuntime buildDetachedGenerationRuntime(", buildStart);
        String build = builderSource.substring(buildStart, buildEnd);

        assertBefore(build, "assembly.runtimeKernel.registerStaticObjects(engine, assembly.mode)",
                "assembly.runtimeKernel.registerDimensionStack(");

        String kernelSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/history/GenerationKernelV1.java")).replace("\r\n", "\n");
        assertBefore(kernelSource, "staticObjects.apply(engine, x, z, blocks)",
                "dimensionStack.actuate(x, z, blocks, multicore, chunkContext)");
        assertBefore(kernelSource, "dimensionStack.actuate(x, z, blocks, multicore, chunkContext)",
                "stackCustom.modify(x, z, blocks, multicore, chunkContext)");
    }

    @Test
    public void detachedRuntimeBuildDoesNotCreateWorldOwnedServices() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineRuntimeBuilder.java")).replace("\r\n", "\n");
        int buildStart = source.indexOf("GenerationRuntime buildDetachedGenerationRuntime(");
        int buildEnd = source.indexOf("void publishRuntime(", buildStart);
        String build = source.substring(buildStart, buildEnd);

        assertTrue(build.contains("assembly.runtimeKernel.createComplex(engine, assembly.transitionPlan)"));
        assertTrue(build.contains("assembly.runtimeKernel.createMantle(engine, assembly.mantleStorageDirectory)"));
        assertTrue(build.contains("assembly.mantle.hotload()"));
        assertTrue(build.contains("assembly.runtimeKernel.createMode(engine)"));
        assertTrue(build.contains("assembly.runtimeKernel.createUpperContext(engine)"));
        assertTrue(build.contains("assembly.runtimeKernel.registerStaticObjects(engine, assembly.mode)"));
        assertFalse(build.contains("EngineEffectsProvider"));
        assertFalse(build.contains("EngineWorldManagerProvider"));
        assertFalse(build.contains("worldManager().start()"));
    }

    @Test
    public void runtimePublicationTransfersAnIdenticalMantleWithoutClosingIt() throws IOException {
        String builderSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineRuntimeBuilder.java")).replace("\r\n", "\n");
        String shutdownSource = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/EngineShutdownSequence.java")).replace("\r\n", "\n");

        assertTrue(builderSource.contains("next.generation().mantle()"));
        assertTrue(shutdownSource.contains("generationRuntime.mantle() != retainedMantle"));
    }

    @Test
    public void engineAndDetachedRuntimeAcceptExplicitMantleDirectories() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/IrisEngine.java")).replace("\r\n", "\n");

        assertTrue(source.contains("InitializationMode initializationMode,\n            Path mantleStorageDirectory"));
        assertTrue(source.contains("EngineTarget runtimeTarget,\n            Path mantleStorageDirectory"));
        assertTrue(source.contains("exclusive mantle storage directory"));
    }

    @Test
    public void mantleIoPinsTheRuntimeDataAcrossWorkerThreads() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/IrisEngineMantle.java")).replace("\r\n", "\n");

        assertTrue(source.contains("runtimeData::get"));
        assertTrue(source.contains("runtimeData.set(Objects.requireNonNull(engine.getData()"));
        assertFalse(source.contains("createDataAdapter(engine::getData)"));
    }

    @Test
    public void failedDetachedBuildReleasesTransferredTargetOwnership() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/engine/IrisEngine.java")).replace("\r\n", "\n");
        int buildStart = source.indexOf("public GenerationRuntimeBinding buildDetachedGenerationRuntime(");
        int buildEnd = source.indexOf("public void closeDetachedGenerationRuntime(", buildStart);
        String build = source.substring(buildStart, buildEnd);

        assertTrue(build.contains("detachedData.registerEngine(this)"));
        assertTrue(build.contains("shutdownSequence.closeDetachedTarget(requiredTarget, null)"));
        assertTrue(build.contains("shutdownSequence.closeDetachedGenerationRuntime(detached, null)"));
    }

    private static void assertBefore(String source, String first, String second) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        assertTrue("Missing source contract token: " + first, firstIndex >= 0);
        assertTrue("Missing source contract token: " + second, secondIndex >= 0);
        assertTrue(first + " must occur before " + second, firstIndex < secondIndex);
    }
}
