package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisCarveModifierSubterrainMaskTest {
    private static final int MINIMUM_Y = -64;
    private static final int ROOF = 2;

    @Test
    public void preservedSubterrainNeverReopensACellAboveTheCaveCeiling() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("room").setFamily(IrisSubterrainFamily.CENOTE)
                .setProbability(1).setLength(64).setRadius(12).setHeight(24).setPillarSpacing(0).setFormationFraction(0)
                .setWorldYRange(new IrisRange(-48, 60));
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, MINIMUM_Y, 128, (x, z) -> "region"));
        SubterrainPlan plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
        int blockX = plan.centerX() >> 4 << 4;
        int blockZ = plan.centerZ() >> 4 << 4;
        HydrologyRuntime hydrology = mock(HydrologyRuntime.class);
        when(hydrology.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        IrisCarveModifier modifier = modifier(planner, hydrology);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState(anyString())).thenReturn(mock(NativeBlockState.class));
            List<Integer> open = reopened(modifier, planner, blockX, blockZ);
            assertTrue("open subterrain cells " + open.size(), open.size() > 20);
            int threshold = open.stream().mapToInt(Integer::intValue).sorted().toArray()[open.size() / 2];
            assertTrue(open.stream().anyMatch(y -> y > threshold));

            ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
            when(raster.caveCeiling(anyInt(), anyInt())).thenReturn(threshold + ROOF);
            when(hydrology.raster(anyInt(), anyInt())).thenReturn(raster);
            List<Integer> masked = reopened(modifier, planner, blockX, blockZ);
            assertTrue(masked.size() > 0);
            assertTrue("subterrain reopened above the ceiling " + threshold, masked.stream().allMatch(y -> y <= threshold));

            when(hydrology.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.PROVISIONAL);
            assertThrows(IllegalStateException.class, () -> reopened(modifier, planner, blockX, blockZ));
        }
    }

    @Test
    public void preservedSubterrainLeavesEveryInletCellAlone() {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("room").setFamily(IrisSubterrainFamily.CENOTE)
                .setProbability(1).setLength(64).setRadius(12).setHeight(24).setPillarSpacing(0).setFormationFraction(0)
                .setWorldYRange(new IrisRange(-48, 60));
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, MINIMUM_Y, 128, (x, z) -> "region"));
        SubterrainPlan plan = planner.plansForBounds(-512, -512, 512, 512).getFirst();
        int blockX = plan.centerX() >> 4 << 4;
        int blockZ = plan.centerZ() >> 4 << 4;
        HydrologyRuntime hydrology = mock(HydrologyRuntime.class);
        when(hydrology.raster(anyInt(), anyInt())).thenReturn(ChunkWaterRaster.EMPTY);
        Map<Long, HydrologyCaveCell> inlet = new HashMap<>();
        IrisCarveModifier modifier = modifier(planner, hydrology, inlet);
        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getState(anyString())).thenReturn(mock(NativeBlockState.class));
            Set<Long> room = written(modifier, blockX, blockZ);
            int[] levels = room.stream().mapToInt(SubterrainMaskKeys::y).sorted().distinct().toArray();
            assertTrue("room levels " + levels.length, levels.length > 6);
            int water = levels[levels.length / 2];
            for (long cell : room) {
                int y = SubterrainMaskKeys.y(cell);
                if (y <= water + 2 && y >= water - 3) {
                    HydrologyCaveAction action = y > water ? HydrologyCaveAction.DRY_AIR
                            : y == water - 3 ? HydrologyCaveAction.SEAL_GUARD : HydrologyCaveAction.WET_SOURCE;
                    inlet.put(cell, new HydrologyCaveCell(action, HydrologyCaveCell.SEA_FLUID, ""));
                }
            }
            assertTrue("inlet cells crossing the room " + inlet.size(), inlet.size() > 20);
            Set<Long> crossed = written(modifier, blockX, blockZ);
            assertTrue("the room still opens away from the inlet", crossed.size() > 0);
            for (long cell : inlet.keySet()) {
                assertFalse("the room reopened inlet cell at y " + SubterrainMaskKeys.y(cell), crossed.contains(cell));
            }
        }
    }

    private static IrisCarveModifier modifier(SubterrainPlanner planner, HydrologyRuntime hydrology) {
        return modifier(planner, hydrology, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static IrisCarveModifier modifier(SubterrainPlanner planner, HydrologyRuntime hydrology, Map<Long, HydrologyCaveCell> inlet) {
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.useChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.exists(anyInt())).thenReturn(true);
        when(chunk.get(anyInt())).thenAnswer(call -> section(call.getArgument(0), inlet));
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(MINIMUM_Y).maxHeight(64).build());
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(complex.getSubterrainPlanner()).thenReturn(planner);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        return modifier;
    }

    @SuppressWarnings("unchecked")
    private static Matter section(int section, Map<Long, HydrologyCaveCell> inlet) {
        Matter matter = mock(Matter.class);
        MatterSlice<HydrologyCaveCell> slice = mock(MatterSlice.class);
        when(matter.hasSlice(HydrologyCaveCell.class)).thenReturn(true);
        when(matter.getSlice(HydrologyCaveCell.class)).thenReturn(slice);
        when(slice.get(anyInt(), anyInt(), anyInt())).thenAnswer(call -> inlet.get(SubterrainMaskKeys.key(call.getArgument(0),
                section * 16 + (int) call.getArgument(1), call.getArgument(2))));
        return matter;
    }

    @SuppressWarnings("unchecked")
    private static Set<Long> written(IrisCarveModifier modifier, int blockX, int blockZ) {
        Set<Long> written = new HashSet<>();
        Hunk<NativeBlockState> output = mock(Hunk.class);
        when(output.getHeight()).thenReturn(128);
        doAnswer(call -> {
            written.add(SubterrainMaskKeys.key(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            return null;
        }).when(output).setRaw(anyInt(), anyInt(), anyInt(), any());
        modifier.preserveSubterrain(blockX, blockZ, output);
        return written;
    }

    private static final class SubterrainMaskKeys {
        private static long key(int localX, int y, int localZ) {
            return ((long) (localX & 15) << 40) | ((long) (localZ & 15) << 32) | (y & 0xFFFFFFFFL);
        }

        private static int y(long key) {
            return (int) key;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> reopened(IrisCarveModifier modifier, SubterrainPlanner planner, int blockX, int blockZ) {
        List<Integer> open = new ArrayList<>();
        Hunk<NativeBlockState> output = mock(Hunk.class);
        when(output.getHeight()).thenReturn(128);
        doAnswer(call -> {
            int x = call.getArgument(0);
            int y = call.getArgument(1);
            int z = call.getArgument(2);
            if (!planner.sample(blockX + x, y + MINIMUM_Y, blockZ + z).solid()) {
                open.add(y);
            }
            return null;
        }).when(output).setRaw(anyInt(), anyInt(), anyInt(), any());
        modifier.preserveSubterrain(blockX, blockZ, output);
        return open;
    }
}
