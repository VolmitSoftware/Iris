package art.arcane.iris.world.history;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationPackFingerprintPrefetchTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void mixedFilesAndMetadataMatchTheSerialContentAddressForBothVersions() throws Exception {
        Path pack = temporaryFolder.newFolder("mixed").toPath();
        Map<String, byte[]> included = new TreeMap<>();
        for (int index = 0; index < 70; index++) {
            included.put("objects/entry-" + index + ".bin", new byte[]{(byte) index, 3, 7});
        }
        byte[] large = new byte[300 * 1_024 + 1];
        new Random(1827L).nextBytes(large);
        included.put("objects/large.bin", large);
        included.put("objects/zero.bin", new byte[0]);
        included.put("objects/.nested/resource.bin", new byte[]{17});
        included.put("objects/.DS_Store", new byte[]{42});
        included.put("objects/.nested/.DS_Store", new byte[]{43});
        for (Map.Entry<String, byte[]> entry : included.entrySet()) {
            write(pack.resolve(entry.getKey()), entry.getValue());
        }
        write(pack.resolve(".git/config"), new byte[]{1});
        write(pack.resolve(".hidden"), new byte[]{2});
        write(pack.resolve(".DS_Store"), new byte[]{3});
        write(pack.resolve("objects/ignored.code-workspace"), new byte[]{4});
        write(pack.resolve("workspace.code-workspace"), new byte[]{5});

        assertEquals(serialFingerprint(included), GenerationPackFingerprint.compute(pack, 1));
        included.remove("objects/.DS_Store");
        included.remove("objects/.nested/.DS_Store");
        assertEquals(serialFingerprint(included), GenerationPackFingerprint.compute(pack, 2));
    }

    @Test
    public void fewerThanSixtyFourSmallFilesStayOnTheCallingThread() throws Exception {
        Path pack = smallPack("small", 63);
        Set<Thread> readers = ConcurrentHashMap.newKeySet();
        Thread caller = Thread.currentThread();

        GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
            readers.add(Thread.currentThread());
            return GenerationPackFingerprint.openInput(path);
        });

        assertEquals(Set.of(caller), readers);
    }

    @Test
    public void largeFilesStreamOnTheCallerAndSmallReadsAreBounded() throws Exception {
        requireParallelReaders();
        Path pack = smallPack("parallel", 64);
        Path large = pack.resolve("large.bin");
        Files.write(large, new byte[256 * 1_024 + 1]);
        AtomicReference<Thread> largeReader = new AtomicReference<>();
        Set<Thread> smallReaders = ConcurrentHashMap.newKeySet();
        Thread caller = Thread.currentThread();

        GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
            if (path.equals(large)) {
                largeReader.set(Thread.currentThread());
            } else {
                smallReaders.add(Thread.currentThread());
            }
            return GenerationPackFingerprint.openInput(path);
        });

        assertSame(caller, largeReader.get());
        assertFalse(smallReaders.contains(caller));
        assertFalse(smallReaders.isEmpty());
        assertTrue(smallReaders.size() <= 4);
        assertStopped(smallReaders);
    }

    @Test
    public void shortAndGrowingFilesFailAndCloseEveryStartedInput() throws Exception {
        requireParallelReaders();
        for (int changedSize : new int[]{0, 2}) {
            Path pack = smallPack("changed-" + changedSize, 64);
            AtomicInteger active = new AtomicInteger();
            Set<Thread> readers = ConcurrentHashMap.newKeySet();
            IOException failure = assertThrows(IOException.class, () -> GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
                readers.add(Thread.currentThread());
                if (path.getFileName().toString().equals("000.bin")) {
                    Files.write(path, new byte[changedSize]);
                }
                return tracked(GenerationPackFingerprint.openInput(path), active);
            }));

            assertTrue(failure.getMessage().contains("changed while fingerprinting"));
            assertEquals(0, active.get());
            assertStopped(readers);
        }
    }

    @Test
    public void leafReplacedByASymlinkAfterCollectionIsRejected() throws Exception {
        requireParallelReaders();
        Path pack = smallPack("replaced", 64);
        Path outside = temporaryFolder.newFile("outside.bin").toPath();
        Files.write(outside, new byte[]{99});
        Path probe = temporaryFolder.getRoot().toPath().resolve("symlink-probe");
        try {
            Files.createSymbolicLink(probe, outside);
        } catch (IOException | UnsupportedOperationException failure) {
            Assume.assumeNoException(failure);
        }
        Files.delete(probe);

        assertThrows(IOException.class, () -> GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
            if (path.getFileName().toString().equals("000.bin")) {
                Files.delete(path);
                Files.createSymbolicLink(path, outside);
            }
            return GenerationPackFingerprint.openInput(path);
        }));
    }

    @Test
    public void firstOrderedReadFailureWaitsForEveryStartedInputToClose() throws Exception {
        requireParallelReaders();
        Path pack = smallPack("failure", 64);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        Set<Thread> readers = ConcurrentHashMap.newKeySet();
        IOException expected = new IOException("first ordered failure");
        IOException later = new IOException("later ordered failure");
        CompletableFuture<Throwable> completion = new CompletableFuture<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
                    readers.add(Thread.currentThread());
                    String name = path.getFileName().toString();
                    if (name.equals("000.bin")) {
                        await(blocked);
                        failed.countDown();
                        throw expected;
                    }
                    if (name.equals("001.bin")) {
                        return tracked(blockingInput(blocked, release, null), active);
                    }
                    if (name.equals("002.bin")) {
                        throw later;
                    }
                    return tracked(GenerationPackFingerprint.openInput(path), active);
                });
                completion.complete(null);
            } catch (Throwable failure) {
                completion.complete(failure);
            }
        });
        try {
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            caller.join(5_000L);
        }

        assertFalse(caller.isAlive());
        assertSame(expected, completion.get(5, TimeUnit.SECONDS));
        assertEquals(1, expected.getSuppressed().length);
        assertSame(later, expected.getSuppressed()[0]);
        assertEquals(0, active.get());
        assertStopped(readers);
    }

    @Test
    public void interruptedCallerDrainsStartedReadsAndRetainsItsInterruptFlag() throws Exception {
        requireParallelReaders();
        Path pack = smallPack("interrupted", 64);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch interruptedReader = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicBoolean interruptPreserved = new AtomicBoolean();
        Set<Thread> readers = ConcurrentHashMap.newKeySet();
        CompletableFuture<Throwable> completion = new CompletableFuture<>();
        Thread caller = Thread.ofPlatform().start(() -> {
            try {
                GenerationPackFingerprint.computeWithInput(pack, 2, path -> {
                    readers.add(Thread.currentThread());
                    if (path.getFileName().toString().equals("000.bin")) {
                        return tracked(blockingInput(blocked, release, interruptedReader), active);
                    }
                    return tracked(GenerationPackFingerprint.openInput(path), active);
                });
                completion.complete(null);
            } catch (Throwable failure) {
                interruptPreserved.set(Thread.currentThread().isInterrupted());
                completion.complete(failure);
            }
        });
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue(interruptedReader.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> completion.get(100, TimeUnit.MILLISECONDS));
        } finally {
            release.countDown();
            caller.join(5_000L);
        }

        assertFalse(caller.isAlive());
        assertTrue(completion.get(5, TimeUnit.SECONDS) instanceof IOException);
        assertTrue(interruptPreserved.get());
        assertEquals(0, active.get());
        assertStopped(readers);
    }

    private Path smallPack(String name, int count) throws IOException {
        Path pack = temporaryFolder.newFolder(name).toPath();
        for (int index = 0; index < count; index++) {
            Files.write(pack.resolve(String.format("%03d.bin", index)), new byte[]{1});
        }
        return pack;
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static String serialFingerprint(Map<String, byte[]> entries) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            byte[] name = entry.getKey().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(name.length).array());
            digest.update(name);
            digest.update(ByteBuffer.allocate(8).putLong(entry.getValue().length).array());
            digest.update(entry.getValue());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static InputStream tracked(InputStream input, AtomicInteger active) {
        active.incrementAndGet();
        return new FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    active.decrementAndGet();
                }
            }
        };
    }

    private static InputStream blockingInput(CountDownLatch blocked, CountDownLatch release,
                                             CountDownLatch interrupted) {
        return new ByteArrayInputStream(new byte[]{1}) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                blocked.countDown();
                boolean wasInterrupted = false;
                while (true) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException failure) {
                        wasInterrupted = true;
                        if (interrupted != null) {
                            interrupted.countDown();
                        }
                    }
                }
                if (wasInterrupted) {
                    Thread.currentThread().interrupt();
                }
                return super.read(bytes, offset, length);
            }
        };
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for the concurrent reader");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException(failure);
        }
    }

    private static void assertStopped(Set<Thread> readers) throws InterruptedException {
        for (Thread reader : readers) {
            reader.join(5_000L);
            assertFalse("Fingerprint reader did not stop: " + reader.getName(), reader.isAlive());
        }
    }

    private static void requireParallelReaders() {
        Assume.assumeTrue(Runtime.getRuntime().availableProcessors() > 1);
    }
}
