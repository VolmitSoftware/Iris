package art.arcane.iris.platform.generation;

import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.world.history.GenerationRegistryContract;
import org.mockito.MockedConstruction;
import java.lang.reflect.Method;
import static org.mockito.Mockito.mockConstruction;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.GenerationHistory;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorHistoryTargetTest {
    @Test
    public void selectedFrozenRegistryIsBoundBeforeRuntimeConstruction() throws Exception {
        Fixture fixture = new Fixture();
        GenerationActivation activation = mock(GenerationActivation.class);
        GenerationEpoch epoch = mock(GenerationEpoch.class);
        GenerationRegistryContract registry = mock(GenerationRegistryContract.class);
        GenerationHistoryPaths paths = mock(GenerationHistoryPaths.class);
        when(fixture.history.activeActivation()).thenReturn(activation);
        when(fixture.history.activeEpoch()).thenReturn(epoch);
        when(fixture.history.paths()).thenReturn(paths);
        when(activation.isInitial()).thenReturn(true);
        when(activation.activationId()).thenReturn(1L);
        when(epoch.registryContract()).thenReturn(registry);
        when(epoch.kernelVersion()).thenReturn(new GenerationKernelRegistry.Version(1, 1, 1));
        when(paths.activationMantleRoot(1L)).thenReturn(Path.of("generation", "mantle", "1"));
        try (MockedConstruction<IrisEngine> engines = mockConstruction(IrisEngine.class,
                (engine, context) -> verify(fixture.data).bindGenerationRegistryContract(registry))) {
            Method create = BukkitChunkGenerator.class.getDeclaredMethod("createEngine", EngineTarget.class);
            create.setAccessible(true);
            Object created = create.invoke(fixture.generator, fixture.target);
            assertSame(engines.constructed().getFirst(), created);
        }
    }

    @Test
    public void unchangedVerifiedPackRetainsThePreviouslyLoadedTarget() throws Exception {
        Fixture fixture = new Fixture();

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertSame(fixture.target, fixture.generator.getTarget());
        verify(fixture.history).prepareStartup(128);
        verify(fixture.history).activePackRoot();
        verify(fixture.data, never()).close();
        verify(fixture.data, never()).dump();
        verify(fixture.data, never()).clearLists();
    }

    @Test
    public void changedActivePackInvalidatesAndClosesTheDetachedTarget() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.history.activePackRoot()).thenReturn(Path.of("generation", "packs", "next"));

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertNull(fixture.targets.getIfPresent());
        verify(fixture.data).close();
    }

    @Test
    public void closedTargetCannotBeReusedEvenWhenItsPackIsUnchanged() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.data.isClosed()).thenReturn(true);

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertNull(fixture.targets.getIfPresent());
        verify(fixture.history).activePackRoot();
        verify(fixture.data, never()).close();
    }

    @Test
    public void finalPackVerificationStillRejectsTamperingAfterPreparation() throws Exception {
        Fixture fixture = new Fixture();
        IOException failure = new IOException("Active pack fingerprint changed");
        when(fixture.history.activePackRoot()).thenThrow(failure);

        assertSame(failure, assertThrows(IOException.class,
                () -> fixture.generator.prepareGenerationHistoryTarget(128)));

        verify(fixture.history).prepareStartup(128);
        verify(fixture.preparation).close();
        verify(fixture.data, never()).close();
    }

    @Test
    public void preparationFailureCannotBeHiddenByAnExistingTarget() throws Exception {
        Fixture fixture = new Fixture();
        IOException failure = new IOException("Pending activation is invalid");
        when(fixture.history.prepareStartup(128)).thenThrow(failure);

        assertSame(failure, assertThrows(IOException.class,
                () -> fixture.generator.prepareGenerationHistoryTarget(128)));

        verify(fixture.history, never()).activePackRoot();
        verify(fixture.data, never()).close();
    }

    @Test
    public void discardedTargetDoesNotCloseDataHeldByAnotherEngine() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.history.activePackRoot()).thenReturn(Path.of("generation", "packs", "next"));
        when(fixture.data.getEngines()).thenReturn(List.of(mock(Engine.class)));

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertNull(fixture.targets.getIfPresent());
        verify(fixture.data, never()).close();
    }

    @Test
    public void discardedTargetDoesNotCloseDeferredStartupData() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.history.activePackRoot()).thenReturn(Path.of("generation", "packs", "next"));
        setField(fixture.generator, "startupTarget", fixture.target);

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertNull(fixture.targets.getIfPresent());
        assertSame(fixture.target, fixture.generator.getTarget());
        verify(fixture.data, never()).close();
    }

    @Test
    public void discardedTargetDoesNotClosePublishedEngineData() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.history.activePackRoot()).thenReturn(Path.of("generation", "packs", "next"));
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(fixture.data);
        fixture.generator.setEngine(engine);

        fixture.generator.prepareGenerationHistoryTarget(128);

        assertNull(fixture.targets.getIfPresent());
        verify(fixture.data, never()).close();
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final GenerationHistory history = mock(GenerationHistory.class);
        private final GenerationHistory.StartupPreparation preparation = mock(GenerationHistory.StartupPreparation.class);
        private final AtomicCache<EngineTarget> targets = new AtomicCache<>();
        private final EngineTarget target = mock(EngineTarget.class);
        private final IrisData data = mock(IrisData.class);

        private Fixture() throws Exception {
            Path packRoot = Path.of("generation", "packs", "current");
            when(history.prepareStartup(128)).thenReturn(preparation);
            when(history.activePackRoot()).thenReturn(packRoot);
            when(target.getData()).thenReturn(data);
            when(data.getDataFolder()).thenReturn(packRoot.toFile());
            when(data.getEngines()).thenReturn(List.of());
            targets.aquireOrThrow(() -> target);
            setField(generator, "generationHistory", history);
            setField(generator, "targetCache", targets);
        }
    }
}
