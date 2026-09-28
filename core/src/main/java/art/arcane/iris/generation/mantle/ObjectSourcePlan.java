package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.integration.Identifier;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class ObjectSourcePlan {
    private static final int PLAN_BYTES = 256;
    private static final int DESTINATION_INDEX_BYTES = 96;
    private static final int MUTATION_BYTES = 96;
    private final Long2ObjectOpenHashMap<List<ObjectDestinationTransaction.Mutation>> destinations;
    private final int estimatedRetainedBytes;

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations) {
        Long2ObjectOpenHashMap<ArrayList<ObjectDestinationTransaction.Mutation>> grouped = new Long2ObjectOpenHashMap<>(2);
        long weight = PLAN_BYTES;
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            long key = destinationKey(mutation.x() >> 4, mutation.z() >> 4);
            ArrayList<ObjectDestinationTransaction.Mutation> local = grouped.get(key);
            if (local == null) {
                local = new ArrayList<>();
                grouped.put(key, local);
                weight += DESTINATION_INDEX_BYTES;
            }
            local.add(mutation);
            Object payload = switch (mutation) {
                case ObjectDestinationTransaction.SetMutation set -> set.value();
                case ObjectDestinationTransaction.CustomBlockMutation custom -> custom.state();
            };
            weight = Math.min(Integer.MAX_VALUE, weight + MUTATION_BYTES + payloadBytes(payload));
        }
        destinations = new Long2ObjectOpenHashMap<>(grouped.size());
        for (Long2ObjectMap.Entry<ArrayList<ObjectDestinationTransaction.Mutation>> entry : grouped.long2ObjectEntrySet()) {
            destinations.put(entry.getLongKey(), List.copyOf(entry.getValue()));
        }
        this.estimatedRetainedBytes = weight >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    List<ObjectDestinationTransaction.Mutation> mutationsFor(int chunkX, int chunkZ) {
        List<ObjectDestinationTransaction.Mutation> mutations = destinations.get(destinationKey(chunkX, chunkZ));
        return mutations == null ? List.of() : mutations;
    }

    int estimatedRetainedBytes() {
        return estimatedRetainedBytes;
    }

    private static long payloadBytes(Object value) {
        return switch (value) {
            case null -> 0L;
            case String text -> stringBytes(text);
            case TreeBlockMaterial material -> 24L + stringBytes(material.materialKey());
            case Identifier identifier -> 32L + stringBytes(identifier.namespace()) + stringBytes(identifier.key());
            case NativeBlockState ignored -> 64L;
            case MatterCavern cavern -> 32L + stringBytes(cavern.getCustomBiome());
            case HydrologyCaveCell cell -> 64L + stringBytes(cell.fluidProfileKey()) + stringBytes(cell.floodedBiomeKey());
            case Byte ignored -> 24L;
            case Short ignored -> 24L;
            case Integer ignored -> 24L;
            case Long ignored -> 24L;
            case Float ignored -> 24L;
            case Double ignored -> 24L;
            case Boolean ignored -> 24L;
            case Character ignored -> 24L;
            case Enum<?> ignored -> 0L;
            default -> Integer.MAX_VALUE;
        };
    }

    private static long stringBytes(String value) {
        return value == null ? 0L : 48L + 2L * value.length();
    }

    private static long destinationKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
