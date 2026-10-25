package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class MantleHydrologyComponentTest {
    private static final int WORLD_HEIGHT = 384;
    private static final int GRID = 18;
    private static final int NO_WATER = MantleHydrologyComponent.NO_WATER;
    private static final int MINIMUM_Y = -64;

    @Test
    public void componentRunsInTheTerrainPhaseWithoutWaitingForCarving() {
        ComponentFlag flag = MantleHydrologyComponent.class.getAnnotation(ComponentFlag.class);
        MantleHydrologyComponent component = new MantleHydrologyComponent(mock(EngineMantle.class));

        assertNotNull(flag);
        assertEquals(ReservedFlag.RIVER_HYDROLOGY, flag.value());
        assertEquals(1, MantleHydrologyComponent.PRIORITY);
        assertEquals(MatterGenerationPhase.TERRAIN, component.getGenerationPhase());
        assertEquals(0, component.getPrerequisiteFlags().length);
    }

    @Test
    public void hydrologyEnablesTheComponentEvenWithCarvingOff() {
        IrisDimension dimension = new IrisDimension();
        assertFalse(MantleHydrologyComponent.isEnabledFor(dimension));

        dimension.setHydrology(new IrisHydrology().setEnabled(true));
        assertTrue(MantleHydrologyComponent.isEnabledFor(dimension));

        dimension.setCarvingEnabled(false);
        assertTrue(MantleHydrologyComponent.isEnabledFor(dimension));

        dimension.getDisabledComponents().add(ReservedFlag.RIVER_HYDROLOGY);
        assertFalse(MantleHydrologyComponent.isEnabledFor(dimension));
    }

    @Test
    public void everyDropBetweenWetColumnsFallsFromTheHigherHead() {
        MantleHydrologyComponent.Falls falls = MantleHydrologyComponent.plan(0, 0, WORLD_HEIGHT,
                grid(0, 0, channel((x, z) -> x < 6 ? 99 : x < 10 ? 79 : 78)));

        assertNotNull(falls);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int index = z << 4 | x;
                boolean wet = z >= 4 && z <= 7;
                if (wet && x == 6) {
                    assertFall(falls, index, 80, 99);
                    assertEquals(5, falls.sourceX(index));
                    assertEquals(z, falls.sourceZ(index));
                } else if (wet && x == 10) {
                    assertFall(falls, index, 79, 79);
                    assertEquals(9, falls.sourceX(index));
                } else {
                    assertFalse("no fall at " + x + "," + z, falls.falling(index));
                }
            }
        }
    }

    @Test
    public void levelWaterAndDryChunksPlanNoFalls() {
        assertNull(MantleHydrologyComponent.plan(0, 0, WORLD_HEIGHT, grid(0, 0, channel((x, z) -> 79))));
        assertNull(MantleHydrologyComponent.plan(0, 0, WORLD_HEIGHT, grid(0, 0, (x, z) -> NO_WATER)));
    }

    @Test
    public void edgeColumnsTakeTheirTopFromTheHaloOfTheNeighbouringChunk() {
        IntBinaryOperator westward = channel((x, z) -> x < 16 ? 90 : 70);
        assertNull(MantleHydrologyComponent.plan(0, 0, WORLD_HEIGHT, grid(0, 0, westward)));
        MantleHydrologyComponent.Falls east = MantleHydrologyComponent.plan(1, 0, WORLD_HEIGHT, grid(1, 0, westward));
        assertNotNull(east);
        for (int z = 4; z <= 7; z++) {
            assertFall(east, z << 4, 71, 90);
            assertEquals(15, east.sourceX(z << 4));
        }

        IntBinaryOperator northward = (x, z) -> x >= 4 && x <= 7 ? z < 0 ? 64 : 63 : NO_WATER;
        assertNull(MantleHydrologyComponent.plan(0, -1, WORLD_HEIGHT, grid(0, -1, northward)));
        MantleHydrologyComponent.Falls south = MantleHydrologyComponent.plan(0, 0, WORLD_HEIGHT, grid(0, 0, northward));
        assertNotNull(south);
        for (int x = 4; x <= 7; x++) {
            assertFall(south, x, 64, 64);
            assertEquals(-1, south.sourceZ(x));
        }
    }

    @Test
    public void fallsOutsideTheWorldFailTheChunk() {
        assertThrows(IllegalStateException.class, () -> MantleHydrologyComponent.plan(0, 0, 100,
                grid(0, 0, channel((x, z) -> x < 6 ? 100 : 80))));
    }

    @Test
    public void waterAndLavaFallAtLevelEightEvenWhenTheirStateNamesNoLevel() {
        for (String key : new String[]{"minecraft:water", "minecraft:lava", "minecraft:water[level=0]"}) {
            NativeBlockState fluid = mock(NativeBlockState.class);
            NativeBlockState falling = mock(NativeBlockState.class);
            when(fluid.key()).thenReturn(key);
            when(fluid.withProperty("level", "8")).thenReturn(falling);
            assertEquals(key, falling, MantleHydrologyComponent.fallingFluidState(fluid));
        }
        NativeBlockState custom = mock(NativeBlockState.class);
        when(custom.key()).thenReturn("example:slime");
        assertEquals(custom, MantleHydrologyComponent.fallingFluidState(custom));
        verify(custom, never()).withProperty(anyString(), anyString());
        NativeBlockState alreadyFalling = mock(NativeBlockState.class);
        when(alreadyFalling.key()).thenReturn("minecraft:water[level=8]");
        assertEquals(alreadyFalling, MantleHydrologyComponent.fallingFluidState(alreadyFalling));
    }

    @Test
    public void generatedFallsAreFallingFluidFromTheHigherColumnWithUpdateFlagsAndNothingElse() {
        NativeBlockState upper = mock(NativeBlockState.class);
        NativeBlockState falling = mock(NativeBlockState.class);
        NativeBlockState lower = mock(NativeBlockState.class);
        when(upper.key()).thenReturn("minecraft:water[level=0]");
        when(upper.withProperty("level", "8")).thenReturn(falling);
        IntBinaryOperator heads = channel((x, z) -> x < 6 ? 99 : 79);
        ChunkWaterRaster raster = wetRaster();
        when(raster.fluid(anyInt(), anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) < 6 ? "upper" : "lower");
        Fixture fixture = new Fixture(raster, heads);
        when(fixture.complex.resolveWaterFluid(eq("upper"), anyInt(), anyInt())).thenReturn(upper);
        when(fixture.complex.resolveWaterFluid(eq("lower"), anyInt(), anyInt())).thenReturn(lower);

        fixture.generate();

        InOrder order = inOrder(fixture.writer);
        for (int z = 4; z <= 7; z++) {
            for (int y = 80; y <= 99; y++) {
                order.verify(fixture.writer).setData(6, y, z, falling);
                order.verify(fixture.writer).setData(6, y, z, UpdateMatter.ON);
            }
            verify(fixture.complex).resolveWaterFluid("upper", 5, z);
        }
        verify(fixture.writer, times(4 * 20)).setData(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        verify(fixture.writer, times(4 * 20)).setData(anyInt(), anyInt(), anyInt(), any(MatterUpdate.class));
        verify(fixture.writer, never()).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
        verify(fixture.complex, never()).resolveWaterFluid(eq("lower"), anyInt(), anyInt());
        for (int slot = 0; slot < 9; slot++) {
            verify(fixture.runtime).raster(slot % 3 - 1, slot / 3 - 1);
        }
    }

    @Test
    public void chunksWithoutWaterNeverReadTheirNeighbours() {
        Fixture empty = new Fixture(ChunkWaterRaster.EMPTY, (x, z) -> 99);
        empty.generate();
        verifyNoInteractions(empty.writer);
        verify(empty.runtime, times(1)).raster(anyInt(), anyInt());
        verify(empty.complex, never()).effectiveWaterHeads(anyInt(), anyInt(), any(), any());

        ChunkWaterRaster dry = mock(ChunkWaterRaster.class);
        when(dry.waterHead(anyInt(), anyInt())).thenReturn(Integer.MIN_VALUE);
        Fixture banks = new Fixture(dry, (x, z) -> 99);
        banks.generate();
        verifyNoInteractions(banks.writer);
        verify(banks.complex, never()).effectiveWaterHeads(anyInt(), anyInt(), any(), any());

        Fixture closed = new Fixture(wetRaster(), (x, z) -> 99);
        when(closed.complex.allowsMantleChunkWrite(0, 0)).thenReturn(false);
        closed.generate();
        verifyNoInteractions(closed.writer, closed.runtime);
    }

    @Test
    public void seaInletsAreWrittenEvenWhereNoSurfaceWaterFlows() {
        Fixture dry = new Fixture(ChunkWaterRaster.EMPTY, (x, z) -> 99);
        SeaInlet inlet = new SeaInlet(5L, new float[]{-8f, 8f, 24f}, new float[]{8f, 8f, 8f}, new float[]{4f, 4f, 3f}, 63,
                new int[]{60, 60, 59}, new int[]{90, 68, 67}, 24, 8, 6);
        when(dry.runtime.inlets(0, 0)).thenReturn(List.of(inlet));
        List<HydrologyCaveCell> written = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        doAnswer(call -> {
            written.add(call.getArgument(3));
            heights.add(call.getArgument(1));
            return null;
        }).when(dry.writer).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
        dry.generate();
        verify(dry.complex, never()).effectiveWaterHeads(anyInt(), anyInt(), any(), any());
        int wet = 0;
        for (int index = 0; index < written.size(); index++) {
            HydrologyCaveCell cell = written.get(index);
            assertEquals(HydrologyCaveCell.SEA_FLUID, cell.fluidProfileKey());
            if (cell.action() == HydrologyCaveAction.WET_SOURCE) {
                wet++;
                assertTrue(heights.get(index) <= 63);
            }
        }
        assertTrue("wet inlet cells " + wet, wet > 100);
    }

    @Test
    public void subterrainFluidBesideSeaInletAirIsSealedAboveSeaLevel() {
        Fixture dry = new Fixture(ChunkWaterRaster.EMPTY, (x, z) -> 99);
        SeaInlet inlet = new SeaInlet(5L, new float[]{-8f, 8f, 24f}, new float[]{8f, 8f, 8f}, new float[]{4f, 4f, 3f}, 63,
                new int[]{60, 60, 59}, new int[]{90, 68, 67}, 24, 8, 6);
        when(dry.runtime.inlets(0, 0)).thenReturn(List.of(inlet));
        SubterrainPlanner planner = mock(SubterrainPlanner.class);
        List<SubterrainPlan> plans = List.of(mock(SubterrainPlan.class));
        SubterrainCell water = new SubterrainCell(SubterrainCell.Kind.WATER, "", null);
        when(planner.isEmpty()).thenReturn(false);
        when(planner.plansForBounds(0, 0, 15, 15)).thenReturn(plans);
        when(planner.sample(eq(plans), anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(2) == 69 + MINIMUM_Y ? water : SubterrainCell.OUTSIDE);
        when(dry.complex.getSubterrainPlanner()).thenReturn(planner);
        Engine engine = mock(Engine.class);
        IrisWorld world = mock(IrisWorld.class);
        when(dry.writer.getEngine()).thenReturn(engine);
        when(engine.getWorld()).thenReturn(world);
        when(world.minHeight()).thenReturn(MINIMUM_Y);
        Map<Long, HydrologyCaveCell> cells = new HashMap<>();
        doAnswer(call -> {
            cells.put(cell(call.getArgument(0), call.getArgument(1), call.getArgument(2)), call.getArgument(3));
            return null;
        }).when(dry.writer).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
        when(dry.writer.getDataIfPresent(anyInt(), anyInt(), anyInt(), eq(HydrologyCaveCell.class))).thenAnswer(call ->
                cells.get(cell(call.getArgument(0), call.getArgument(1), call.getArgument(2))));

        dry.generate();

        assertEquals("inlet air keeps its cell", HydrologyCaveAction.DRY_AIR, cells.get(cell(4, 69, 8)).action());
        assertEquals("subterrain water beside the passage", HydrologyCaveAction.SEAL_GUARD, cells.get(cell(8, 69, 13)).action());
        assertEquals("subterrain water over the passage roof", HydrologyCaveAction.SEAL_GUARD, cells.get(cell(12, 69, 8)).action());
        assertNull("subterrain water three blocks from the passage", cells.get(cell(8, 69, 15)));
        assertNull("rock beside the passage above sea level", cells.get(cell(8, 70, 13)));
        for (Map.Entry<Long, HydrologyCaveCell> entry : cells.entrySet()) {
            int y = (int) ((entry.getKey() >>> 20) & 0xFFFFF);
            if (y > 63 && entry.getValue().action() == HydrologyCaveAction.SEAL_GUARD) {
                assertEquals(69, y);
            }
        }
    }

    @Test
    public void provisionalRastersFailTheChunk() {
        Fixture own = new Fixture(ChunkWaterRaster.PROVISIONAL, (x, z) -> 99);
        assertThrows(IllegalStateException.class, own::generate);
        verifyNoInteractions(own.writer);

        Fixture neighbour = new Fixture(wetRaster(), channel((x, z) -> x < 6 ? 99 : 79));
        when(neighbour.runtime.raster(1, 1)).thenReturn(ChunkWaterRaster.PROVISIONAL);
        assertThrows(IllegalStateException.class, neighbour::generate);
        verifyNoInteractions(neighbour.writer);
    }

    static int[] grid(int chunkX, int chunkZ, IntBinaryOperator heads) {
        int[] grid = new int[GRID * GRID];
        for (int cell = 0; cell < grid.length; cell++) {
            grid[cell] = heads.applyAsInt((chunkX << 4) - 1 + cell % GRID, (chunkZ << 4) - 1 + cell / GRID);
        }
        return grid;
    }

    private static long cell(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) y << 20) | (z + 4096);
    }

    private static IntBinaryOperator channel(IntBinaryOperator head) {
        return (x, z) -> z >= 4 && z <= 7 ? head.applyAsInt(x, z) : NO_WATER;
    }

    private static ChunkWaterRaster wetRaster() {
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        when(raster.waterHead(anyInt(), anyInt())).thenReturn(79);
        when(raster.fluid(anyInt(), anyInt())).thenReturn("water");
        return raster;
    }

    private static void assertFall(MantleHydrologyComponent.Falls falls, int index, int bottom, int top) {
        assertTrue("fall at column " + index, falls.falling(index));
        assertEquals("fall bottom at column " + index, bottom, falls.bottom()[index]);
        assertEquals("fall top at column " + index, top, falls.top()[index]);
    }

    private static final class Fixture {
        private final IrisComplex complex = mock(IrisComplex.class);
        private final HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        private final MantleWriter writer = mock(MantleWriter.class);
        private final ChunkContext context = mock(ChunkContext.class);

        @SuppressWarnings("unchecked")
        private Fixture(ChunkWaterRaster raster, IntBinaryOperator heads) {
            Mantle<Matter> mantle = mock(Mantle.class);
            when(context.getComplex()).thenReturn(complex);
            when(complex.allowsMantleChunkWrite(0, 0)).thenReturn(true);
            when(complex.getHydrologyRuntime()).thenReturn(runtime);
            when(runtime.raster(anyInt(), anyInt())).thenReturn(raster);
            when(complex.resolveWaterFluid(anyString(), anyInt(), anyInt())).thenReturn(mock(NativeBlockState.class));
            doAnswer(call -> {
                int[] target = call.getArgument(3);
                System.arraycopy(grid(call.getArgument(0), call.getArgument(1), heads), 0, target, 0, target.length);
                return null;
            }).when(complex).effectiveWaterHeads(anyInt(), anyInt(), any(), any());
            when(writer.getMantle()).thenReturn(mantle);
            when(mantle.getWorldHeight()).thenReturn(WORLD_HEIGHT);
        }

        private void generate() {
            new MantleHydrologyComponent(mock(EngineMantle.class)).generateLayer(writer, 0, 0, context);
        }
    }
}
