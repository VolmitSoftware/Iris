package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HydrologyPlanStoreTest {
    private static final long SEED = 1337L;
    private static final long DAY_SECONDS = 86_400L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void openingAStoreSweepsFilesStagedByEarlierRunsWhateverTheirProcess() throws Exception {
        Path root = folder.newFolder("hydrology").toPath();
        Path scope = Files.createDirectories(root.resolve("scope"));
        List<Path> staged = List.of(
                scope.resolve("w-1-1.bin.gz." + ProcessHandle.current().pid() + ".42.tmp"),
                scope.resolve("w-2-2.bin.gz.0f1e2d3c4b5a69788796a5b4c3d2e1f0.42.tmp"),
                scope.resolve("r-3-3.bin.gz.8675309.tmp"));
        for (Path file : staged) {
            Files.write(file, new byte[]{7, 7, 7});
        }
        Files.write(scope.resolve("w-4-4.bin.gz"), new byte[]{1});

        new HydrologyPlanStore(root, "scope", 2, settings(SEED));

        for (Path file : staged) {
            assertFalse(file + " was staged by an earlier run", Files.exists(file));
        }
        assertTrue(Files.exists(scope.resolve("w-4-4.bin.gz")));
    }

    @Test(timeout = 120_000L)
    public void reopeningAStoreNeverBreaksWritesOfAnEarlierStore() throws Exception {
        HydrologySettings settings = settings(SEED);
        Path root = folder.newFolder("hydrology").toPath();
        HydrologyPlanStore writer = new HydrologyPlanStore(root, "scope", 2, settings);
        WindowPlan plan = window(settings, new WindowKey(1, 1), 1f);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            AtomicBoolean writing = new AtomicBoolean(true);
            Future<Integer> reopening = threads.submit(() -> {
                int opened = 0;
                while (writing.get()) {
                    new HydrologyPlanStore(root, "scope", 2, settings);
                    opened++;
                }
                return opened;
            });
            Future<?> writes = threads.submit(() -> {
                try {
                    for (int write = 0; write < 500; write++) {
                        writer.writeWindow(plan);
                    }
                } finally {
                    writing.set(false);
                }
            });
            writes.get(60, TimeUnit.SECONDS);
            assertTrue(reopening.get(60, TimeUnit.SECONDS) > 0);
        } finally {
            threads.shutdownNow();
        }
        assertEquals(Optional.of(plan), writer.readWindow(new WindowKey(1, 1)));
    }

    @Test
    public void storeWritesAtomicallyAndKeepsTwoScopes() throws Exception {
        HydrologySettings settings = settings(SEED);
        Instant past = Instant.now().minusSeconds(DAY_SECONDS);
        Path root = folder.newFolder("hydrology").toPath();
        oldScope(root, "old-a", past.minusSeconds(3 * 3600L), true);
        oldScope(root, "old-b", past.minusSeconds(2 * 3600L), false);
        oldScope(root, "old-c", past.minusSeconds(3600L), false);
        Files.writeString(root.resolve("notes.txt"), "kept");
        Path orphan = Files.createDirectories(root.resolve("scope-new")).resolve("w-1-1.bin.gz.8675309.tmp");
        Files.write(orphan, new byte[]{7, 7, 7});

        HydrologyPlanStore store = new HydrologyPlanStore(root, "scope-new", 2, settings);
        assertFalse("an unfinished write from an earlier run is swept", Files.exists(orphan));
        assertEquals(Set.of("notes.txt", "old-a", "old-c", "scope-new"), names(root));
        assertEquals("a foreign file keeps its directory, plan files go", Set.of("keep.txt"), names(root.resolve("old-a")));
        assertEquals(Set.of("w-0-0.bin.gz", "r-0-0.bin.gz"), names(root.resolve("old-c")));

        WindowPlan window = window(settings, new WindowKey(-2, 5), 1f);
        RegionPlan region = new RegionPlan(new RegionKey(-1, 0), window.reaches(), window.ponds());
        store.writeWindow(window);
        store.writeRegion(region);
        Path scope = root.resolve("scope-new");
        assertEquals(Set.of("w--2-5.bin.gz", "r--1-0.bin.gz"), names(scope));
        for (String name : names(scope)) {
            byte[] bytes = Files.readAllBytes(scope.resolve(name));
            assertTrue(name + " is gzip", bytes.length > 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B);
        }
        assertEquals(Optional.of(window), store.readWindow(new WindowKey(-2, 5)));
        assertEquals(Optional.of(region), store.readRegion(new RegionKey(-1, 0)));
        assertEquals(Optional.empty(), store.readWindow(new WindowKey(-2, 6)));
        assertEquals(Optional.empty(), store.readRegion(new RegionKey(-2, 5)));

        Files.write(scope.resolve("w-9-9.bin.gz"), "not a plan".getBytes(StandardCharsets.UTF_8));
        assertEquals(Optional.empty(), store.readWindow(new WindowKey(9, 9)));
        Files.copy(scope.resolve("w--2-5.bin.gz"), scope.resolve("w-3-3.bin.gz"));
        assertEquals("a plan stored under the wrong key", Optional.empty(), store.readWindow(new WindowKey(3, 3)));
        byte[] whole = Files.readAllBytes(scope.resolve("w--2-5.bin.gz"));
        Files.write(scope.resolve("w-4-4.bin.gz"), Arrays.copyOf(whole, whole.length / 2));
        assertEquals("a truncated file", Optional.empty(), store.readWindow(new WindowKey(4, 4)));
        HydrologyPlanStore reseeded = new HydrologyPlanStore(root, "scope-new", 2, settings(SEED + 1L));
        assertEquals("a plan from other settings", Optional.empty(), reseeded.readWindow(new WindowKey(-2, 5)));

        WindowKey contested = new WindowKey(7, -7);
        WindowPlan first = window(settings, contested, 1f);
        WindowPlan second = window(settings, contested, 2f);
        store.writeWindow(first);
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            AtomicBoolean writing = new AtomicBoolean(true);
            List<Future<Integer>> readers = new ArrayList<>();
            for (int reader = 0; reader < 4; reader++) {
                readers.add(threads.submit(() -> {
                    int reads = 0;
                    boolean last = false;
                    while (!last) {
                        last = !writing.get();
                        WindowPlan read = store.readWindow(contested).orElseThrow(() -> new AssertionError("a read saw no whole plan"));
                        assertTrue("a read sees a whole plan", read.equals(first) || read.equals(second));
                        reads++;
                    }
                    return reads;
                }));
            }
            List<Future<?>> writers = new ArrayList<>();
            for (int writer = 0; writer < 4; writer++) {
                WindowPlan plan = writer % 2 == 0 ? first : second;
                writers.add(threads.submit(() -> {
                    for (int write = 0; write < 25; write++) {
                        store.writeWindow(plan);
                    }
                }));
            }
            for (Future<?> writer : writers) {
                writer.get(60, TimeUnit.SECONDS);
            }
            writing.set(false);
            int reads = 0;
            for (Future<Integer> reader : readers) {
                reads += reader.get(60, TimeUnit.SECONDS);
            }
            assertTrue("readers ran during the writes: " + reads, reads >= 4);
        } finally {
            threads.shutdownNow();
        }
        assertEquals("no staged files remain", Set.of("w--2-5.bin.gz", "r--1-0.bin.gz", "w-9-9.bin.gz", "w-3-3.bin.gz", "w-4-4.bin.gz",
                "w-7--7.bin.gz"), names(scope));

        Files.setLastModifiedTime(scope, FileTime.from(past.minusSeconds(600L)));
        new HydrologyPlanStore(root, "scope-next", 2, settings);
        assertEquals(Set.of("notes.txt", "old-a", "scope-new", "scope-next"), names(root));
        assertEquals(Set.of("keep.txt"), names(root.resolve("old-a")));
        assertFalse(Files.exists(root.resolve("old-c")));
        Files.setLastModifiedTime(root.resolve("scope-next"), FileTime.from(past.minusSeconds(300L)));
        new HydrologyPlanStore(root, "scope-new", 2, settings);
        assertEquals(Set.of("notes.txt", "old-a", "scope-new", "scope-next"), names(root));
        new HydrologyPlanStore(root, "scope-third", 2, settings);
        assertEquals("reopening scope-new stamped it newer than scope-next", Set.of("notes.txt", "old-a", "scope-new", "scope-third"),
                names(root));
        assertEquals(Set.of("keep.txt"), names(root.resolve("old-a")));
    }

    private static void oldScope(Path root, String name, Instant modified, boolean foreignFile) throws IOException {
        Path directory = Files.createDirectory(root.resolve(name));
        Files.write(directory.resolve("w-0-0.bin.gz"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("r-0-0.bin.gz"), new byte[]{4, 5, 6});
        if (foreignFile) {
            Files.writeString(directory.resolve("keep.txt"), "not ours");
        }
        Files.setLastModifiedTime(directory, FileTime.from(modified));
    }

    private static Set<String> names(Path directory) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> entries = Files.list(directory)) {
            entries.forEach(entry -> names.add(entry.getFileName().toString()));
        }
        return names;
    }

    private static WindowPlan window(HydrologySettings settings, WindowKey key, float width) {
        HydrologyTier tier = settings.tiers().get(0);
        float x = key.wx() * WindowKey.SIZE + 256f;
        float z = key.wz() * WindowKey.SIZE + 256f;
        Reach reach = new Reach(key.wx() * 31L + key.wz(), tier, -1L, new float[]{x, x + 8f, x + 16f}, new float[]{z, z + 1f, z + 2f},
                new int[]{70, 69, 69}, new float[]{width, width, width}, new float[]{1f, 1f, 1f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.CASCADE, ReachStyle.RUN}, 1600f);
        Pond pond = new Pond(key.wz() * 17L - key.wx(), PondKind.NATURAL, 72, 68, (int) x, (int) z, 72.25f,
                new PondMask((int) x, (int) z, 4, 3, 2, new long[]{0b101101L}), reach.id(), 0, "water", (int) x + 4, (int) z);
        return new WindowPlan(key, List.of(reach), List.of(pond), List.of());
    }

    private static HydrologySettings settings(long seed) {
        return HydrologySettings.compile(new IrisHydrology(), seed, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }
}
