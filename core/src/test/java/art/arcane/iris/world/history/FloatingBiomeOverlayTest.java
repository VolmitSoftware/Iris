package art.arcane.iris.world.history;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class FloatingBiomeOverlayTest {
    @Test
    public void volumeFollowsTheQuartAnchorAndLastWriterWhileSurfaceUsesTheHighestWrite() {
        FloatingBiomeOverlay overlay = new FloatingBiomeOverlay(16);
        FloatingBiomeOverlay.Identity first = new FloatingBiomeOverlay.Identity("first", "region");
        FloatingBiomeOverlay.Identity second = new FloatingBiomeOverlay.Identity("second", "region");
        overlay.record(0, 8, 0, first);
        overlay.record(0, 9, 0, second);
        overlay.record(1, 8, 1, second);
        assertEquals(first, overlay.volumeAt(3, 11, 3));
        overlay.record(0, 8, 0, second);
        assertEquals(second, overlay.volumeAt(3, 11, 3));
        assertNull(overlay.volumeAt(4, 8, 0));
        overlay.retainHighestSurfaces((x, z) -> x == 0 ? 9 : 12);
        assertEquals(second, overlay.surfaceAt(0, 0));
        assertEquals(9, overlay.surfaceYAt(0, 0));
        assertNull(overlay.surfaceAt(1, 1));
        assertEquals(-1, overlay.surfaceYAt(1, 1));
        assertThrows(IllegalArgumentException.class, () -> overlay.record(16, 8, 0, first));
        assertThrows(IllegalArgumentException.class, () -> overlay.volumeAt(0, 16, 0));
    }
}
