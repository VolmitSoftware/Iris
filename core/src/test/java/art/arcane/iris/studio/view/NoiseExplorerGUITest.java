package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.ClassRule;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NoiseExplorerGUITest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void openingInitialSourceBuildsSamplerExactlyOnce() throws Exception {
        AtomicInteger factoryCalls = new AtomicInteger();
        CountDownLatch firstFactoryCall = new CountDownLatch(1);
        CountDownLatch releaseFactory = new CountDownLatch(1);
        CountDownLatch duplicateFactoryCall = new CountDownLatch(1);
        AtomicReference<NoiseExplorerGUI> explorerReference = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                NoiseExplorerGUI explorer = new NoiseExplorerGUI(sourceSeed -> {
                    int invocation = factoryCalls.incrementAndGet();
                    if (invocation == 1) {
                        firstFactoryCall.countDown();
                        try {
                            releaseFactory.await(3L, TimeUnit.SECONDS);
                        } catch (InterruptedException error) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        duplicateFactoryCall.countDown();
                    }
                    return constantSampler();
                }, "Test source", 12345L);
                explorer.buildSidebar();
                explorer.openViewer();
                explorerReference.set(explorer);
            });

            assertTrue(firstFactoryCall.await(3L, TimeUnit.SECONDS));
            releaseFactory.countDown();
            assertFalse(duplicateFactoryCall.await(300L, TimeUnit.MILLISECONDS));
            assertEquals(1, factoryCalls.get());
        } finally {
            releaseFactory.countDown();
            NoiseExplorerGUI explorer = explorerReference.get();
            if (explorer != null) {
                SwingUtilities.invokeAndWait(explorer::close);
            }
        }
    }

    @Test
    public void allExecutorsRegisterForPreservationAndCloseWithWindow() throws Exception {
        PreservationRegistry previous = IrisServices.getOrNull(PreservationRegistry.class);
        RecordingPreservationRegistry preservation = new RecordingPreservationRegistry();
        AtomicReference<NoiseExplorerGUI> explorerReference = new AtomicReference<>();
        IrisServices.register(PreservationRegistry.class, preservation);
        try {
            SwingUtilities.invokeAndWait(() -> explorerReference.set(
                    new NoiseExplorerGUI(sourceSeed -> constantSampler(), "Test source", 12345L)
            ));

            assertEquals(2, preservation.executors.size());
            SwingUtilities.invokeAndWait(explorerReference.get()::close);
            for (ExecutorService executor : preservation.executors) {
                assertTrue(executor.isShutdown());
            }
        } finally {
            NoiseExplorerGUI explorer = explorerReference.get();
            if (explorer != null) {
                SwingUtilities.invokeAndWait(explorer::close);
            }
            if (previous == null) {
                IrisServices.remove(PreservationRegistry.class);
            } else {
                IrisServices.register(PreservationRegistry.class, previous);
            }
        }
    }

    private static NoiseProvider constantSampler() {
        return (x, z) -> 0.5D;
    }

    private static final class RecordingPreservationRegistry implements PreservationRegistry {
        private final List<ExecutorService> executors = new ArrayList<>();

        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
            executors.add(service);
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
