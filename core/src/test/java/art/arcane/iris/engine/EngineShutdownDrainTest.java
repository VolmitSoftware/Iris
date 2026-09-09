package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.EngineRuntimeBuilder.RuntimeAssembly;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedComponent;
import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.framework.GenerationSessionException;
import art.arcane.iris.engine.framework.GenerationSessionManager;
import art.arcane.iris.engine.framework.NativeStructureOwnershipStore;
import art.arcane.iris.engine.history.GenerationKernelRegistry;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationAdmission;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.DurabilityMode;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class EngineShutdownDrainTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static IrisPlatform previousPlatform;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.block(anyString())).thenReturn(mock(PlatformBlockState.class));
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void queuedWorldManagerRoutesAreCancelledBeforeRouterDrain() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        AtomicBoolean queuedRoute = new AtomicBoolean(true);
        doAnswer(invocation -> {
            queuedRoute.set(false);
            return null;
        }).when(fixture.manager).close();
        doAnswer(invocation -> {
            assertFalse("Router drain must not wait for a queued region callback", queuedRoute.get());
            return null;
        }).when(fixture.engine).closeAttachedGenerationHistoryRuntimeRouter();

        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }

        InOrder shutdownOrder = inOrder(fixture.manager, fixture.engine, fixture.sessions);
        shutdownOrder.verify(fixture.manager).close();
        shutdownOrder.verify(fixture.engine).closeAttachedGenerationHistoryRuntimeRouter();
        shutdownOrder.verify(fixture.sessions).sealAndAwait("close", IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS, true);
        verify(fixture.manager).close();
        assertTrue(fixture.engine.closed);
    }

    @Test
    public void failedWorldManagerStopPreventsRouterDrainAndCanBeRetried() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        doThrow(new IllegalStateException("World manager still has work")).doNothing().when(fixture.manager).close();

        assertThrows(IllegalStateException.class, fixture.shutdown::close);
        verify(fixture.engine, never()).closeAttachedGenerationHistoryRuntimeRouter();
        verify(fixture.mantle, never()).close();
        assertFalse(fixture.engine.closed);

        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }

        verify(fixture.manager, times(2)).close();
        verify(fixture.engine).closeAttachedGenerationHistoryRuntimeRouter();
        assertTrue(fixture.engine.closed);
    }

    @Test
    public void successfulEngineCloseAllowsTheSameWorldToPrepareGenerationAgain() throws Exception {
        GenerationHistory history = history();
        ShutdownFixture fixture = new ShutdownFixture();
        fixture.attachHistory(history);
        try (GenerationHistory.GenerationStage ignored = history.openStage(2, 3)) {
        }
        assertThrows(IllegalStateException.class, () -> history.prepareCurrentGenerator(32));
        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertTrue(fixture.engine.closed);
        GenerationHistory reopened = GenerationHistory.open(history.paths().dimensionRoot());
        reopened.prepareCurrentGenerator(32);
        try (GenerationHistory.GenerationStage ignored = reopened.openStage(3, 4)) {
        }
    }

    @Test
    public void failedEngineCloseKeepsAdmissionUntilSuccessfulRetry() throws Exception {
        GenerationHistory history = history();
        ShutdownFixture fixture = new ShutdownFixture();
        fixture.attachHistory(history);
        try (GenerationHistory.GenerationStage ignored = history.openStage(2, 3)) {
        }
        doThrow(new IllegalStateException("Planner still active")).when(fixture.complex).close();
        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            assertThrows(IllegalStateException.class, fixture.shutdown::close);
            GenerationHistory premature = GenerationHistory.open(history.paths().dimensionRoot());
            assertThrows(IllegalStateException.class, () -> premature.prepareCurrentGenerator(32));
            doNothing().when(fixture.complex).close();
            fixture.shutdown.close();
        }
        GenerationHistory reopened = GenerationHistory.open(history.paths().dimensionRoot());
        reopened.prepareCurrentGenerator(32);
    }

    @Test
    public void startupOwnershipSpansReplacementEnginesUsingTheSameHistory() throws Exception {
        GenerationHistory history = history();
        ShutdownFixture first = new ShutdownFixture();
        ShutdownFixture replacement = new ShutdownFixture();
        try (GenerationAdmission.RuntimeLease startup = history.retainRuntime();
             MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            first.attachHistory(history);
            first.shutdown.close();
            history.prepareCurrentGenerator(32);
            replacement.attachHistory(history);
        }
        try (GenerationHistory.GenerationStage ignored = history.openStage(2, 3)) {
        }
        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            replacement.shutdown.close();
        }
        GenerationHistory.open(history.paths().dimensionRoot()).prepareCurrentGenerator(32);
    }

    @Test
    public void reopenedWorldPromotesPendingGenerationAfterTheOldEngineCloses() throws Exception {
        GenerationHistory history = history();
        ShutdownFixture fixture = new ShutdownFixture();
        fixture.attachHistory(history);
        try (GenerationHistory.GenerationStage ignored = history.openStage(2, 3)) {
        }
        Path replacementPack = temporaryFolder.newFolder("replacement-pack").toPath();
        Files.createDirectories(replacementPack.resolve("dimensions"));
        Files.writeString(replacementPack.resolve("dimensions/main.json"), "{\"name\":\"replacement\"}");
        history.stageUpdate(replacementPack,
                GenerationPackFingerprint.compute(replacementPack, GenerationPackFingerprint.CURRENT_VERSION),
                history.activeEpoch().dimensionContract(), GenerationRegistryContract.empty(), 32);
        assertThrows(IllegalStateException.class, () -> history.prepareCurrentGenerator(32));
        try (MockedStatic<NativeStructureOwnershipStore> ownership = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        GenerationHistory reopened = GenerationHistory.open(history.paths().dimensionRoot());
        reopened.prepareCurrentGenerator(32);
        assertEquals(2L, reopened.activeActivation().activationId());
        assertFalse(reopened.pendingActivation().isPresent());
    }

    private GenerationHistory history() throws Exception {
        Path world = temporaryFolder.newFolder("history-world").toPath();
        Path pack = temporaryFolder.newFolder("history-pack").toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/main.json"), "{}");
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld_type", "NORMAL", "OVERWORLD", 127,
                -64, 384, 384, 1D, false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
        return GenerationHistory.create(world, pack,
                GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION),
                42L, contract, GenerationRegistryContract.empty());
    }

    @Test
    public void failedPlannerDrainRetainsRuntimeStorage() {
        GenerationRuntime runtime = mock(GenerationRuntime.class);
        IrisComplex complex = mock(IrisComplex.class);
        EngineMantle mantle = mock(EngineMantle.class);
        CompletableFuture<Long> hash = new CompletableFuture<>();
        when(runtime.mode()).thenReturn(mock(EngineMode.class));
        when(runtime.complex()).thenReturn(complex);
        when(runtime.mantle()).thenReturn(mantle);
        when(runtime.hash32()).thenReturn(hash);
        IllegalStateException failure = new IllegalStateException("Planner still active");
        doThrow(failure).when(complex).close();

        assertSame(failure, new EngineShutdownSequence(null).closeGenerationRuntime(runtime, null));
        verifyNoInteractions(mantle);
        assertFalse(hash.isCancelled());
    }

    @Test
    public void successfulPlannerDrainPrecedesMantleRelease() {
        GenerationRuntime runtime = mock(GenerationRuntime.class);
        IrisComplex complex = mock(IrisComplex.class);
        EngineMantle mantle = mock(EngineMantle.class);
        CompletableFuture<Long> hash = new CompletableFuture<>();
        when(runtime.mode()).thenReturn(mock(EngineMode.class));
        when(runtime.complex()).thenReturn(complex);
        when(runtime.mantle()).thenReturn(mantle);
        when(runtime.hash32()).thenReturn(hash);

        assertNull(new EngineShutdownSequence(null).closeGenerationRuntime(runtime, null));
        InOrder releases = inOrder(complex, mantle);
        releases.verify(complex).close();
        releases.verify(mantle).saveAllNow();
        releases.verify(mantle).close();
        assertTrue(hash.isCancelled());
    }

    @Test
    public void failedAssemblyDrainRetainsMantleOwnership() {
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        assembly.complex = mock(IrisComplex.class);
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        assembly.hash32 = new CompletableFuture<>();
        IllegalStateException failure = new IllegalStateException("Planner still active");
        doThrow(failure).when(assembly.complex).close();

        assertSame(failure, new EngineShutdownSequence(null).closeAssembly(assembly, null));
        verifyNoInteractions(assembly.mantle);
        assertTrue(assembly.ownsMantle);
        assertFalse(assembly.hash32.isCancelled());
    }
    @Test
    public void failedAssemblySaveKeepsOwnershipAndDoesNotCloseStorage() {
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        IllegalStateException failure = new IllegalStateException("Save failed");
        doThrow(failure).doNothing().when(assembly.mantle).saveAllNow();
        EngineShutdownSequence shutdown = new EngineShutdownSequence(null);

        assertSame(failure, shutdown.closeAssembly(assembly, null));
        assertTrue(assembly.ownsMantle);
        verify(assembly.mantle, never()).close();
        assertNull(shutdown.closeAssembly(assembly, null));
        assertFalse(assembly.ownsMantle);
        assertNull(shutdown.closeAssembly(assembly, null));
        verify(assembly.mantle, times(2)).saveAllNow();
        verify(assembly.mantle).close();
    }

    @Test
    public void failedAssemblyCloseKeepsOwnershipUntilRetrySucceeds() {
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        IllegalStateException failure = new IllegalStateException("Close failed");
        doThrow(failure).doNothing().when(assembly.mantle).close();
        EngineShutdownSequence shutdown = new EngineShutdownSequence(null);

        assertSame(failure, shutdown.closeAssembly(assembly, null));
        assertTrue(assembly.ownsMantle);
        assertNull(shutdown.closeAssembly(assembly, null));
        assertFalse(assembly.ownsMantle);
        verify(assembly.mantle, times(2)).saveAllNow();
        verify(assembly.mantle, times(2)).close();
    }

    @Test
    public void failedPartialAssemblyRetainsDataAndRetriesInsideItsAssemblyScope() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        RuntimeAssembly assembly = mock(RuntimeAssembly.class);
        EngineTarget detachedTarget = target();
        setField(assembly, "target", detachedTarget);
        assembly.complex = mock(IrisComplex.class);
        assembly.mantle = mock(EngineMantle.class);
        assembly.ownsMantle = true;
        RuntimeAssembly previous = mock(RuntimeAssembly.class);
        fixture.engine.runtimeAssembly.set(previous);
        IllegalStateException failure = new IllegalStateException("Planner still active");
        doAnswer(invocation -> {
            assertSame(assembly, fixture.engine.runtimeAssembly.get());
            throw failure;
        }).when(assembly.complex).close();

        assertSame(failure, fixture.shutdown.closeAssembly(assembly, null));
        assertSame(previous, fixture.engine.runtimeAssembly.get());
        fixture.engine.runtimeAssembly.remove();
        assertTrue(fixture.shutdown.retainsData(detachedTarget.getData()));
        assertTrue(fixture.shutdown.closeDetachedTarget(detachedTarget, null) instanceof IllegalStateException);
        verify(detachedTarget, never()).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            assertThrows(IllegalStateException.class, fixture.shutdown::close);
            verify(fixture.target, never()).close();
            verifyNoInteractions(assembly.mantle);
            doAnswer(invocation -> {
                assertSame(assembly, fixture.engine.runtimeAssembly.get());
                return null;
            }).when(assembly.complex).close();
            fixture.shutdown.close();
        }

        assertNull(fixture.engine.runtimeAssembly.get());
        assertFalse(assembly.ownsMantle);
        assertFalse(fixture.shutdown.retainsData(detachedTarget.getData()));
        verify(assembly.mantle).close();
        verify(detachedTarget).close();
        verify(detachedTarget.getData()).close();
        assertTrue(fixture.engine.closed);
        assertNull(fixture.engine.runtime);
    }

    @Test
    public void failedManagerStartKeepsPublishedRuntimeWhenCleanupFails() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        fixture.engine.runtime = null;
        IllegalStateException startFailure = new IllegalStateException("Manager start failed");
        IllegalStateException closeFailure = new IllegalStateException("Planner still active");
        doThrow(startFailure).when(fixture.manager).start();
        doThrow(closeFailure).when(fixture.complex).close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new EngineRuntimeBuilder(fixture.engine).publishRuntime(fixture.runtime, null));

        assertSame(startFailure, failure.getCause());
        assertSame(closeFailure, startFailure.getSuppressed()[0]);
        assertSame(fixture.runtime, fixture.engine.runtime);
        assertTrue(fixture.shutdown.retainsData(fixture.target.getData()));
        verify(fixture.mantle, never()).close();
        doNothing().when(fixture.complex).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertNull(fixture.engine.runtime);
        assertTrue(fixture.engine.closed);
        verify(fixture.target).close();
    }

    @Test
    public void failedStartDrainRetainsResourcesUntilGenerationStops() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        fixture.engine.runtime = null;
        doThrow(new IllegalStateException("Manager start failed")).when(fixture.manager).start();
        doThrow(new GenerationSessionException("Generation still active", false)).when(fixture.sessions)
                .sealAndAwait("failed world manager start", IrisEngine.SESSION_DRAIN_TIMEOUT_MILLIS, true);

        assertThrows(IllegalStateException.class,
                () -> new EngineRuntimeBuilder(fixture.engine).publishRuntime(fixture.runtime, null));

        assertSame(fixture.runtime, fixture.engine.runtime);
        verify(fixture.manager, never()).close();
        verifyNoInteractions(fixture.complex, fixture.mantle);
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertNull(fixture.engine.runtime);
        assertTrue(fixture.engine.closed);
    }

    @Test
    public void failedRetirementRetainsUnpublishedRuntimeWithoutClosingSharedMantle() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        EngineTarget nextTarget = target();
        GenerationRuntime nextGeneration = generation(nextTarget, fixture.mantle);
        EngineRuntime next = new EngineRuntime(nextGeneration,
                new ChangingEffects(fixture.engine), mock(EngineWorldManager.class));
        EngineMode nextMode = nextGeneration.mode();
        doThrow(new IllegalStateException("Previous planner active")).when(fixture.complex).close();
        doThrow(new IllegalStateException("Next mode active")).when(nextMode).close();

        assertThrows(IllegalStateException.class,
                () -> new EngineRuntimeBuilder(fixture.engine).publishRuntime(next, fixture.runtime));

        assertSame(fixture.runtime, fixture.engine.runtime);
        assertTrue(fixture.shutdown.retainsData(nextTarget.getData()));
        verifyNoInteractions(fixture.mantle);
        doNothing().when(fixture.complex).close();
        doNothing().when(nextMode).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertFalse(fixture.shutdown.retainsData(nextTarget.getData()));
        verify(nextTarget).close();
        verify(fixture.target).close();
        verify(fixture.mantle).saveAllNow();
        verify(fixture.mantle).close();
        assertTrue(fixture.engine.closed);
    }

    @Test
    public void failedConstructionDrainPreventsAllRuntimeAndTargetRelease() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        doThrow(new GenerationSessionException("Generation still active", false)).doNothing().when(fixture.sessions)
                .sealAndAwait("failed initialization", 0L, true);
        IllegalStateException original = new IllegalStateException("Construction failed");
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.cleanupFailedConstruction(original);
            assertSame(fixture.runtime, fixture.engine.runtime);
            assertFalse(fixture.engine.closed);
            verifyNoInteractions(fixture.complex, fixture.mantle);
            verify(fixture.target, never()).close();
            fixture.shutdown.cleanupFailedConstruction(original);
        }
        assertNull(fixture.engine.runtime);
        assertTrue(fixture.engine.closed);
        verify(fixture.mantle).close();
        verify(fixture.target).close();
    }

    @Test
    public void failedComplexRetirementRetainsReplacementBeforeSharedStorageCanClose() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        IrisComplex replacement = mock(IrisComplex.class);
        fixture.prepareComplexHotload(replacement);
        doThrow(new IllegalStateException("Previous planner still active")).when(fixture.complex).close();
        doThrow(new IllegalStateException("Replacement planner still active")).when(replacement).close();

        assertThrows(IllegalStateException.class, () -> new EngineHotloader(fixture.engine).hotloadComplex());

        assertSame(fixture.runtime, fixture.engine.runtime);
        assertSame(IrisEngine.LifecycleState.FAILED, fixture.engine.lifecycleState);
        doNothing().when(fixture.complex).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            assertThrows(IllegalStateException.class, fixture.shutdown::close);
            verify(replacement, times(2)).close();
            verify(fixture.mantle, never()).close();
            verify(fixture.target, never()).close();
            doNothing().when(replacement).close();
            fixture.shutdown.close();
        }
        assertTrue(fixture.engine.closed);
        verify(replacement, times(3)).close();
        verify(fixture.mantle).close();
    }

    @Test
    public void failedComplexBuildDrainBlocksRollbackUntilShutdownRetry() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        IrisComplex replacement = mock(IrisComplex.class);
        GenerationKernelRegistry.RuntimeKernel kernel = fixture.prepareComplexHotload(replacement);
        IllegalStateException buildFailure = new IllegalStateException("Dimension stack failed");
        when(kernel.createDimensionStackContext(fixture.engine)).thenThrow(buildFailure);
        IllegalStateException drainFailure = new IllegalStateException("Replacement planner still active");
        doThrow(drainFailure).when(replacement).close();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new EngineHotloader(fixture.engine).hotloadComplex());

        assertSame(buildFailure, failure.getCause());
        assertSame(drainFailure, buildFailure.getSuppressed()[0]);
        assertSame(IrisEngine.LifecycleState.FAILED, fixture.engine.lifecycleState);
        assertSame(fixture.runtime, fixture.engine.runtime);
        verify(fixture.sessions, never()).activateNextSession();
        verify(fixture.complex, never()).close();
        doNothing().when(replacement).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertTrue(fixture.engine.closed);
        verify(replacement, times(2)).close();
        verify(fixture.mantle).close();
    }

    @Test
    public void successfullyDrainedFailedComplexBuildRestoresPreviousRuntime() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        IrisComplex replacement = mock(IrisComplex.class);
        GenerationKernelRegistry.RuntimeKernel kernel = fixture.prepareComplexHotload(replacement);
        when(kernel.createDimensionStackContext(fixture.engine))
                .thenThrow(new IllegalStateException("Dimension stack failed"));

        assertThrows(IllegalStateException.class, () -> new EngineHotloader(fixture.engine).hotloadComplex());

        assertSame(fixture.runtime, fixture.engine.runtime);
        assertSame(IrisEngine.LifecycleState.RUNNING, fixture.engine.lifecycleState);
        verify(replacement).close();
        verify(fixture.complex, never()).close();
        verify(fixture.mantle, never()).close();
        verify(fixture.sessions).activateNextSession();
    }

    @Test
    public void successfulComplexHotloadPublishesReplacementWithoutClosingIt() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        IrisComplex replacement = mock(IrisComplex.class);
        fixture.prepareComplexHotload(replacement);

        new EngineHotloader(fixture.engine).hotloadComplex();

        assertSame(replacement, fixture.engine.runtime.generation().complex());
        assertSame(IrisEngine.LifecycleState.RUNNING, fixture.engine.lifecycleState);
        verify(fixture.complex).close();
        verify(replacement, never()).close();
        verify(fixture.mantle, never()).close();
    }

    @Test
    public void failedComplexActivationKeepsPublishedReplacementOwnedForShutdown() throws Exception {
        ShutdownFixture fixture = new ShutdownFixture();
        IrisComplex replacement = mock(IrisComplex.class);
        fixture.prepareComplexHotload(replacement);
        doThrow(new IllegalStateException("Session activation failed")).when(fixture.sessions).activateNextSession();

        assertThrows(IllegalStateException.class, () -> new EngineHotloader(fixture.engine).hotloadComplex());

        assertSame(replacement, fixture.engine.runtime.generation().complex());
        assertSame(IrisEngine.LifecycleState.FAILED, fixture.engine.lifecycleState);
        assertTrue(fixture.engine.getClosing().get());
        verify(fixture.complex).close();
        verify(replacement, never()).close();
        try (MockedStatic<NativeStructureOwnershipStore> ignored = mockStatic(NativeStructureOwnershipStore.class)) {
            fixture.shutdown.close();
        }
        assertTrue(fixture.engine.closed);
        verify(replacement).close();
    }

    private static EngineTarget target() {
        EngineTarget target = mock(EngineTarget.class);
        when(target.getData()).thenReturn(mock(IrisData.class));
        when(target.getWorld()).thenReturn(mock(IrisWorld.class));
        return target;
    }

    private static GenerationRuntime generation(EngineTarget target, EngineMantle mantle) {
        GenerationRuntime generation = mock(GenerationRuntime.class);
        IrisData data = target.getData();
        when(generation.target()).thenReturn(target);
        when(generation.data()).thenReturn(data);
        when(generation.mantle()).thenReturn(mantle);
        when(generation.complex()).thenReturn(mock(IrisComplex.class));
        when(generation.mode()).thenReturn(mock(EngineMode.class));
        when(generation.hash32()).thenReturn(new CompletableFuture<>());
        return generation;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class ChangingEffects extends EngineAssignedComponent implements EngineEffects {
        private int hash = 1;

        private ChangingEffects(Engine engine) {
            super(engine, "changing effects");
        }

        @Override
        public void updatePlayerMap() {
        }

        @Override
        public void tickRandomPlayer() {
        }

        @Override
        public void close() {
            hash++;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class ShutdownFixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final EngineTarget target = target();
        private final EngineMantle mantle = mock(EngineMantle.class);
        private final GenerationRuntime generation = generation(target, mantle);
        private final IrisComplex complex = generation.complex();
        private final EngineWorldManager manager = mock(EngineWorldManager.class);
        private final EngineRuntime runtime = new EngineRuntime(generation, mock(EngineEffects.class), manager);
        private final GenerationSessionManager sessions = mock(GenerationSessionManager.class);
        private final EngineShutdownSequence shutdown = new EngineShutdownSequence(engine);

        private GenerationKernelRegistry.RuntimeKernel prepareComplexHotload(IrisComplex replacement) throws Exception {
            GenerationKernelRegistry.RuntimeKernel kernel = mock(GenerationKernelRegistry.RuntimeKernel.class);
            when(kernel.createComplex(engine, null, false)).thenReturn(replacement);
            when(generation.runtimeKernel()).thenReturn(kernel);
            GenerationRuntime nextGeneration = generation(target, mantle);
            when(nextGeneration.complex()).thenReturn(replacement);
            when(generation.withComplex(anyInt(), same(replacement), isNull(), isNull(), any()))
                    .thenReturn(nextGeneration);
            EngineRuntimeBuilder builder = spy(new EngineRuntimeBuilder(engine));
            doReturn(new GenerationRuntime.BiomeMaxes(0D, 0D, 0D)).when(builder).computeBiomeMaxes();
            setField(engine, "runtimeBuilder", builder);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
            return kernel;
        }

        private void attachHistory(GenerationHistory history) throws Exception {
            GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
            when(router.engine()).thenReturn(engine);
            when(router.history()).thenReturn(history);
            setField(engine, "generationHistoryRuntimeRouterLock", new Object());
            doCallRealMethod().when(engine).attachGenerationHistoryRuntimeRouter(router);
            engine.attachGenerationHistoryRuntimeRouter(router);
        }

        private ShutdownFixture() throws Exception {
            EngineBackgroundTasks background = mock(EngineBackgroundTasks.class);
            when(background.drainBackgroundTasks(anyString()))
                    .thenReturn(new EngineBackgroundTasks.BackgroundTaskDrain(null, true));
            setField(engine, "backgroundTasks", background);
            setField(engine, "shutdownSequence", shutdown);
            setField(engine, "engineDataStore", mock(EngineDataStore.class));
            setField(engine, "lifecycleLock", new Object());
            setField(engine, "runtimeAssembly", new ThreadLocal<RuntimeAssembly>());
            setField(engine, "detachedGenerationRuntimes", ConcurrentHashMap.newKeySet());
            when(engine.getClosing()).thenReturn(new AtomicBoolean());
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(engine.getPlatformHooks()).thenReturn(mock(EnginePlatformHooks.class));
            when(engine.getMantle()).thenReturn(mantle);
            IrisWorld world = target.getWorld();
            when(engine.getWorld()).thenReturn(world);
            when(engine.beginShutdown()).thenReturn(true);
            engine.runtime = runtime;
            engine.publishedTarget = target;
        }
    }

}
