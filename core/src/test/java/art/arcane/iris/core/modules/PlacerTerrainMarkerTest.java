package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.MatterStructurePOI;
import com.volmit.geometry.BlockPosition;
import com.volmit.placement.PlacedMarker;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PlacerTerrainMarkerTest {
    private static final BlockPosition SPOT = new BlockPosition(3, 64, -7);

    private final IObjectPlacer placer = mock(IObjectPlacer.class);
    private final PlacerTerrain terrain = PlacerTerrain.of(placer, mock(IrisData.class));

    @Test
    public void packMarkerIsKeptUnderItsKey() {
        terrain.mark(new PlacedMarker.PackMarker(SPOT, "spawn/zombie"));

        verify(placer).setData(3, 64, -7, new MatterMarker("spawn/zombie"));
    }

    @Test
    public void treeMaterialKeepsTheMaterialOfTheBlockThere() {
        PlatformBlockState log = mock(PlatformBlockState.class);
        when(log.key()).thenReturn("minecraft:oak_log[axis=y]");
        when(placer.get(3, 64, -7)).thenReturn(log);

        terrain.mark(new PlacedMarker.TreeMaterial(SPOT));

        verify(placer).setData(3, 64, -7, TreeBlockMaterial.of("minecraft:oak_log"));
    }

    @Test
    public void buriedTreasureIsKeptAsAPointOfInterest() {
        terrain.mark(new PlacedMarker.BuriedTreasure(SPOT));

        verify(placer).setData(3, 64, -7, MatterStructurePOI.BURIED_TREASURE);
    }
}
