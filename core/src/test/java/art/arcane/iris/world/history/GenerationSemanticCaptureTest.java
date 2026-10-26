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
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.WaterScene;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
    private static final int WATER_SEA_LEVEL = 40;

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
        when(duplicate.isWet()).thenReturn(true);
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
    public void everyChunkHoldingCavePoolWaterRecordsEachPoolItHoldsEvenInTheSeamRing() {
        Fixture fixture = new Fixture();
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getMinHeight()).thenReturn(-64);
        when(fixture.engine.getDimension()).thenReturn(dimension);
        HydrologyCaveCell water = new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "water", "");
        HydrologyCaveCell lava = new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "lava", "");
        HydrologyCaveCell guard = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        HydrologyCaveCell inlet = new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "water", "");
        TransitionDisplacementField seam = mock(TransitionDisplacementField.class);
        when(seam.seamWeight(anyInt(), anyInt())).thenReturn(0.5D);
        when(fixture.complex.getTransitionDisplacement()).thenReturn(seam);
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, HydrologyCaveCell> consumer = invocation.getArgument(3);
            for (int x = 2; x <= 4; x++) {
                for (int y = 40; y <= 42; y++) {
                    consumer.accept(x, y, 6, water);
                }
            }
            consumer.accept(1, 40, 6, guard);
            consumer.accept(10, 20, 12, lava);
            consumer.accept(11, 20, 12, lava);
            consumer.accept(14, 30, 1, inlet);
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(HydrologyCaveCell.class), any());

        ChunkGenerationSemantics captured = GenerationSemanticCapture.capture(fixture.engine, -2, 3, 5L);

        Set<String> pools = new HashSet<>();
        for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : captured.riverFeatures()) {
            assertEquals(HydrologyFeatureType.CAVE_POOL, occurrence.type());
            pools.add(occurrence.profileKey() + "@" + occurrence.position().x() + "," + occurrence.position().y() + "," + occurrence.position().z());
        }
        assertEquals("pools in the seam ring are recorded and other cave water is not a pool",
                Set.of("water@-30,-22,54", "lava@-22,-44,60"), pools);

        when(seam.seamWeight(anyInt(), anyInt())).thenReturn(1D);
        assertEquals("wall guards name no fluid", Set.of("water", "lava"),
                GenerationSemanticCapture.capture(fixture.engine, -2, 3, 5L).riverProfileKeys());
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
        when(duplicate.isWet()).thenReturn(true);
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

    @Test
    public void recordsEachWaterFeatureTypeOncePerChunkFromTheEffectiveWater() {
        HydrologySettings settings = FakeHydrology.settings(WATER_SEA_LEVEL);
        Reach brook = WaterScene.line(11L, settings.tiers().getFirst(), 2, 40, 13, 40, (x, z) -> 62);
        Reach trunk = WaterScene.line(12L, settings.majorTier(), 98, 104, 109, 104, (x, z) -> 62);
        Pond dammed = standing(WaterScene.pond(21L, 63, 8, 8, 5), PondKind.NATURAL, 2);
        Pond lake = standing(WaterScene.pond(31L, 63, 72, 8, 5), PondKind.LAKE, 0);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 60, WATER_SEA_LEVEL, List.of(brook, trunk),
                List.of(dammed), List.of(lake))) {
            Fixture fixture = new Fixture();
            fixture.water(hydrology.runtime);

            assertEquals(Map.of(HydrologyFeatureType.POND, 21L, HydrologyFeatureType.DAM, 21L), features(fixture, 0, 0));
            assertEquals(Map.of(HydrologyFeatureType.LAKE, 31L), features(fixture, 4, 0));
            assertEquals(Map.of(HydrologyFeatureType.STREAM, 11L), features(fixture, 0, 2));
            assertEquals(Map.of(HydrologyFeatureType.MAJOR_RIVER, 12L), features(fixture, 6, 6));
            assertEquals(Map.of(), features(fixture, 5, 5));
            ChunkGenerationSemantics pond = GenerationSemanticCapture.capture(fixture.engine, 0, 0, 5L);
            for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence : pond.riverFeatures()) {
                assertEquals("water", occurrence.profileKey());
                assertEquals(62 - 64, occurrence.position().y());
                assertEquals(WaterRole.POND, hydrology.runtime.raster(0, 0).role(occurrence.position().x() & 15,
                        occurrence.position().z() & 15));
            }
        }
    }

    @Test
    public void recordsSeaInletsInEveryChunkTheyPassUnderWithTheirOwnProfile() {
        int stations = 8;
        float[] xs = new float[stations];
        float[] zs = new float[stations];
        float[] halfWidth = new float[stations];
        int[] floor = new int[stations];
        int[] ceiling = new int[stations];
        for (int station = 0; station < stations; station++) {
            xs[station] = 4f + station * 8f;
            zs[station] = 24f;
            halfWidth[station] = 3f;
            floor[station] = WATER_SEA_LEVEL - 3;
            ceiling[station] = WATER_SEA_LEVEL + 5;
        }
        SeaInlet inlet = new SeaInlet(41L, xs, zs, halfWidth, WATER_SEA_LEVEL, floor, ceiling, 60, 24, 4);
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 90, WATER_SEA_LEVEL, List.of(), List.of(), List.of(),
                List.of(inlet))) {
            Fixture fixture = new Fixture();
            fixture.water(hydrology.runtime);

            for (int chunkX = 0; chunkX <= 3; chunkX++) {
                List<ChunkGenerationSemantics.RiverFeatureOccurrence> occurrences = new ArrayList<>(
                        GenerationSemanticCapture.capture(fixture.engine, chunkX, 1, 5L).riverFeatures());
                assertEquals("chunk " + chunkX, 1, occurrences.size());
                ChunkGenerationSemantics.RiverFeatureOccurrence occurrence = occurrences.getFirst();
                assertEquals(HydrologyFeatureType.SEA_INLET, occurrence.type());
                assertEquals(41L, occurrence.featureId());
                assertEquals(HydrologyFeatureType.SEA_INLET.key(), occurrence.profileKey());
                assertEquals(WATER_SEA_LEVEL - 64, occurrence.position().y());
                assertEquals(chunkX, occurrence.position().x() >> 4);
            }
            assertEquals(Map.of(), features(fixture, 5, 1));
            assertEquals(Map.of(), features(fixture, 1, 3));
        }
    }

    @Test
    public void seaInletCellsNeverRecordTheSeaKeyAsARiverProfile() {
        Fixture fixture = new Fixture();
        when(fixture.engine.getDimension()).thenReturn(new IrisDimension());
        when(fixture.engine.getData()).thenReturn(mock(IrisData.class));
        when(fixture.engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        doAnswer(invocation -> {
            Consumer4<Integer, Integer, Integer, HydrologyCaveCell> consumer = invocation.getArgument(3);
            consumer.accept(1, 4, 2, new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, HydrologyCaveCell.SEA_FLUID, ""));
            consumer.accept(1, 5, 2, new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "iris:river", ""));
            return null;
        }).when(fixture.mantle).iterateChunk(eq(-2), eq(3), eq(HydrologyCaveCell.class), any());
        GenerationHistory.GenerationStage stage = mock(GenerationHistory.GenerationStage.class);
        GenerationActivation activation = mock(GenerationActivation.class);
        when(activation.activationId()).thenReturn(5L);
        when(stage.activation()).thenReturn(activation);
        when(stage.chunkX()).thenReturn(-2);
        when(stage.chunkZ()).thenReturn(3);

        assertEquals(Set.of("iris:river"), GenerationSemanticCapture.capture(fixture.engine, stage, (x, y, z) -> true).riverProfileKeys());
    }

    @Test
    public void waterCaptureFailsClosedOnAProvisionalRaster() {
        try (FakeHydrology hydrology = new FakeHydrology(() -> true, (x, z) -> 60, WATER_SEA_LEVEL, 63)) {
            hydrology.hold();
            Fixture fixture = new Fixture();
            fixture.water(hydrology.runtime);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> GenerationSemanticCapture.capture(fixture.engine, 0, 0, 5L));

            assertTrue(failure.getMessage().contains("water raster is not planned"));
        }
    }

    private static Map<HydrologyFeatureType, Long> features(Fixture fixture, int chunkX, int chunkZ) {
        Map<HydrologyFeatureType, Long> features = new TreeMap<>();
        for (ChunkGenerationSemantics.RiverFeatureOccurrence occurrence
                : GenerationSemanticCapture.capture(fixture.engine, chunkX, chunkZ, 5L).riverFeatures()) {
            assertEquals(null, features.put(occurrence.type(), occurrence.featureId()));
        }
        return features;
    }

    private static Pond standing(Pond pond, PondKind kind, int damRaise) {
        return new Pond(pond.id(), kind, pond.level(), pond.floor(), pond.spillX(), pond.spillZ(), pond.spillHeight(), pond.mask(),
                pond.outletReachId(), damRaise, pond.fluid(), pond.floorX(), pond.floorZ());
    }

    private static final class Fixture {
        private final Engine engine;
        private final IrisComplex complex = mock(IrisComplex.class);
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
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getMantle()).thenReturn(mantle);
        }

        private void water(HydrologyRuntime runtime) {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.getMinHeight()).thenReturn(-64);
            when(engine.getDimension()).thenReturn(dimension);
            when(complex.getHydrologyRuntime()).thenReturn(runtime);
            when(complex.getWaterRoleStream()).thenReturn(ProceduralStream.of((x, z) -> {
                ChunkWaterRaster raster = runtime.raster((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                return raster.role((int) Math.floor(x) & 15, (int) Math.floor(z) & 15);
            }, Interpolated.of(role -> 0D, value -> WaterRole.NONE)));
            when(complex.getWaterHeadStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> {
                ChunkWaterRaster raster = runtime.raster((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                return raster.waterHead((int) Math.floor(x) & 15, (int) Math.floor(z) & 15);
            }));
        }
    }
}
