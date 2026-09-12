package art.arcane.iris.studio.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NoiseViewportTest {
    @Test
    public void zoomKeepsCursorWorldPositionFixed() {
        NoiseViewport viewport = new NoiseViewport(120D, -35D, 2.5D);
        double worldX = viewport.worldX(713D, 1200);
        double worldZ = viewport.worldZ(147D, 700);

        NoiseViewport zoomed = viewport.zoomAt(713D, 147D, 1200, 700, 0.4D);

        assertEquals(worldX, zoomed.worldX(713D, 1200), 0.000000001D);
        assertEquals(worldZ, zoomed.worldZ(147D, 700), 0.000000001D);
        assertEquals(1D, zoomed.blocksPerPixel(), 0D);
    }

    @Test
    public void panMovesCenterByPixelDeltaAtCurrentScale() {
        NoiseViewport viewport = new NoiseViewport(10D, 20D, 2D);

        NoiseViewport panned = viewport.panPixels(7D, -3D);

        assertEquals(-4D, panned.centerX(), 0D);
        assertEquals(26D, panned.centerZ(), 0D);
        assertEquals(2D, panned.blocksPerPixel(), 0D);
    }
}
