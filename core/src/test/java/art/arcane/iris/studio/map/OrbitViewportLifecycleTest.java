package art.arcane.iris.studio.map;

import org.junit.Test;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.OptionalDouble;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrbitViewportLifecycleTest {
    @Test
    public void closedViewportIgnoresLateLoadingAndScenes() throws Exception {
        AtomicReference<OrbitViewport> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            OrbitViewport viewport = new OrbitViewport("Closed viewport test", "Empty");
            holder.set(viewport);
            viewport.setSize(32, 32);
            viewport.close();
            viewport.close();
            viewport.showLoading("Late textures");
            viewport.show(new DelayedScene());
        });
        OrbitViewport viewport = holder.get();
        assertFalse(((Timer) field(viewport, "loadingTicker")).isRunning());
        assertNull(field(viewport, "scene"));
    }

    @Test
    public void renderCompletingAfterClosureDoesNotPublishOrRequestAnotherFrame() throws Exception {
        DelayedScene scene = new DelayedScene();
        AtomicReference<OrbitViewport> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            OrbitViewport viewport = new OrbitViewport("Closing viewport test", "Empty");
            holder.set(viewport);
            viewport.setSize(32, 32);
            viewport.showLoading("Blocks");
            viewport.show(scene);
        });
        OrbitViewport viewport = holder.get();
        try {
            assertTrue(scene.started.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(viewport::close);
            scene.release.countDown();
            assertTrue(((ExecutorService) field(viewport, "renderThread")).awaitTermination(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> { });

            assertNull(field(viewport, "picture"));
            assertFalse(((Timer) field(viewport, "loadingTicker")).isRunning());
            assertFalse(((Timer) field(viewport, "loadingCloser")).isRunning());
        } finally {
            scene.release.countDown();
            SwingUtilities.invokeAndWait(viewport::close);
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static final class DelayedScene implements OrbitScene {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public OrbitPicture render(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
            started.countDown();
            while (release.getCount() != 0) {
                try {
                    assertTrue(release.await(3, TimeUnit.SECONDS));
                } catch (InterruptedException ignored) {
                }
            }
            return OrbitPicture.whole(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), OptionalDouble.empty()).stillCatchingUp();
        }

        @Override
        public OrbitProjection projection(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
            return null;
        }
    }
}
