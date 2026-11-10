package art.arcane.iris.util.project.hunk.storage;

import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class VolmLibStorageHunkTest {
    private static final int WIDTH = 3;
    private static final int HEIGHT = 4;
    private static final int DEPTH = 5;

    @Test
    public void everyStorageHunkReadsBackWhatWasWritten() {
        assertRoundTrip(new ArrayHunk<Integer>(WIDTH, HEIGHT, DEPTH));
        assertRoundTrip(new AtomicHunk<Integer>(WIDTH, HEIGHT, DEPTH));
        assertRoundTrip(new SynchronizedArrayHunk<Integer>(WIDTH, HEIGHT, DEPTH));
        assertRoundTrip(new AtomicIntegerHunk(WIDTH, HEIGHT, DEPTH));
    }

    @Test
    public void atomicHunksReportAtomic() {
        assertTrue(new AtomicHunk<Integer>(WIDTH, HEIGHT, DEPTH).isAtomic());
        assertTrue(new AtomicDoubleHunk(WIDTH, HEIGHT, DEPTH).isAtomic());
        assertTrue(new AtomicIntegerHunk(WIDTH, HEIGHT, DEPTH).isAtomic());
        assertTrue(new AtomicLongHunk(WIDTH, HEIGHT, DEPTH).isAtomic());
    }

    @Test
    public void filledHunkHoldsTheValueEverywhere() {
        ArrayHunk<Integer> hunk = new ArrayHunk<>(WIDTH, HEIGHT, DEPTH);
        hunk.fill(7);

        assertEquals(Integer.valueOf(7), hunk.getRaw(0, 0, 0));
        assertEquals(Integer.valueOf(7), hunk.getRaw(WIDTH - 1, HEIGHT - 1, DEPTH - 1));
    }

    @Test
    public void emptyHunkIsRejected() {
        assertThrows(RuntimeException.class, () -> new ArrayHunk<Integer>(0, HEIGHT, DEPTH));
    }

    private static void assertRoundTrip(Hunk<Integer> hunk) {
        assertEquals(WIDTH, hunk.getWidth());
        assertEquals(HEIGHT, hunk.getHeight());
        assertEquals(DEPTH, hunk.getDepth());

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    hunk.setRaw(x, y, z, x * 100 + y * 10 + z);
                }
            }
        }

        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    assertEquals(Integer.valueOf(x * 100 + y * 10 + z), hunk.getRaw(x, y, z));
                }
            }
        }
    }
}
