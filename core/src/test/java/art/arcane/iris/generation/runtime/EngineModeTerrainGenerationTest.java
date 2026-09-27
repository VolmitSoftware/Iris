package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.SavedTerrainChunk;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class EngineModeTerrainGenerationTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void transitionChunksGenerateOnceAndKeepCustomStatesInTheDestination() throws Exception {
        Fixture fixture = new Fixture();
        NativeBlockState custom = Fixture.block("itemsadder:rocks/ruby_ore", false);
        when(custom.isCustom()).thenReturn(true);
        when(custom.placementBaseState()).thenReturn(fixture.stone);
        when(custom.deferredPlacementKey()).thenReturn("itemsadder:rocks/ruby_ore");
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.hasTransitionAtChunk(anyInt(), anyInt())).thenReturn(true);
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
        AtomicInteger customWrites = new AtomicInteger();
        AtomicReference<SavedTerrainChunk> receipt = new AtomicReference<>();
        doAnswer(invocation -> {
            receipt.set(invocation.getArgument(0));
            return null;
        }).when(fixture.router).recordNaturalTerrain(any());
        Hunk<NativeBlockState> blocks = Hunk.<NativeBlockState>newArrayHunk(16, 16, 16).listen((x, y, z, state) -> {
            if (state.isCustom()) {
                customWrites.incrementAndGet();
            }
        });

        fixture.mode.generate(-16, -32, blocks, Hunk.newArrayHunk(16, 16, 16), true, 41L);

        assertEquals(Map.of("-1,-2", 1), fixture.computations);
        assertEquals(256, customWrites.get());
        assertSame(custom, blocks.getRaw(0, 2, 0));
        assertEquals("itemsadder:rocks/ruby_ore", receipt.get().column(-16, -32).geometry().voxelAt(2).stateKey());
        verify(fixture.engine, never()).openGenerationHistoryCoordinateScope(anyInt(), anyInt());
        assertEquals(41L, fixture.lastSessionId);
        assertNull(IrisContext.get());
    }

    @Test
    public void actualGenerationPublishesFloatingIdentityWithItsNaturalReceipt() throws Exception {
        Fixture fixture = new Fixture();
        fixture.floatingIdentity = new FloatingBiomeOverlay.Identity("floating-child", "region");
        AtomicReference<FloatingBiomeOverlay> recorded = new AtomicReference<>();
        doAnswer(invocation -> {
            recorded.set(invocation.getArgument(2));
            return null;
        }).when(fixture.router).recordFloatingBiomes(anyInt(), anyInt(), any());

        fixture.mode.generate(0, 0, Hunk.newArrayHunk(16, 16, 16), Hunk.newArrayHunk(16, 16, 16), false, 41L);

        assertEquals(Map.of("0,0", 1), fixture.computations);
        assertEquals(fixture.floatingIdentity, recorded.get().volumeAt(0, 0, 0));
        assertEquals(fixture.floatingIdentity, recorded.get().surfaceAt(0, 0));
    }

    @Test
    public void failedTerrainLeavesTheCallerContextAndSkipsReceiptAndContent() {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("Terrain failed");
        fixture.terrainOverride = (x, z, blocks, biomes, multicore, context) -> {
            throw failure;
        };
        EngineStage content = mock(EngineStage.class);
        when(fixture.mode.getStages()).thenReturn(new KList<>(content));
        ChunkContext caller = new ChunkContext(64, 64, fixture.complex, false, ChunkContext.PrefillPlan.NONE, null);
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 73L, caller)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.mode.generate(
                    0, 0, Hunk.newArrayHunk(16, 16, 16), Hunk.newArrayHunk(16, 16, 16), false, 41L)));
            assertSame(caller, IrisContext.require().getChunkContext());
        }
        verify(fixture.router, never()).recordNaturalTerrain(any());
        verify(content, never()).generate(anyInt(), anyInt(), any(), any(), anyBoolean(), any());
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
        Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, 16, 16);
        Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, 16, 16);

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

    private static final class Fixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final EngineMode mode = mock(EngineMode.class);
        private final GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        private final NativeBlockState air = block("minecraft:air", true);
        private final NativeBlockState stone = block("minecraft:stone", false);
        private final NativeBiome biome = mock(NativeBiome.class);
        private final Map<String, Integer> computations = new HashMap<>();
        private final List<String> events = new ArrayList<>();
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
            when(hooks.shouldDisableChunkContextCache(engine)).thenReturn(true);
            when(mode.getEngine()).thenReturn(engine);
            when(mode.getComplex()).thenReturn(complex);
            when(mode.getStages()).thenReturn(new KList<>());
            doCallRealMethod().when(mode).generate(anyInt(), anyInt(), any(), any(), anyBoolean(), anyLong());
            when(biome.key()).thenReturn("minecraft:plains");
            ProceduralStream<Double> height = mock(ProceduralStream.class);
            when(height.getDouble(anyDouble(), anyDouble())).thenReturn(3D);
            ProceduralStream<IrisBiome> biomes = mock(ProceduralStream.class);
            ProceduralStream<IrisRegion> regions = mock(ProceduralStream.class);
            ProceduralStream<NativeBlockState> materials = mock(ProceduralStream.class);
            when(materials.get(anyDouble(), anyDouble())).thenReturn(stone);
            when(complex.getRawHeightStream()).thenReturn(height);
            when(complex.getTrueBiomeStream()).thenReturn(biomes);
            when(complex.getCaveBiomeStream()).thenReturn(biomes);
            when(complex.getRegionStream()).thenReturn(regions);
            when(complex.getRockStream()).thenReturn(materials);
            when(complex.getFluidStream()).thenReturn(materials);
            doAnswer(invocation -> {
                int x = invocation.getArgument(0);
                int z = invocation.getArgument(1);
                Hunk<NativeBlockState> blocks = invocation.getArgument(2);
                Hunk<NativeBiome> physicalBiomes = invocation.getArgument(3);
                ChunkContext context = invocation.getArgument(5);
                fill(x, z, blocks, physicalBiomes, context);
                return null;
            }).when(mode).generateTerrain(anyInt(), anyInt(), any(), any(), anyBoolean(), any());
        }

        private void fill(int x, int z, Hunk<NativeBlockState> blocks,
                          Hunk<NativeBiome> biomes, ChunkContext context) {
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

        private static NativeBlockState block(String key, boolean air) {
            NativeBlockState state = mock(NativeBlockState.class);
            when(state.key()).thenReturn(key);
            when(state.isAir()).thenReturn(air);
            return state;
        }
    }
}
