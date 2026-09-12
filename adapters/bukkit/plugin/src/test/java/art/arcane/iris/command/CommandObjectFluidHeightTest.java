package art.arcane.iris.command;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CommandObjectFluidHeightTest {
    @Test
    public void absoluteObjectPlacementShiftsTheColumnRiverHeadFromNegativeMinY() {
        World world = mock(World.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisComplex complex = mock(IrisComplex.class);
        @SuppressWarnings("unchecked")
        ProceduralStream<Double> riverHead = mock(ProceduralStream.class);
        Map<Block, BlockData> future = new HashMap<>();

        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(dimension.getFluidHeight()).thenReturn(127);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(riverHead);
        when(riverHead.get(12, -7)).thenReturn(131D);

        IObjectPlacer placer = CommandObject.createPlacer(world, future, engine);

        assertEquals(63, placer.getFluidHeight());
        assertEquals(67, placer.getFluidHeight(12, -7));
        verify(riverHead).get(12, -7);
    }
}
