package art.arcane.iris.world.tree;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TreeSVCFluidHeightTest {
    @Test
    public void liveWorldTreePlacementConvertsLocalFluidHeightToWorldY() {
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getFluidHeight()).thenReturn(127);

        assertEquals(63, TreeSVC.worldFluidHeight(engine));
    }
}
