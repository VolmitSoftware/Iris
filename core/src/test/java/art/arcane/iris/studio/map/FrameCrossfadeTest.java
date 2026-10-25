package art.arcane.iris.studio.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FrameCrossfadeTest {
    private final FrameCrossfade crossfade = new FrameCrossfade(() -> {
    });

    @Test
    public void heldFrameStaysFullyVisibleUntilTheNextFrameArrives() {
        NoiseRenderCoordinator.Result frame = frame();

        crossfade.hold(frame);

        assertSame(frame, crossfade.outgoing());
        assertEquals(1F, crossfade.outgoingOpacity(), 0F);
    }

    @Test
    public void heldFrameFadesOnceTheNextFrameArrives() throws InterruptedException {
        crossfade.hold(frame());
        crossfade.reveal();
        Thread.sleep(20L);

        assertTrue(crossfade.outgoingOpacity() < 1F);
    }

    @Test
    public void switchingAgainBeforeAnythingDrewKeepsTheFrameHeld() {
        NoiseRenderCoordinator.Result frame = frame();
        crossfade.hold(frame);

        crossfade.hold(null);

        assertSame(frame, crossfade.outgoing());
    }

    @Test
    public void frameWithoutAHeldFrameHasNothingToFade() {
        crossfade.reveal();

        assertNull(crossfade.outgoing());
    }

    @Test
    public void clearDropsTheHeldFrame() {
        crossfade.hold(frame());

        crossfade.clear();

        assertNull(crossfade.outgoing());
    }

    private static NoiseRenderCoordinator.Result frame() {
        return new NoiseRenderCoordinator.Result(null, null, 1D, 1L, 0D, 1D, 0.5D, 0L, 0L, 0L);
    }
}
