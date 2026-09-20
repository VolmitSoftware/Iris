package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NativeTeleportBoundsTest {
    @Test
    public void blockCoordinatesUseFloorAcrossZeroAndChunkBoundaries() {
        assertEquals(0, ModdedTeleportBounds.blockCoordinate(0.99D));
        assertEquals(-1, ModdedTeleportBounds.blockCoordinate(-0.01D));
        assertEquals(-17, ModdedTeleportBounds.blockCoordinate(-16.01D));
        assertEquals(16, ModdedTeleportBounds.blockCoordinate(16D));
    }

    @Test
    public void teleportYReservesSupportFeetAndHeadSpace() {
        assertEquals(-63, ModdedTeleportBounds.clampY(-64, 320, -100));
        assertEquals(318, ModdedTeleportBounds.clampY(-64, 320, 400));
        assertEquals(72, ModdedTeleportBounds.clampY(-64, 320, 72));
    }

    @Test(expected = IllegalArgumentException.class)
    public void teleportYRejectsWorldsWithoutThreeVerticalBlocks() {
        ModdedTeleportBounds.clampY(0, 2, 1);
    }
}
