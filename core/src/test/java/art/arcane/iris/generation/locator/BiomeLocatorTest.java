package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.BiomeEnvironment;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.SavedBiomeRuntime;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import java.util.function.Predicate;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;

public class BiomeLocatorTest {
    @Test
    public void unownedActivePredictionDoesNotQueueSavedBiomeReads() {
        Fixture fixture = new Fixture();
        IrisEngine engine = (IrisEngine) fixture.engine;
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(router.biomes()).thenReturn(saved);
        when(history.isActiveUnowned(0, 0)).thenReturn(true);
        when(history.semantics(0, 0)).thenReturn(Optional.empty());
        when(engine.getHeight()).thenReturn(384);
        when(engine.getCaveBiome(8, 44, 8)).thenReturn(biome("deep"));
        assertEquals(new SubterrainPosition(8, -20, 8), fixture.locator.position(engine, new Position2(0, 0)));
        verify(saved, never()).readChunkAsync(0, 0);
    }

    @Test
    public void recordedPositionReadsSavedVerticalIdentityWithoutActiveDefinition() {
        Fixture fixture = new Fixture();
        IrisEngine retained = (IrisEngine) fixture.engine;
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        BiomeEnvironment environment = mock(BiomeEnvironment.class);
        when(retained.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(mock(GenerationHistory.class));
        when(router.biomes()).thenReturn(saved);
        when(environment.biome()).thenReturn(biome("deep"));
        when(saved.resolve(8, -20, 8, false)).thenReturn(Optional.of(environment));
        SavedBiomeRuntime.ReadSession read = mock(SavedBiomeRuntime.ReadSession.class);
        when(saved.readChunkAsync(0, 0)).thenReturn(CompletableFuture.completedFuture(read));
        when(read.resolve(8, -20, 8, false)).thenReturn(Optional.of(environment));
        when(retained.getHeight()).thenReturn(384);
        assertEquals(new SubterrainPosition(8, -20, 8), fixture.locator.position(retained, new Position2(0, 0)));
        verify(retained, never()).getCaveBiome(8, 44, 8);
    }

    @Test
    public void recordedMetadataWithoutVolumeMatchDoesNotBoundSearch() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        Position2 near = new Position2(1, 0);
        Position2 far = new Position2(4, 0);
        BiomeLocator locator = spy(new BiomeLocator("deep", false));
        doReturn(null).when(locator).position(engine, near);
        doReturn(new SubterrainPosition(65, -20, 3)).when(locator).position(engine, far);
        ChunkGenerationSemantics nearRecord = mock(ChunkGenerationSemantics.class);
        when(nearRecord.chunkX()).thenReturn(1);
        GenerationSemanticIndex.Match nearMatch = new GenerationSemanticIndex.Match(
                GenerationSemanticIndex.SemanticKind.CAVE_BIOME, "deep",
                new GenerationSemanticIndex.ChunkReference(1, 0, 1), Optional.empty());
        GenerationSemanticIndex.Match farMatch = new GenerationSemanticIndex.Match(
                GenerationSemanticIndex.SemanticKind.CAVE_BIOME, "deep",
                new GenerationSemanticIndex.ChunkReference(4, 0, 1), Optional.empty());
        when(history.findRecorded(any(), any())).thenAnswer(invocation -> {
            Predicate<ChunkGenerationSemantics> eligible = invocation.getArgument(1);
            return Optional.of(eligible.test(nearRecord) ? nearMatch : farMatch);
        });
        Locator.SearchCandidate candidate = locator.nearestRecordedCandidate(engine, new Position2(0, 0), 20);
        assertEquals(far, candidate.chunk());
        assertEquals(65, candidate.blockX());
        verify(locator).position(engine, near);
        verify(locator).position(engine, far);
    }

    @Test
    public void mantleCandidatesRequireDryCavernAndCanonicalVolumeIdentity() {
        Fixture fixture = new Fixture();
        when(fixture.engine.getCaveOrMantleBiome(8, 44, 8)).thenReturn(biome("deep"));
        when(fixture.engine.getBiomeOrMantle(8, 44, 8)).thenReturn(biome("surface"));
        assertNull(fixture.locator.cavernPosition(fixture.engine, 8, 44, 8, new MatterCavern(true, "", (byte) 0)));
        when(fixture.engine.getBiomeOrMantle(8, 44, 8)).thenReturn(biome("deep"));
        assertNull(fixture.locator.cavernPosition(fixture.engine, 8, 44, 8, new MatterCavern(false, "", (byte) 0)));
        assertNull(fixture.locator.cavernPosition(fixture.engine, 8, 44, 8, new MatterCavern(true, "", (byte) 1)));
        assertNull(fixture.locator.cavernPosition(fixture.engine, 8, 44, 8, new MatterCavern(true, "", (byte) 2)));
        assertEquals(new SubterrainPosition(8, -20, 8), fixture.locator.cavernPosition(fixture.engine, 8, 44, 8,
                new MatterCavern(true, "", (byte) 3)));
    }

    @Test
    public void recordedCavePositionFindsBiomeAwayFromChunkCenter() {
        Fixture fixture = new Fixture();
        IrisEngine retained = (IrisEngine) fixture.engine;
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        SavedBiomeRuntime.ReadSession read = mock(SavedBiomeRuntime.ReadSession.class);
        BiomeEnvironment environment = mock(BiomeEnvironment.class);
        when(environment.biome()).thenReturn(biome("deep"));
        when(retained.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(mock(GenerationHistory.class));
        when(router.biomes()).thenReturn(saved);
        when(saved.readChunkAsync(0, 0)).thenReturn(CompletableFuture.completedFuture(read));
        when(read.resolve(0, -20, 0, false)).thenReturn(Optional.of(environment));
        when(retained.getHeight()).thenReturn(384);
        IrisDimension dimension = mock(IrisDimension.class);
        when(retained.getDimension()).thenReturn(dimension);
        when(dimension.getAllRegions(retained)).thenReturn(new KList<>(new IrisRegion().setCaveBiomes(new KList<>("deep"))));
        when(dimension.getCarving()).thenReturn(new KList<>());
        when(dimension.getSubterrainFeatures()).thenReturn(new KList<>());
        when(retained.getAllBiomes()).thenReturn(new KList<>());
        when(retained.getData()).thenReturn(mock(IrisData.class, RETURNS_DEEP_STUBS));
        when(retained.getData().getBiomeLoader().load("deep")).thenReturn(biome("deep"));
        when(retained.getCaveBiome(8, 8)).thenReturn(biome("other"));
        when(retained.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        BiomeLocator locator = (BiomeLocator) BiomeLocator.forBiome(retained, "deep", 59);
        when(retained.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        assertEquals(new SubterrainPosition(0, -20, 0), locator.position(retained, new Position2(0, 0)));
        verify(read).resolve(0, -20, 0, false);
    }

    @Test
    public void liveLandingReadsExactRetainedBiomeDespiteDifferentActiveBiome() {
        Fixture fixture = new Fixture();
        IrisEngine retained = (IrisEngine) fixture.engine;
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        SavedBiomeRuntime saved = mock(SavedBiomeRuntime.class);
        BiomeEnvironment environment = mock(BiomeEnvironment.class);
        when(retained.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(mock(GenerationHistory.class));
        when(router.biomes()).thenReturn(saved);
        when(environment.biome()).thenReturn(biome("deep"));
        SavedBiomeRuntime.ReadSession read = mock(SavedBiomeRuntime.ReadSession.class);
        when(read.resolve(8, -20, 8, false)).thenReturn(Optional.of(environment));
        when(retained.getBiomeOrMantle(8, 44, 8)).thenReturn(biome("active-surface"));
        assertEquals(new SubterrainPosition(8, -20, 8), fixture.locator.landing(retained, fixture.world,
                new SubterrainPosition(8, -15, 8), Optional.of(read)));
        verify(retained, never()).getBiomeOrMantle(8, 44, 8);
        verify(read).resolve(8, -20, 8, false);
        verify(saved, never()).resolve(8, -20, 8, false);
    }

    @Test
    public void registeredCarvingBiomeUsesCaveSearchAndSurfaceKeepsItsLocator() {
        Engine engine = catalogEngine();
        IrisBiome surface = biome("surface");
        surface.setCarvingBiome("cave");
        when(engine.getAllBiomes()).thenReturn(new KList<>(surface, biome("cave")));
        assertTrue(BiomeLocator.forBiome(engine, "cave", 80) instanceof BiomeLocator);
        assertFalse(BiomeLocator.forBiome(engine, "surface", 80) instanceof BiomeLocator);
    }

    @Test
    public void sharedSurfaceAndCaveKeyStillReturnsSurfacePosition() {
        Engine engine = catalogEngine();
        IrisRegion region = new IrisRegion().setLandBiomes(new KList<>("shared")).setCaveBiomes(new KList<>("shared"));
        when(engine.getDimension().getAllRegions(engine)).thenReturn(new KList<>(region));
        IrisBiome shared = biome("shared");
        when(engine.getData().getBiomeLoader().load("shared")).thenReturn(shared);
        when(engine.getSurfaceBiome(8, 8)).thenReturn(shared);
        when(engine.getHeight(8, 8, false)).thenReturn(100);
        BiomeLocator locator = (BiomeLocator) BiomeLocator.forBiome(engine, "shared", 80);
        assertTrue(locator.matches(engine, new Position2(0, 0)));
        assertEquals(new SubterrainPosition(8, 102, 8), locator.position(engine, new Position2(0, 0)));
    }

    private static Engine catalogEngine() {
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getCarving()).thenReturn(new KList<>());
        when(dimension.getSubterrainFeatures()).thenReturn(new KList<>());
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        return engine;
    }

    @Test
    public void depthBiomeMatchRetainsAbsoluteNegativeY() {
        Engine engine = mock(Engine.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getHeight()).thenReturn(384);
        when(engine.getHeight(8, 8)).thenReturn(100);
        IrisBiome cave = biome("deep");
        when(engine.getCaveBiome(8, 11, 8)).thenReturn(cave);
        BiomeLocator locator = new BiomeLocator("deep", false);
        assertEquals(new SubterrainPosition(8, -53, 8), locator.position(engine, new Position2(0, 0)));
        assertNull(new BiomeLocator("other", false).position(engine, new Position2(0, 0)));
    }

    @Test
    public void landingUsesActualCaveFloorAndExactBiomeInsteadOfSurface() {
        Fixture fixture = new Fixture();
        assertEquals(new SubterrainPosition(8, -20, 8), fixture.locator.landing(fixture.engine, fixture.world,
                new SubterrainPosition(8, -15, 8), Optional.empty()));
        when(fixture.engine.getBiomeOrMantle(8, 44, 8)).thenReturn(biome("surface"));
        assertNull(fixture.locator.landing(fixture.engine, fixture.world, new SubterrainPosition(8, -15, 8), Optional.empty()));
    }

    @Test
    public void fluidAndHazardousFloorsCannotBeUsedAsLanding() {
        Fixture fixture = new Fixture();
        when(fixture.floor.isWaterLogged()).thenReturn(true);
        assertNull(fixture.locator.landing(fixture.engine, fixture.world, new SubterrainPosition(8, -15, 8), Optional.empty()));
        when(fixture.floor.isWaterLogged()).thenReturn(false);
        when(fixture.floor.materialKey()).thenReturn("minecraft:magma_block");
        assertNull(fixture.locator.landing(fixture.engine, fixture.world, new SubterrainPosition(8, -15, 8), Optional.empty()));
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static final class Fixture {
        private final Engine engine = mock(IrisEngine.class);
        private final NativeWorld world = mock(NativeWorld.class);
        private final NativeBlockState floor = mock(NativeBlockState.class);
        private final BiomeLocator locator = new BiomeLocator("deep", false);

        private Fixture() {
            NativeBlockState air = mock(NativeBlockState.class);
            when(air.isAir()).thenReturn(true);
            when(floor.isSolid()).thenReturn(true);
            when(floor.materialKey()).thenReturn("minecraft:stone");
            when(world.minHeight()).thenReturn(-64);
            when(world.maxHeight()).thenReturn(320);
            when(world.getBlock(anyInt(), anyInt(), anyInt())).thenReturn(floor);
            when(world.getBlock(8, -20, 8)).thenReturn(air);
            when(world.getBlock(8, -19, 8)).thenReturn(air);
            when(engine.getMinHeight()).thenReturn(-64);
            when(engine.getHeight(anyInt(), anyInt())).thenReturn(100);
            when(engine.getBiomeOrMantle(8, 44, 8)).thenReturn(biome("deep"));
        }
    }
}
