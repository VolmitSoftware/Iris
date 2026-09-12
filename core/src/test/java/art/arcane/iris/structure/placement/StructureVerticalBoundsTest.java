package art.arcane.iris.structure.placement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StructureVerticalBoundsTest {
    @Test
    public void trialChamberShiftCannotProducePurpurPoiSectionBelowLiveWorld() {
        int worldMinY = -64;
        int worldMaxYExclusive = 320;
        int structureMinY = -48;
        int structureMaxY = -16;

        int offset = StructureVerticalBounds.clampOffset(
                structureMinY,
                structureMaxY,
                -64,
                worldMinY,
                worldMaxYExclusive);

        int placedMinY = structureMinY + offset;
        int placedMaxY = structureMaxY + offset;
        int placedMinSection = Math.floorDiv(placedMinY, 16);
        assertEquals(-16, offset);
        assertEquals(-64, placedMinY);
        assertEquals(-32, placedMaxY);
        assertEquals(-4, placedMinSection);
        assertTrue(placedMinSection >= Math.floorDiv(worldMinY, 16));
        assertTrue(placedMinSection <= Math.floorDiv(worldMaxYExclusive - 1, 16));
    }

    @Test
    public void offsetIsClampedAtBothLiveWorldBoundaries() {
        assertEquals(-20, StructureVerticalBounds.clampOffset(20, 40, -100, 0, 256));
        assertEquals(215, StructureVerticalBounds.clampOffset(20, 40, 300, 0, 256));
        assertEquals(0, StructureVerticalBounds.clampOffset(20, 40, 0, 0, 256));
    }

    @Test
    public void structureAlreadyOutsideRangeIsMovedInside() {
        assertEquals(48, StructureVerticalBounds.clampOffset(-112, -80, 0, -64, 320));
        assertEquals(-81, StructureVerticalBounds.clampOffset(400, 400, 0, -64, 320));
    }

    @Test
    public void oversizedStructureIsRejected() {
        try {
            StructureVerticalBounds.clampOffset(-100, 400, 0, -64, 320);
            fail("Oversized structure must be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("exceeds writable world height"));
        }
    }
}
