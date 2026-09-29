package art.arcane.iris.world.storage;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class DeferredDurabilityTest {
    private static final long NEVER_MILLIS = TimeUnit.HOURS.toMillis(1L);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void syncForcesEachWrittenFileOnceAndThenNothing() throws Exception {
        Path first = temporaryFolder.newFile().toPath();
        Path second = temporaryFolder.newFile().toPath();
        DeferredDurability durability = new DeferredDurability(NEVER_MILLIS, failure -> {
            throw new AssertionError(failure);
        });
        durability.written(first);
        durability.written(second);
        durability.written(first);
        Map<Path, AtomicInteger> forces = new ConcurrentHashMap<>();
        try (MockedStatic<FileChannel> ignored = countingForces(forces, null)) {
            durability.sync();
            assertEquals(Map.of(first, 1, second, 1), snapshot(forces));
            durability.sync();
            assertEquals(Map.of(first, 1, second, 1), snapshot(forces));
        }
    }

    @Test
    public void syncForcesLinkedDirectories() throws Exception {
        assumeTrue(File.separatorChar != '\\');
        Path directory = temporaryFolder.newFolder().toPath();
        DeferredDurability durability = new DeferredDurability(NEVER_MILLIS, failure -> {
            throw new AssertionError(failure);
        });
        durability.linked(directory);
        Map<Path, AtomicInteger> forces = new ConcurrentHashMap<>();
        try (MockedStatic<FileChannel> ignored = countingForces(forces, null)) {
            durability.sync();
        }
        assertEquals(Map.of(directory, 1), snapshot(forces));
    }

    @Test
    public void relaxedModeTracksNothing() throws Exception {
        Path file = temporaryFolder.newFile().toPath();
        String previous = System.getProperty(Durability.MODE_PROPERTY);
        System.setProperty(Durability.MODE_PROPERTY, "relaxed");
        try {
            DeferredDurability durability = new DeferredDurability(NEVER_MILLIS, failure -> {
                throw new AssertionError(failure);
            });
            durability.written(file);
            durability.linked(file.getParent());
            Map<Path, AtomicInteger> forces = new ConcurrentHashMap<>();
            try (MockedStatic<FileChannel> ignored = countingForces(forces, null)) {
                durability.sync();
            }
            assertTrue(forces.isEmpty());
        } finally {
            if (previous == null) {
                System.clearProperty(Durability.MODE_PROPERTY);
            } else {
                System.setProperty(Durability.MODE_PROPERTY, previous);
            }
        }
    }

    @Test
    public void replacedFilesAreSkippedAndFailuresReachTheSyncCaller() throws Exception {
        Path removed = temporaryFolder.newFile().toPath();
        Path failing = temporaryFolder.newFile().toPath();
        DeferredDurability durability = new DeferredDurability(NEVER_MILLIS, failure -> {
            throw new AssertionError(failure);
        });
        durability.written(removed);
        durability.written(failing);
        Files.delete(removed);
        IOException failure = new IOException("force failed");
        Map<Path, AtomicInteger> forces = new ConcurrentHashMap<>();
        try (MockedStatic<FileChannel> ignored = countingForces(forces, path -> path.equals(failing) ? failure : null)) {
            assertSame(failure, assertThrows(IOException.class, durability::sync));
            durability.sync();
        }
        assertEquals(Map.of(failing, 1), snapshot(forces));
    }

    @Test
    public void backgroundFlushRunsAfterTheDelayAndReportsFailures() throws Exception {
        Path directory = temporaryFolder.newFolder().toPath();
        CountDownLatch reported = new CountDownLatch(1);
        AtomicReference<IOException> failure = new AtomicReference<>();
        DeferredDurability durability = new DeferredDurability(10L, background -> {
            failure.set(background);
            reported.countDown();
        });
        durability.written(directory);
        assertTrue(reported.await(5L, TimeUnit.SECONDS));
        assertNotNull(failure.get());
        durability.sync();
    }

    private static Map<Path, Integer> snapshot(Map<Path, AtomicInteger> forces) {
        Map<Path, Integer> values = new ConcurrentHashMap<>();
        forces.forEach((path, count) -> values.put(path, count.get()));
        return values;
    }

    private static MockedStatic<FileChannel> countingForces(Map<Path, AtomicInteger> forces, FailurePlan failures) {
        return mockStatic(FileChannel.class, invocation -> {
            FileChannel source = (FileChannel) invocation.callRealMethod();
            if (invocation.getMethod().getParameterCount() != 2) {
                return source;
            }
            Path path = invocation.getArgument(0);
            FileChannel intercepted = mock(FileChannel.class, delegatesTo(source));
            doAnswer(force -> {
                forces.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                IOException planned = failures == null ? null : failures.failureFor(path);
                if (planned != null) {
                    throw planned;
                }
                source.force(true);
                return null;
            }).when(intercepted).force(true);
            return intercepted;
        });
    }

    @FunctionalInterface
    private interface FailurePlan {
        IOException failureFor(Path path);
    }
}
