package art.arcane.iris.engine.history;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.IrisEngine;
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.StructurePlacementMarker;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class GenerationSemanticCaptureTest {
    @BeforeClass
    public static void initializeMantleBlockState() throws Exception {
        PlatformBlockState air = mock(PlatformBlockState.class);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState("AIR")).thenReturn(air);
            Class.forName(EngineMantle.class.getName());
        }
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
        verify(fixture.engine, never()).getCaveBiome(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void cavePositionsRemainDistinctAcrossColumnsAndSectionBoundaries() {
        Fixture fixture = new Fixture();
        List<Integer> heights = List.of(0, 15, 16, 255, 256, 767, 4063);
        Set<String> expected = new HashSet<>();
        List<ChunkGenerationSemantics.BlockPosition> resolved = new ArrayList<>();
        when(fixture.engine.getCaveBiome(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
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

        assertEquals(Set.of(new Pair<>("buried_treasure", new BlockPos(-30, 70, 51))), engine.getPOIsAt(-2, 3));
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        @SuppressWarnings("unchecked")
        private final Mantle<Matter> mantle = mock(Mantle.class);

        private Fixture() {
            IrisComplex complex = mock(IrisComplex.class);
            EngineMantle engineMantle = mock(EngineMantle.class);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getMantle()).thenReturn(engineMantle);
            when(engineMantle.getMantle()).thenReturn(mantle);
        }
    }
}
