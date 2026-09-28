package art.arcane.iris.generation.mantle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import art.arcane.volmlib.util.cache.CacheKey;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static art.arcane.iris.generation.cache.Cache.key;

final class ObjectSourcePlanCache {
    private static final long MAXIMUM_RETAINED_BYTES = 256L * 1024L * 1024L;

    private final long maximumRetainedBytes;
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
        return Math.max(1L, Math.min(MAXIMUM_RETAINED_BYTES, maximumHeapBytes / 64L));
    }

    ObjectSourcePlan get(int sourceChunkX, int sourceChunkZ, Supplier<ObjectSourcePlan> builder) {
        Objects.requireNonNull(builder, "Source plan builder");
        State current = state;
        long source = CacheKey.mix(key(sourceChunkX, sourceChunkZ));
        ObjectSourcePlan cached = current.plans.getIfPresent(source);
        if (cached != null) {
            return cached;
        }
        Pending created = new Pending(Thread.currentThread());
        Pending pending = current.pending.putIfAbsent(source, created);
        if (pending != null) {
            return pending.await();
        }
        try {
            ObjectSourcePlan plan = current.plans.getIfPresent(source);
            if (plan == null) {
                plan = builder.get();
                if (plan != null && plan.estimatedRetainedBytes() < Integer.MAX_VALUE
                        && plan.estimatedRetainedBytes() <= maximumRetainedBytes) {
                    current.plans.put(source, plan);
                }
            }
            created.result.complete(plan);
            return plan;
        } catch (RuntimeException | Error failure) {
            created.failure = failure;
            created.result.completeExceptionally(failure);
            throw failure;
        } finally {
            current.pending.remove(source, created);
        }
    }

    void clear() {
        state = new State(maximumRetainedBytes);
    }

    long estimatedSize() {
        State current = state;
        current.plans.cleanUp();
        return current.plans.estimatedSize();
    }

    private static final class State {
        private final Cache<Long, ObjectSourcePlan> plans;
        private final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();

        private State(long maximumRetainedBytes) {
            plans = Caffeine.newBuilder()
                    .maximumWeight(maximumRetainedBytes)
                    .weigher((Long key, ObjectSourcePlan plan) -> plan.estimatedRetainedBytes())
                    .build();
        }
    }

    private static final class Pending {
        private final Thread owner;
        private final CompletableFuture<ObjectSourcePlan> result = new CompletableFuture<>();
        private Throwable failure;

        private Pending(Thread owner) {
            this.owner = owner;
        }

        private ObjectSourcePlan await() {
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
