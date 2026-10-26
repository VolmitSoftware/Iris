package art.arcane.iris.studio.view;

import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
    public void hoverCoordinatesFollowPaintedRectangularBoundsAndLetterboxing() throws Exception {
        PregenMapPanel wide = panel(new PregenRenderSnapshot.Bounds(-2, -3, 1, -2));
        SwingUtilities.invokeAndWait(() -> wide.setSize(802, 602));
        assertEquals("Chunk X -2 · Z -3", tooltip(wide, 1, 101));
        assertEquals("Chunk X -2 · Z -3", tooltip(wide, 200, 300));
        assertEquals("Chunk X -1 · Z -2", tooltip(wide, 201, 301));
        assertEquals("Chunk X 1 · Z -2", tooltip(wide, 800, 500));
        assertNull(tooltip(wide, 0, 300));
        assertNull(tooltip(wide, 801, 300));
        assertNull(tooltip(wide, 400, 100));
        assertNull(tooltip(wide, 400, 501));
        SwingUtilities.invokeAndWait(wide::disposeMap);
        assertNull(tooltip(wide, 400, 300));

        PregenMapPanel tall = panel(new PregenRenderSnapshot.Bounds(-7, -2, -6, 1));
        SwingUtilities.invokeAndWait(() -> tall.setSize(802, 602));
        assertEquals("Chunk X -7 · Z -2", tooltip(tall, 251, 1));
        assertEquals("Chunk X -6 · Z 1", tooltip(tall, 550, 600));
        assertNull(tooltip(tall, 250, 300));
        assertNull(tooltip(tall, 551, 300));
        SwingUtilities.invokeAndWait(tall::disposeMap);
    }

    @Test
    public void hoverCoordinatesPreserveSingleChunkAndExtremeIntegerBoundsAfterResize() throws Exception {
        PregenMapPanel single = panel(new PregenRenderSnapshot.Bounds(5, -9, 5, -9));
        SwingUtilities.invokeAndWait(() -> single.setSize(64, 64));
        assertEquals("Chunk X 5 · Z -9", tooltip(single, 1, 1));
        assertEquals("Chunk X 5 · Z -9", tooltip(single, 62, 62));
        SwingUtilities.invokeAndWait(() -> single.setSize(128, 64));
        assertNull(tooltip(single, 1, 1));
        assertEquals("Chunk X 5 · Z -9", tooltip(single, 33, 1));
        SwingUtilities.invokeAndWait(single::disposeMap);

        PregenMapPanel extreme = panel(new PregenRenderSnapshot.Bounds(
                Integer.MIN_VALUE, -10, Integer.MAX_VALUE, 10));
        SwingUtilities.invokeAndWait(() -> extreme.setSize(1026, 3));
        assertEquals("Chunk X -2147483648 · Z -10", tooltip(extreme, 1, 1));
        assertEquals("Chunk X 0 · Z -10", tooltip(extreme, 513, 1));
        assertEquals("Chunk X 2143289344 · Z -10", tooltip(extreme, 1024, 1));
        assertNull(tooltip(extreme, 1025, 1));
        SwingUtilities.invokeAndWait(extreme::disposeMap);
    }

    @Test
    public void wheelZoomPreservesTheChunkUnderThePointerAndRetainedPixels() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(-32, -32, 31, 31));
        SwingUtilities.invokeAndWait(() -> panel.setSize(802, 602));
        panel.submit(-7, -19, Color.GREEN);
        flush(panel);
        BufferedImage retained = image(panel);
        String before = tooltip(panel, 337, 123);
        SwingUtilities.invokeAndWait(() -> panel.dispatchEvent(new MouseWheelEvent(panel,
                MouseEvent.MOUSE_WHEEL, 1L, 0, 337, 123, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -4)));
        assertEquals(before, tooltip(panel, 337, 123));
        assertTrue(panel.zoom() > 1D);
        assertTrue(retained == image(panel));
        assertEquals(Color.GREEN.getRGB(), image(panel).getRGB(25, 13));
        SwingUtilities.invokeAndWait(panel::disposeMap);
    }

    @Test
    public void draggingPansTheMapAndResetFitsBesideTheSidebar() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(-32, -32, 31, 31));
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1000, 600);
            panel.setCoveredLeftWidth(300);
        });
        String initial = tooltip(panel, 650, 300);
        assertNull(tooltip(panel, 250, 300));
        SwingUtilities.invokeAndWait(() -> {
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 1L,
                    MouseEvent.BUTTON1_DOWN_MASK, 650, 300, 1, false, MouseEvent.BUTTON1));
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 2L,
                    MouseEvent.BUTTON1_DOWN_MASK, 710, 350, 0, false, MouseEvent.NOBUTTON));
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 3L,
                    0, 710, 350, 1, false, MouseEvent.BUTTON1));
        });
        assertEquals(initial, tooltip(panel, 710, 350));
        assertFalse(initial.equals(tooltip(panel, 650, 300)));
        SwingUtilities.invokeAndWait(() -> {
            panel.zoomIn();
            panel.resetView();
        });
        assertEquals(1D, panel.zoom(), 0D);
        assertEquals(initial, tooltip(panel, 650, 300));
        SwingUtilities.invokeAndWait(panel::disposeMap);
    }

    @Test
    public void zoomBoundsAndGridPaintingHandleExtremeMapCoordinates() throws Exception {
        PregenMapPanel panel = panel(new PregenRenderSnapshot.Bounds(
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(820, 520);
            panel.toggleGrid();
            for (int index = 0; index < 100; index++) {
                panel.zoomIn();
            }
            assertEquals(128D, panel.zoom(), 0D);
            BufferedImage target = new BufferedImage(820, 520, BufferedImage.TYPE_INT_RGB);
            Graphics2D canvas = target.createGraphics();
            try {
                panel.paint(canvas);
                panel.panBy(1_000_000, -1_000_000);
                panel.paint(canvas);
                for (int index = 0; index < 100; index++) {
                    panel.zoomOut();
                }
                assertEquals(0.25D, panel.zoom(), 0D);
                panel.resetView();
                panel.paint(canvas);
            } finally {
                canvas.dispose();
                panel.disposeMap();
            }
        });
    }

    private static String tooltip(PregenMapPanel panel, int x, int y) throws Exception {
        String[] result = new String[1];
        SwingUtilities.invokeAndWait(() -> result[0] = panel.getToolTipText(
                new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0L, 0, x, y, 0, false)));
        return result[0];
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
        SwingUtilities.invokeAndWait(() -> result[0] = new PregenMapPanel(new PregenMapState(bounds)));
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
