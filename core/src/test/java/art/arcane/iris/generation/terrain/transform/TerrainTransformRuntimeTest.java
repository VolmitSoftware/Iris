package art.arcane.iris.generation.terrain.transform;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class TerrainTransformRuntimeTest {
    @Test
    public void reusesTerrainAndCopiesOutputMetadata() {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger transforms = new AtomicInteger();
            TerrainTransformRuntime runtime = fixture.runtime(0, context -> {
                transforms.incrementAndGet();
                context.set(0, 2, 0, fixture.air);
                context.set(0, 5, 0, fixture.stone);
                context.set(0, 6, 0, fixture.water);
            });
            assertEquals(Integer.MIN_VALUE, runtime.readyHeight(0, 0, true));
            assertEquals(Integer.MIN_VALUE, runtime.readyFluidHeight(0, 0));
            assertNull(runtime.readyBlock(0, 0, 0));
            assertNull(runtime.readyColumn(0, 0));
            assertEquals(5, runtime.height(0, 0, true));
            assertEquals(6, runtime.height(0, 0, false));
            assertEquals(6, runtime.fluidHeight(0, 0));
            assertEquals(6, runtime.readyFluidHeight(0, 0));
            assertSame(fixture.air, runtime.block(0, 2, 0));
            assertSame(fixture.stone, runtime.readyBlock(0, 5, 0));
            assertEquals(5, runtime.column(0, 0).topY());
            assertSame(runtime.column(0, 0), runtime.readyColumn(0, 0));
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, 8, 16);
            Hunk<NativeBiome> biomes = Hunk.newArrayHunk(16, 8, 16);
            ChunkContext first = fixture.context(0);
            runtime.generate(0, 0, blocks, biomes, false, first);
            assertEquals(5, first.getRoundedHeight(0, 0));
            assertSame(fixture.stone, blocks.getRaw(0, 5, 0));
            assertSame(fixture.biome, biomes.getRaw(0, 2, 0));
            blocks.setRaw(0, 5, 0, fixture.air);
            first.getFloatingBiomes().retainHighestSurfaces((x, z) -> -1);
            ChunkContext second = fixture.context(0);
            runtime.generate(0, 0, blocks, biomes, false, second);
            assertSame(fixture.stone, blocks.getRaw(0, 5, 0));
            assertNotSame(first.getFloatingBiomes(), second.getFloatingBiomes());
            assertEquals(2, second.getFloatingBiomes().surfaceYAt(1, 1));
            assertEquals(1, fixture.generations.get());
            assertEquals(1, transforms.get());
            assertEquals(5, runtime.readyHeight(0, 0, true));
            runtime.close();
            assertThrows(IllegalStateException.class, () -> runtime.height(0, 0, true));
        }
    }

    @Test
    public void neighboringReadsAlwaysUseOriginalTerrainInEitherOrder() {
        try (Fixture fixture = new Fixture()) {
            TerrainTransformer transformer = fixture.transformer(16, context -> {
                int neighbor = context.blockX() == 0 ? 16 : 0;
                assertSame(fixture.stone, context.original(neighbor, -2, 0));
                assertSame(fixture.air, context.original(neighbor, -5, 0));
                context.set(0, 2, 0, fixture.air);
            });
            TerrainTransformRuntime forward = new TerrainTransformRuntime(new TerrainTransformRuntime.Options(fixture.engine, fixture.complex), transformer);
            TerrainTransformRuntime backward = new TerrainTransformRuntime(new TerrainTransformRuntime.Options(fixture.engine, fixture.complex), transformer);
            assertEquals(1, forward.height(0, 0, true));
            assertEquals(1, forward.height(16, 0, true));
            assertEquals(1, backward.height(16, 0, true));
            assertEquals(1, backward.height(0, 0, true));
            forward.close();
            backward.close();
        }
    }

    @Test
    public void rejectsReadsBeyondDeclaredRadiusAndWritesBeyondChunk() {
        try (Fixture fixture = new Fixture()) {
            TerrainTransformRuntime runtime = fixture.runtime(1, context -> {
                assertThrows(IllegalArgumentException.class, () -> context.original(17, -2, 0));
                assertThrows(IllegalArgumentException.class, () -> context.original(-2, -2, 0));
                assertThrows(IllegalArgumentException.class, () -> context.set(16, 0, 0, fixture.stone));
                assertThrows(IllegalArgumentException.class, () -> context.set(0, 8, 0, fixture.stone));
                assertSame(fixture.stone, context.original(-1, -2, 0));
                assertEquals(-4, context.minY());
                assertEquals(8, context.height());
            });
            assertEquals(2, runtime.height(0, 0, true));
            runtime.close();
        }
    }

    @Test
    public void preservesHistoricalColumnsAndCountsWaterloggedFluid() {
        try (Fixture fixture = new Fixture()) {
            when(fixture.complex.allowsMantleWrite(0, 0)).thenReturn(false);
            NativeBlockState wetSlab = mock(NativeBlockState.class);
            when(wetSlab.isWaterLogged()).thenReturn(true);
            TerrainTransformRuntime runtime = fixture.runtime(0, context -> {
                context.set(0, 5, 0, fixture.stone);
                context.set(1, 5, 0, wetSlab);
            });
            assertEquals(2, runtime.height(0, 0, true));
            assertEquals(5, runtime.height(1, 0, true));
            assertEquals(5, runtime.fluidHeight(1, 0));
            assertEquals(-1, runtime.fluidHeight(0, 0));
            runtime.close();
        }
    }

    @Test
    public void concurrentNeighborTransformsDoNotDependOnEachOther() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch entered = new CountDownLatch(2);
            TerrainTransformRuntime runtime = fixture.runtime(16, context -> {
                entered.countDown();
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(failure);
                }
                int neighbor = context.blockX() == 0 ? 16 : 0;
                context.set(0, 4, 0, context.original(neighbor, -2, 0));
            });
            ExecutorService workers = Executors.newFixedThreadPool(2);
            try {
                Future<Integer> first = workers.submit(() -> runtime.height(0, 0, true));
                Future<Integer> second = workers.submit(() -> runtime.height(16, 0, true));
                assertEquals(Integer.valueOf(4), first.get(10, TimeUnit.SECONDS));
                assertEquals(Integer.valueOf(4), second.get(10, TimeUnit.SECONDS));
                assertEquals(2, fixture.generations.get());
            } finally {
                workers.shutdownNow();
                runtime.close();
            }
        }
    }

    @Test
    public void sparseColumnQueriesAvoidFullTransformationAndReuseOccupancy() {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger fullCalls = new AtomicInteger();
            AtomicInteger columnCalls = new AtomicInteger();
            TerrainTransformRuntime runtime = fixture.columnRuntime(0, (context, x, z) -> {
                columnCalls.incrementAndGet();
                context.set(x, 2, z, fixture.air);
                context.set(x, 5, z, fixture.stone);
                context.set(x, 6, z, fixture.water);
                assertThrows(IllegalArgumentException.class, () -> context.set((x + 1) & 15, 0, z, fixture.stone));
                assertThrows(IllegalArgumentException.class, () -> context.set(x, 0, (z + 1) & 15, fixture.stone));
            }, context -> fullCalls.incrementAndGet());
            assertEquals(Integer.MIN_VALUE, runtime.readyHeight(3, 5, true));
            assertEquals(5, runtime.height(3, 5, true));
            assertEquals(6, runtime.height(3, 5, false));
            assertEquals(6, runtime.fluidHeight(3, 5));
            assertEquals(5, runtime.readyHeight(3, 5, true));
            assertEquals(6, runtime.readyHeight(3, 5, false));
            assertEquals(6, runtime.readyFluidHeight(3, 5));
            assertEquals(5, runtime.column(3, 5).topY());
            assertSame(runtime.column(3, 5), runtime.readyColumn(3, 5));
            assertNull(runtime.readyBlock(3, 5, 5));
            assertEquals(Integer.MIN_VALUE, runtime.readyHeight(4, 5, true));
            assertEquals(1, columnCalls.get());
            assertEquals(0, fullCalls.get());
            assertEquals(1, fixture.generations.get());
            runtime.close();
        }
    }

    @Test
    public void columnPredictionMatchesBoundaryGenerationAndFullOutputTakesPrecedence() {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger fullCalls = new AtomicInteger();
            AtomicInteger columnCalls = new AtomicInteger();
            TerrainColumnTransformer operation = (context, x, z) -> {
                NativeBlockState neighbor = context.original(context.blockX() + x + 1,
                        context.minY() + 2, context.blockZ() + z + 1);
                context.set(x, 2, z, fixture.air);
                context.set(x, 4, z, neighbor);
                context.set(x, 5, z, fixture.water);
            };
            TerrainTransformRuntime runtime = fixture.columnRuntime(1, (context, x, z) -> {
                columnCalls.incrementAndGet();
                operation.transform(context, x, z);
            }, context -> {
                fullCalls.incrementAndGet();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        operation.transform(context, x, z);
                    }
                }
            });
            assertEquals(4, runtime.height(-1, 15, true));
            assertEquals(5, runtime.height(-1, 15, false));
            Terrain3DColumn predicted = runtime.column(-1, 15);
            assertEquals(2, fixture.generations.get());
            assertEquals(0, fullCalls.get());
            Hunk<NativeBlockState> blocks = Hunk.newArrayHunk(16, 8, 16);
            runtime.generate(-16, 0, blocks, Hunk.newArrayHunk(16, 8, 16), false, fixture.context(-16));
            assertEquals(1, fullCalls.get());
            assertEquals(1, columnCalls.get());
            assertEquals(4, runtime.height(-1, 15, true));
            assertEquals(5, runtime.fluidHeight(-1, 15));
            assertEquals(predicted, runtime.column(-1, 15));
            assertNotSame(predicted, runtime.column(-1, 15));
            assertSame(runtime.column(-1, 15), runtime.readyColumn(-1, 15));
            assertSame(fixture.stone, runtime.readyBlock(-1, 4, 15));
            assertSame(fixture.water, blocks.getRaw(15, 5, 15));
            runtime.close();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final EngineMode mode = mock(EngineMode.class);
        private final NativeBlockState air = mock(NativeBlockState.class);
        private final NativeBlockState stone = mock(NativeBlockState.class);
        private final NativeBlockState water = mock(NativeBlockState.class);
        private final NativeBiome biome = mock(NativeBiome.class);
        private final AtomicInteger generations = new AtomicInteger();
        private final MockedStatic<B> states = mockStatic(B.class);

        private Fixture() {
            states.when(() -> B.getState("AIR")).thenReturn(air);
            when(air.isAir()).thenReturn(true);
            when(water.isFluid()).thenReturn(true);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getMode()).thenReturn(mode);
            when(engine.getHeight()).thenReturn(8);
            when(engine.getMinHeight()).thenReturn(-4);
            when(engine.getGenerationSessionId()).thenReturn(7L);
            when(complex.getRawHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 2));
            when(complex.getTrueBiomeStream()).thenReturn(mock(ProceduralStream.class));
            when(complex.getRockStream()).thenReturn(mock(ProceduralStream.class));
            when(complex.getFluidStream()).thenReturn(mock(ProceduralStream.class));
            when(complex.getRegionStream()).thenReturn(mock(ProceduralStream.class));
            when(complex.allowsMantleWrite(anyInt(), anyInt())).thenReturn(true);
            doAnswer(invocation -> {
                generations.incrementAndGet();
                Hunk<NativeBlockState> blocks = invocation.getArgument(2);
                Hunk<NativeBiome> biomes = invocation.getArgument(3);
                ChunkContext context = invocation.getArgument(5);
                assertTrue(context.isNaturalTerrain());
                assertSame(context, IrisContext.require().getChunkContext());
                assertEquals(7L, IrisContext.require().getGenerationSessionId());
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y <= 2; y++) {
                            blocks.setRaw(x, y, z, stone);
                            biomes.setRaw(x, y, z, biome);
                        }
                    }
                }
                context.floatingBiomes(8).record(1, 2, 1, new FloatingBiomeOverlay.Identity("island", "region"));
                return null;
            }).when(mode).generateTerrain(anyInt(), anyInt(), any(), any(), anyBoolean(), any());
        }

        private TerrainTransformRuntime runtime(int radius, Consumer<TerrainTransformContext> action) {
            return new TerrainTransformRuntime(new TerrainTransformRuntime.Options(engine, complex), transformer(radius, action));
        }

        private TerrainTransformer transformer(int radius, Consumer<TerrainTransformContext> action) {
            return new TerrainTransformer() {
                @Override
                public int radius() {
                    return radius;
                }

                @Override
                public void transform(TerrainTransformContext context) {
                    action.accept(context);
                }
            };
        }

        private TerrainTransformRuntime columnRuntime(int radius, TerrainColumnTransformer column,
                                                       Consumer<TerrainTransformContext> full) {
            TerrainTransformer transformer = new TerrainTransformer() {
                @Override
                public int radius() {
                    return radius;
                }

                @Override
                public void transform(TerrainTransformContext context) {
                    full.accept(context);
                }

                @Override
                public TerrainColumnTransformer columnTransformer() {
                    return column;
                }
            };
            return new TerrainTransformRuntime(new TerrainTransformRuntime.Options(engine, complex), transformer);
        }

        private ChunkContext context(int x) {
            return new ChunkContext(x, 0, complex, 7L, true, ChunkContext.PrefillPlan.NONE, null);
        }

        @Override
        public void close() {
            states.close();
        }
    }
}
