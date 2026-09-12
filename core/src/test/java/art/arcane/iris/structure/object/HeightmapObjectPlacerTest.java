package art.arcane.iris.structure.object;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HeightmapObjectPlacerTest {
    @Test
    public void engineOwnershipDelegatesWithoutAmbientContext() {
        Engine engine = mock(Engine.class);
        IObjectPlacer ownedDelegate = mock(IObjectPlacer.class);
        when(ownedDelegate.getEngine()).thenReturn(engine);
        HeightmapObjectPlacer owned = new HeightmapObjectPlacer(mock(RNG.class), 1, 2, 3, new IrisObjectPlacement(), ownedDelegate);
        assertSame(engine, owned.getEngine());

        IObjectPlacer unownedDelegate = mock(IObjectPlacer.class);
        HeightmapObjectPlacer unowned = new HeightmapObjectPlacer(mock(RNG.class), 1, 2, 3, new IrisObjectPlacement(), unownedDelegate);
        assertNull(unowned.getEngine());
    }
}
