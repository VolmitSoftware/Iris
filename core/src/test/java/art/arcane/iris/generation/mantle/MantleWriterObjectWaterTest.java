package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MantleWriterObjectWaterTest {
    @Rule
    public final PlatformBinding platform = PlatformBinding.mockPlatform();

    private MantleWriter writer;
    private MatterSlice<NativeBlockState> blocks;
    private MatterSlice<MatterCavern> caverns;
    private NativeBlockState stone;
    private NativeBlockState air;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        EngineMantle engineMantle = mock(EngineMantle.class);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Matter matter = mock(Matter.class);
        blocks = mock(MatterSlice.class);
        caverns = mock(MatterSlice.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getComplex()).thenReturn(complex);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.raster(anyInt(), anyInt())).thenReturn(raster);
        when(raster.role(anyInt(), anyInt())).thenReturn(WaterRole.NONE);
        when(raster.waterHead(anyInt(), anyInt())).thenReturn(Integer.MIN_VALUE);
        when(raster.terrainHeight(anyInt(), anyInt())).thenReturn(12);
        when(raster.role(3, 3)).thenReturn(WaterRole.CHANNEL);
        when(raster.waterHead(3, 3)).thenReturn(9);
        when(raster.terrainHeight(3, 3)).thenReturn(5);
        followRaster(complex, raster);
        when(mantle.getWorldHeight()).thenReturn(64);
        when(mantle.getChunk(0, 0)).thenReturn(chunk);
        when(chunk.use()).thenReturn(chunk);
        when(mantle.useChunk(0, 0)).thenAnswer(call -> chunk.use());
        when(chunk.getOrCreate(0)).thenReturn(matter);
        when(chunk.exists(0)).thenReturn(true);
        when(chunk.get(0)).thenReturn(matter);
        when(matter.getClass(any())).thenAnswer(call -> call.getArgument(0).getClass());
        doReturn(blocks).when(matter).slice(NativeBlockState.class);
        doReturn(caverns).when(matter).slice(MatterCavern.class);
        doReturn(mock(MatterSlice.class)).when(matter).getSlice(Identifier.class);
        doReturn(null).when(matter).getSlice(HydrologyCaveCell.class);
        stone = mock(NativeBlockState.class);
        when(stone.isSolid()).thenReturn(true);
        air = mock(NativeBlockState.class);
        writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
    }

    @After
    public void tearDown() {
        writer.close();
    }

    @Test
    public void structurePiecesKeepOutOfRiverWaterUnlessTheyAllowIt() {
        MatterCavern cavern = new MatterCavern(true, "", (byte) 3);
        int land = writer.beginObjectPlacement(ObjectWaterRule.KEEP_OUT);
        writer.set(3, 7, 3, stone);
        writer.set(3, 8, 3, air);
        writer.setData(3, 6, 3, cavern);
        writer.set(3, 5, 3, stone);
        writer.set(3, 10, 3, stone);
        writer.set(8, 7, 8, stone);
        writer.endObjectPlacement();

        verify(blocks, never()).set(3, 7, 3, stone);
        verify(blocks, never()).set(3, 8, 3, air);
        verify(caverns, never()).set(3, 6, 3, cavern);
        verify(blocks).set(3, 5, 3, stone);
        verify(blocks).set(3, 10, 3, stone);
        verify(blocks).set(8, 7, 8, stone);

        int water = writer.beginObjectPlacement(ObjectWaterRule.ENTERS_WATER);
        writer.set(3, 6, 3, stone);
        writer.set(3, 9, 3, air);
        writer.endObjectPlacement();

        verify(blocks).set(3, 6, 3, stone);
        verify(blocks, never()).set(3, 9, 3, air);
    }

    @Test
    public void forcedPlacementsWriteThroughRiverWater() {
        MatterCavern cavern = new MatterCavern(true, "", (byte) 3);
        writer.beginObjectPlacement(ObjectWaterRule.FORCED);
        writer.set(3, 7, 3, stone);
        writer.set(3, 8, 3, air);
        writer.setData(3, 6, 3, cavern);
        writer.endObjectPlacement();

        verify(blocks).set(3, 7, 3, stone);
        verify(blocks).set(3, 8, 3, air);
        verify(caverns).set(3, 6, 3, cavern);
    }

    @Test
    public void writesOutsideAnObjectPlacementAreNotVetoed() {
        int land = writer.beginObjectPlacement(ObjectWaterRule.KEEP_OUT);
        writer.endObjectPlacement();

        writer.set(3, 7, 3, stone);
        writer.set(3, 8, 3, air);

        verify(blocks).set(3, 7, 3, stone);
        verify(blocks).set(3, 8, 3, air);
    }

    @SuppressWarnings("unchecked")
    private static void followRaster(IrisComplex complex, ChunkWaterRaster raster) {
        ProceduralStream<WaterRole> roles = mock(ProceduralStream.class);
        ProceduralStream<Double> heads = mock(ProceduralStream.class);
        ProceduralStream<Integer> heights = mock(ProceduralStream.class);
        when(roles.get(anyDouble(), anyDouble())).thenAnswer(call -> raster.role(local(call.getArgument(0)), local(call.getArgument(1))));
        when(heads.getDouble(anyDouble(), anyDouble())).thenAnswer(call -> (double) raster.waterHead(local(call.getArgument(0)), local(call.getArgument(1))));
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(call -> raster.terrainHeight(local(call.getArgument(0)), local(call.getArgument(1))));
        when(complex.getWaterRoleStream()).thenReturn(roles);
        when(complex.getWaterHeadStream()).thenReturn(heads);
        when(complex.getRoundedHeighteightStream()).thenReturn(heights);
    }

    private static int local(Object coordinate) {
        return (int) Math.floor(((Number) coordinate).doubleValue()) & 15;
    }
}
