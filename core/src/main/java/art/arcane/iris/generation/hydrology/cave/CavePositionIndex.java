package art.arcane.iris.generation.hydrology.cave;

import java.util.Collection;

public final class CavePositionIndex {
    private static final int DEFAULT_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private int[] xCoordinates;
    private int[] yCoordinates;
    private int[] zCoordinates;
    private boolean[] occupied;
    private int mask;
    private int resizeThreshold;
    private int size;

    public CavePositionIndex() {
        initialize(DEFAULT_CAPACITY);
    }

    public CavePositionIndex(int expectedSize) {
        initialize(capacityFor(expectedSize));
    }

    public static CavePositionIndex copyOf(Collection<CavePosition> positions) {
        CavePositionIndex index = new CavePositionIndex(positions.size());
        for (CavePosition position : positions) {
            index.add(position.x(), position.y(), position.z());
        }
        return index;
    }

    public boolean add(int x, int y, int z) {
        int slot = findSlot(x, y, z);
        if (occupied[slot]) {
            return false;
        }
        occupied[slot] = true;
        xCoordinates[slot] = x;
        yCoordinates[slot] = y;
        zCoordinates[slot] = z;
        size++;
        if (size > resizeThreshold) {
            resize();
        }
        return true;
    }

    public boolean contains(int x, int y, int z) {
        return occupied[findSlot(x, y, z)];
    }

    private void initialize(int capacity) {
        xCoordinates = new int[capacity];
        yCoordinates = new int[capacity];
        zCoordinates = new int[capacity];
        occupied = new boolean[capacity];
        mask = capacity - 1;
        resizeThreshold = capacity - (capacity >>> 2);
        size = 0;
    }

    private int findSlot(int x, int y, int z) {
        int slot = hash(x, y, z) & mask;
        while (occupied[slot]
                && (xCoordinates[slot] != x || yCoordinates[slot] != y || zCoordinates[slot] != z)) {
            slot = slot + 1 & mask;
        }
        return slot;
    }

    private void resize() {
        if (occupied.length == MAXIMUM_CAPACITY) {
            resizeThreshold = Integer.MAX_VALUE;
            return;
        }
        int[] previousXCoordinates = xCoordinates;
        int[] previousYCoordinates = yCoordinates;
        int[] previousZCoordinates = zCoordinates;
        boolean[] previousOccupied = occupied;
        initialize(occupied.length << 1);
        for (int index = 0; index < previousOccupied.length; index++) {
            if (previousOccupied[index]) {
                add(previousXCoordinates[index], previousYCoordinates[index], previousZCoordinates[index]);
            }
        }
    }

    private static int capacityFor(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("Expected position count must not be negative.");
        }
        long requiredCapacity = Math.max(DEFAULT_CAPACITY, ((long) expectedSize * 4L + 2L) / 3L);
        if (requiredCapacity >= MAXIMUM_CAPACITY) {
            return MAXIMUM_CAPACITY;
        }
        return Integer.highestOneBit((int) requiredCapacity - 1) << 1;
    }

    private static int hash(int x, int y, int z) {
        int hash = 0x811C9DC5;
        hash = (hash ^ x) * 0x01000193;
        hash = (hash ^ y) * 0x01000193;
        hash = (hash ^ z) * 0x01000193;
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        return hash ^ hash >>> 16;
    }
}
