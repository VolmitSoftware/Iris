package art.arcane.iris.studio.map;

import org.junit.After;
import org.junit.Test;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FrameCrossfadeTest {
    private final FrameCrossfade crossfade = new FrameCrossfade(() -> {
    });

    @After
    public void stopRepaintTimer() throws Exception {
        SwingUtilities.invokeAndWait(crossfade::clear);
    }

    @Test
    public void heldFrameStaysFullyVisibleUntilTheNextFrameArrives() {
        NoiseRenderCoordinator.Result frame = frame();

        crossfade.hold(frame);

        assertSame(frame, crossfade.outgoing());
        assertEquals(1F, crossfade.outgoingOpacity(), 0F);
    }

    @Test
    public void heldFrameFadesOnceTheNextFrameArrives() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            crossfade.hold(frame());
            crossfade.reveal();
        });
        Thread.sleep(20L);

        SwingUtilities.invokeAndWait(() -> assertTrue(crossfade.outgoing() == null || crossfade.outgoingOpacity() < 1F));
    }

    @Test
    public void fadingStopsAndReleasesTheFrameWithoutPainting() throws Exception {
        Timer timer = (Timer) field("repaintTimer");
        SwingUtilities.invokeAndWait(() -> {
            crossfade.hold(frame());
            crossfade.reveal();
            assertTrue(timer.isRunning());
        });

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (timer.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        SwingUtilities.invokeAndWait(() -> { });

        assertFalse("Hidden views must stop scheduling fade repaints", timer.isRunning());
        assertNull(field("outgoing"));
    }

    @Test
    public void holdingDuringFadeStopsRepaintsUntilAnotherFrameArrives() throws Exception {
        Timer timer = (Timer) field("repaintTimer");
        NoiseRenderCoordinator.Result frame = frame();
        SwingUtilities.invokeAndWait(() -> {
            crossfade.hold(frame);
            crossfade.reveal();
            crossfade.hold(null);

            assertFalse("A held frame must not keep the fade timer running", timer.isRunning());
            assertSame(frame, crossfade.outgoing());
            assertEquals(1F, crossfade.outgoingOpacity(), 0F);

            crossfade.reveal();
            assertTrue(timer.isRunning());
        });
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

    private Object field(String name) throws Exception {
        Field field = FrameCrossfade.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(crossfade);
    }
}
