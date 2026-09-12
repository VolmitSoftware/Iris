package art.arcane.iris.generation.mantle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Objects;
import java.util.function.Supplier;

import static art.arcane.iris.generation.cache.Cache.key;

final class ObjectSourcePlanCache {
    static final long MAXIMUM_MUTATION_WEIGHT = 1_048_576L;

    private final Cache<Long, ObjectSourcePlan> plans;

    ObjectSourcePlanCache() {
        this(MAXIMUM_MUTATION_WEIGHT);
    }

    ObjectSourcePlanCache(long maximumMutationWeight) {
        if (maximumMutationWeight <= 0L) {
            throw new IllegalArgumentException("Maximum mutation weight must be positive");
        }
        this.plans = Caffeine.newBuilder()
                .maximumWeight(maximumMutationWeight)
                .weigher((Long key, ObjectSourcePlan plan) -> plan.mutationWeight())
                .build();
    }

    ObjectSourcePlan get(int sourceChunkX, int sourceChunkZ, Supplier<ObjectSourcePlan> builder) {
        Objects.requireNonNull(builder, "Source plan builder");
        return plans.get(key(sourceChunkX, sourceChunkZ), ignored -> builder.get());
    }

    void clear() {
        plans.invalidateAll();
    }

    long estimatedSize() {
        plans.cleanUp();
        return plans.estimatedSize();
    }
}
