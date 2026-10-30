package art.arcane.iris.generation.validation;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.math.ChunkSpiral;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChunkSpiralScanTest {
    private IrisSettings previousSettings;

    @Before
    public void setUp() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void tearDown() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void singleThreadVisitsTargetsInSpiralOrder() throws Exception {
        List<int[]> targets = ChunkSpiral.centerOut(3, -2, 3);
        List<String> visited = Collections.synchronizedList(new ArrayList<>());
        RecordingListener listener = new RecordingListener();

        int failures = ChunkSpiralScan.scan(targets, 1, (int chunkX, int chunkZ) -> visited.add(chunkX + "," + chunkZ), listener);

        List<String> expected = new ArrayList<>();
        for (int[] target : targets) {
            expected.add(target[0] + "," + target[1]);
        }
        assertEquals(0, failures);
        assertEquals("3,-2", visited.getFirst());
        assertEquals(expected, visited);
        assertEquals(expected, listener.done);
    }

    @Test
    public void parallelScanVisitsEveryTargetOnce() throws Exception {
        List<int[]> targets = ChunkSpiral.centerOut(0, 0, 4);
        List<String> visited = Collections.synchronizedList(new ArrayList<>());
        RecordingListener listener = new RecordingListener();

        int failures = ChunkSpiralScan.scan(targets, 4, (int chunkX, int chunkZ) -> visited.add(chunkX + "," + chunkZ), listener);

        Set<String> unique = new HashSet<>(visited);
        assertEquals(0, failures);
        assertEquals(81, visited.size());
        assertEquals(81, unique.size());
        assertEquals(81, listener.ok.get());
    }

    @Test
    public void failedChunksAreCountedAndReported() throws Exception {
        List<int[]> targets = ChunkSpiral.centerOut(0, 0, 1);
        RecordingListener listener = new RecordingListener();

        int failures = ChunkSpiralScan.scan(targets, 2, (int chunkX, int chunkZ) -> {
            if (chunkX == 1) {
                throw new IllegalStateException("boom " + chunkX + "," + chunkZ);
            }
        }, listener);

        assertEquals(3, failures);
        assertEquals(3, listener.failed.size());
        assertEquals(9, listener.done.size());
        assertEquals(6, listener.ok.get());
        assertTrue(listener.failed.stream().allMatch((String chunk) -> chunk.startsWith("1,")));
    }

    private static final class RecordingListener implements ChunkSpiralScan.Listener {
        private final List<String> done = Collections.synchronizedList(new ArrayList<>());
        private final List<String> failed = Collections.synchronizedList(new ArrayList<>());
        private final AtomicInteger ok = new AtomicInteger();

        @Override
        public void chunkDone(int chunkX, int chunkZ, boolean success) {
            done.add(chunkX + "," + chunkZ);
            if (success) {
                ok.incrementAndGet();
            }
        }

        @Override
        public void chunkFailed(int chunkX, int chunkZ, Throwable error) {
            failed.add(chunkX + "," + chunkZ);
        }
    }
}
