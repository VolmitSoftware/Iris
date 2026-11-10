package art.arcane.iris.engine.mantle.components;

import com.volmit.block.BlockState;
import com.volmit.block.PaletteSampler;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleLayer;
import com.volmit.placement.PlacementTerrain;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IrisStructureFoundationWriteTest {
    private static final BlockState SUPPORT = new BlockState("minecraft:deepslate_bricks");

    @Test
    @SuppressWarnings("unchecked")
    public void resolvesWorldCoordinatesThenClearsCavernBeforeWritingSupport() {
        Mantle mantle = mock(Mantle.class);
        MantleLayer<CarvedVoid> carved = mock(MantleLayer.class);
        when(mantle.carved()).thenReturn(carved);
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        List<String> asked = new ArrayList<>();
        PaletteSampler picker = (x, y, z) -> {
            asked.add((int) x + "," + (int) y + "," + (int) z);
            return SUPPORT;
        };

        StructurePlacing.writeFoundationSupport(terrain, mantle, picker, 12, 9, -18, -256);

        assertEquals(List.of("12,-247,-18"), asked);
        InOrder order = inOrder(carved, terrain);
        order.verify(carved).remove(12, 9, -18);
        order.verify(terrain).set(12, 9, -18, SUPPORT);
    }

    @Test
    public void unresolvedPaletteFailsBeforeChangingCavernOrBlocks() {
        Mantle mantle = mock(Mantle.class);
        PlacementTerrain terrain = mock(PlacementTerrain.class);

        try {
            StructurePlacing.writeFoundationSupport(terrain, mantle, (x, y, z) -> null, 12, 9, -18, -256);
        } catch (IllegalStateException error) {
            assertTrue(error.getMessage().contains("12,-247,-18"));
            verifyNoInteractions(mantle, terrain);
            return;
        }
        throw new AssertionError("Expected an unresolved structure stilt palette to fail");
    }
}
