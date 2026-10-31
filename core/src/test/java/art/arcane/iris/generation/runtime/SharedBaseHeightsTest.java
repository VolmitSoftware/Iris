package art.arcane.iris.generation.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SharedBaseHeightsTest {
    @Test
    public void storedColumnsComeBackWithTheirExactBits() {
        SharedBaseHeights cache = new SharedBaseHeights(1 << 10);
        double height = Math.nextUp(71.25D);

        assertTrue(Double.isNaN(cache.get(12, -40)));
        cache.put(12, -40, height);

        assertEquals(Double.doubleToRawLongBits(height), Double.doubleToRawLongBits(cache.get(12, -40)));
        assertTrue(Double.isNaN(cache.get(-40, 12)));
    }

    @Test
    public void originMissesUntilItIsStored() {
        SharedBaseHeights cache = new SharedBaseHeights(2);

        assertTrue(Double.isNaN(cache.get(0, 0)));
        cache.put(0, 0, -0D);
        assertEquals(Double.doubleToRawLongBits(-0D), Double.doubleToRawLongBits(cache.get(0, 0)));
    }

    @Test
    public void nonFiniteHeightsAreNeverStored() {
        SharedBaseHeights cache = new SharedBaseHeights(2);
        cache.put(3, 4, 64D);
        cache.put(3, 4, Double.NaN);
        cache.put(3, 4, Double.POSITIVE_INFINITY);

        assertEquals(64D, cache.get(3, 4), 0D);
    }

    @Test
    public void fullCoordinateDomainKeepsDistinctColumns() {
        int[] coordinates = {0, 1, -1, 1 << 21, -(1 << 21), 30_000_000, -30_000_000,
                Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int x : coordinates) {
            for (int z : coordinates) {
                SharedBaseHeights cache = new SharedBaseHeights(2);
                cache.put(x, z, 12D);
                for (int otherX : coordinates) {
                    for (int otherZ : coordinates) {
                        if (x == otherX && z == otherZ) {
                            assertEquals(12D, cache.get(otherX, otherZ), 0D);
                        } else {
                            assertTrue(Double.isNaN(cache.get(otherX, otherZ)));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void aCollidingColumnReplacesTheOldOne() {
        SharedBaseHeights cache = new SharedBaseHeights(2);
        cache.put(5, 9, 40D);
        cache.put(6, 9, 41D);
        cache.put(7, 9, 42D);

        int stored = 0;
        for (int x = 5; x <= 7; x++) {
            double value = cache.get(x, 9);
            if (!Double.isNaN(value)) {
                assertEquals(35D + x, value, 0D);
                stored++;
            }
        }
        assertTrue(stored >= 1 && stored <= 2);
    }

    @Test
    public void capacityFollowsTheHeapWithinBounds() {
        assertEquals(SharedBaseHeights.MINIMUM_CAPACITY, SharedBaseHeights.capacityFor(0L));
        assertEquals(SharedBaseHeights.MAXIMUM_CAPACITY, SharedBaseHeights.capacityFor(Long.MAX_VALUE));
        int capacity = SharedBaseHeights.capacityFor(6L << 30);
        assertEquals(1, Integer.bitCount(capacity));
        assertTrue(capacity >= SharedBaseHeights.MINIMUM_CAPACITY && capacity <= SharedBaseHeights.MAXIMUM_CAPACITY);
    }
}
