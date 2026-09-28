package art.arcane.iris.generation.mantle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import art.arcane.volmlib.util.cache.CacheKey;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import static art.arcane.iris.generation.cache.Cache.key;

final class ObjectSourcePlanCache {
    private static final long MAXIMUM_RETAINED_BYTES = 512L * 1024L * 1024L;
    private static final int ENTRY_BYTES = 64;

    private final long maximumRetainedBytes;
    private final LongAdder lookups = new LongAdder();
    private final LongAdder builds = new LongAdder();
    private final LongAdder waits = new LongAdder();
    private final LongAdder replays = new LongAdder();
    private final LongAdder drained = new LongAdder();
    private final LongAdder retired = new LongAdder();
    private volatile State state;

    ObjectSourcePlanCache() {
        this(retainedByteBudget(Runtime.getRuntime().maxMemory()));
    }

    ObjectSourcePlanCache(long maximumRetainedBytes) {
        if (maximumRetainedBytes <= 0L) {
            throw new IllegalArgumentException("Maximum retained bytes must be positive");
        }
        this.maximumRetainedBytes = maximumRetainedBytes;
        state = new State(maximumRetainedBytes);
    }

    static long retainedByteBudget(long maximumHeapBytes) {
        return Math.max(1L, Math.min(MAXIMUM_RETAINED_BYTES, maximumHeapBytes / 32L));
    }

    /**
     * Returns the plan whose mutations the destination still has to replay, or null when the source writes nothing
     * there. Each destination consumes a retained plan once; a plan whose destinations were all consumed keeps only
     * its destination index, so a repeated consumption rebuilds it instead of trusting released state. The entry is
     * dropped once every destination within the source chunk radius has asked for it.
     */
    ObjectSourcePlan acquire(int sourceChunkX, int sourceChunkZ, int destinationChunkX, int destinationChunkZ,
                             int sourceChunkRadius, Supplier<ObjectSourcePlan> builder) {
        Objects.requireNonNull(builder, "Source plan builder");
        lookups.increment();
        State current = state;
        long source = CacheKey.mix(key(sourceChunkX, sourceChunkZ));
        Entry entry = current.plans.getIfPresent(source);
        if (entry == null) {
            entry = load(current, source, sourceChunkRadius, builder);
        }
        boolean lastVisitor = entry.visitors.visit(destinationChunkX - sourceChunkX, destinationChunkZ - sourceChunkZ);
        ObjectSourcePlan plan = claim(current, source, entry, destinationChunkX, destinationChunkZ, builder);
        if (lastVisitor) {
            retired.increment();
            Visitors visitors = entry.visitors;
            current.plans.asMap().computeIfPresent(source, (Long key, Entry mapped) -> mapped.visitors == visitors ? null : mapped);
        }
        return plan;
    }

    private ObjectSourcePlan claim(State current, long source, Entry entry, int destinationChunkX, int destinationChunkZ,
                                   Supplier<ObjectSourcePlan> builder) {
        int slot = entry.slot(destinationChunkX, destinationChunkZ);
        if (slot < 0) {
            return null;
        }
        Claim claim = entry.claim(slot);
        if (claim == null) {
            replays.increment();
            return replay(current, source, entry, destinationChunkX, destinationChunkZ, builder);
        }
        if (claim.last()) {
            drained.increment();
            current.plans.asMap().replace(source, entry, entry.released());
        }
        return claim.plan();
    }

    private ObjectSourcePlan replay(State current, long source, Entry consumed, int destinationChunkX, int destinationChunkZ,
                                    Supplier<ObjectSourcePlan> builder) {
        Entry rebuilt = new Entry(build(builder), consumed.visitors);
        int slot = rebuilt.slot(destinationChunkX, destinationChunkZ);
        Claim claim = slot < 0 ? null : rebuilt.claim(slot);
        if (claim == null) {
            return null;
        }
        if (!claim.last() && rebuilt.weight() <= maximumRetainedBytes) {
            current.plans.asMap().replace(source, consumed, rebuilt);
        }
        return claim.plan();
    }

    private Entry load(State current, long source, int sourceChunkRadius, Supplier<ObjectSourcePlan> builder) {
        Pending created = new Pending(Thread.currentThread());
        Pending pending = current.pending.putIfAbsent(source, created);
        if (pending != null) {
            waits.increment();
            return pending.await();
        }
        try {
            Entry entry = current.plans.getIfPresent(source);
            if (entry == null) {
                entry = new Entry(build(builder), new Visitors(sourceChunkRadius));
                if (entry.weight() <= maximumRetainedBytes) {
                    current.plans.put(source, entry);
                }
            }
            created.result.complete(entry);
            return entry;
        } catch (RuntimeException | Error failure) {
            created.failure = failure;
            created.result.completeExceptionally(failure);
            throw failure;
        } finally {
            current.pending.remove(source, created);
        }
    }

    private ObjectSourcePlan build(Supplier<ObjectSourcePlan> builder) {
        builds.increment();
        return Objects.requireNonNull(builder.get(), "Source plan builder returned no plan");
    }

    void clear() {
        state = new State(maximumRetainedBytes);
    }

    long estimatedSize() {
        State current = state;
        current.plans.cleanUp();
        return current.plans.estimatedSize();
    }

    Stats stats() {
        State current = state;
        current.plans.cleanUp();
        long retainedBytes = current.plans.policy().eviction()
                .map(eviction -> eviction.weightedSize().orElse(0L))
                .orElse(0L);
        return new Stats(lookups.sum(), builds.sum(), waits.sum(), replays.sum(), drained.sum(), retired.sum(),
                current.plans.estimatedSize(), retainedBytes, maximumRetainedBytes);
    }

    record Stats(long lookups, long builds, long waits, long replays, long drained, long retired, long retained,
                 long retainedBytes, long budgetBytes) {
    }

    private static final class State {
        private final Cache<Long, Entry> plans;
        private final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();

        private State(long maximumRetainedBytes) {
            plans = Caffeine.newBuilder()
                    .maximumWeight(maximumRetainedBytes)
                    .weigher((Long key, Entry entry) -> entry.weight())
                    .build();
        }
    }

    private record Claim(ObjectSourcePlan plan, boolean last) {
    }

    private static final class Entry {
        private final ObjectSourcePlan index;
        private final boolean[] claimed;
        private final Visitors visitors;
        private final int weight;
        private ObjectSourcePlan plan;
        private int remaining;

        private Entry(ObjectSourcePlan plan, Visitors visitors) {
            this.index = plan.isEmpty() ? ObjectSourcePlan.EMPTY : plan;
            this.plan = plan.isEmpty() ? null : plan;
            this.claimed = new boolean[plan.destinationCount()];
            this.visitors = visitors;
            this.remaining = claimed.length;
            this.weight = weigh(index, claimed, visitors);
        }

        private Entry(ObjectSourcePlan index, boolean[] claimed, Visitors visitors) {
            this.index = index;
            this.claimed = claimed;
            this.visitors = visitors;
            this.weight = weigh(index, claimed, visitors);
        }

        private int slot(int destinationChunkX, int destinationChunkZ) {
            return index.destinationSlot(destinationChunkX, destinationChunkZ);
        }

        private synchronized Claim claim(int slot) {
            ObjectSourcePlan retained = plan;
            if (retained == null || claimed[slot]) {
                return null;
            }
            claimed[slot] = true;
            if (--remaining == 0) {
                plan = null;
                return new Claim(retained, true);
            }
            return new Claim(retained, false);
        }

        private Entry released() {
            return new Entry(index.destinationIndex(), claimed, visitors);
        }

        private int weight() {
            return weight;
        }

        private static int weigh(ObjectSourcePlan index, boolean[] claimed, Visitors visitors) {
            return (int) Math.min(Integer.MAX_VALUE,
                    ENTRY_BYTES + (long) claimed.length + visitors.retainedBytes() + index.estimatedRetainedBytes());
        }
    }

    private static final class Visitors {
        private final int radius;
        private final int side;
        private final long[] seen;
        private int remaining;

        private Visitors(int radius) {
            if (radius < 0) {
                throw new IllegalArgumentException("Source chunk radius must not be negative");
            }
            this.radius = radius;
            this.side = 2 * radius + 1;
            this.seen = new long[(side * side + 63) >>> 6];
            this.remaining = side * side;
        }

        private synchronized boolean visit(int offsetX, int offsetZ) {
            if (Math.abs(offsetX) > radius || Math.abs(offsetZ) > radius) {
                return false;
            }
            int bit = (offsetX + radius) * side + offsetZ + radius;
            long mask = 1L << (bit & 63);
            if ((seen[bit >>> 6] & mask) != 0L) {
                return false;
            }
            seen[bit >>> 6] |= mask;
            return --remaining == 0;
        }

        private long retainedBytes() {
            return 32L + 8L * seen.length;
        }
    }

    private static final class Pending {
        private final Thread owner;
        private final CompletableFuture<Entry> result = new CompletableFuture<>();
        private Throwable failure;

        private Pending(Thread owner) {
            this.owner = owner;
        }

        private Entry await() {
            if (owner == Thread.currentThread()) {
                throw new IllegalStateException("Recursive source plan construction");
            }
            try {
                return result.join();
            } catch (CompletionException completion) {
                if (failure instanceof Error error) {
                    throw error;
                }
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw completion;
            }
        }
    }
}
