package art.arcane.iris.generation.hydrology.grid;

import java.util.Arrays;

public final class CellHeap {
    private long[] keys;
    private int size;

    public CellHeap(int capacity) {
        keys = new long[Math.max(1, capacity)];
    }

    public static int sortable(float value) {
        int bits = Float.floatToIntBits(value);
        return bits ^ ((bits >> 31) & 0x7fffffff);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void push(float priority, int cell) {
        if (size == keys.length) {
            keys = Arrays.copyOf(keys, size * 2);
        }
        long key = ((long) sortable(priority) << 32) | (cell & 0xffffffffL);
        int index = size++;
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (keys[parent] <= key) {
                break;
            }
            keys[index] = keys[parent];
            index = parent;
        }
        keys[index] = key;
    }

    public int pop() {
        long top = keys[0];
        long last = keys[--size];
        int index = 0;
        int half = size >>> 1;
        while (index < half) {
            int child = 2 * index + 1;
            if (child + 1 < size && keys[child + 1] < keys[child]) {
                child++;
            }
            if (last <= keys[child]) {
                break;
            }
            keys[index] = keys[child];
            index = child;
        }
        if (size > 0) {
            keys[index] = last;
        }
        return (int) top;
    }
}
