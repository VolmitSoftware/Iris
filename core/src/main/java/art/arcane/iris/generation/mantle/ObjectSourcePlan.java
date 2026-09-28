package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterMarker;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class ObjectSourcePlan {
    static final ObjectSourcePlan EMPTY = new ObjectSourcePlan(List.of());
    private static final int PLAN_BYTES = 160;
    private static final int DESTINATION_BYTES = 12;
    private static final int MUTATION_BYTES = 8;
    private static final int VALUE_BYTES = 8;
    private static final int PLACEMENT_BYTES = 128;
    private static final int PLACEMENT_DESTINATION_BYTES = 40;
    private static final int UNKNOWN_PAYLOAD_BYTES = 256;
    private static final int CUSTOM_BIT = 1 << 8;
    private static final int Y_SHIFT = 9;
    private static final int MAXIMUM_Y = (1 << (31 - Y_SHIFT)) - 1;

    private final long[] destinations;
    private final int[] offsets;
    private final int[] cells;
    private final int[] valueIndexes;
    private final Object[] values;
    private final Placement[] placements;
    private final int estimatedRetainedBytes;

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations) {
        this(mutations, List.of());
    }

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations, List<ObjectDestinationTransaction.PlacementRange> accepted) {
        int count = mutations.size();
        long[] mutationDestinations = new long[count];
        Long2IntOpenHashMap destinationSlots = new Long2IntOpenHashMap();
        destinationSlots.defaultReturnValue(-1);
        LongArrayList firstSeen = new LongArrayList();
        for (int index = 0; index < count; index++) {
            ObjectDestinationTransaction.Mutation mutation = Objects.requireNonNull(mutations.get(index), "mutation");
            long destination = destinationKey(mutation.x() >> 4, mutation.z() >> 4);
            mutationDestinations[index] = destination;
            if (destinationSlots.putIfAbsent(destination, firstSeen.size()) == -1) {
                firstSeen.add(destination);
            }
        }
        destinations = firstSeen.toLongArray();
        Arrays.sort(destinations);
        for (int slot = 0; slot < destinations.length; slot++) {
            destinationSlots.put(destinations[slot], slot);
        }
        offsets = new int[destinations.length + 1];
        for (int index = 0; index < count; index++) {
            offsets[destinationSlots.get(mutationDestinations[index]) + 1]++;
        }
        for (int slot = 0; slot < destinations.length; slot++) {
            offsets[slot + 1] += offsets[slot];
        }
        int[] cursor = Arrays.copyOf(offsets, destinations.length);
        int[] positions = new int[count];
        cells = new int[count];
        valueIndexes = new int[count];
        ValueTable table = new ValueTable();
        for (int index = 0; index < count; index++) {
            ObjectDestinationTransaction.Mutation mutation = mutations.get(index);
            int position = cursor[destinationSlots.get(mutationDestinations[index])]++;
            positions[index] = position;
            ObjectDestinationTransaction.DataKey key = mutation.key();
            if (key.y() < 0 || key.y() > MAXIMUM_Y) {
                throw new IllegalArgumentException("Object mutation height is outside the plan range: " + key.y());
            }
            Object value;
            int custom;
            switch (mutation) {
                case ObjectDestinationTransaction.SetMutation set -> {
                    value = Objects.requireNonNull(set.value(), "mutation value");
                    if (typeOf(value) != key.type()) {
                        throw new IllegalArgumentException("Object mutation type " + key.type().getName()
                                + " does not match its value " + value.getClass().getName());
                    }
                    custom = 0;
                }
                case ObjectDestinationTransaction.CustomBlockMutation block -> {
                    value = Objects.requireNonNull(block.state(), "custom block state");
                    custom = CUSTOM_BIT;
                }
            }
            cells[position] = (key.y() << Y_SHIFT) | custom | ((key.z() & 15) << 4) | (key.x() & 15);
            valueIndexes[position] = table.indexOf(value);
        }
        values = table.values.toArray();

        ArrayList<Placement> crossing = new ArrayList<>();
        for (int ordinal = 0; ordinal < accepted.size(); ordinal++) {
            ObjectDestinationTransaction.PlacementRange range = accepted.get(ordinal);
            Placement placement = crossingPlacement(ordinal, range.start(), range.end(), mutations, mutationDestinations, destinationSlots, positions);
            if (placement != null) {
                crossing.add(placement);
            }
        }
        placements = crossing.toArray(new Placement[0]);

        long weight = PLAN_BYTES
                + (long) destinations.length * DESTINATION_BYTES
                + (long) count * MUTATION_BYTES
                + table.payloadBytes
                + (long) values.length * VALUE_BYTES;
        for (Placement placement : placements) {
            weight += PLACEMENT_BYTES + (long) placement.touched().size() * PLACEMENT_DESTINATION_BYTES;
        }
        estimatedRetainedBytes = (int) Math.min(Integer.MAX_VALUE, weight);
    }

    private ObjectSourcePlan(long[] destinations) {
        this.destinations = destinations;
        this.offsets = new int[destinations.length + 1];
        this.cells = new int[0];
        this.valueIndexes = new int[0];
        this.values = new Object[0];
        this.placements = new Placement[0];
        this.estimatedRetainedBytes = PLAN_BYTES + destinations.length * DESTINATION_BYTES;
    }

    ObjectSourcePlan destinationIndex() {
        return new ObjectSourcePlan(destinations);
    }

    private static Placement crossingPlacement(
            int ordinal,
            int start,
            int end,
            List<ObjectDestinationTransaction.Mutation> mutations,
            long[] mutationDestinations,
            Long2IntOpenHashMap destinationSlots,
            int[] positions
    ) {
        if (start < 0 || end > mutations.size() || start >= end) {
            throw new IllegalArgumentException("Object placement range is outside the plan");
        }
        long first = mutationDestinations[start];
        int index = start + 1;
        while (index < end && mutationDestinations[index] == first) {
            index++;
        }
        if (index == end) {
            return null;
        }
        LongArrayList touched = new LongArrayList(4);
        Long2IntOpenHashMap touchedIndexes = new Long2IntOpenHashMap(4);
        touchedIndexes.defaultReturnValue(-1);
        ArrayList<int[]> runs = new ArrayList<>(4);
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (int mutationIndex = start; mutationIndex < end; mutationIndex++) {
            ObjectDestinationTransaction.Mutation mutation = mutations.get(mutationIndex);
            minimumX = Math.min(minimumX, mutation.x());
            minimumZ = Math.min(minimumZ, mutation.z());
            maximumX = Math.max(maximumX, mutation.x());
            maximumZ = Math.max(maximumZ, mutation.z());
            long destination = mutationDestinations[mutationIndex];
            int touchedIndex = touchedIndexes.get(destination);
            if (touchedIndex < 0) {
                touchedIndexes.put(destination, touched.size());
                touched.add(destination);
                runs.add(new int[]{destinationSlots.get(destination), positions[mutationIndex], 1});
            } else {
                runs.get(touchedIndex)[2]++;
            }
        }
        int size = touched.size();
        ArrayList<ObjectContinuationBundle.ChunkPosition> chunks = new ArrayList<>(size);
        int[] slots = new int[size];
        int[] runStarts = new int[size];
        int[] runLengths = new int[size];
        for (int touchedIndex = 0; touchedIndex < size; touchedIndex++) {
            long destination = touched.getLong(touchedIndex);
            chunks.add(new ObjectContinuationBundle.ChunkPosition(destinationX(destination), destinationZ(destination)));
            int[] run = runs.get(touchedIndex);
            slots[touchedIndex] = run[0];
            runStarts[touchedIndex] = run[1];
            runLengths[touchedIndex] = run[2];
        }
        return new Placement(ordinal, new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ),
                List.copyOf(chunks), slots, runStarts, runLengths);
    }

    int destinationSlot(int chunkX, int chunkZ) {
        return Arrays.binarySearch(destinations, destinationKey(chunkX, chunkZ));
    }

    int destinationCount() {
        return destinations.length;
    }

    boolean isEmpty() {
        return destinations.length == 0;
    }

    List<ObjectDestinationTransaction.Mutation> mutationsFor(int chunkX, int chunkZ) {
        int slot = destinationSlot(chunkX, chunkZ);
        return slot < 0 ? List.of() : decode(chunkX, chunkZ, offsets[slot], offsets[slot + 1] - offsets[slot]);
    }

    void persistContinuations(MantleWriter writer, int sourceX, int sourceZ, int destinationX, int destinationZ) {
        if (placements.length == 0) {
            return;
        }
        int destinationSlot = destinationSlot(destinationX, destinationZ);
        if (destinationSlot < 0) {
            return;
        }
        for (Placement placement : placements) {
            if (!placement.touches(destinationSlot)) {
                continue;
            }
            ObjectContinuationBundle.PlacementKey key = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.BIOME, sourceX, sourceZ, placement.ordinal());
            for (int touchedIndex = 0; touchedIndex < placement.slots().length; touchedIndex++) {
                if (placement.slots()[touchedIndex] == destinationSlot) {
                    continue;
                }
                ObjectContinuationBundle.ChunkPosition target = placement.touched().get(touchedIndex);
                int runStart = placement.runStarts()[touchedIndex];
                int runLength = placement.runLengths()[touchedIndex];
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
                            encode(decode(target.x(), target.z(), runStart, runLength), writer.getMantle().getWorldHeight()));
                    writer.setData(x, 0, z, bundle == null ? new ObjectContinuationBundle(List.of(fragment)) : bundle.with(fragment));
                });
            }
        }
    }

    private List<ObjectDestinationTransaction.Mutation> decode(int chunkX, int chunkZ, int start, int length) {
        ObjectDestinationTransaction.Mutation[] decoded = new ObjectDestinationTransaction.Mutation[length];
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int index = 0; index < length; index++) {
            int cell = cells[start + index];
            int x = baseX | (cell & 15);
            int z = baseZ | ((cell >>> 4) & 15);
            int y = cell >>> Y_SHIFT;
            Object value = values[valueIndexes[start + index]];
            decoded[index] = (cell & CUSTOM_BIT) != 0
                    ? new ObjectDestinationTransaction.CustomBlockMutation(
                    new ObjectDestinationTransaction.DataKey(x, y, z, NativeBlockState.class), (NativeBlockState) value)
                    : new ObjectDestinationTransaction.SetMutation(
                    new ObjectDestinationTransaction.DataKey(x, y, z, typeOf(value)), value);
        }
        return List.of(decoded);
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

    int estimatedRetainedBytes() {
        return estimatedRetainedBytes;
    }

    static Class<?> typeOf(Object value) {
        return value instanceof NativeBlockState ? NativeBlockState.class : value.getClass();
    }

    static long destinationKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static int destinationX(long destination) {
        return (int) (destination >> 32);
    }

    private static int destinationZ(long destination) {
        return (int) destination;
    }

    private static long payloadBytes(Object value) {
        return switch (value) {
            case String text -> stringBytes(text);
            case TreeBlockMaterial material -> 16L + stringBytes(material.materialKey());
            case Identifier identifier -> 24L + stringBytes(identifier.namespace()) + stringBytes(identifier.key());
            case NativeBlockState ignored -> 0L;
            case MatterCavern cavern -> 24L + stringBytes(cavern.getCustomBiome());
            case HydrologyCaveCell cell -> 48L + stringBytes(cell.fluidProfileKey()) + stringBytes(cell.floodedBiomeKey());
            case MatterMarker marker -> 16L + stringBytes(marker.getTag());
            case TileWrapper ignored -> 4L * UNKNOWN_PAYLOAD_BYTES;
            case Enum<?> ignored -> 0L;
            case Number ignored -> 16L;
            case Boolean ignored -> 16L;
            case Character ignored -> 16L;
            default -> UNKNOWN_PAYLOAD_BYTES;
        };
    }

    private static long stringBytes(String value) {
        return value == null ? 0L : 48L + 2L * value.length();
    }

    private static final class ValueTable {
        private final ArrayList<Object> values = new ArrayList<>();
        private final Object2IntOpenHashMap<Object> equalValues = new Object2IntOpenHashMap<>();
        private final Reference2IntOpenHashMap<Object> identityValues = new Reference2IntOpenHashMap<>();
        private long payloadBytes;

        private ValueTable() {
            equalValues.defaultReturnValue(-1);
            identityValues.defaultReturnValue(-1);
        }

        private int indexOf(Object value) {
            boolean byValue = value instanceof String || value instanceof TreeBlockMaterial;
            int index = byValue ? equalValues.getInt(value) : identityValues.getInt(value);
            if (index >= 0) {
                return index;
            }
            index = values.size();
            values.add(value);
            payloadBytes += payloadBytes(value);
            if (byValue) {
                equalValues.put(value, index);
            } else {
                identityValues.put(value, index);
            }
            return index;
        }
    }

    private record Placement(int ordinal, ObjectContinuationBundle.Bounds bounds,
                             List<ObjectContinuationBundle.ChunkPosition> touched,
                             int[] slots, int[] runStarts, int[] runLengths) {
        private boolean touches(int slot) {
            for (int touchedSlot : slots) {
                if (touchedSlot == slot) {
                    return true;
                }
            }
            return false;
        }
    }
}
