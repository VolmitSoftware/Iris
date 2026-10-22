package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class MutationLog {
    private static final byte CUSTOM = 1;
    private static final byte CANOPY = 2;

    private int size;
    private int[] x = new int[32];
    private int[] y = new int[32];
    private int[] z = new int[32];
    private Object[] values = new Object[32];
    private byte[] flags = new byte[32];

    static MutationLog of(List<ObjectDestinationTransaction.Mutation> mutations) {
        MutationLog log = new MutationLog();
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            Objects.requireNonNull(mutation, "mutation");
            ObjectDestinationTransaction.DataKey key = mutation.key();
            switch (mutation) {
                case ObjectDestinationTransaction.SetMutation set -> {
                    Object value = Objects.requireNonNull(set.value(), "mutation value");
                    if (ObjectSourcePlan.typeOf(value) != key.type()) {
                        throw new IllegalArgumentException("Object mutation type " + key.type().getName()
                                + " does not match its value " + value.getClass().getName());
                    }
                    log.add(key.x(), key.y(), key.z(), value, false, set.canopy());
                }
                case ObjectDestinationTransaction.CustomBlockMutation block -> log.add(key.x(), key.y(), key.z(),
                        Objects.requireNonNull(block.state(), "custom block state"), true, block.canopy());
            }
        }
        return log;
    }

    void add(int blockX, int blockY, int blockZ, Object value, boolean custom, boolean canopy) {
        if (size == values.length) {
            int capacity = size * 2;
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            values = Arrays.copyOf(values, capacity);
            flags = Arrays.copyOf(flags, capacity);
        }
        x[size] = blockX;
        y[size] = blockY;
        z[size] = blockZ;
        values[size] = value;
        flags[size] = (byte) ((custom ? CUSTOM : 0) | (canopy ? CANOPY : 0));
        size++;
    }

    int size() {
        return size;
    }

    int x(int index) {
        return x[index];
    }

    int y(int index) {
        return y[index];
    }

    int z(int index) {
        return z[index];
    }

    Object value(int index) {
        return values[index];
    }

    boolean custom(int index) {
        return (flags[index] & CUSTOM) != 0;
    }

    boolean canopy(int index) {
        return (flags[index] & CANOPY) != 0;
    }

    Class<?> type(int index) {
        return custom(index) ? NativeBlockState.class : ObjectSourcePlan.typeOf(values[index]);
    }
}
