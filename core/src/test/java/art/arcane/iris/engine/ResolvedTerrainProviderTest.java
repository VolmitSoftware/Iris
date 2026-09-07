package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EnginePlatformHooks;
import art.arcane.iris.engine.framework.EngineStage;
import art.arcane.iris.engine.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.engine.history.FloatingBiomeOverlay;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import art.arcane.iris.engine.history.TerrainBoundarySignatureStore;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.engine.history.SavedTerrainChunk;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class ResolvedTerrainProviderTest {
    @Test
    public void cachedTransitionTerrainRetainsDeferredBlocksWhenCopied() {
        Fixture fixture = new Fixture();
        String key = "itemsadder:rocks/ruby_ore";
        PlatformBlockState custom = Fixture.block(key, false);
        when(custom.isCustom()).thenReturn(true);
        when(custom.placementBaseState()).thenReturn(fixture.stone);
        when(custom.deferredPlacementKey()).thenReturn(key);
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.hasTransitionAtChunk(0, 0)).thenReturn(true);
        when(plan.newEpochWeightAt(anyInt(), anyInt())).thenReturn(1D);
        when(fixture.complex.getTransitionGenerationPlan()).thenReturn(plan);
        fixture.terrainOverride = (x, z, blocks, biomes, multicore, context) -> {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    context.setTerrainHeight(localX, localZ, 2);
                    for (int y = 0; y < 16; y++) {
                        blocks.setRaw(localX, y, localZ, y == 2 ? custom : y < 2 ? fixture.stone : fixture.air);
                        biomes.setRaw(localX, y, localZ, fixture.biome);
                    }
                }
            }
        };
        TerrainBoundarySignature captured = fixture.provider.column(0, 0);
        assertEquals(key, captured.geometry().voxelAt(2).stateKey());
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.blockOrNull(key)).thenReturn(custom);
        when(registries.blockOrNull("minecraft:stone")).thenReturn(fixture.stone);
        when(registries.blockOrNull("minecraft:air")).thenReturn(fixture.air);
        when(registries.biome("minecraft:plains")).thenReturn(fixture.biome);
        ChunkContext context = new ChunkContext(0, 0, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        AtomicInteger customWrites = new AtomicInteger();
        Hunk<PlatformBlockState> blocks = Hunk.<PlatformBlockState>newArrayHunk(16, 16, 16).listen((x, y, z, state) -> {
            if (state.isCustom()) {
                customWrites.incrementAndGet();
            }
        });
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             IrisContext.Scope ignored = IrisContext.open(fixture.engine, 5L, context)) {
            platforms.when(IrisPlatforms::get).thenReturn(platform);
            fixture.provider.generate(fixture.mode, 0, 0, blocks, Hunk.newArrayHunk(16, 16, 16), false, context);
        }

        assertEquals(256, customWrites.get());
        assertSame(custom, blocks.getRaw(0, 2, 0));
        assertEquals(key, blocks.getRaw(0, 2, 0).deferredPlacementKey());
        assertEquals(Integer.valueOf(1), fixture.computations.get("0,0"));
    }

    @Test
    public void cachedSpeculativeFloatingIdentityIsPublishedOnlyByActualGeneration() throws Exception {
        Fixture fixture = new Fixture();
        fixture.floatingIdentity = new FloatingBiomeOverlay.Identity("floating-child", "region");
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.hasTransitionAtChunk(0, 0)).thenReturn(true);
        when(plan.newEpochWeightAt(anyInt(), anyInt())).thenReturn(1D);
        when(fixture.complex.getTransitionGenerationPlan()).thenReturn(plan);
        fixture.provider.column(0, 0);
        verify(fixture.router, never()).recordFloatingBiomes(anyInt(), anyInt(), any());
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.blockOrNull("minecraft:stone")).thenReturn(fixture.stone);
        when(registries.blockOrNull("minecraft:air")).thenReturn(fixture.air);
        when(registries.biome("minecraft:plains")).thenReturn(fixture.biome);
        ChunkContext context = new ChunkContext(0, 0, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             IrisContext.Scope ignored = IrisContext.open(fixture.engine, 5L, context)) {
            platforms.when(IrisPlatforms::get).thenReturn(platform);
            fixture.provider.generate(fixture.mode, 0, 0, Hunk.newArrayHunk(16, 16, 16),
                    Hunk.newArrayHunk(16, 16, 16), false, context);
        }
        assertEquals(Integer.valueOf(1), fixture.computations.get("0,0"));
        assertEquals(fixture.floatingIdentity, context.getFloatingBiomes().volumeAt(0, 0, 0));
        assertEquals(fixture.floatingIdentity, context.getFloatingBiomes().surfaceAt(0, 0));
        verify(fixture.router).recordFloatingBiomes(0, 0, context.getFloatingBiomes());
    }

    @Test
    public void columnRequestsAreDeterministicAcrossOrderAndNegativeCoordinates() {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        int[][] columns = {{0, 0}, {-1, -17}, {31, 16}, {7, 13}};
        List<TerrainBoundarySignature> expected = new ArrayList<>();
        for (int[] column : columns) {
            expected.add(first.provider.column(column[0], column[1]));
        }
        for (int index = columns.length - 1; index >= 0; index--) {
            int[] coordinate = columns[index];
            assertColumnEquals(expected.get(index), second.provider.column(coordinate[0], coordinate[1]));
        }
        assertEquals(3, first.computations.size());
        assertEquals(3, second.computations.size());
        assertNull(IrisContext.get());
    }

    @Test
    public void evictedChunksRegenerateTheSameGeometryAndBiomes() {
        Fixture fixture = new Fixture();
        TerrainBoundarySignature original = fixture.provider.column(0, 0);
        assertSame(original, fixture.provider.column(0, 0));
        for (int chunkX = 1; chunkX <= 64; chunkX++) {
            fixture.provider.column(chunkX * 16, 0);
        }

        TerrainBoundarySignature regenerated = fixture.provider.column(0, 0);

        assertColumnEquals(original, regenerated);
        assertEquals(Integer.valueOf(2), fixture.computations.get("0,0"));
        fixture.provider.clear();
        assertColumnEquals(original, fixture.provider.column(0, 0));
        assertEquals(Integer.valueOf(3), fixture.computations.get("0,0"));
    }

    @Test
    public void scalarHeightsSurviveGeometryEvictionForEveryColumnAndClearWithProvider() {
        Fixture fixture = new Fixture();
        int expected = fixture.provider.height(-1, -17, true);
        for (int chunkX = 1; chunkX <= 64; chunkX++) {
            fixture.provider.column(chunkX * 16, 0);
        }
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = -16 + localX;
                int z = -32 + localZ;
                int height = 2 + Math.floorMod(x + z, 5);
                assertEquals(height, fixture.provider.height(x, z, true));
                assertEquals(height, fixture.provider.height(x, z, false));
            }
        }
        assertEquals(Integer.valueOf(1), fixture.computations.get("-1,-2"));
        assertEquals(expected, fixture.provider.column(-1, -17).oceanFloorHeight());
        assertEquals(Integer.valueOf(2), fixture.computations.get("-1,-2"));
        fixture.provider.clear();
        assertEquals(expected, fixture.provider.height(-1, -17, true));
        assertEquals(Integer.valueOf(3), fixture.computations.get("-1,-2"));
        verify(fixture.router, never()).recordNaturalTerrain(any());
    }

    @Test
    public void scalarHeightLookupHonorsNaturalContextAndTransitionBand() throws Exception {
        Fixture fixture = new Fixture();
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.hasTransitionAtChunk(-1, -2)).thenReturn(true);
        Field transitionField = IrisComplex.class.getDeclaredField("transitionGenerationPlan");
        transitionField.setAccessible(true);
        transitionField.set(fixture.complex, plan);
        Field providerField = IrisComplex.class.getDeclaredField("resolvedTerrain");
        providerField.setAccessible(true);
        providerField.set(fixture.complex, fixture.provider);
        doCallRealMethod().when(fixture.complex).resolvedTerrainHeight(anyInt(), anyInt(), anyBoolean());
        doCallRealMethod().when(fixture.complex).isNaturalTerrainContext();
        ChunkContext context = new ChunkContext(-16, -32, fixture.complex, false,
                ChunkContext.PrefillPlan.NONE, null);

        assertTrue(fixture.complex.resolvedTerrainHeight(0, 0, true).isEmpty());
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 5L, context)) {
            assertTrue(fixture.complex.resolvedTerrainHeight(-1, -17, true).isEmpty());
            assertTrue(fixture.computations.isEmpty());
            context.beginContent();
            assertEquals(OptionalInt.of(4), fixture.complex.resolvedTerrainHeight(-1, -17, true));
        }
        transitionField.set(fixture.complex, null);
        assertTrue(fixture.complex.resolvedTerrainHeight(-1, -17, true).isEmpty());
    }

    @Test
    public void speculativeQueriesRestoreTheCallerContextAndNeverRecordTerrain() {
        Fixture fixture = new Fixture();
        ChunkContext outer = new ChunkContext(64, 64, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        outer.beginContent();
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 73L, outer)) {
            IrisContext caller = IrisContext.require();
            fixture.provider.column(-1, -1);
            assertSame(caller, IrisContext.require());
            assertSame(outer, IrisContext.require().getChunkContext());
            assertEquals(73L, fixture.lastSessionId);
        }
        verify(fixture.router, never()).recordNaturalTerrain(any());
        assertNull(IrisContext.get());
    }

    @Test
    public void neighborRawTerrainKeepsOneRuntimeScopeThroughPrefillAndRestoresCaller() throws Exception {
        Fixture fixture = new Fixture();
        ChunkContext outer = new ChunkContext(64, 64, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        outer.beginContent();
        AtomicBoolean scoped = new AtomicBoolean();
        GenerationHistoryRuntimeRouter.CoordinateScope runtimeScope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        when(fixture.engine.openGenerationHistoryCoordinateScope(-16, -16)).thenAnswer(invocation -> {
            assertFalse(scoped.getAndSet(true));
            return runtimeScope;
        });
        ProceduralStream<Double> height = fixture.complex.getRawHeightStream();
        when(fixture.complex.getRawHeightStream()).thenAnswer(invocation -> {
            assertTrue(scoped.get());
            return height;
        });
        doAnswer(invocation -> {
            assertTrue(scoped.get());
            assertSame(outer, IrisContext.require().getChunkContext());
            scoped.set(false);
            return null;
        }).when(runtimeScope).close();
        doAnswer(invocation -> {
            assertTrue(scoped.get());
            fixture.fill(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2),
                    invocation.getArgument(3), invocation.getArgument(5));
            return null;
        }).when(fixture.mode).generateTerrain(anyInt(), anyInt(), any(), any(), anyBoolean(), any());

        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 73L, outer)) {
            TerrainBoundarySignature result = fixture.provider.column(-1, -1);
            assertSame(result, fixture.provider.column(-1, -1));
            assertSame(outer, IrisContext.require().getChunkContext());
            assertFalse(scoped.get());
        }
        verify(fixture.engine, times(1)).openGenerationHistoryCoordinateScope(-16, -16);
        verify(runtimeScope, times(1)).close();
        verify(fixture.router, never()).recordNaturalTerrain(any());
    }

    @Test
    public void failedNeighborTerrainClosesItsRuntimeScopeAndRestoresCaller() throws Exception {
        Fixture fixture = new Fixture();
        ChunkContext outer = new ChunkContext(64, 64, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        GenerationHistoryRuntimeRouter.CoordinateScope runtimeScope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        when(fixture.engine.openGenerationHistoryCoordinateScope(16, 0)).thenReturn(runtimeScope);
        IllegalStateException expected = new IllegalStateException("terrain failed");
        fixture.terrainOverride = (x, z, blocks, biomes, multicore, context) -> {
            throw expected;
        };

        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 73L, outer)) {
            assertSame(expected, assertThrows(IllegalStateException.class, () -> fixture.provider.column(16, 0)));
            assertSame(outer, IrisContext.require().getChunkContext());
        }
        verify(runtimeScope, times(1)).close();
        assertNull(IrisContext.get());
    }

    @Test
    public void actualGenerationRecordsNaturalTerrainBeforeContentMutatesBlocks() {
        Fixture fixture = new Fixture();
        AtomicReference<SavedTerrainChunk> recorded = new AtomicReference<>();
        doAnswer(invocation -> {
            assertEquals(List.of("terrain"), fixture.events);
            assertTrue(IrisContext.require().getChunkContext().isNaturalTerrain());
            recorded.set(invocation.getArgument(0));
            fixture.events.add("receipt");
            return null;
        }).when(fixture.router).recordNaturalTerrain(any());
        EngineStage content = (x, z, blocks, biomes, multicore, context) -> {
            assertFalse(context.isNaturalTerrain());
            assertEquals(List.of("terrain", "receipt"), fixture.events);
            assertNotNull(recorded.get());
            assertEquals("minecraft:stone", recorded.get().column(x, z).geometry().voxelAt(0).stateKey());
            blocks.setRaw(0, 0, 0, fixture.air);
            fixture.events.add("content");
        };
        when(fixture.mode.getStages()).thenReturn(new KList<>(content));
        doCallRealMethod().when(fixture.mode).generate(anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong());
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 16, 16);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, 16, 16);

        fixture.mode.generate(0, 0, blocks, biomes, false, 41L);

        assertEquals(List.of("terrain", "receipt", "content"), fixture.events);
        assertSame(fixture.air, blocks.getRaw(0, 0, 0));
        assertEquals("minecraft:stone", recorded.get().column(0, 0).geometry().voxelAt(0).stateKey());
        assertFalse(recorded.get().hasColumn(1, 1));
        assertNull(IrisContext.get());
    }

    @Test
    public void naturalContextGuardAppliesOnlyToItsOwnComplexUntilContentBegins() {
        Fixture fixture = new Fixture();
        IrisComplex other = mock(IrisComplex.class);
        doCallRealMethod().when(fixture.complex).isNaturalTerrainContext();
        doCallRealMethod().when(other).isNaturalTerrainContext();
        ChunkContext context = new ChunkContext(0, 0, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        assertFalse(fixture.complex.isNaturalTerrainContext());
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 5L, context)) {
            assertTrue(fixture.complex.isNaturalTerrainContext());
            assertFalse(other.isNaturalTerrainContext());
            context.beginContent();
            assertFalse(fixture.complex.isNaturalTerrainContext());
        }
        assertFalse(fixture.complex.isNaturalTerrainContext());
    }

    @Test
    public void containmentReadsHistoricalFacesFromSavedColumnsWithoutGeneratingOldChunks() throws Exception {
        Fixture fixture = fluidBoundary();
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(fixture.complex.getTransitionGenerationPlan().terrainSignatures()).thenReturn(snapshot);
        SavedTerrainChunk saved = SavedTerrainChunk.capture(0, 0, 0, 16, "minecraft:noise", new SavedTerrainChunk.VoxelSource() {
            @Override
            public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                return new BoundaryColumnGeometry.Voxel("minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
            }

            @Override
            public String biome(int x, int y, int z) {
                return "minecraft:plains";
            }
        });
        when(snapshot.signatureAt(anyInt(), anyInt())).thenAnswer(invocation ->
                Optional.of(saved.column(invocation.getArgument(0), invocation.getArgument(1))));

        assertEquals(3, fixture.provider.height(16, 8, true));
        assertEquals(3, fixture.provider.height(16, 8, false));
        assertEquals(0, fixture.provider.height(17, 8, true));
        assertEquals(3, fixture.provider.height(17, 8, false));
        TerrainBoundarySignature bank = fixture.provider.column(16, 8);

        assertEquals("minecraft:stone", bank.geometry().voxelAt(2).stateKey());
        assertEquals("minecraft:water[level=0]", fixture.provider.column(17, 8).geometry().voxelAt(2).stateKey());
        assertFalse(fixture.computations.containsKey("0,0"));
        verify(fixture.router, never()).recordNaturalTerrain(any());
    }

    @Test
    public void containmentFailsIfAHistoricalFaceIsMissing() {
        Fixture fixture = fluidBoundary();
        TerrainBoundarySignatureStore.Snapshot snapshot = mock(TerrainBoundarySignatureStore.Snapshot.class);
        when(fixture.complex.getTransitionGenerationPlan().terrainSignatures()).thenReturn(snapshot);
        when(snapshot.signatureAt(anyInt(), anyInt())).thenReturn(Optional.empty());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> fixture.provider.column(16, 8));

        assertTrue(failure.getCause() instanceof IOException);
        assertFalse(fixture.computations.containsKey("0,0"));
        verify(fixture.router, never()).recordNaturalTerrain(any());
    }

    private static Fixture fluidBoundary() {
        Fixture fixture = new Fixture();
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.hasTransitionAtChunk(anyInt(), anyInt())).thenAnswer(invocation -> (int) invocation.getArgument(0) == 1);
        when(plan.isHistoricalBlock(anyInt(), anyInt())).thenAnswer(invocation -> (int) invocation.getArgument(0) < 16);
        when(plan.newEpochWeightAt(anyInt(), anyInt())).thenReturn(0.5D);
        when(fixture.complex.getTransitionGenerationPlan()).thenReturn(plan);
        PlatformBlockState water = Fixture.block("minecraft:water[level=0]", false);
        when(water.isFluid()).thenReturn(true);
        fixture.terrainOverride = (x, z, blocks, biomes, multicore, context) -> {
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    context.setTerrainHeight(localX, localZ, 0);
                    for (int y = 0; y < 16; y++) {
                        blocks.setRaw(localX, y, localZ, y == 0 ? fixture.stone : y < 4 ? water : fixture.air);
                        biomes.setRaw(localX, y, localZ, fixture.biome);
                    }
                }
            }
        };
        return fixture;
    }

    private static void assertColumnEquals(TerrainBoundarySignature expected, TerrainBoundarySignature actual) {
        assertEquals(expected.column(), actual.column());
        assertEquals(expected.samples(), actual.samples());
        assertEquals(expected.geometry(), actual.geometry());
    }

    private static final class Fixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final EngineMode mode = mock(EngineMode.class);
        private final GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        private final PlatformBlockState air = block("minecraft:air", true);
        private final PlatformBlockState stone = block("minecraft:stone", false);
        private final PlatformBiome biome = mock(PlatformBiome.class);
        private final Map<String, Integer> computations = new HashMap<>();
        private final List<String> events = new ArrayList<>();
        private final ResolvedTerrainProvider provider;
        private long lastSessionId;
        private EngineStage terrainOverride;
        private FloatingBiomeOverlay.Identity floatingIdentity;

        @SuppressWarnings("unchecked")
        private Fixture() {
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getHeight()).thenReturn(16);
            when(engine.getMinHeight()).thenReturn(0);
            when(engine.getMode()).thenReturn(mode);
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
            when(engine.getPlatformHooks()).thenReturn(hooks);
            when(mode.getEngine()).thenReturn(engine);
            when(mode.getComplex()).thenReturn(complex);
            when(biome.key()).thenReturn("minecraft:plains");
            ProceduralStream<Double> height = mock(ProceduralStream.class);
            when(height.getDouble(anyDouble(), anyDouble())).thenReturn(3D);
            ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
            ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
            ProceduralStream<PlatformBlockState> materials = mock(ProceduralStream.class);
            when(materials.get(anyDouble(), anyDouble())).thenReturn(stone);
            when(complex.getRawHeightStream()).thenReturn(height);
            when(complex.getTrueBiomeStream()).thenReturn(biomes);
            when(complex.getCaveBiomeStream()).thenReturn(biomes);
            when(complex.getRegionStream()).thenReturn(regions);
            when(complex.getRockStream()).thenReturn(materials);
            when(complex.getFluidStream()).thenReturn(materials);
            provider = new ResolvedTerrainProvider(engine);
            when(complex.getResolvedTerrain()).thenReturn(provider);
            doAnswer(invocation -> {
                int x = invocation.getArgument(0);
                int z = invocation.getArgument(1);
                Hunk<PlatformBlockState> blocks = invocation.getArgument(2);
                Hunk<PlatformBiome> physicalBiomes = invocation.getArgument(3);
                ChunkContext context = invocation.getArgument(5);
                fill(x, z, blocks, physicalBiomes, context);
                return null;
            }).when(mode).generateTerrain(anyInt(), anyInt(), any(), any(), anyBoolean(), any());
        }

        private void fill(int x, int z, Hunk<PlatformBlockState> blocks,
                          Hunk<PlatformBiome> biomes, ChunkContext context) {
            assertTrue(context.isNaturalTerrain());
            assertSame(context, IrisContext.require().getChunkContext());
            lastSessionId = IrisContext.require().getGenerationSessionId();
            computations.merge((x >> 4) + "," + (z >> 4), 1, Integer::sum);
            events.add("terrain");
            if (terrainOverride != null) {
                terrainOverride.generate(x, z, blocks, biomes, false, context);
                return;
            }
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int surface = 2 + Math.floorMod(x + localX + z + localZ, 5);
                    context.setTerrainHeight(localX, localZ, surface);
                    for (int y = 0; y < 16; y++) {
                        blocks.setRaw(localX, y, localZ, y <= surface ? stone : air);
                        biomes.setRaw(localX, y, localZ, biome);
                    }
                }
            }
            if (floatingIdentity != null) {
                context.floatingBiomes(16).record(0, 0, 0, floatingIdentity);
                context.floatingBiomes(16).record(0, 2, 0, floatingIdentity);
            }
        }

        private static PlatformBlockState block(String key, boolean air) {
            PlatformBlockState state = mock(PlatformBlockState.class);
            when(state.key()).thenReturn(key);
            when(state.isAir()).thenReturn(air);
            return state;
        }
    }
}
