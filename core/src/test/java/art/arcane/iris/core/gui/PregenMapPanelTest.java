package art.arcane.iris.core.gui;

import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PregenMapPanelTest {
    @Test
    public void singleChunkAndInclusiveFinalEdgesReceivePixels() throws Exception {
        PregenMapPanel single = panel(new PregenRenderSnapshot.Bounds(5, -9, 5, -9));
        single.submit(5, -9, Color.GREEN);
        flush(single);
        assertEquals(Color.GREEN.getRGB(), image(single).getRGB(0, 0));

        PregenMapPanel rectangle = panel(new PregenRenderSnapshot.Bounds(-2, -3, 1, -1));
        rectangle.submit(1, -1, Color.RED);
        rectangle.submit(-2, -3, Color.BLUE);
        flush(rectangle);
        assertEquals(Color.RED.getRGB(), image(rectangle).getRGB(3, 2));
        assertEquals(Color.BLUE.getRGB(), image(rectangle).getRGB(0, 0));
    }

    @Test
    public void largeMapsStayBoundedAndEverySubmittedChunkCanReachAPixel() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(Integer.MIN_VALUE, -10, Integer.MAX_VALUE, 10));
        panel.submit(Integer.MAX_VALUE, 10, Color.GREEN);
        panel.submit(0, 0, Color.RED);
        flush(panel);

        assertEquals(1024, image(panel).getWidth());
        assertEquals(21, image(panel).getHeight());
        assertEquals(Color.GREEN.getRGB(), image(panel).getRGB(1023, 20));
        assertEquals(Color.RED.getRGB(), image(panel).getRGB(512, 10));
    }

    @Test
    public void duplicateAndOutOfBoundsSubmissionsDoNotRequestAnotherFrame() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(0, 0, 1, 1));
        panel.submit(2, 0, Color.GREEN);
        SwingUtilities.invokeAndWait(() -> assertFalse(panel.flush()));
        panel.submit(0, 0, Color.GREEN);
        SwingUtilities.invokeAndWait(() -> assertTrue(panel.flush()));
        panel.submit(0, 0, Color.GREEN);
        SwingUtilities.invokeAndWait(() -> assertFalse(panel.flush()));
        SwingUtilities.invokeAndWait(panel::disposeMap);
        panel.submit(0, 0, Color.RED);
        SwingUtilities.invokeAndWait(() -> assertFalse(panel.flush()));
    }

    @Test
    public void pixelUpdatesRetainTheirLatestValueUntilTheViewFlushes() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(0, 0, 10_000, 0));
        for (int index = 0; index < 100_000; index++) {
            panel.submit(index % 10_001, 0, Color.RED);
        }
        panel.submit(10_000, 0, Color.GREEN);
        flush(panel);
        assertEquals(Color.GREEN.getRGB(), image(panel).getRGB(1023, 0));
        assertEquals(1024, image(panel).getWidth());
    }

    @Test
    public void mapFitPreservesRectangularChunkAspect() {
        Rectangle wide = PregenMapPanel.fit(new PregenRenderSnapshot.Bounds(0, 0, 3, 1), 802, 602);
        assertEquals(new Rectangle(1, 101, 800, 400), wide);
        Rectangle tall = PregenMapPanel.fit(new PregenRenderSnapshot.Bounds(0, 0, 1, 3), 802, 602);
        assertEquals(new Rectangle(251, 1, 300, 600), tall);
    }

    @Test
    public void arithmeticTaskBoundsMatchActualRectangularTraversal() {
        PregenTask task = PregenTask.builder().center(new Position2(-17, 35)).radiusX(9).radiusZ(37).build();
        int[] actual = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        task.iterateAllChunks((x, z) -> {
            actual[0] = Math.min(actual[0], x);
            actual[1] = Math.min(actual[1], z);
            actual[2] = Math.max(actual[2], x);
            actual[3] = Math.max(actual[3], z);
        });
        assertArrayEquals(actual, task.chunkBounds());
    }

    private static PregenMapPanel panel(PregenRenderSnapshot.Bounds bounds) throws Exception {
        PregenMapPanel[] result = new PregenMapPanel[1];
        SwingUtilities.invokeAndWait(() -> result[0] = new PregenMapPanel(bounds));
        return result[0];
    }

    private static void flush(PregenMapPanel panel) throws Exception {
        SwingUtilities.invokeAndWait(panel::flush);
    }

    private static BufferedImage image(PregenMapPanel panel) throws Exception {
        Field field = PregenMapPanel.class.getDeclaredField("image");
        field.setAccessible(true);
        return (BufferedImage) field.get(panel);
    }
}
