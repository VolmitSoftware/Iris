package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.pregen.CachedPregenMethod;
import art.arcane.iris.world.pregen.PregenCache;
import art.arcane.iris.world.pregen.PregenSavedChunkStatus;
import art.arcane.iris.world.pregen.PregenTask;
import art.arcane.iris.world.pregen.PregeneratorMethod;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class PregenMapRetentionTest {
    @Test
    public void existingStartingChunksPublishedBeforeAttachmentAppearInActualPaintAndReopenedView() throws Exception {
        Fixture fixture = new Fixture();
        fixture.job.onChunkExistsInRegionGen(-16, -16);
        fixture.job.onChunkGenerated(-17, -16, false);
        verifyNoInteractions(fixture.engine);

        PregenMapPanel first = panel(fixture.state);
        assertEquals(PregenRenderer.EXISTS.getRGB(), paint(first, fixture.state).getRGB(17, 17));
        assertEquals(PregenRenderer.GENERATED.getRGB(), paint(first, fixture.state).getRGB(16, 17));
        SwingUtilities.invokeAndWait(first::disposeMap);
        fixture.job.onChunkGenerated(-16, -17, true);
        PregenMapPanel reopened = panel(fixture.state);
        assertEquals(PregenRenderer.EXISTS.getRGB(), paint(reopened, fixture.state).getRGB(17, 17));
        assertEquals(PregenRenderer.GENERATED.getRGB(), paint(reopened, fixture.state).getRGB(17, 16));
        SwingUtilities.invokeAndWait(reopened::disposeMap);
    }

    @Test
    public void whollyCachedNegativeRegionPublishesAllChunksBeforeGuiOpens() throws Exception {
        Fixture fixture = new Fixture();
        PregenCache cache = mock(PregenCache.class);
        when(cache.sync()).thenReturn(cache);
        when(cache.isRegionCached(-1, -1)).thenReturn(true);
        PregeneratorMethod backend = mock(PregeneratorMethod.class);
        CachedPregenMethod cached = new CachedPregenMethod(new CachedPregenMethod.Configuration(
                backend, cache, fixture.task, new PregenSavedChunkStatus((x, z) -> true)));
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            cached.generateRegion(-1, -1, fixture.job);
        }
        verifyNoInteractions(backend);
        PregenMapPanel panel = panel(fixture.state);
        BufferedImage rendered = paint(panel, fixture.state);
        for (int x = 1; x < 32; x++) {
            for (int z = 1; z < 32; z++) {
                assertEquals(PregenRenderer.GENERATED.getRGB(), rendered.getRGB(x + 1, z + 1));
            }
        }
        SwingUtilities.invokeAndWait(panel::disposeMap);
    }

    @Test
    public void shutdownSkippedRegionsStayWaitingInsteadOfBeingPaintedAsExisting() throws Exception {
        Fixture fixture = new Fixture();
        fixture.job.onRegionSkipped(-1, -1);
        PregenMapPanel panel = panel(fixture.state);
        assertEquals(PregenMapState.WAITING.getRGB(), paint(panel, fixture.state).getRGB(17, 17));
        SwingUtilities.invokeAndWait(panel::disposeMap);
    }

    @Test
    public void concurrentPublicationAndIndependentViewsKeepTheFinalRaster() throws Exception {
        PregenMapState state = new PregenMapState(new PregenRenderSnapshot.Bounds(-8, -8, 7, 7));
        PregenMapPanel first = panel(state);
        PregenMapPanel second = panel(state);
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> updates = worker.submit(() -> {
                started.countDown();
                for (int round = 0; round < 100; round++) {
                    for (int x = -8; x < 8; x++) {
                        for (int z = -8; z < 8; z++) {
                            state.submit(x, z, (round & 1) == 0 ? Color.RED : Color.GREEN);
                        }
                    }
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int round = 0; round < 10; round++) {
                SwingUtilities.invokeAndWait(first::flush);
            }
            updates.get(5, TimeUnit.SECONDS);
            BufferedImage one = paint(first, state);
            BufferedImage two = paint(second, state);
            for (int x = 2; x < 16; x++) {
                for (int z = 2; z < 16; z++) {
                    assertEquals(Color.GREEN.getRGB(), one.getRGB(x, z));
                    assertEquals(one.getRGB(x, z), two.getRGB(x, z));
                }
            }
        } finally {
            worker.shutdownNow();
            SwingUtilities.invokeAndWait(first::disposeMap);
            SwingUtilities.invokeAndWait(second::disposeMap);
        }
    }

    private static PregenMapPanel panel(PregenMapState state) throws Exception {
        PregenMapPanel[] result = new PregenMapPanel[1];
        SwingUtilities.invokeAndWait(() -> result[0] = new PregenMapPanel(state));
        return result[0];
    }

    private static BufferedImage paint(PregenMapPanel panel, PregenMapState state) throws Exception {
        BufferedImage image = new BufferedImage(state.width() + 2, state.height() + 2, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            panel.flush();
            panel.setSize(image.getWidth(), image.getHeight());
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }
        });
        return image;
    }

    private static final class Fixture {
        private final PregeneratorJob job = mock(PregeneratorJob.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class);
        private final PregenTask task = PregenTask.builder().center(new Position2(-256, -256))
                .radiusX(256).radiusZ(256).build();
        private final PregenMapState state;

        private Fixture() throws Exception {
            int[] bounds = task.chunkBounds();
            state = new PregenMapState(new PregenRenderSnapshot.Bounds(bounds[0], bounds[1], bounds[2], bounds[3]));
            set("mapState", state);
            set("task", task);
            set("engine", engine);
        }

        private void set(String name, Object value) throws Exception {
            Field field = PregeneratorJob.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(job, value);
        }
    }
}
