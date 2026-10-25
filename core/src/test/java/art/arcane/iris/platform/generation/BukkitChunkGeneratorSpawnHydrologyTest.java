package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.studio.StudioMode;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

public class BukkitChunkGeneratorSpawnHydrologyTest {
    @Test
    public void studioDeferralSurvivesStructureBootstrapAndEndsOnlyAfterEntryCompletion() throws Exception {
        Fixture fixture = new Fixture();
        setField(fixture.generator, "studio", true);
        setField(fixture.generator, "studioEntryBootstrapActive", new AtomicBoolean(true));
        fixture.generator.setEngine(null);
        fixture.generator.beginInitialEntry(false);
        fixture.generator.publishInitializedEngine(fixture.engine);

        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);
        verify(fixture.engine).startEntryHydrology(0, 0);
        fixture.generator.endStudioEntryBootstrap();
        verifyOnlyEnablementChecked(fixture.hydrology);

        fixture.generator.completeInitialEntry();
        fixture.generator.completeInitialEntry();
        verify(fixture.engine, times(1)).startEntryHydrology(0, 0);
    }

    @Test
    public void studioCompletionBeforePublicationDoesNotSuspendNewRuntime() throws Exception {
        Fixture fixture = new Fixture();
        setField(fixture.generator, "studio", true);
        fixture.generator.setEngine(null);
        fixture.generator.beginInitialEntry(true);
        fixture.generator.completeInitialEntry();
        fixture.generator.publishInitializedEngine(fixture.engine);

        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void studioWatcherAndExplicitHotloadWaitUntilInitialEntryCompletes() throws Exception {
        Fixture fixture = new Fixture();
        setField(fixture.generator, "studio", true);
        fixture.generator.setEngine(null);
        fixture.generator.beginInitialEntry(false);
        fixture.generator.publishInitializedEngine(fixture.engine);
        Method watcher = BukkitChunkGenerator.class.getDeclaredMethod("hotloadFromWatcher");
        watcher.setAccessible(true);

        watcher.invoke(fixture.generator);
        fixture.generator.hotload();
        verify(fixture.engine, never()).hotload();

        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(fixture.generator).withExclusiveControl(any(Runnable.class));
        fixture.generator.completeInitialEntry();
        fixture.generator.hotload();
        verify(fixture.engine).hotload();
    }

    @Test
    public void creationDefersNeighboursBeforePublishingAndKeepsEntryDemandTracked() throws Exception {
        Fixture fixture = new Fixture();
        fixture.generator.setEngine(null);
        setField(fixture.generator, "studioEntryBootstrapActive", new AtomicBoolean(false));
        fixture.generator.beginInitialEntry(false);
        fixture.generator.publishInitializedEngine(fixture.engine);
        assertTrue(fixture.generator.shouldGenerateStructures());

        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verify(fixture.engine).startEntryHydrology(0, 0);
        verifyOnlyEnablementChecked(fixture.hydrology);
        fixture.generator.completeInitialEntry();
        fixture.generator.completeInitialEntry();
        verify(fixture.engine, times(1)).startEntryHydrology(0, 0);
    }

    @Test
    public void completionBeforePublicationDoesNotLeaveNormalStartupDisabled() throws Exception {
        Fixture fixture = new Fixture();
        fixture.generator.setEngine(null);
        fixture.generator.beginInitialEntry(false);
        fixture.generator.completeInitialEntry();
        fixture.generator.publishInitializedEngine(fixture.engine);
        verifyOnlyEnablementChecked(fixture.hydrology);

        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verify(fixture.hydrology).prepareWindowsAround(-16, -16, 15, 15, 1);
        verify(fixture.engine, never()).startEntryHydrology(anyInt(), anyInt());
    }

    @Test
    public void initializedGeneratorCannotBeginLateEntryDeferral() throws Exception {
        Fixture fixture = new Fixture();

        assertThrows(IllegalStateException.class, () -> fixture.generator.beginInitialEntry(false));
        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void startupWaitsAsynchronouslyWithoutHoldingGenerationAdmission() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();

        assertFalse(prefetch.isDone());
        assertEquals(2, fixture.gate.availablePermits());
        verifyOnlyEnablementChecked(fixture.hydrology);
        verify(fixture.world).getChunkAtAsync(0, 0, false);
        verify(fixture.world, never()).isChunkGenerated(anyInt(), anyInt());
        verify(fixture.world, never()).getChunkAtAsync(anyInt(), anyInt(), eq(true));

        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verify(fixture.hydrology).prepareWindowsAround(-16, -16, 15, 15, 1);
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void existingSpawnDoesNotPrefetchHydrology() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(mock(Chunk.class));
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void lateLookupDoesNotEnterClosingGenerator() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        setField(fixture.generator, "closing", true);
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void closeRejectsCompletionAlreadyWaitingForAdmission() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch acquiring = new CountDownLatch(1);
        doAnswer(invocation -> {
            acquiring.countDown();
            return invocation.callRealMethod();
        }).when(fixture.generator).acquireGenerationStage(anyString());
        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.gate.acquireExclusive();
        try {
            fixture.lookup.complete(null);
            assertTrue(acquiring.await(5L, TimeUnit.SECONDS));
            setField(fixture.generator, "closing", true);
        } finally {
            fixture.gate.releaseExclusive();
        }
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void lateLookupDoesNotEnterReplacementEngine() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.generator.setEngine(mock(Engine.class));
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void lateLookupDoesNotEnterSealedEngine() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        when(fixture.engine.isClosing()).thenReturn(true);
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void lateLookupDoesNotEnterReplacementHydrology() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        HydrologyRuntime replacement = enabledHydrology();
        when(fixture.complex.getHydrologyRuntime()).thenReturn(replacement);
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
        verifyNoInteractions(replacement);
    }

    @Test
    public void normalStudioUsesTrackedEntryPreparation() throws Exception {
        Fixture fixture = new Fixture();
        setField(fixture.generator, "studio", true);
        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(null);
        prefetch.get(5L, TimeUnit.SECONDS);

        verify(fixture.engine).startEntryHydrology(0, 0);
        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void failedLookupDoesNotStartHydrology() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        IllegalStateException failure = new IllegalStateException("Native chunk lookup failed");
        fixture.lookup.completeExceptionally(failure);

        ExecutionException result = assertThrows(ExecutionException.class,
                () -> prefetch.get(5L, TimeUnit.SECONDS));
        assertSame(failure, result.getCause());
        verifyOnlyEnablementChecked(fixture.hydrology);
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void lateLookupFailureDoesNotFailClosedGenerator() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> prefetch = fixture.prefetch();
        setField(fixture.generator, "closing", true);
        fixture.lookup.completeExceptionally(new IllegalStateException("World unloaded"));
        prefetch.get(5L, TimeUnit.SECONDS);

        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    @Test
    public void admittedPlanningFailureSurvivesConcurrentClose() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("Planner scheduling failed");
        doAnswer(invocation -> {
            setField(fixture.generator, "closing", true);
            throw failure;
        }).when(fixture.hydrology).prepareWindowsAround(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        CompletableFuture<Void> prefetch = fixture.prefetch();
        fixture.lookup.complete(null);

        ExecutionException result = assertThrows(ExecutionException.class,
                () -> prefetch.get(5L, TimeUnit.SECONDS));
        assertSame(failure, result.getCause());
        assertEquals(2, fixture.gate.availablePermits());
    }

    @Test
    public void disabledHydrologySkipsTheSpawnLookup() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.hydrology.enabled()).thenReturn(false);

        fixture.prefetch().get(5L, TimeUnit.SECONDS);

        verify(fixture.world, never()).getChunkAtAsync(anyInt(), anyInt(), eq(false));
        verifyOnlyEnablementChecked(fixture.hydrology);
    }

    private static HydrologyRuntime enabledHydrology() {
        HydrologyRuntime hydrology = mock(HydrologyRuntime.class);
        when(hydrology.enabled()).thenReturn(true);
        return hydrology;
    }

    private static void verifyOnlyEnablementChecked(HydrologyRuntime hydrology) {
        verify(hydrology, atMostOnce()).enabled();
        verifyNoMoreInteractions(hydrology);
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value)
            throws ReflectiveOperationException {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final BukkitChunkGenerator.GenerationStageGate gate =
                new BukkitChunkGenerator.GenerationStageGate(2, generator::isClosing);
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final HydrologyRuntime hydrology = enabledHydrology();
        private final World world = mock(World.class);
        private final CompletableFuture<Chunk> lookup = new CompletableFuture<>();

        private Fixture() throws ReflectiveOperationException {
            setField(generator, "loadLock", gate);
            generator.setEngine(engine);
            doReturn(false).when(generator).usesFlatStudioTerrain();
            when(engine.getComplex()).thenReturn(complex);
            when(complex.getHydrologyRuntime()).thenReturn(hydrology);
            IrisDimension dimension = mock(IrisDimension.class);
            when(engine.getDimension()).thenReturn(dimension);
            when(dimension.getStudioMode()).thenReturn(StudioMode.NORMAL);
            when(world.getMinHeight()).thenReturn(-64);
            when(world.getMaxHeight()).thenReturn(320);
            when(world.getChunkAtAsync(0, 0, false)).thenReturn(lookup);
            when(world.isChunkGenerated(anyInt(), anyInt()))
                    .thenThrow(new AssertionError("World initialization must not query chunk status synchronously"));
        }

        @SuppressWarnings("unchecked")
        private CompletableFuture<Void> prefetch() throws ReflectiveOperationException {
            Method prefetch = BukkitChunkGenerator.class.getDeclaredMethod(
                    "prefetchSpawnHydrology", Engine.class, World.class);
            prefetch.setAccessible(true);
            return (CompletableFuture<Void>) prefetch.invoke(generator, engine, world);
        }
    }
}
