package art.arcane.iris.platform.agent;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClassLoaderCloseDeferralTest {
    @Test
    public void unarmedPluginDisableClosesNormally() throws Exception {
        TestLoader loader = new TestLoader(new URL[0]);

        loader.close();
        Installer.releaseClassLoader(loader);

        assertEquals(1, loader.closes.get());
    }

    @Test
    public void holdingOnePluginDoesNotHoldAnotherEqualLoader() throws Exception {
        TestLoader iris = new TestLoader(new URL[0]);
        TestLoader other = new TestLoader(new URL[0]);
        Installer.retainClassLoader(iris);
        try {
            other.close();
            iris.close();

            assertEquals(1, other.closes.get());
            assertEquals(0, iris.closes.get());
        } finally {
            Installer.releaseClassLoader(iris);
        }
        assertEquals(1, iris.closes.get());
    }

    @Test
    public void deferredJarRemainsReadableUntilCleanupReleasesIt() throws Exception {
        Path jar = Files.createTempFile("iris-loader-close", ".jar");
        try {
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry("late-cleanup.txt"));
                output.write("available".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            TestLoader loader = new TestLoader(new URL[]{jar.toUri().toURL()});
            Installer.retainClassLoader(loader);
            try {
                loader.close();
                loader.close();
                try (InputStream input = loader.getResourceAsStream("late-cleanup.txt")) {
                    assertEquals("available", new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            } finally {
                Installer.releaseClassLoader(loader);
            }
            Installer.releaseClassLoader(loader);
            assertEquals(1, loader.closes.get());
            assertNull(loader.getResourceAsStream("late-cleanup.txt"));
        } finally {
            Files.delete(jar);
        }
    }

    @Test
    public void cancellingBeforePaperRequestsCloseDoesNotCloseTheLoader() throws Exception {
        TestLoader loader = new TestLoader(new URL[0]);
        Installer.retainClassLoader(loader);
        Installer.releaseClassLoader(loader);

        assertEquals(0, loader.closes.get());
        loader.close();
        assertEquals(1, loader.closes.get());
    }

    @Test
    public void concurrentReleaseClosesOnceOutsideLifecycleLock() throws Exception {
        TestLoader loader = new TestLoader(new URL[0]);
        loader.closeEntered = new CountDownLatch(1);
        loader.finishClose = new CountDownLatch(1);
        Installer.retainClassLoader(loader);
        loader.close();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> release(loader, failure), "first-loader-release");
        first.start();
        try {
            assertTrue(loader.closeEntered.await(5L, TimeUnit.SECONDS));
            Thread second = new Thread(() -> release(loader, failure), "second-loader-release");
            second.start();
            second.join(5000L);
            assertFalse(second.isAlive());
            assertEquals(1, loader.closes.get());
        } finally {
            loader.finishClose.countDown();
            first.join(5000L);
        }
        assertFalse(first.isAlive());
        assertNull(failure.get());
    }

    private static void release(TestLoader loader, AtomicReference<Throwable> failure) {
        try {
            Installer.releaseClassLoader(loader);
        } catch (Throwable exception) {
            failure.compareAndSet(null, exception);
        }
    }

    private static final class TestLoader extends URLClassLoader {
        private final AtomicInteger closes = new AtomicInteger();
        private CountDownLatch closeEntered;
        private CountDownLatch finishClose;

        private TestLoader(URL[] urls) {
            super(urls, ClassLoader.getPlatformClassLoader());
        }

        @Override
        public void close() throws IOException {
            if (Installer.deferClassLoaderClose(this)) {
                return;
            }
            assertFalse(Thread.holdsLock(Installer.class));
            closes.incrementAndGet();
            if (closeEntered != null) {
                closeEntered.countDown();
                try {
                    if (!finishClose.await(5L, TimeUnit.SECONDS)) {
                        throw new IOException("Loader close was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(exception);
                }
            }
            super.close();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestLoader;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
