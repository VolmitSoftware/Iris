package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.structure.placement.StructurePlacementMarker;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationSemanticCaptureTest {
    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        NativeBlockState air = mock(NativeBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void scopedCaptureKeepsExplicitPrecedenceAndScalarFallback() {
        Fixture fixture = new Fixture();
        IrisBiome configured = new IrisBiome();
        configured.setLoadKey("iris:configured");
        IrisBiome fallback = new IrisBiome();
        fallback.setLoadKey("iris:fallback");
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        when(data.getBiomeLoader()).thenReturn(loader);
        when(loader.load("iris:configured")).thenReturn(configured);
        IrisDimensionCarvingEntry root = new IrisDimensionCarvingEntry();
        root.setBiome("iris:configured");
        root.setWorldYRange(new IrisRange(-64, -48));
        root.setChildRecursionDepth(0);
        IrisDimension dimension = new IrisDimension();
        dimension.setCarving(new KList<>(List.of(root)));
        when(fixture.engine.getDimension()).thenReturn(dimension);
        when(fixture.engine.getData()).thenReturn(data);
        when(fixture.engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        when(fixture.engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class)))
                .thenAnswer(invocation -> (int) invocation.getArgument(1) <= 16 ? configured : fallback);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            for (int y = 0; y < 64; y++) {
                consumer.accept(1, y, 2, new MatterCavern(true, y == 3 ? "iris:explicit" : null, (byte) 0));
            }
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());
        HydrologyCaveCell duplicate = mock(HydrologyCaveCell.class);
        when(duplicate.floodedBiomeKey()).thenReturn("iris:duplicate");
        when(duplicate.fluidProfileKey()).thenReturn("iris:river");
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, HydrologyCaveCell> consumer = invocation.getArgument(3);
            consumer.accept(1, 4, 2, duplicate);
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(HydrologyCaveCell.class), any());
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        when(activation.activationId()).thenReturn(5L);
        when(stage.activation()).thenReturn(activation);
        when(stage.chunkX()).thenReturn(-2);
        when(stage.chunkZ()).thenReturn(3);

        ChunkGenerationSemantics expected = GenerationSemanticCapture.capture(fixture.engine, stage, (x, y, z) -> y != 8);
        verify(fixture.engine, times(62)).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
        clearInvocations(fixture.engine);
        ChunkGenerationSemantics actual = GenerationSemanticCapture.captureScoped(fixture.engine, stage, (x, y, z) -> y != 8);

        assertEquals(expected, actual);
        assertEquals(Set.of("iris:configured", "iris:fallback", "iris:explicit"), actual.caveBiomeKeys());
        verify(fixture.engine, times(47)).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
    }

    @Test
    public void scopedFallbackMatchesScalarDepthAndStackBiomesWithOneSamplePerColumn() {
        IrisEngine engine = mock(IrisEngine.class);
        Fixture fixture = new Fixture(engine);
        IrisComplex complex = engine.getComplex();
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(engine.hasGenerationRuntimeScope()).thenReturn(true);
        IrisDimension dimension = new IrisDimension();
        IrisData data = mock(IrisData.class);
        IrisWorld world = IrisWorld.builder().minHeight(-64).maxHeight(320).build();
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getWorld()).thenReturn(world);
        DimensionStackContext stack = mock(DimensionStackContext.class);
        when(engine.getDimensionStackContext()).thenReturn(stack);
        IrisBiome[] surfaces = new IrisBiome[256];
        IrisBiome[] caves = new IrisBiome[256];
        for (int index = 0; index < 256; index++) {
            surfaces[index] = biome("surface:" + index, 7);
            caves[index] = index % 3 == 0 ? null : biome(index % 3 == 1 ? null : "cave:" + index, 5);
        }
        when(complex.getTrueBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> surfaces[column(x, z)], Interpolated.of(value -> 0D, value -> null)));
        when(complex.getCaveBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> caves[column(x, z)], Interpolated.of(value -> 0D, value -> null)));
        AtomicInteger heightSamples = new AtomicInteger();
        when(complex.getHeightStream()).thenReturn(ProceduralStream.of((x, z) -> {
            heightSamples.incrementAndGet();
            return 40D + (column(x, z) % 8);
        }, Interpolated.DOUBLE));
        when(stack.getLayout(anyInt(), anyInt())).thenAnswer(invocation -> {
            int index = column((int) invocation.getArgument(0), (int) invocation.getArgument(1));
            DimensionStackLayout layout = mock(DimensionStackLayout.class);
            DimensionStackLayout.Layer layer = mock(DimensionStackLayout.Layer.class);
            when(layer.biome()).thenReturn(index % 2 == 0 ? biome("stack:" + index, 3) : null);
            when(layout.surfaceLayer()).thenReturn(layer);
            return layout;
        });
        Engine scalar = mock(Engine.class, CALLS_REAL_METHODS);
        doReturn(complex).when(scalar).getComplex();
        doReturn(dimension).when(scalar).getDimension();
        doReturn(stack).when(scalar).getDimensionStackContext();
        doReturn(data).when(scalar).getData();
        doReturn(world).when(scalar).getWorld();
        doReturn(hooks).when(scalar).getPlatformHooks();
        Set<String> expected = new HashSet<>();
        IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int index = (x << 4) | z;
                int surfaceY = 40 + index % 8;
                int y = surfaceY - index % 10;
                expected.add(scalar.getCaveBiome(x - 32, y, z + 48, state).getLoadKey());
                expected.add(scalar.getCaveBiome(x - 32, y + 1, z + 48, state).getLoadKey());
            }
        }
        heightSamples.set(0);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int index = (x << 4) | z;
                    int y = 40 + index % 8 - index % 10;
                    consumer.accept(x, y, z, new MatterCavern(true, null, (byte) 0));
                    consumer.accept(x, y + 1, z, new MatterCavern(true, null, (byte) 0));
                }
            }
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());

        ChunkGenerationSemantics captured = GenerationSemanticCapture.captureScoped(engine, stage(), (x, y, z) -> true);

        assertEquals(expected, captured.caveBiomeKeys());
        assertEquals(256, heightSamples.get());
        verify(engine, never()).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
    }

    @Test
    public void scopedFallbackRetainsCapturedRuntimeWhenAmbientInputsChange() {
        IrisEngine engine = mock(IrisEngine.class);
        Fixture fixture = new Fixture(engine);
        IrisComplex capturedComplex = engine.getComplex();
        EnginePlatformHooks hooks = mock(EnginePlatformHooks.class);
        when(engine.getPlatformHooks()).thenReturn(hooks);
        when(engine.hasGenerationRuntimeScope()).thenReturn(true);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        when(capturedComplex.getTrueBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> biome("captured:surface", 0), Interpolated.of(value -> 0D, value -> null)));
        when(capturedComplex.getCaveBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> biome("captured:cave", 5), Interpolated.of(value -> 0D, value -> null)));
        when(capturedComplex.getHeightStream()).thenReturn(ProceduralStream.of((x, z) -> 40D, Interpolated.DOUBLE));
        IrisComplex changedComplex = mock(IrisComplex.class);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            consumer.accept(0, 40, 0, new MatterCavern(true, null, (byte) 0));
            when(engine.getComplex()).thenReturn(changedComplex);
            when(engine.getDimension()).thenReturn(new IrisDimension());
            when(engine.getData()).thenReturn(mock(IrisData.class));
            consumer.accept(0, 35, 0, new MatterCavern(true, null, (byte) 0));
            consumer.accept(15, 20, 15, new MatterCavern(true, null, (byte) 0));
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());

        ChunkGenerationSemantics captured = GenerationSemanticCapture.captureScoped(engine, stage(), (x, y, z) -> true);

        assertEquals(Set.of("captured:surface", "captured:cave"), captured.caveBiomeKeys());
        verify(changedComplex, never()).getCaveBiomeStream();
        verify(changedComplex, never()).getHeightStream();
        verify(engine, never()).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
    }

    private static IrisBiome biome(String key, int minimumDepth) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        biome.setCaveMinDepthBelowSurface(minimumDepth);
        return biome;
    }

    private static int column(double x, double z) {
        return (((int) x & 15) << 4) | ((int) z & 15);
    }

    private static GenerationHistory.GenerationStage stage() {
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        when(activation.activationId()).thenReturn(5L);
        when(stage.activation()).thenReturn(activation);
        when(stage.chunkX()).thenReturn(-2);
        when(stage.chunkZ()).thenReturn(3);
        return stage;
    }

    @Test
    public void unscopedCapturePreservesCustomThreeArgumentCaveBiomeDispatch() {
        Fixture fixture = new Fixture();
        IrisBiome custom = biome("custom:cave", 0);
        doReturn(custom).when(fixture.engine).getCaveBiome(-31, 20, 50);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            consumer.accept(1, 20, 2, new MatterCavern(true, null, (byte) 0));
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());
        clearInvocations(fixture.engine);

        ChunkGenerationSemantics captured = GenerationSemanticCapture.capture(fixture.engine, -2, 3, 5L);

        assertEquals(Set.of("custom:cave"), captured.caveBiomeKeys());
        verify(fixture.engine).getCaveBiome(-31, 20, 50);
        verify(fixture.engine, never()).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
    }

    @Test
    public void captureKeepsWorldPoiCoordinatesAndInternalHeight() {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterStructurePOI> consumer = invocation.getArgument(3);
            consumer.accept(2, 70, 3, MatterStructurePOI.BURIED_TREASURE);
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterStructurePOI.class), any());
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, String> consumer = invocation.getArgument(3);
            consumer.accept(2, 70, 3, StructurePlacementMarker.encodeStructure("iris:old_chest", 3, "iris:ruin"));
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(String.class), any());

        ChunkGenerationSemantics captured = GenerationSemanticCapture.capture(fixture.engine, -2, 3, 5L);

        assertEquals(Set.of(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                new ChunkGenerationSemantics.BlockPosition(-30, 70, 51))), captured.pointsOfInterest());
        assertEquals(Set.of("iris:old_chest"), captured.objectKeys());
        assertTrue(captured.sealed());
    }

    @Test
    public void caveSpaceRejectsCavesAndHydrologyFactsWhereReconciledTerrainIsSolid() {
        Fixture fixture = new Fixture();
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            consumer.accept(1, 10, 2, new MatterCavern(true, "iris:open", (byte) 0));
            consumer.accept(3, 11, 4, new MatterCavern(true, "iris:filled", (byte) 0));
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());
        HydrologyCaveCell filled = mock(HydrologyCaveCell.class);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, HydrologyCaveCell> consumer = invocation.getArgument(3);
            consumer.accept(5, 12, 6, filled);
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(HydrologyCaveCell.class), any());
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        when(activation.activationId()).thenReturn(5L);
        when(stage.activation()).thenReturn(activation);
        when(stage.chunkX()).thenReturn(-2);
        when(stage.chunkZ()).thenReturn(3);
        List<ChunkGenerationSemantics.BlockPosition> checked = new ArrayList<>();

        ChunkGenerationSemantics captured = GenerationSemanticCapture.capture(fixture.engine, stage, (x, y, z) -> {
            checked.add(new ChunkGenerationSemantics.BlockPosition(x, y, z));
            return x == 1 && y == 10 && z == 2;
        });

        assertEquals(Set.of("iris:open"), captured.caveBiomeKeys());
        assertTrue(captured.riverProfileKeys().isEmpty());
        assertEquals(List.of(new ChunkGenerationSemantics.BlockPosition(1, 10, 2),
                new ChunkGenerationSemantics.BlockPosition(3, 11, 4),
                new ChunkGenerationSemantics.BlockPosition(5, 12, 6)), checked);
        verify(filled, never()).fluidProfileKey();
        verify(fixture.engine, never()).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
    }

    @Test
    public void cavePositionsRemainDistinctAcrossColumnsAndSectionBoundaries() {
        Fixture fixture = new Fixture();
        List<Integer> heights = List.of(0, 15, 16, 255, 256, 767, 4063);
        Set<String> expected = new HashSet<>();
        List<ChunkGenerationSemantics.BlockPosition> resolved = new ArrayList<>();
        when(fixture.engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class))).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int y = invocation.getArgument(1);
            int z = invocation.getArgument(2);
            resolved.add(new ChunkGenerationSemantics.BlockPosition(x, y, z));
            IrisBiome biome = new IrisBiome();
            biome.setLoadKey(x + ":" + y + ":" + z);
            return biome;
        });
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterCavern> consumer = invocation.getArgument(3);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y : heights) {
                        consumer.accept(x, y, z, new MatterCavern(true, null, (byte) 0));
                        expected.add((x - 32) + ":" + y + ":" + (z + 48));
                    }
                }
            }
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(MatterCavern.class), any());
        HydrologyCaveCell duplicate = mock(HydrologyCaveCell.class);
        when(duplicate.floodedBiomeKey()).thenReturn("iris:duplicate");
        when(duplicate.fluidProfileKey()).thenReturn("iris:river");
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, HydrologyCaveCell> consumer = invocation.getArgument(3);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y : heights) {
                        consumer.accept(x, y, z, duplicate);
                    }
                }
            }
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(HydrologyCaveCell.class), any());

        ChunkGenerationSemantics captured = GenerationSemanticCapture.capture(fixture.engine, -2, 3, 5L);

        assertEquals(expected, captured.caveBiomeKeys());
        assertEquals(expected.size(), resolved.size());
        assertEquals(Set.of("iris:river"), captured.riverProfileKeys());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void unsealedPoiQueryUsesMantleWithWorldHorizontalCoordinates() {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        when(router.history()).thenReturn(history);
        when(history.semantics(-2, 3)).thenReturn(Optional.of(ChunkGenerationSemantics.builder(-2, 3, 5L)
                .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("incomplete",
                        new ChunkGenerationSemantics.BlockPosition(-31, 0, 49))).build()));
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, MatterStructurePOI> consumer = invocation.getArgument(3);
            consumer.accept(2, 70, 3, MatterStructurePOI.BURIED_TREASURE);
            return null;
        }).when(mantle).iterateChunk(eq(-2), eq(3), eq(MatterStructurePOI.class), any());
        doCallRealMethod().when(engine).getPOIsAt(anyInt(), anyInt());

        assertEquals(Set.of(new ChunkGenerationSemantics.PointOfInterest("buried_treasure", new ChunkGenerationSemantics.BlockPosition(-30, 70, 51))), engine.getPOIsAt(-2, 3));
    }

    private static final class Fixture {
        private final Engine engine;
        @SuppressWarnings("unchecked")
        private final Mantle<Matter> mantle = mock(Mantle.class);

        private Fixture() {
            this(mock(Engine.class));
        }

        private Fixture(Engine engine) {
            this.engine = engine;
            IrisDimensionCarvingResolver.State state = new IrisDimensionCarvingResolver.State();
            when(engine.getCaveBiome(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> engine.getCaveBiome(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), state));
            IrisComplex complex = mock(IrisComplex.class);
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getMantle()).thenReturn(mantle);
        }
    }
}
