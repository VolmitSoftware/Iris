package art.arcane.iris.core.commands;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommandObjectFluidHeightTest {
    @Test
    public void absoluteObjectPlacementShiftsFluidHeightFromNegativeMinY() {
        World world = mock(World.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        Map<Block, BlockData> future = new HashMap<>();

        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(127);

        IObjectPlacer placer = CommandObject.createPlacer(world, future, engine);

        assertEquals(63, placer.getFluidHeight());
    }
}
