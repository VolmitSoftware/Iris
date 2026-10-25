package art.arcane.iris.studio.map;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class WorldExplorerTest {
    @Test
    public void closedExplorerIgnoresShowAndRefresh() throws Exception {
        Engine engine = mock(Engine.class);
        SwingUtilities.invokeAndWait(() -> {
            WorldExplorer explorer = new WorldExplorer(() -> engine);
            explorer.close();
            explorer.close();
            explorer.show(0D, 0D, true);
            explorer.refresh(0D, 0D);
        });
    }

    @Test
    public void hiddenHotloadDiscardsPendingWorldMeasurement() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Engine engine = engineWithHeights(ProceduralStream.ofDouble((x, z) -> {
            started.countDown();
            try {
                assertTrue(release.await(3, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return 64D;
        }));
        AtomicReference<WorldExplorer> opened = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                WorldExplorer explorer = new WorldExplorer(() -> engine);
                opened.set(explorer);
                explorer.show(0D, 0D, true);
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                opened.get().hide();
                opened.get().refresh(0D, 0D);
            });
            release.countDown();
            ((ExecutorService) field(opened.get(), "lookups")).submit(() -> { }).get(3, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(() -> { });
            assertNull(field(opened.get(), "sampledEngine"));
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(() -> opened.get().close());
        }
    }

    @Test
    public void provisionalWorldBoundsAreMeasuredAgainWithoutInteraction() throws Exception {
        AtomicInteger samples = new AtomicInteger();
        CountDownLatch retried = new CountDownLatch(1);
        Engine engine = engineWithHeights(ProceduralStream.ofDouble((x, z) -> {
            int sample = samples.incrementAndGet();
            if (sample <= 81) {
                ProvisionalSampling.mark();
            } else {
                retried.countDown();
            }
            return 64D;
        }));
        AtomicReference<WorldExplorer> opened = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                WorldExplorer explorer = new WorldExplorer(() -> engine);
                opened.set(explorer);
                explorer.show(0D, 0D, true);
            });
            assertTrue(retried.await(3, TimeUnit.SECONDS));
        } finally {
            SwingUtilities.invokeAndWait(() -> opened.get().close());
        }
    }

    private static Engine engineWithHeights(ProceduralStream<Double> heights) {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getHeight()).thenReturn(256);
        when(complex.getHeightStream()).thenReturn(heights);
        when(complex.getFluidHeight()).thenReturn(64D);
        return engine;
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
