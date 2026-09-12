package art.arcane.iris.studio.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class VisionViewportTest {
    @Test
    public void granularZoomKeepsTheCursorWorldCoordinateFixed() {
        VisionViewport viewport = new VisionViewport(-21_672D, -10_148D, 256D);
        double cursorWorldX = viewport.worldX(1_194D, 1_440);
        double cursorWorldZ = viewport.worldZ(283D, 792);

        VisionViewport zoomed = viewport.zoomAt(1_194D, 283D, 1_440, 792, Math.pow(2D, -0.08D), 1D, 4_096D);

        assertEquals(cursorWorldX, zoomed.worldX(1_194D, 1_440), 0.000000001D);
        assertEquals(cursorWorldZ, zoomed.worldZ(283D, 792), 0.000000001D);
        assertEquals(242.19075756175255D, zoomed.blocksPerPixel(), 0.000000001D);
    }

    @Test
    public void zoomClampingStillKeepsTheCursorAnchored() {
        VisionViewport viewport = new VisionViewport(800D, -400D, 2D);
        double cursorWorldX = viewport.worldX(925D, 1_200);
        double cursorWorldZ = viewport.worldZ(127D, 700);

        VisionViewport zoomed = viewport.zoomAt(925D, 127D, 1_200, 700, 0.001D, 1D, 4_096D);

        assertEquals(1D, zoomed.blocksPerPixel(), 0D);
        assertEquals(cursorWorldX, zoomed.worldX(925D, 1_200), 0.000000001D);
        assertEquals(cursorWorldZ, zoomed.worldZ(127D, 700), 0.000000001D);
    }
}
