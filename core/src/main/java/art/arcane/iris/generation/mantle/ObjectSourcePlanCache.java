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
    static final long MAXIMUM_MUTATION_WEIGHT = 4_194_304L;

    private final long maximumMutationWeight;
    private volatile State state;

    ObjectSourcePlanCache() {
        this(MAXIMUM_MUTATION_WEIGHT);
    }

    ObjectSourcePlanCache(long maximumMutationWeight) {
        if (maximumMutationWeight <= 0L) {
            throw new IllegalArgumentException("Maximum mutation weight must be positive");
        }
        this.maximumMutationWeight = maximumMutationWeight;
        state = new State(maximumMutationWeight);
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
                if (plan != null) {
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
        state = new State(maximumMutationWeight);
    }

    long estimatedSize() {
        State current = state;
        current.plans.cleanUp();
        return current.plans.estimatedSize();
    }

    private static final class State {
        private final Cache<Long, ObjectSourcePlan> plans;
        private final ConcurrentHashMap<Long, Pending> pending = new ConcurrentHashMap<>();

        private State(long maximumMutationWeight) {
            plans = Caffeine.newBuilder()
                    .maximumWeight(maximumMutationWeight)
                    .weigher((Long key, ObjectSourcePlan plan) -> plan.mutationWeight())
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
