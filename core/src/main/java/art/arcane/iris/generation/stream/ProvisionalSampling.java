package art.arcane.iris.generation.stream;

import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.function.Supplier;

/**
 * Tracks answers a thread gave from inputs that are not final yet. The server thread never waits for
 * a hydrology plan, so it answers from natural terrain until the plan lands; a value derived from such
 * an answer is right for that caller only and must never be memoized, or generation would later read
 * it and its output would depend on timing.
 */
public final class ProvisionalSampling {
    private static final ThreadLocal<Tracker> TRACKERS = ThreadLocal.withInitial(Tracker::new);

    private ProvisionalSampling() {
    }

    /** Records that the calling thread just answered from inputs that are not final. */
    public static void mark() {
        TRACKERS.get().provisional = true;
    }

    /**
     * The stream's value for a memoizing cache, or {@link Unmemoizable} carrying it when sampling it
     * read a provisional answer. The mark still reaches every enclosing resolver.
     */
    public static <T> T memoizable(ProceduralStream<T> stream, int x, int z) {
        Tracker tracker = TRACKERS.get();
        boolean outer = tracker.begin();
        T value;
        boolean provisional;
        try {
            value = stream.get(x, z);
        } finally {
            provisional = tracker.end(outer);
        }
        if (provisional) {
            throw new Unmemoizable(value);
        }
        return value;
    }

    public static double memoizableDouble(ProceduralStream<Double> stream, int x, int z) {
        Tracker tracker = TRACKERS.get();
        boolean outer = tracker.begin();
        double value;
        boolean provisional;
        try {
            value = stream.getDouble(x, z);
        } finally {
            provisional = tracker.end(outer);
        }
        if (provisional) {
            throw new Unmemoizable(value);
        }
        return value;
    }

    public static <T> T memoizable(Supplier<T> resolver) {
        Tracker tracker = TRACKERS.get();
        boolean outer = tracker.begin();
        T value;
        boolean provisional;
        try {
            value = resolver.get();
        } finally {
            provisional = tracker.end(outer);
        }
        if (provisional) {
            throw new Unmemoizable(value);
        }
        return value;
    }

    private static final class Tracker {
        private boolean provisional;

        private boolean begin() {
            boolean outer = provisional;
            provisional = false;
            return outer;
        }

        private boolean end(boolean outer) {
            boolean inner = provisional;
            provisional = outer || inner;
            return inner;
        }
    }

    /** Carries a provisional value past a memoizing cache so the cache stores nothing. */
    public static final class Unmemoizable extends RuntimeException {
        private final transient Object value;

        private Unmemoizable(Object value) {
            super(null, null, false, false);
            this.value = value;
        }

        @SuppressWarnings("unchecked")
        public <T> T value() {
            return (T) value;
        }

        public double doubleValue() {
            return (Double) value;
        }
    }
}
