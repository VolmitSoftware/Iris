package art.arcane.iris.engine.terrain;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerArray;

final class Terrain3DFragmentFilter {
    private static final int MAXIMUM_FRAGMENT_VOLUME = 512;
    private static final int VISITED_CAPACITY = 2048;
    private static final int UNKNOWN = 0;
    private static final int KEEP = 1;
    private static final int REMOVE = 2;

    // Static on purpose: a per-instance ThreadLocal whose value was an inner class kept every
    // retired filter (and the runtime and stream caches behind it) reachable from each worker
    // thread that had ever classified for it. The scratch holds primitives only between calls.
    private static final ThreadLocal<Traversal> TRAVERSALS = ThreadLocal.withInitial(Traversal::new);
    private final ColumnSource source;

    Terrain3DFragmentFilter(ColumnSource source) {
        this.source = Objects.requireNonNull(source, "Raw density columns");
    }

    Terrain3DColumn column(int x, int z) {
        DensityColumn density = source.column(x, z);
        Terrain3DColumn resolved = density.resolved;
        if (resolved != null) {
            return resolved;
        }
        Terrain3DColumn raw = density.raw;
        int removed = 0;
        for (int span = 1; span < raw.spanCount(); span++) {
            if (density.state(span) == UNKNOWN) {
                TRAVERSALS.get().classify(this, x, z, density, span);
            }
            if (density.state(span) == REMOVE) {
                removed++;
            }
        }
        resolved = removed == 0 ? raw : retainSpans(density, removed);
        density.resolved = resolved;
        return resolved;
    }

    void clear() {
        TRAVERSALS.remove();
    }

    private Terrain3DColumn retainSpans(DensityColumn density, int removed) {
        Terrain3DColumn raw = density.raw;
        int[] boundaries = new int[(raw.spanCount() - removed) * 2];
        int index = 0;
        for (int span = 0; span < raw.spanCount(); span++) {
            if (density.state(span) != REMOVE) {
                boundaries[index++] = raw.ceiling(span);
                boundaries[index++] = raw.floor(span);
            }
        }
        return new Terrain3DColumn(raw.baseHeight(), raw.minY(), raw.shaped(), boundaries);
    }

    @FunctionalInterface
    interface ColumnSource {
        DensityColumn column(int x, int z);
    }

    static final class DensityColumn {
        private final Terrain3DColumn raw;
        private final AtomicIntegerArray states;
        private volatile Terrain3DColumn resolved;

        DensityColumn(Terrain3DColumn raw) {
            this.raw = Objects.requireNonNull(raw, "Raw density column");
            states = raw.spanCount() == 1 ? null : new AtomicIntegerArray(raw.spanCount() - 1);
            resolved = states == null ? raw : null;
        }

        private int state(int span) {
            return span == 0 ? KEEP : states.get(span - 1);
        }
    }

    private static final class Traversal {
        private final int[] xs = new int[MAXIMUM_FRAGMENT_VOLUME + 1];
        private final int[] zs = new int[MAXIMUM_FRAGMENT_VOLUME + 1];
        private final int[] spans = new int[MAXIMUM_FRAGMENT_VOLUME + 1];
        private final DensityColumn[] columns = new DensityColumn[MAXIMUM_FRAGMENT_VOLUME + 1];
        private final int[] visited = new int[VISITED_CAPACITY];
        private final int[] visitedSlots = new int[MAXIMUM_FRAGMENT_VOLUME + 1];
        private int count;
        private int volume;
        private int decision;

        private void classify(Terrain3DFragmentFilter filter, int x, int z, DensityColumn density, int span) {
            try {
                visit(x, z, density, span);
                for (int cursor = 0; cursor < count && decision == UNKNOWN; cursor++) {
                    Terrain3DColumn raw = columns[cursor].raw;
                    int bottom = raw.ceiling(spans[cursor]);
                    int top = raw.floor(spans[cursor]);
                    inspect(filter, (long) xs[cursor] - 1, zs[cursor], bottom, top);
                    inspect(filter, (long) xs[cursor] + 1, zs[cursor], bottom, top);
                    inspect(filter, xs[cursor], (long) zs[cursor] - 1, bottom, top);
                    inspect(filter, xs[cursor], (long) zs[cursor] + 1, bottom, top);
                }
                int result = decision == UNKNOWN ? REMOVE : decision;
                for (int index = 0; index < count; index++) {
                    columns[index].states.set(spans[index] - 1, result);
                }
            } finally {
                for (int index = 0; index < count; index++) {
                    visited[visitedSlots[index]] = 0;
                }
                Arrays.fill(columns, 0, count, null);
                count = 0;
                volume = 0;
                decision = UNKNOWN;
            }
        }

        private void inspect(Terrain3DFragmentFilter filter, long x, long z, int bottom, int top) {
            if (decision != UNKNOWN) {
                return;
            }
            if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE
                    || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
                decision = KEEP;
                return;
            }
            DensityColumn density = filter.source.column((int) x, (int) z);
            Terrain3DColumn raw = density.raw;
            for (int span = 0; span < raw.spanCount(); span++) {
                if (raw.ceiling(span) > top || decision != UNKNOWN) {
                    return;
                }
                if (raw.floor(span) >= bottom) {
                    visit((int) x, (int) z, density, span);
                }
            }
        }

        private void visit(int x, int z, DensityColumn density, int span) {
            int state = density.state(span);
            if (state != UNKNOWN) {
                decision = state;
                return;
            }
            int slot = visitedSlot(x, z, span);
            if (visited[slot] != 0) {
                return;
            }
            visited[slot] = count + 1;
            visitedSlots[count] = slot;
            xs[count] = x;
            zs[count] = z;
            spans[count] = span;
            columns[count++] = density;
            volume += density.raw.floor(span) - density.raw.ceiling(span) + 1;
            if (volume > MAXIMUM_FRAGMENT_VOLUME) {
                decision = KEEP;
            }
        }

        private int visitedSlot(int x, int z, int span) {
            long coordinate = (long) x << 32 ^ z & 0xffffffffL;
            int slot = (int) HashCommon.mix(coordinate ^ span * 0x9e3779b97f4a7c15L)
                    & (VISITED_CAPACITY - 1);
            while (visited[slot] != 0) {
                int index = visited[slot] - 1;
                if (xs[index] == x && zs[index] == z && spans[index] == span) {
                    return slot;
                }
                slot = (slot + 1) & (VISITED_CAPACITY - 1);
            }
            return slot;
        }
    }
}
