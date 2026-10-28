package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.studio.jigsaw.JigsawStudioCapture.LocalPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JigsawStudioConnectorRepairTest {
    @Test
    public void resetFindsUnsavedMarkersSoTopologyErrorsCanBeRepaired() {
        JigsawStudioBounds bounds = new JigsawStudioBounds(0, 65, 0, new JigsawStudioCellDimensions(3, 2, 3));
        World world = mock(World.class);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        Block saved = mock(Block.class);
        when(saved.getType()).thenReturn(Material.JIGSAW);
        Block stray = mock(Block.class);
        when(stray.getType()).thenReturn(Material.JIGSAW);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(1, 65, 0)).thenReturn(saved);
        when(world.getBlockAt(1, 66, 1)).thenReturn(stray);
        JigsawStudioCapture.ChunkCaptureArea area = JigsawStudioCapture.chunkIntersections(bounds).getFirst();
        Set<LocalPosition> savedConnectors = Set.of(new LocalPosition(1, 0, 0));

        assertEquals(
                Set.of(new LocalPosition(1, 1, 1)),
                JigsawStudioMaterializer.strayMarkerPositions(world, bounds, area, savedConnectors, true));
        assertEquals(
                Set.of(new LocalPosition(1, 0, 0), new LocalPosition(1, 1, 1)),
                JigsawStudioMaterializer.strayMarkerPositions(world, bounds, area, savedConnectors, false));
    }
}
