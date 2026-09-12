package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisModdedStructureParityTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void surfaceBiomeFastPathBeginsAboveTheCaveSwitch() {
        assertFalse(IrisModdedBiomeSource.isGuaranteedSurfaceBiome(-2, -256));
        assertTrue(IrisModdedBiomeSource.isGuaranteedSurfaceBiome(-1, -256));
        assertFalse(IrisModdedBiomeSource.isGuaranteedSurfaceBiome(10, 0));
        assertTrue(IrisModdedBiomeSource.isGuaranteedSurfaceBiome(11, 0));
    }

    @Test
    public void visibleCaveBiomeBeginsEightBlocksBelowTheSurface() {
        assertFalse(IrisModdedBiomeSource.isUnderground(93, 100));
        assertTrue(IrisModdedBiomeSource.isUnderground(92, 100));
        assertTrue(IrisModdedBiomeSource.isUnderground(-20, 100));
    }

    @Test
    public void monumentBiomeCubeUsesSurfaceBiomesAtShiftedSeaLevel() {
        assertTrue(IrisModdedBiomeSource.isMonumentSurfaceBiomeQuery(50, 29, -256, 306));
        assertFalse(IrisModdedBiomeSource.isMonumentSurfaceBiomeQuery(51, 29, -256, 306));
        assertFalse(IrisModdedBiomeSource.isMonumentSurfaceBiomeQuery(50, 28, -256, 306));
    }

    @Test
    public void strongholdRingSearchSamplesOneQuartColumnPerChunk() {
        assertEquals(4, IrisModdedBiomeSource.horizontalBiomeSearchQuartStep(0, 112));
        assertEquals(1, IrisModdedBiomeSource.horizontalBiomeSearchQuartStep(1, 112));
        assertEquals(1, IrisModdedBiomeSource.horizontalBiomeSearchQuartStep(0, 111));
    }

    @Test
    public void spawnHeightMatchesPaperFixedSpawnClamp() {
        assertEquals(96, ModdedDimensionMetadata.clampSpawnHeight(-64, 384));
        assertEquals(96, ModdedDimensionMetadata.clampSpawnHeight(0, 128));
        assertEquals(88, ModdedDimensionMetadata.clampSpawnHeight(80, 10));
        assertEquals(101, ModdedDimensionMetadata.clampSpawnHeight(100, 20));
    }

    @Test
    public void freshAndStudioWorldsReconcileToOriginWhileCustomSpawnsRemain() {
        assertTrue(ModdedEngineBootstrap.shouldReconcileSpawn(true, false, 120, -64));
        assertTrue(ModdedEngineBootstrap.shouldReconcileSpawn(false, true, 120, -64));
        assertTrue(ModdedEngineBootstrap.shouldReconcileSpawn(false, false, 0, 0));
        assertFalse(ModdedEngineBootstrap.shouldReconcileSpawn(false, false, 1, 0));
        assertFalse(ModdedEngineBootstrap.shouldReconcileSpawn(false, false, 0, -1));
    }

    @Test
    public void reconciledSpawnUsesOriginAndClampedSurfaceHeight() {
        assertEquals(new BlockPos(0, 73, 0), ModdedEngineBootstrap.reconciledSpawnPosition(73, -64, 384));
        assertEquals(new BlockPos(0, -63, 0), ModdedEngineBootstrap.reconciledSpawnPosition(-100, -64, 384));
        assertEquals(new BlockPos(0, 318, 0), ModdedEngineBootstrap.reconciledSpawnPosition(400, -64, 384));
    }

    @Test
    public void biomeResolutionUsesRawPlatformSeedFormula() {
        long worldSeed = 998877665544L;
        int blockX = -124;
        int blockY = 48;
        int blockZ = 712;
        long expected = worldSeed
                ^ ((long) blockX * 341873128712L)
                ^ ((long) blockY * 132897987541L)
                ^ ((long) blockZ * 42317861L);

        assertEquals(expected, IrisModdedBiomeSource.biomeResolutionSeed(worldSeed, blockX, blockY, blockZ));
    }

    @Test
    public void stackedCustomBiomesUseTheirOwningDimensionNamespace() {
        IrisDimension host = new IrisDimension();
        host.setLoadKey("Host");
        IrisDimension upper = new IrisDimension();
        upper.setLoadKey("Upper");

        assertEquals(
                ModdedWorldgenIds.biomeRef("pack", "Upper", "Aurora"),
                IrisModdedBiomeSource.customBiomeRef("pack", upper, "Aurora")
        );
        assertFalse(IrisModdedBiomeSource.customBiomeRef("pack", host, "Aurora")
                .equals(IrisModdedBiomeSource.customBiomeRef("pack", upper, "Aurora")));
    }

    @Test
    public void temporaryNaturalBiomeAnswersAreNotMemoized() {
        Engine engine = mock(Engine.class);
        when(engine.answersFromNaturalTerrain(12, -8)).thenReturn(true);
        assertFalse(IrisModdedBiomeSource.isBiomeCacheable(engine, 12, -8));
        when(engine.answersFromNaturalTerrain(12, -8)).thenReturn(false);
        assertTrue(IrisModdedBiomeSource.isBiomeCacheable(engine, 12, -8));
    }

    @Test
    public void configuredBiomeKeysContainOnlyPackDerivativesAndCustomBiomes() {
        IrisBiome ocean = new IrisBiome()
                .setDerivative("minecraft:desert")
                .setVanillaDerivative("minecraft:deep_ocean");
        IrisBiome custom = new IrisBiome()
                .setDerivative("forest")
                .setCustomDerivitives(new KList<>(new IrisBiomeCustom().setId("Aurora")));
        IrisBiome shore = new IrisBiome()
                .setVanillaDerivative("minecraft:desert")
                .setInferredType(InferredType.SHORE);
        IrisBiome unsafeSea = new IrisBiome()
                .setVanillaDerivative("minecraft:plains")
                .setInferredType(InferredType.SEA);

        Set<String> keys = ModdedDimensionMetadata.collectConfiguredBiomeKeys(
                List.of(ocean, custom, shore, unsafeSea),
                customBiome -> "iris:biomes/" + customBiome.getId().toLowerCase(Locale.ROOT));

        assertEquals(Set.of("minecraft:deep_ocean", "minecraft:forest", "minecraft:beach",
                "minecraft:the_void", "iris:biomes/aurora"), keys);
        assertFalse(keys.contains("minecraft:desert"));
        assertFalse(keys.contains("minecraft:plains"));

        Set<String> recursiveKeys = ModdedDimensionMetadata.collectConfiguredBiomeKeys(
                List.of(custom), "Layers/Sky");
        assertTrue(recursiveKeys.contains("layers:sky/aurora"));
    }

    @Test
    public void structureStateRejectsBiomesOutsideThePackContract() {
        Set<String> generated = Set.of("minecraft:deep_ocean", "minecraft:dark_forest");

        assertTrue(IrisModdedBiomeSource.isGeneratedBiomeKey("minecraft:deep_ocean", generated));
        assertTrue(IrisModdedBiomeSource.isGeneratedBiomeKey("MINECRAFT:DARK_FOREST", generated));
        assertFalse(IrisModdedBiomeSource.isGeneratedBiomeKey("minecraft:desert", generated));
        assertFalse(IrisModdedBiomeSource.isGeneratedBiomeKey(null, generated));
    }

    @Test
    public void structureBiomeContractRejectsAnEmptyConfiguredSet() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> IrisModdedBiomeSource.requireConfiguredStructureBiomeKeys(Set.of()));

        assertEquals("Iris has no configured structure biomes", error.getMessage());
    }

    @Test
    public void structureBiomeContractPreservesConfiguredKeysDuringBootstrap() {
        Set<String> configured = Set.of("minecraft:deep_ocean", "minecraft:dark_forest");

        assertSame(configured, IrisModdedBiomeSource.requireConfiguredStructureBiomeKeys(configured));
    }

    @Test
    public void liveReplacementRequiresTheSameStructureBiomeUniverse() {
        IrisModdedChunkGenerator.requireStructureBiomeUniverseCompatible(
                Set.of("minecraft:plains", "minecraft:beach"),
                Set.of("minecraft:beach", "minecraft:plains"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> IrisModdedChunkGenerator.requireStructureBiomeUniverseCompatible(
                        Set.of("minecraft:plains"), Set.of("minecraft:desert")));

        assertTrue(error.getMessage().contains("Restart the server"));
    }

    @Test
    public void configuredDimensionMetadataIsExactBeforeEngineBinding() {
        IrisDimension dimension = new IrisDimension()
                .setDimensionHeight(new IrisRange(-256, 512))
                .setFluidHeight(50);
        dimension.setLoadKey("bootstrap_contract");

        ModdedDimensionMetadata.DimensionMetadata metadata =
                ModdedDimensionMetadata.dimensionMetadata(dimension);

        assertEquals(-256, metadata.minY());
        assertEquals(512, metadata.maxY());
        assertEquals(768, metadata.depth());
        assertEquals(50, metadata.seaLevel());
    }

    @Test
    public void structureRingWorkersWaitWithoutBlockingLifecycleBinding() throws Exception {
        ModdedEngineBinding<String> binding =
                new ModdedEngineBinding<>(5L, TimeUnit.SECONDS);
        String exactEngine = "exact-engine";
        CountDownLatch workerStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> ringWorker = executor.submit(() -> {
                workerStarted.countDown();
                return binding.await("overworld:overworld");
            });

            assertTrue(workerStarted.await(1L, TimeUnit.SECONDS));
            assertFalse(ringWorker.isDone());
            binding.complete(exactEngine);

            assertSame(exactEngine, ringWorker.get(1L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void structureRingBindingPropagatesBootstrapFailure() {
        ModdedEngineBinding<String> binding =
                new ModdedEngineBinding<>(1L, TimeUnit.SECONDS);
        IllegalArgumentException failure = new IllegalArgumentException("broken pack");
        binding.fail(failure);

        try {
            binding.await("overworld:overworld");
        } catch (IllegalStateException error) {
            assertSame(failure, error.getCause());
            return;
        }
        throw new AssertionError("Expected failed engine binding to propagate");
    }

    @Test
    public void structureBiomeBootstrapAllowsOnlyPendingBindingsToUseMetadata() {
        ModdedEngineBinding<String> binding =
                new ModdedEngineBinding<>(1L, TimeUnit.SECONDS);

        binding.throwIfFailed("overworld:overworld");
    }

    @Test
    public void structureBiomeBootstrapPropagatesBindingFailure() {
        ModdedEngineBinding<String> binding =
                new ModdedEngineBinding<>(1L, TimeUnit.SECONDS);
        IllegalArgumentException failure = new IllegalArgumentException("broken pack");
        binding.fail(failure);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> binding.throwIfFailed("overworld:overworld"));

        assertSame(failure, error.getCause());
    }

    @Test
    public void initialEntitySpawnsUseThePaperCompletionMarker() {
        assertSame(MantleFlag.INITIAL_SPAWNED_MARKER, ModdedWorldManager.INITIAL_SPAWN_COMPLETION_FLAG);
    }
}
