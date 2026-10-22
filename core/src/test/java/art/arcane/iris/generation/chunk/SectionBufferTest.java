package art.arcane.iris.generation.chunk;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SectionBufferTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private NativeBlockState air;

    @Before
    public void bindPlatform() {
        air = mock(NativeBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(air);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void columnTopTracksTheHighestStoredValueAndIgnoresNulls() {
        SectionBuffer buffer = new SectionBuffer(64);
        NativeBlockState stone = mock(NativeBlockState.class);

        assertEquals(-1, buffer.highestStoredY(3, 7));
        buffer.setRaw(3, 40, 7, null);
        assertEquals(-1, buffer.highestStoredY(3, 7));
        buffer.setRaw(3, 12, 7, stone);
        buffer.setRaw(3, 30, 7, stone);
        buffer.setRaw(3, 5, 7, stone);

        assertEquals(30, buffer.highestStoredY(3, 7));
        assertEquals(-1, buffer.highestStoredY(7, 3));
        assertSame(stone, buffer.getStoredRaw(3, 30, 7));
        assertNull(buffer.getStoredRaw(3, 31, 7));
        assertEquals(30, ColumnExtent.highestStoredY(new ColumnExtentListeningHunk<>(buffer, (x, y, z, state) -> {
        }), 3, 7));
    }

    @Test
    public void unwrittenAndOutOfRangeCellsReadAsAir() {
        SectionBuffer buffer = new SectionBuffer(48);
        NativeBlockState stone = mock(NativeBlockState.class);
        buffer.setRaw(2, 20, 9, stone);

        assertSame(air, buffer.getRaw(2, 21, 9));
        assertSame(air, buffer.getRaw(2, 3, 9));
        assertSame(air, buffer.getRaw(2, -1, 9));
        assertSame(air, buffer.getRaw(2, 48, 9));
        assertSame(stone, buffer.getRaw(2, 20, 9));
        assertNull(buffer.getStoredRaw(2, 21, 9));
        assertNull(buffer.getStoredRaw(2, 3, 9));
    }

    @Test
    public void paletteKeepsEveryDistinctInstance() {
        SectionBuffer buffer = new SectionBuffer(32);
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        when(first.matches(second)).thenReturn(true);
        when(second.matches(first)).thenReturn(true);

        buffer.setRaw(0, 0, 0, first);
        buffer.setRaw(1, 0, 0, second);
        buffer.setRaw(2, 0, 0, first);

        assertSame(first, buffer.getRaw(0, 0, 0));
        assertSame(second, buffer.getRaw(1, 0, 0));
        assertSame(first, buffer.getRaw(2, 0, 0));
        assertEquals(buffer.paletteId(first), buffer.paletteId(first));
        assertNotEquals(buffer.paletteId(first), buffer.paletteId(second));
        assertEquals(0, buffer.paletteId(null));
        NativeBlockState[] palette = buffer.palette();
        assertEquals(3, palette.length);
        assertNull(palette[0]);
        assertSame(first, palette[buffer.paletteId(first)]);
        assertSame(second, palette[buffer.paletteId(second)]);
    }

    @Test
    public void sectionsUseTheYzxLayoutAndStayUnallocatedUntilWritten() {
        SectionBuffer buffer = new SectionBuffer(64);
        NativeBlockState stone = mock(NativeBlockState.class);
        NativeBlockState dirt = mock(NativeBlockState.class);
        buffer.setRaw(5, 37, 11, stone);
        buffer.setRaw(15, 47, 15, dirt);

        assertEquals(4, buffer.sectionCount());
        assertNull(buffer.section(0));
        assertNull(buffer.section(1));
        assertNull(buffer.section(3));
        short[] section = buffer.section(2);
        assertEquals(4096, section.length);
        assertEquals(buffer.paletteId(stone), section[(5 << 8) | (11 << 4) | 5]);
        assertEquals(buffer.paletteId(dirt), section[(15 << 8) | (15 << 4) | 15]);
        int written = 0;
        for (short id : section) {
            written += id == 0 ? 0 : 1;
        }
        assertEquals(2, written);
    }

    @Test
    public void columnRunsMatchRepeatedSingleWrites() {
        Random random = new Random(41L);
        List<NativeBlockState> pool = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            pool.add(mock(NativeBlockState.class));
        }
        pool.add(null);
        for (int trial = 0; trial < 40; trial++) {
            int height = 16 * (1 + random.nextInt(12));
            SectionBuffer runs = new SectionBuffer(height);
            SectionBuffer cells = new SectionBuffer(height);
            for (int write = 0; write < 300; write++) {
                int x = random.nextInt(16);
                int z = random.nextInt(16);
                int from = random.nextInt(height);
                int to = Math.min(height - 1, from + random.nextInt(70));
                NativeBlockState state = pool.get(random.nextInt(pool.size()));
                switch (random.nextInt(3)) {
                    case 0 -> runs.fillColumnRun(x, z, from, to, state);
                    case 1 -> runs.fillColumnRun(x, z, from, to, runs.paletteId(state));
                    default -> ColumnRunHunk.fillColumnRun(runs, x, z, from, to, state);
                }
                for (int y = to; y >= from; y--) {
                    cells.setRaw(x, y, z, state);
                }
            }
            assertSameContents(cells, runs);
        }
    }

    @Test
    public void runsWithAnEmptyRangeOrNoStateWriteNothing() {
        SectionBuffer buffer = new SectionBuffer(32);
        NativeBlockState stone = mock(NativeBlockState.class);
        buffer.fillColumnRun(1, 1, 10, 9, stone);
        buffer.fillColumnRun(1, 1, 3, 9, (NativeBlockState) null);
        buffer.fillColumnRun(1, 1, 3, 9, 0);

        assertEquals(-1, buffer.highestStoredY(1, 1));
        assertNull(buffer.section(0));
        assertEquals(1, buffer.palette().length);
    }

    @Test
    public void concurrentWritersNeverLoseTheColumnMaximum() throws InterruptedException {
        for (int trial = 0; trial < 20; trial++) {
            SectionBuffer buffer = new SectionBuffer(768);
            NativeBlockState stone = mock(NativeBlockState.class);
            int threads = 8;
            runConcurrently(threads, thread -> {
                for (int y = thread; y < 768; y += threads) {
                    buffer.setRaw(9, y, 4, stone);
                }
            });
            assertEquals(767, buffer.highestStoredY(9, 4));
        }
    }

    @Test
    public void concurrentPaletteGrowthKeepsOneIdPerInstance() throws InterruptedException {
        int threads = 8;
        int statesPerThread = 300;
        NativeBlockState[][] states = new NativeBlockState[threads][statesPerThread];
        NativeBlockState[] shared = new NativeBlockState[40];
        for (int index = 0; index < shared.length; index++) {
            shared[index] = mock(NativeBlockState.class);
        }
        for (int thread = 0; thread < threads; thread++) {
            for (int index = 0; index < statesPerThread; index++) {
                states[thread][index] = mock(NativeBlockState.class);
            }
        }
        for (int trial = 0; trial < 10; trial++) {
            SectionBuffer buffer = new SectionBuffer(384);
            runConcurrently(threads, thread -> {
                for (int index = 0; index < statesPerThread; index++) {
                    int x = thread * 2 + (index & 1);
                    int z = (index >> 1) & 15;
                    int y = index;
                    buffer.setRaw(x, y, z, states[thread][index]);
                    buffer.setRaw(x, y + 1, z, shared[index % shared.length]);
                }
            });
            for (int thread = 0; thread < threads; thread++) {
                for (int index = 0; index < statesPerThread; index++) {
                    int x = thread * 2 + (index & 1);
                    int z = (index >> 1) & 15;
                    assertSame(states[thread][index], buffer.getStoredRaw(x, index, z));
                    assertSame(shared[index % shared.length], buffer.getRaw(x, index + 1, z));
                }
            }
            assertEquals(1 + threads * statesPerThread + shared.length, buffer.palette().length);
            for (NativeBlockState state : shared) {
                assertSame(state, buffer.palette()[buffer.paletteId(state)]);
            }
        }
    }

    @Test
    public void listeningHunksForwardRunsInTopDownOrder() {
        SectionBuffer buffer = new SectionBuffer(32);
        NativeBlockState stone = mock(NativeBlockState.class);
        List<Integer> heard = new ArrayList<>();
        ColumnExtentListeningHunk<NativeBlockState> listening = new ColumnExtentListeningHunk<>(buffer,
                (x, y, z, state) -> heard.add(y));

        ColumnRunHunk.fillColumnRun(listening, 4, 6, 10, 13, stone);

        assertEquals(List.of(13, 12, 11, 10), heard);
        for (int y = 10; y <= 13; y++) {
            assertSame(stone, buffer.getStoredRaw(4, y, 6));
        }
        assertNull(buffer.getStoredRaw(4, 9, 6));
        assertEquals(13, buffer.highestStoredY(4, 6));
    }

    @Test
    public void plainHunksReceiveRunsAsTopDownSingleWrites() {
        Hunk<NativeBlockState> array = Hunk.newArrayHunk(16, 32, 16);
        NativeBlockState stone = mock(NativeBlockState.class);
        array.setRaw(2, 5, 2, stone);
        List<Integer> order = new ArrayList<>();
        Hunk<NativeBlockState> recording = new ColumnExtentListeningHunk<>(array, (x, y, z, state) -> order.add(y));

        ColumnRunHunk.fillColumnRun(Hunk.newArrayHunk(16, 32, 16), 0, 0, 0, 3, stone);
        ColumnRunHunk.fillColumnRun(array, 2, 2, 4, 6, null);
        ColumnRunHunk.fillColumnRun(recording, 1, 1, 7, 9, stone);

        assertNull(array.getRaw(2, 5, 2));
        assertSame(stone, array.getRaw(1, 8, 1));
        assertEquals(List.of(9, 8, 7), order);
    }

    private static void assertSameContents(SectionBuffer expected, SectionBuffer actual) {
        assertEquals(expected.sectionCount(), actual.sectionCount());
        NativeBlockState[] expectedPalette = expected.palette();
        NativeBlockState[] actualPalette = actual.palette();
        for (int sectionIndex = 0; sectionIndex < expected.sectionCount(); sectionIndex++) {
            short[] expectedSection = expected.section(sectionIndex);
            short[] actualSection = actual.section(sectionIndex);
            if (expectedSection == null) {
                assertNull(actualSection);
                continue;
            }
            NativeBlockState[] expectedStates = new NativeBlockState[4096];
            NativeBlockState[] actualStates = new NativeBlockState[4096];
            for (int index = 0; index < 4096; index++) {
                expectedStates[index] = expectedPalette[expectedSection[index] & 0xFFFF];
                actualStates[index] = actualPalette[actualSection[index] & 0xFFFF];
            }
            assertArrayEquals(expectedStates, actualStates);
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(expected.highestStoredY(x, z), actual.highestStoredY(x, z));
            }
        }
        assertEquals(Arrays.stream(expectedPalette).filter(state -> state != null).distinct().count(),
                Arrays.stream(actualPalette).filter(state -> state != null).distinct().count());
    }

    private static void runConcurrently(int threads, ThreadTask task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int index = thread;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                task.run(index);
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
    }

    @FunctionalInterface
    private interface ThreadTask {
        void run(int thread);
    }
}
