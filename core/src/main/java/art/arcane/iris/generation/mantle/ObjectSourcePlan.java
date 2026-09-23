package art.arcane.iris.generation.mantle;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class ObjectSourcePlan {
    private static final int DESTINATION_INDEX_WEIGHT = 8;
    private final Long2ObjectOpenHashMap<List<ObjectDestinationTransaction.Mutation>> destinations;
    private final int mutationWeight;

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations) {
        Long2ObjectOpenHashMap<ArrayList<ObjectDestinationTransaction.Mutation>> grouped = new Long2ObjectOpenHashMap<>(2);
        long weight = 1L;
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            long key = destinationKey(mutation.x() >> 4, mutation.z() >> 4);
            ArrayList<ObjectDestinationTransaction.Mutation> local = grouped.get(key);
            if (local == null) {
                local = new ArrayList<>();
                grouped.put(key, local);
                weight += DESTINATION_INDEX_WEIGHT;
            }
            local.add(mutation);
            weight += mutation.weight();
        }
        destinations = new Long2ObjectOpenHashMap<>(grouped.size());
        for (Long2ObjectMap.Entry<ArrayList<ObjectDestinationTransaction.Mutation>> entry : grouped.long2ObjectEntrySet()) {
            destinations.put(entry.getLongKey(), List.copyOf(entry.getValue()));
        }
        this.mutationWeight = weight >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    List<ObjectDestinationTransaction.Mutation> mutationsFor(int chunkX, int chunkZ) {
        List<ObjectDestinationTransaction.Mutation> mutations = destinations.get(destinationKey(chunkX, chunkZ));
        return mutations == null ? List.of() : mutations;
    }

    int mutationWeight() {
        return mutationWeight;
    }

    private static long destinationKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
