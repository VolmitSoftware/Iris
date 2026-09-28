package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ObjectSourcePlan {
    private static final int PLAN_BYTES = 256;
    private static final int DESTINATION_INDEX_BYTES = 96;
    private static final int MUTATION_BYTES = 96;
    private static final int PLACEMENT_BYTES = 256;
    private static final int PLACEMENT_DESTINATION_BYTES = 128;
    private static final int REFERENCE_BYTES = 8;
    private final Long2ObjectOpenHashMap<List<ObjectDestinationTransaction.Mutation>> destinations;
    private final int estimatedRetainedBytes;
    private final List<Placement> placements;

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations) {
        this(mutations, List.of());
    }

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations, List<List<ObjectDestinationTransaction.Mutation>> placements) {
        ArrayList<Placement> crossing = new ArrayList<>();
        for (int ordinal = 0; ordinal < placements.size(); ordinal++) {
            List<ObjectDestinationTransaction.Mutation> placement = placements.get(ordinal);
            LinkedHashMap<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> fragments = group(placement);
            if (fragments.size() > 1) {
                crossing.add(new Placement(ordinal, bounds(placement), List.copyOf(fragments.keySet()), fragments));
            }
        }
        this.placements = List.copyOf(crossing);
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
        for (Placement placement : this.placements) {
            weight += PLACEMENT_BYTES + (long) placement.touched().size() * PLACEMENT_DESTINATION_BYTES;
            for (List<ObjectDestinationTransaction.Mutation> fragment : placement.fragments().values()) {
                long retainedCapacity = Math.max(10L, fragment.size() + (fragment.size() + 1L) / 2L);
                weight += retainedCapacity * REFERENCE_BYTES;
            }
        }
        this.estimatedRetainedBytes = weight >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    List<ObjectDestinationTransaction.Mutation> mutationsFor(int chunkX, int chunkZ) {
        List<ObjectDestinationTransaction.Mutation> mutations = destinations.get(destinationKey(chunkX, chunkZ));
        return mutations == null ? List.of() : mutations;
    }

    void persistContinuations(MantleWriter writer, int sourceX, int sourceZ, int destinationX, int destinationZ) {
        ObjectContinuationBundle.ChunkPosition destination = new ObjectContinuationBundle.ChunkPosition(destinationX, destinationZ);
        for (Placement placement : placements) {
            if (!placement.fragments().containsKey(destination)) {
                continue;
            }
            ObjectContinuationBundle.PlacementKey key = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, sourceX, sourceZ, placement.ordinal());
            for (Map.Entry<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> entry : placement.fragments().entrySet()) {
                ObjectContinuationBundle.ChunkPosition target = entry.getKey();
                if (target.equals(destination)) {
                    continue;
                }
                writer.withChunkFence(target.x(), target.z(), () -> {
                    MantleChunk<Matter> chunk = writer.acquireChunk(target.x(), target.z());
                    if (chunk.isFlagged(MantleFlag.REAL)) {
                        return;
                    }
                    int x = target.x() << 4;
                    int z = target.z() << 4;
                    ObjectContinuationBundle bundle = writer.getDataIfPresent(x, 0, z, ObjectContinuationBundle.class);
                    if (bundle != null) {
                        for (ObjectContinuationBundle.Fragment existing : bundle.fragments()) {
                            if (existing.key().equals(key)) {
                                return;
                            }
                        }
                    }
                    ObjectContinuationBundle.Fragment fragment = new ObjectContinuationBundle.Fragment(key, placement.bounds(), placement.touched(),
                            encode(entry.getValue(), writer.getMantle().getWorldHeight()));
                    writer.setData(x, 0, z, bundle == null ? new ObjectContinuationBundle(List.of(fragment)) : bundle.with(fragment));
                });
            }
        }
    }

    static byte[] encode(List<ObjectDestinationTransaction.Mutation> mutations, int height) {
        IrisMatterSupport.ensureRegistered();
        Matter payload = new IrisMatter(16, height, 16);
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            ObjectDestinationTransaction.DataKey key = mutation.key();
            int x = key.x() & 15;
            int z = key.z() & 15;
            if (mutation instanceof ObjectDestinationTransaction.CustomBlockMutation custom) {
                payload.slice(NativeBlockState.class).set(x, key.y(), z, custom.state().placementBaseState());
                payload.slice(Identifier.class).set(x, key.y(), z, Identifier.fromString(custom.state().deferredPlacementKey()));
            } else if (mutation instanceof ObjectDestinationTransaction.SetMutation set) {
                if (key.type() == NativeBlockState.class) {
                    payload.slice(Identifier.class).set(x, key.y(), z, null);
                }
                payload.slice(key.type()).set(x, key.y(), z, set.value());
            }
        }
        return ObjectContinuationBundle.encode(payload);
    }

    private static LinkedHashMap<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> group(
            List<ObjectDestinationTransaction.Mutation> mutations
    ) {
        LinkedHashMap<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> grouped = new LinkedHashMap<>();
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            ObjectContinuationBundle.ChunkPosition destination = new ObjectContinuationBundle.ChunkPosition(mutation.x() >> 4, mutation.z() >> 4);
            grouped.computeIfAbsent(destination, ignored -> new ArrayList<>()).add(mutation);
        }
        return grouped;
    }

    private static ObjectContinuationBundle.Bounds bounds(List<ObjectDestinationTransaction.Mutation> mutations) {
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (ObjectDestinationTransaction.Mutation mutation : mutations) {
            minimumX = Math.min(minimumX, mutation.x());
            minimumZ = Math.min(minimumZ, mutation.z());
            maximumX = Math.max(maximumX, mutation.x());
            maximumZ = Math.max(maximumZ, mutation.z());
        }
        return new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ);
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

    private record Placement(int ordinal, ObjectContinuationBundle.Bounds bounds,
                             List<ObjectContinuationBundle.ChunkPosition> touched,
                             Map<ObjectContinuationBundle.ChunkPosition, List<ObjectDestinationTransaction.Mutation>> fragments) {
    }

    private static long destinationKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }
}
