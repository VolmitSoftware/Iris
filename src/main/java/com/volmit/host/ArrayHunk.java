package com.volmit.host;

import com.volmit.hunk.Hunk;
import java.util.Arrays;
import java.util.Objects;

/** Reference dense volume with checked local coordinates and non-null values.
 * Each invocation owns its volume; concurrent writes to the same volume are unsupported.
 */
public final class ArrayHunk<T> implements Hunk<T> {
    private final Class<T> type;
    private final int width;
    private final int height;
    private final int depth;
    private final Object[] values;

    public ArrayHunk(Class<T> type, int width, int height, int depth, T initial) {
        this.type = Objects.requireNonNull(type, "type");
        if (type.isPrimitive()) {
            throw new IllegalArgumentException("Hunk requires a reference type");
        }
        validateDimensions(width, height, depth);

        this.width = width;
        this.height = height;
        this.depth = depth;
        values = new Object[valueCount(width, height, depth)];
        Arrays.fill(values, type.cast(Objects.requireNonNull(initial, "initial")));
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public T get(int x, int y, int z) {
        return type.cast(values[index(x, y, z)]);
    }

    public void set(int x, int y, int z, T value) {
        int valueIndex = index(x, y, z);
        values[valueIndex] = type.cast(Objects.requireNonNull(value, "value"));
    }

    private static void validateDimensions(int width, int height, int depth) {
        if (width < 0) {
            throw negativeDimension();
        }
        if (height < 0) {
            throw negativeDimension();
        }
        if (depth < 0) {
            throw negativeDimension();
        }
    }

    private static IllegalArgumentException negativeDimension() {
        return new IllegalArgumentException("Negative hunk dimension");
    }

    private static int valueCount(int width, int height, int depth) {
        return Math.multiplyExact(Math.multiplyExact(width, height), depth);
    }

    private int index(int x, int y, int z) {
        if (x < 0 || x >= width) {
            throw outOfBounds(x, y, z);
        }
        if (y < 0 || y >= height) {
            throw outOfBounds(x, y, z);
        }
        if (z < 0 || z >= depth) {
            throw outOfBounds(x, y, z);
        }
        return (y * depth + z) * width + x;
    }

    private static IndexOutOfBoundsException outOfBounds(int x, int y, int z) {
        return new IndexOutOfBoundsException("Local hunk position: " + x + "," + y + "," + z);
    }
}
