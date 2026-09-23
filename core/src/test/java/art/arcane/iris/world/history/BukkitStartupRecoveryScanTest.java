package art.arcane.iris.world.history;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.generator.BlockPopulator;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class BukkitStartupRecoveryScanTest extends GenerationHistoryRuntimeRouterSupport {
    @Test
    public void unchangedBukkitStartupScansSavedChunksOnce() throws Exception {
        Path world = temporaryFolder.newFolder("startup-recovery-world").toPath();
        GenerationHistory history = createHistory(world, createPack("startup-recovery-pack", "alpha"));
        Path region = Files.createDirectories(world.resolve("region")).resolve("r.0.0.mca");
        writeRegion(region, new int[][]{{0, 0}, {1, 0}});
        byte[] originalRegion = Files.readAllBytes(region);
        GenerationEpoch epoch = history.activeEpoch();
        EngineTarget target = mock(EngineTarget.class);
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisWorld irisWorld = mock(IrisWorld.class);
        when(target.getData()).thenReturn(data);
        when(target.getDimension()).thenReturn(dimension);
        when(target.getWorld()).thenReturn(irisWorld);
        when(data.getDataFolder()).thenReturn(history.activePackRoot().toFile());
        when(dimension.getLoadKey()).thenReturn(epoch.dimensionContract().dimensionKey());
        when(irisWorld.getRawWorldSeed()).thenReturn(epoch.worldSeed());
        IrisEngine.GenerationRuntimeBinding binding = new FakeRuntimeFactory().binding(history, history.activeActivation());
        when(binding.target()).thenReturn(target);
        BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        AtomicCache<EngineTarget> targets = new AtomicCache<>();
        targets.aquireOrThrow(() -> target);
        setField(generator, "generationHistory", history);
        setField(generator, "targetCache", targets);
        setField(generator, "studioEntryBootstrapActive", new AtomicBoolean());
        setField(generator, "populators", new KList<BlockPopulator>());
        IrisSettings settings = new IrisSettings();

        try (MockedStatic<IrisSettings> configuration = mockStatic(IrisSettings.class);
             MockedStatic<GenerationEpochContractFactory> contracts = mockStatic(GenerationEpochContractFactory.class);
             MockedStatic<WorldChunkInventory> inventory = mockStatic(WorldChunkInventory.class, CALLS_REAL_METHODS);
             MockedConstruction<IrisEngine> engines = mockConstruction(IrisEngine.class, (engine, context) -> {
                 when(engine.getActiveGenerationRuntimeBinding()).thenReturn(binding);
             })) {
            configuration.when(IrisSettings::get).thenReturn(settings);
            contracts.when(() -> GenerationEpochContractFactory.createForEpoch(
                    dimension, epoch.dimensionContract().dimensionTypeKey(), epoch)).thenReturn(epoch.dimensionContract());
            Method setup = BukkitChunkGenerator.class.getDeclaredMethod("setupEngine");
            setup.setAccessible(true);
            setup.invoke(generator);

            assertEquals(1, engines.constructed().size());
            IrisEngine engine = engines.constructed().getFirst();
            ArgumentCaptor<GenerationHistoryRuntimeRouter> attached = ArgumentCaptor.forClass(GenerationHistoryRuntimeRouter.class);
            verify(engine).attachGenerationHistoryRuntimeRouter(attached.capture());
            try (GenerationHistoryRuntimeRouter router = attached.getValue()) {
                assertSame(history, router.history());
                assertSame(engine, generator.getEngine());
                assertEquals(1L, history.activeActivation().activationId());
                inventory.verify(() -> WorldChunkInventory.scan(world), times(1));
                assertArrayEquals(originalRegion, Files.readAllBytes(region));
            }
        }
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }
}
