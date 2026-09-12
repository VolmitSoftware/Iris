package art.arcane.iris.platform.bukkit.nms;

import org.junit.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerShutdownBoundaryTest {
    @Test
    public void await_returnsImmediatelyWhenBoundaryIsAlreadyReached() {
        assertTrue(new ServerShutdownBoundary(
                () -> true,
                Thread.currentThread()).await(
                0L,
                TimeUnit.MILLISECONDS
        ));
    }

    @Test
    public void await_doesNotJoinTheCallingServerThread() {
        assertFalse(new ServerShutdownBoundary(
                () -> false,
                Thread.currentThread()).await(
                5L,
                TimeUnit.SECONDS
        ));
    }

    @Test
    public void await_blocksUntilAuthoritativeBoundaryIsReached() throws Exception {
        CountDownLatch serverStarted = new CountDownLatch(1);
        CountDownLatch releaseServer = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        CountDownLatch waiterFinished = new CountDownLatch(1);
        AtomicBoolean boundaryReached = new AtomicBoolean(false);
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        Thread serverThread = new Thread(() -> {
            serverStarted.countDown();
            await(releaseServer);
            boundaryReached.set(true);
        }, "server-boundary-test");
        ServerShutdownBoundary boundary = new ServerShutdownBoundary(boundaryReached::get, serverThread);
        Thread waiterThread = new Thread(() -> {
            waiterStarted.countDown();
            result.set(boundary.await(
                    5L,
                    TimeUnit.SECONDS
            ));
            waiterFinished.countDown();
        }, "server-boundary-waiter-test");

        serverThread.start();
        assertTrue(serverStarted.await(1L, TimeUnit.SECONDS));
        waiterThread.start();
        assertTrue(waiterStarted.await(1L, TimeUnit.SECONDS));
        assertFalse(waiterFinished.await(0L, TimeUnit.MILLISECONDS));

        releaseServer.countDown();

        assertTrue(waiterFinished.await(2L, TimeUnit.SECONDS));
        assertTrue(result.get());
        serverThread.join();
        waiterThread.join();
    }

    @Test
    public void await_returnsFalseWhenBoundaryDoesNotArriveBeforeTimeout() throws Exception {
        CountDownLatch releaseServer = new CountDownLatch(1);
        Thread serverThread = new Thread(() -> await(releaseServer), "server-boundary-timeout-test");
        serverThread.start();

        try {
            assertFalse(new ServerShutdownBoundary(
                    () -> false,
                    serverThread).await(
                    0L,
                    TimeUnit.MILLISECONDS
            ));
        } finally {
            releaseServer.countDown();
            serverThread.join();
        }
    }

    @Test
    public void preparationLinksTheBoundarySupplierBeforeShutdown() {
        AtomicInteger reads = new AtomicInteger();
        ServerShutdownBoundary boundary = new ServerShutdownBoundary(
                () -> reads.incrementAndGet() > 1, Thread.currentThread());

        assertEquals(1, reads.get());
        assertTrue(boundary.await(0L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void preparedBoundaryRemainsUsableAfterItsClassLoaderCloses() throws Exception {
        URL classes = ServerShutdownBoundary.class.getProtectionDomain().getCodeSource().getLocation();
        AtomicBoolean reached = new AtomicBoolean(false);
        BooleanSupplier supplier = reached::get;
        Object boundary;
        Method awaitMethod;
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = Class.forName(ServerShutdownBoundary.class.getName(), true, loader);
            boundary = type.getConstructor(BooleanSupplier.class, Thread.class)
                    .newInstance(supplier, Thread.currentThread());
            awaitMethod = type.getMethod("await", long.class, TimeUnit.class);
        }

        reached.set(true);
        assertEquals(Boolean.TRUE, awaitMethod.invoke(boundary, 0L, TimeUnit.MILLISECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
