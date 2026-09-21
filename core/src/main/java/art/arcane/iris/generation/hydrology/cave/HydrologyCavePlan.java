package art.arcane.iris.generation.hydrology.cave;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public record HydrologyCavePlan(
        HydrologyCaveSource source,
        HydrologyCaveRejection rejection,
        Map<CavePosition, HydrologyCaveAction> actions,
        Map<CavePosition, CaveVoxelPrecondition> baselinePreconditions,
        OptionalLong arbitrationWinnerSourceId
) {
    private static final int ACTION_MASK = 0x03;
    private static final int ACTION_PRESENT = 0x04;
    private static final int VOXEL_SHIFT = 3;
    private static final int VOXEL_MASK = 0x38;
    private static final int OPEN_TO_SURFACE = 0x40;
    private static final HydrologyCaveAction[] ACTIONS = HydrologyCaveAction.values();
    private static final CaveVoxel[] VOXELS = CaveVoxel.values();
    private static final CaveVoxelPrecondition[][] PRECONDITIONS = createPreconditions();

    public HydrologyCavePlan {
        Objects.requireNonNull(source);
        Objects.requireNonNull(rejection);
        Map<CavePosition, HydrologyCaveAction> requestedActions = Objects.requireNonNull(actions);
        Map<CavePosition, CaveVoxelPrecondition> requestedPreconditions =
                Objects.requireNonNull(baselinePreconditions);
        Objects.requireNonNull(arbitrationWinnerSourceId);
        if (rejection != HydrologyCaveRejection.NONE
                && (!requestedActions.isEmpty() || !requestedPreconditions.isEmpty())) {
            throw new IllegalArgumentException("Rejected cave plans cannot contain mutations or preconditions");
        }
        if (!requestedPreconditions.keySet().containsAll(requestedActions.keySet())) {
            throw new IllegalArgumentException("Every cave action requires a baseline precondition");
        }
        if (rejection != HydrologyCaveRejection.OVERLAPPING_SOURCE && arbitrationWinnerSourceId.isPresent()) {
            throw new IllegalArgumentException("Only overlap rejections can name an arbitration winner");
        }
        if (requestedPreconditions.isEmpty()) {
            actions = Map.of();
            baselinePreconditions = Map.of();
        } else {
            PackedPositions packed = new PackedPositions(requestedActions, requestedPreconditions);
            PositionSpatialIndex spatialIndex = new PositionSpatialIndex(packed);
            actions = new ActionMap(packed, requestedActions.size(), spatialIndex);
            baselinePreconditions = new PreconditionMap(packed, packed.preconditionOrder(requestedPreconditions), spatialIndex);
        }
    }

    public boolean accepted() {
        return rejection == HydrologyCaveRejection.NONE;
    }

    public long estimatedRetainedBytes() {
        if (!(baselinePreconditions instanceof PreconditionMap preconditions)) {
            return 192L;
        }
        PackedPositions packed = preconditions.packed;
        PositionSpatialIndex spatialIndex = preconditions.spatialIndex;
        long spatialCapacity = (long) HashCommon.arraySize(spatialIndex.positionsByChunk.size(), 0.75F) + 1L;
        long bytes = 512L + arrayBytes((long) packed.x.length * Integer.BYTES) * 3L
                + arrayBytes(packed.flags.length) + arrayBytes((long) packed.slots.length * Integer.BYTES)
                + arrayBytes((long) preconditions.order.rows.length * Integer.BYTES)
                + arrayBytes(spatialCapacity * Long.BYTES) * 2L;
        for (int[] rows : spatialIndex.positionsByChunk.values()) {
            bytes += arrayBytes((long) rows.length * Integer.BYTES);
        }
        return bytes;
    }

    private static long arrayBytes(long elementsBytes) {
        return (elementsBytes + 23L) & ~7L;
    }

    public void forEachAction(BiConsumer<CavePosition, HydrologyCaveAction> consumer) {
        actions.forEach(consumer);
    }

    public void forEachPrecondition(BiConsumer<CavePosition, CaveVoxelPrecondition> consumer) {
        baselinePreconditions.forEach(consumer);
    }

    public boolean allPreconditions(BiPredicate<CavePosition, CaveVoxelPrecondition> predicate) {
        Objects.requireNonNull(predicate);
        for (CavePosition position : baselinePreconditions.keySet()) {
            if (!predicate.test(position, baselinePreconditions.get(position))) {
                return false;
            }
        }
        return true;
    }

    public boolean intersectsActions(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        if (maximumX <= minimumX || maximumZ <= minimumZ || !(actions instanceof ActionMap actionMap)) {
            return false;
        }
        return actionMap.intersects(minimumX, minimumZ, maximumX, maximumZ);
    }

    public void forEachActionIn(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            BiConsumer<CavePosition, HydrologyCaveAction> consumer
    ) {
        Objects.requireNonNull(consumer);
        if (maximumX <= minimumX || maximumZ <= minimumZ || !(actions instanceof ActionMap actionMap)) {
            return;
        }
        actionMap.forEachIn(minimumX, minimumZ, maximumX, maximumZ, consumer);
    }

    public boolean allPreconditionsIn(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ,
            BiPredicate<CavePosition, CaveVoxelPrecondition> predicate
    ) {
        Objects.requireNonNull(predicate);
        if (maximumX <= minimumX || maximumZ <= minimumZ
                || !(baselinePreconditions instanceof PreconditionMap preconditionMap)) {
            return true;
        }
        return preconditionMap.allIn(minimumX, minimumZ, maximumX, maximumZ, predicate);
    }

    private static byte encode(
            HydrologyCaveAction action,
            CaveVoxelPrecondition precondition
    ) {
        Objects.requireNonNull(precondition);
        int packed = precondition.voxel().ordinal() << VOXEL_SHIFT;
        if (precondition.openToSurface()) {
            packed |= OPEN_TO_SURFACE;
        }
        if (action != null) {
            packed |= ACTION_PRESENT | action.ordinal();
        }
        return (byte) packed;
    }

    private static boolean hasAction(byte packed) {
        return (packed & ACTION_PRESENT) != 0;
    }

    private static HydrologyCaveAction decodeAction(byte packed) {
        return ACTIONS[packed & ACTION_MASK];
    }

    private static CaveVoxelPrecondition decodePrecondition(byte packed) {
        int unsigned = Byte.toUnsignedInt(packed);
        int voxel = unsigned & VOXEL_MASK;
        int open = unsigned & OPEN_TO_SURFACE;
        return PRECONDITIONS[voxel >>> VOXEL_SHIFT][open == 0 ? 0 : 1];
    }

    private static CaveVoxelPrecondition[][] createPreconditions() {
        if (ACTIONS.length > ACTION_MASK + 1) {
            throw new IllegalStateException("Hydrology cave action packing capacity exceeded");
        }
        if (VOXELS.length > (VOXEL_MASK >>> VOXEL_SHIFT) + 1) {
            throw new IllegalStateException("Hydrology cave voxel packing capacity exceeded");
        }
        CaveVoxelPrecondition[][] preconditions = new CaveVoxelPrecondition[VOXELS.length][2];
        for (CaveVoxel voxel : VOXELS) {
            preconditions[voxel.ordinal()][0] = new CaveVoxelPrecondition(voxel, false);
            preconditions[voxel.ordinal()][1] = new CaveVoxelPrecondition(voxel, true);
        }
        return preconditions;
    }

    private static final class PackedPositions {
        private final int[] x;
        private final int[] y;
        private final int[] z;
        private final byte[] flags;
        private final int[] slots;
        private final int mask;
        private int count;

        private PackedPositions(Map<CavePosition, HydrologyCaveAction> actions,
                                Map<CavePosition, CaveVoxelPrecondition> preconditions) {
            int size = preconditions.size();
            x = new int[size];
            y = new int[size];
            z = new int[size];
            flags = new byte[size];
            long required = Math.max(2L, ((long) size * 4L + 2L) / 3L);
            if (required > 1 << 30) {
                throw new IllegalArgumentException("Too many cave positions");
            }
            int capacity = Integer.highestOneBit((int) required - 1) << 1;
            slots = new int[capacity];
            mask = capacity - 1;
            for (Map.Entry<CavePosition, HydrologyCaveAction> entry : actions.entrySet()) {
                append(entry.getKey(), encode(entry.getValue(), preconditions.get(entry.getKey())));
            }
            for (Map.Entry<CavePosition, CaveVoxelPrecondition> entry : preconditions.entrySet()) {
                if (row(entry.getKey()) < 0) {
                    append(entry.getKey(), encode(null, entry.getValue()));
                }
            }
        }

        private void append(CavePosition position, byte value) {
            int slot = slot(position);
            x[count] = position.x();
            y[count] = position.y();
            z[count] = position.z();
            flags[count] = value;
            slots[slot] = ++count;
        }

        private int slot(CavePosition position) {
            int hash = 0x811C9DC5;
            hash = (hash ^ position.x()) * 0x01000193;
            hash = (hash ^ position.y()) * 0x01000193;
            hash = (hash ^ position.z()) * 0x01000193;
            hash ^= hash >>> 16;
            hash *= 0x7FEB352D;
            hash ^= hash >>> 15;
            hash *= 0x846CA68B;
            hash ^= hash >>> 16;
            int slot = hash & mask;
            while (slots[slot] != 0) {
                int row = slots[slot] - 1;
                if (x[row] == position.x() && y[row] == position.y() && z[row] == position.z()) {
                    return slot;
                }
                slot = slot + 1 & mask;
            }
            return slot;
        }

        private int row(CavePosition position) {
            return slots[slot(position)] - 1;
        }

        private CavePosition position(int row) {
            return new CavePosition(x[row], y[row], z[row]);
        }

        private PositionOrder preconditionOrder(Map<CavePosition, CaveVoxelPrecondition> preconditions) {
            int[] order = new int[count];
            int index = 0;
            for (CavePosition position : preconditions.keySet()) {
                order[index++] = row(position);
            }
            return new PositionOrder(this, order);
        }

        public Byte get(Object key) {
            if (!(key instanceof CavePosition position)) {
                return null;
            }
            int row = row(position);
            return row < 0 ? null : flags[row];
        }

        public boolean containsKey(Object key) {
            return key instanceof CavePosition position && row(position) >= 0;
        }

        public int size() {
            return count;
        }
    }

    private static final class PositionOrder extends AbstractList<CavePosition> {
        private final PackedPositions packed;
        private final int[] rows;

        private PositionOrder(PackedPositions packed, int[] rows) {
            this.packed = packed;
            this.rows = rows;
        }

        @Override
        public CavePosition get(int index) {
            return packed.position(rows[index]);
        }

        @Override
        public int size() {
            return rows.length;
        }
    }

    private static final class ActionMap extends AbstractMap<CavePosition, HydrologyCaveAction> {
        private final PackedPositions packed;
        private final int size;
        private final PositionSpatialIndex spatialIndex;

        private ActionMap(PackedPositions packed, int size, PositionSpatialIndex spatialIndex) {
            this.packed = packed;
            this.size = size;
            this.spatialIndex = spatialIndex;
        }

        @Override
        public HydrologyCaveAction get(Object key) {
            Byte value = packed.get(key);
            return value == null || !hasAction(value) ? null : decodeAction(value);
        }

        @Override
        public boolean containsKey(Object key) {
            Byte value = packed.get(key);
            return value != null && hasAction(value);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public Set<CavePosition> keySet() {
            return new ActionKeySet(packed, size);
        }

        @Override
        public Set<Entry<CavePosition, HydrologyCaveAction>> entrySet() {
            return new ActionEntrySet(packed, size);
        }

        @Override
        public Collection<HydrologyCaveAction> values() {
            return new ActionValues(packed, size);
        }

        @Override
        public void forEach(BiConsumer<? super CavePosition, ? super HydrologyCaveAction> consumer) {
            Objects.requireNonNull(consumer);
            for (int row = 0; row < packed.size(); row++) {
                if (hasAction(packed.flags[row])) {
                    consumer.accept(packed.position(row), decodeAction(packed.flags[row]));
                }
            }
        }

        private boolean intersects(int minimumX, int minimumZ, int maximumX, int maximumZ) {
            return spatialIndex.anyIn(minimumX, minimumZ, maximumX, maximumZ,
                    (int row) -> hasAction(packed.flags[row]));
        }

        private void forEachIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                BiConsumer<CavePosition, HydrologyCaveAction> consumer
        ) {
            spatialIndex.forEachIn(minimumX, minimumZ, maximumX, maximumZ, (int row) -> {
                byte value = packed.flags[row];
                if (hasAction(value)) {
                    consumer.accept(packed.position(row), decodeAction(value));
                }
            });
        }
    }

    private static final class PreconditionMap extends AbstractMap<CavePosition, CaveVoxelPrecondition> {
        private final PackedPositions packed;
        private final PositionOrder order;
        private final PositionSpatialIndex spatialIndex;

        private PreconditionMap(
                PackedPositions packed,
                PositionOrder order,
                PositionSpatialIndex spatialIndex
        ) {
            this.packed = packed;
            this.order = order;
            this.spatialIndex = spatialIndex;
        }

        @Override
        public CaveVoxelPrecondition get(Object key) {
            Byte value = packed.get(key);
            return value == null ? null : decodePrecondition(value);
        }

        @Override
        public boolean containsKey(Object key) {
            return packed.containsKey(key);
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public Set<CavePosition> keySet() {
            return new PreconditionKeySet(packed, order);
        }

        @Override
        public Set<Entry<CavePosition, CaveVoxelPrecondition>> entrySet() {
            return new PreconditionEntrySet(packed, order);
        }

        @Override
        public Collection<CaveVoxelPrecondition> values() {
            return new PreconditionValues(packed, order);
        }

        @Override
        public void forEach(BiConsumer<? super CavePosition, ? super CaveVoxelPrecondition> consumer) {
            Objects.requireNonNull(consumer);
            for (int row : order.rows) {
                consumer.accept(packed.position(row), decodePrecondition(packed.flags[row]));
            }
        }

        private boolean allIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                BiPredicate<CavePosition, CaveVoxelPrecondition> predicate
        ) {
            return spatialIndex.allIn(minimumX, minimumZ, maximumX, maximumZ,
                    (int row) -> predicate.test(packed.position(row), decodePrecondition(packed.flags[row])));
        }
    }

    private static final class PositionSpatialIndex {
        private static final int CHUNK_SHIFT = 4;

        private final PackedPositions packed;
        private final Long2ObjectOpenHashMap<int[]> positionsByChunk;

        private PositionSpatialIndex(PackedPositions packed) {
            this.packed = packed;
            Long2ObjectOpenHashMap<IntArrayList> mutable = new Long2ObjectOpenHashMap<>();
            for (int row = 0; row < packed.size(); row++) {
                long chunkKey = packChunk(packed.x[row] >> CHUNK_SHIFT, packed.z[row] >> CHUNK_SHIFT);
                IntArrayList rows = mutable.get(chunkKey);
                if (rows == null) {
                    rows = new IntArrayList();
                    mutable.put(chunkKey, rows);
                }
                rows.add(row);
            }
            positionsByChunk = new Long2ObjectOpenHashMap<>(mutable.size());
            for (Long2ObjectMap.Entry<IntArrayList> entry : mutable.long2ObjectEntrySet()) {
                positionsByChunk.put(entry.getLongKey(), entry.getValue().toIntArray());
            }
        }

        private boolean anyIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                IntPredicate predicate
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    int[] positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (int row : positions) {
                        if (inside(row, minimumX, minimumZ, maximumX, maximumZ)
                                && predicate.test(row)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private boolean allIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                IntPredicate predicate
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    int[] positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (int row : positions) {
                        if (inside(row, minimumX, minimumZ, maximumX, maximumZ)
                                && !predicate.test(row)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void forEachIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                IntConsumer consumer
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    int[] positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (int row : positions) {
                        if (inside(row, minimumX, minimumZ, maximumX, maximumZ)) {
                            consumer.accept(row);
                        }
                    }
                }
            }
        }

        private boolean inside(
                int row,
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ
        ) {
            return packed.x[row] >= minimumX && packed.x[row] < maximumX
                    && packed.z[row] >= minimumZ && packed.z[row] < maximumZ;
        }

        private static long packChunk(int chunkX, int chunkZ) {
            return (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        }
    }

    private static final class ActionKeySet extends AbstractSet<CavePosition> {
        private final PackedPositions packed;
        private final int size;

        private ActionKeySet(PackedPositions packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<CavePosition> iterator() {
            return new ActionKeyIterator(packed);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object value) {
            Byte packedValue = packed.get(value);
            return packedValue != null && hasAction(packedValue);
        }
    }

    private static final class ActionEntrySet extends AbstractSet<Entry<CavePosition, HydrologyCaveAction>> {
        private final PackedPositions packed;
        private final int size;

        private ActionEntrySet(PackedPositions packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<Entry<CavePosition, HydrologyCaveAction>> iterator() {
            return new ActionEntryIterator(packed);
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class PreconditionEntrySet
            extends AbstractSet<Entry<CavePosition, CaveVoxelPrecondition>> {
        private final PackedPositions packed;
        private final PositionOrder order;

        private PreconditionEntrySet(PackedPositions packed, PositionOrder order) {
            this.packed = packed;
            this.order = order;
        }

        @Override
        public Iterator<Entry<CavePosition, CaveVoxelPrecondition>> iterator() {
            return new PreconditionEntryIterator(packed, order.rows);
        }

        @Override
        public int size() {
            return order.size();
        }
    }

    private static final class PreconditionKeySet extends AbstractSet<CavePosition> {
        private final PackedPositions packed;
        private final PositionOrder order;

        private PreconditionKeySet(PackedPositions packed, PositionOrder order) {
            this.packed = packed;
            this.order = order;
        }

        @Override
        public Iterator<CavePosition> iterator() {
            return order.iterator();
        }

        @Override
        public int size() {
            return order.size();
        }

        @Override
        public boolean contains(Object value) {
            return packed.containsKey(value);
        }
    }

    private static final class ActionValues extends AbstractCollection<HydrologyCaveAction> {
        private final PackedPositions packed;
        private final int size;

        private ActionValues(PackedPositions packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<HydrologyCaveAction> iterator() {
            return new ActionValueIterator(packed);
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class PreconditionValues extends AbstractCollection<CaveVoxelPrecondition> {
        private final PackedPositions packed;
        private final PositionOrder order;

        private PreconditionValues(PackedPositions packed, PositionOrder order) {
            this.packed = packed;
            this.order = order;
        }

        @Override
        public Iterator<CaveVoxelPrecondition> iterator() {
            return new PreconditionValueIterator(packed, order.rows);
        }

        @Override
        public int size() {
            return order.size();
        }
    }

    private abstract static class FilteredActionIterator<T> implements Iterator<T> {
        protected final PackedPositions packed;
        private int next;

        private FilteredActionIterator(PackedPositions packed) {
            this.packed = packed;
        }

        @Override
        public boolean hasNext() {
            advance();
            return next < packed.size();
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return map(next++);
        }

        protected abstract T map(int row);

        private void advance() {
            while (next < packed.size() && !hasAction(packed.flags[next])) {
                next++;
            }
        }
    }

    private static final class ActionKeyIterator extends FilteredActionIterator<CavePosition> {
        private ActionKeyIterator(PackedPositions packed) {
            super(packed);
        }

        @Override
        protected CavePosition map(int row) {
            return packed.position(row);
        }
    }

    private static final class ActionEntryIterator
            extends FilteredActionIterator<Entry<CavePosition, HydrologyCaveAction>> {
        private ActionEntryIterator(PackedPositions packed) {
            super(packed);
        }

        @Override
        protected Entry<CavePosition, HydrologyCaveAction> map(int row) {
            return Map.entry(packed.position(row), decodeAction(packed.flags[row]));
        }
    }

    private static final class ActionValueIterator extends FilteredActionIterator<HydrologyCaveAction> {
        private ActionValueIterator(PackedPositions packed) {
            super(packed);
        }

        @Override
        protected HydrologyCaveAction map(int row) {
            return decodeAction(packed.flags[row]);
        }
    }

    private static final class PreconditionEntryIterator
            implements Iterator<Entry<CavePosition, CaveVoxelPrecondition>> {
        private final PackedPositions packed;
        private final int[] rows;
        private int index;

        private PreconditionEntryIterator(
                PackedPositions packed,
                int[] rows
        ) {
            this.packed = packed;
            this.rows = rows;
        }

        @Override
        public boolean hasNext() {
            return index < rows.length;
        }

        @Override
        public Entry<CavePosition, CaveVoxelPrecondition> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            int row = rows[index++];
            return Map.entry(packed.position(row), decodePrecondition(packed.flags[row]));
        }
    }

    private static final class PreconditionValueIterator implements Iterator<CaveVoxelPrecondition> {
        private final PackedPositions packed;
        private final int[] rows;
        private int index;

        private PreconditionValueIterator(
                PackedPositions packed,
                int[] rows
        ) {
            this.packed = packed;
            this.rows = rows;
        }

        @Override
        public boolean hasNext() {
            return index < rows.length;
        }

        @Override
        public CaveVoxelPrecondition next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return decodePrecondition(packed.flags[rows[index++]]);
        }
    }
}
