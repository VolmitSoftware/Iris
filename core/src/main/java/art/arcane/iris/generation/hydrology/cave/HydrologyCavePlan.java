package art.arcane.iris.generation.hydrology.cave;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

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
            LinkedHashMap<CavePosition, Byte> packed = new LinkedHashMap<>(requestedPreconditions.size());
            for (Map.Entry<CavePosition, HydrologyCaveAction> entry : requestedActions.entrySet()) {
                CaveVoxelPrecondition precondition = requestedPreconditions.get(entry.getKey());
                packed.put(entry.getKey(), encode(entry.getValue(), precondition));
            }
            for (Map.Entry<CavePosition, CaveVoxelPrecondition> entry : requestedPreconditions.entrySet()) {
                packed.putIfAbsent(entry.getKey(), encode(null, entry.getValue()));
            }
            Map<CavePosition, Byte> immutablePacked = Collections.unmodifiableMap(packed);
            PositionSpatialIndex spatialIndex = new PositionSpatialIndex(immutablePacked);
            actions = new ActionMap(immutablePacked, requestedActions.size(), spatialIndex);
            baselinePreconditions = new PreconditionMap(
                    immutablePacked,
                    List.copyOf(requestedPreconditions.keySet()),
                    spatialIndex
            );
        }
    }

    public boolean accepted() {
        return rejection == HydrologyCaveRejection.NONE;
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

    private static final class ActionMap extends AbstractMap<CavePosition, HydrologyCaveAction> {
        private final Map<CavePosition, Byte> packed;
        private final int size;
        private final PositionSpatialIndex spatialIndex;

        private ActionMap(Map<CavePosition, Byte> packed, int size, PositionSpatialIndex spatialIndex) {
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
            for (Map.Entry<CavePosition, Byte> entry : packed.entrySet()) {
                if (hasAction(entry.getValue())) {
                    consumer.accept(entry.getKey(), decodeAction(entry.getValue()));
                }
            }
        }

        private boolean intersects(int minimumX, int minimumZ, int maximumX, int maximumZ) {
            return spatialIndex.anyIn(minimumX, minimumZ, maximumX, maximumZ,
                    (CavePosition position) -> hasAction(packed.get(position)));
        }

        private void forEachIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                BiConsumer<CavePosition, HydrologyCaveAction> consumer
        ) {
            spatialIndex.forEachIn(minimumX, minimumZ, maximumX, maximumZ, (CavePosition position) -> {
                byte value = packed.get(position);
                if (hasAction(value)) {
                    consumer.accept(position, decodeAction(value));
                }
            });
        }
    }

    private static final class PreconditionMap extends AbstractMap<CavePosition, CaveVoxelPrecondition> {
        private final Map<CavePosition, Byte> packed;
        private final List<CavePosition> order;
        private final PositionSpatialIndex spatialIndex;

        private PreconditionMap(
                Map<CavePosition, Byte> packed,
                List<CavePosition> order,
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
            for (CavePosition position : order) {
                consumer.accept(position, decodePrecondition(packed.get(position)));
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
                    (CavePosition position) -> predicate.test(position, decodePrecondition(packed.get(position))));
        }
    }

    private static final class PositionSpatialIndex {
        private static final int CHUNK_SHIFT = 4;

        private final Map<Long, List<CavePosition>> positionsByChunk;

        private PositionSpatialIndex(Map<CavePosition, Byte> packed) {
            HashMap<Long, ArrayList<CavePosition>> mutable = new HashMap<>();
            for (CavePosition position : packed.keySet()) {
                long chunkKey = packChunk(position.x() >> CHUNK_SHIFT, position.z() >> CHUNK_SHIFT);
                mutable.computeIfAbsent(chunkKey, ignored -> new ArrayList<>()).add(position);
            }
            HashMap<Long, List<CavePosition>> immutable = HashMap.newHashMap(mutable.size());
            for (Map.Entry<Long, ArrayList<CavePosition>> entry : mutable.entrySet()) {
                immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            positionsByChunk = Map.copyOf(immutable);
        }

        private boolean anyIn(
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ,
                java.util.function.Predicate<CavePosition> predicate
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    List<CavePosition> positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (CavePosition position : positions) {
                        if (inside(position, minimumX, minimumZ, maximumX, maximumZ)
                                && predicate.test(position)) {
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
                java.util.function.Predicate<CavePosition> predicate
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    List<CavePosition> positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (CavePosition position : positions) {
                        if (inside(position, minimumX, minimumZ, maximumX, maximumZ)
                                && !predicate.test(position)) {
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
                java.util.function.Consumer<CavePosition> consumer
        ) {
            int minimumChunkX = minimumX >> CHUNK_SHIFT;
            int maximumChunkX = maximumX - 1 >> CHUNK_SHIFT;
            int minimumChunkZ = minimumZ >> CHUNK_SHIFT;
            int maximumChunkZ = maximumZ - 1 >> CHUNK_SHIFT;
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
                    List<CavePosition> positions = positionsByChunk.get(packChunk(chunkX, chunkZ));
                    if (positions == null) {
                        continue;
                    }
                    for (CavePosition position : positions) {
                        if (inside(position, minimumX, minimumZ, maximumX, maximumZ)) {
                            consumer.accept(position);
                        }
                    }
                }
            }
        }

        private static boolean inside(
                CavePosition position,
                int minimumX,
                int minimumZ,
                int maximumX,
                int maximumZ
        ) {
            return position.x() >= minimumX && position.x() < maximumX
                    && position.z() >= minimumZ && position.z() < maximumZ;
        }

        private static long packChunk(int chunkX, int chunkZ) {
            return (long) chunkX << 32 ^ chunkZ & 0xffffffffL;
        }
    }

    private static final class ActionKeySet extends AbstractSet<CavePosition> {
        private final Map<CavePosition, Byte> packed;
        private final int size;

        private ActionKeySet(Map<CavePosition, Byte> packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<CavePosition> iterator() {
            return new ActionKeyIterator(packed.entrySet().iterator());
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
        private final Map<CavePosition, Byte> packed;
        private final int size;

        private ActionEntrySet(Map<CavePosition, Byte> packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<Entry<CavePosition, HydrologyCaveAction>> iterator() {
            return new ActionEntryIterator(packed.entrySet().iterator());
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class PreconditionEntrySet
            extends AbstractSet<Entry<CavePosition, CaveVoxelPrecondition>> {
        private final Map<CavePosition, Byte> packed;
        private final List<CavePosition> order;

        private PreconditionEntrySet(Map<CavePosition, Byte> packed, List<CavePosition> order) {
            this.packed = packed;
            this.order = order;
        }

        @Override
        public Iterator<Entry<CavePosition, CaveVoxelPrecondition>> iterator() {
            return new PreconditionEntryIterator(packed, order.iterator());
        }

        @Override
        public int size() {
            return order.size();
        }
    }

    private static final class PreconditionKeySet extends AbstractSet<CavePosition> {
        private final Map<CavePosition, Byte> packed;
        private final List<CavePosition> order;

        private PreconditionKeySet(Map<CavePosition, Byte> packed, List<CavePosition> order) {
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
        private final Map<CavePosition, Byte> packed;
        private final int size;

        private ActionValues(Map<CavePosition, Byte> packed, int size) {
            this.packed = packed;
            this.size = size;
        }

        @Override
        public Iterator<HydrologyCaveAction> iterator() {
            return new ActionValueIterator(packed.entrySet().iterator());
        }

        @Override
        public int size() {
            return size;
        }
    }

    private static final class PreconditionValues extends AbstractCollection<CaveVoxelPrecondition> {
        private final Map<CavePosition, Byte> packed;
        private final List<CavePosition> order;

        private PreconditionValues(Map<CavePosition, Byte> packed, List<CavePosition> order) {
            this.packed = packed;
            this.order = order;
        }

        @Override
        public Iterator<CaveVoxelPrecondition> iterator() {
            return new PreconditionValueIterator(packed, order.iterator());
        }

        @Override
        public int size() {
            return order.size();
        }
    }

    private abstract static class FilteredActionIterator<T> implements Iterator<T> {
        private final Iterator<Map.Entry<CavePosition, Byte>> entries;
        private Map.Entry<CavePosition, Byte> next;

        private FilteredActionIterator(Iterator<Map.Entry<CavePosition, Byte>> entries) {
            this.entries = entries;
        }

        @Override
        public boolean hasNext() {
            advance();
            return next != null;
        }

        @Override
        public T next() {
            advance();
            if (next == null) {
                throw new NoSuchElementException();
            }
            Map.Entry<CavePosition, Byte> selected = next;
            next = null;
            return map(selected);
        }

        protected abstract T map(Map.Entry<CavePosition, Byte> entry);

        private void advance() {
            while (next == null && entries.hasNext()) {
                Map.Entry<CavePosition, Byte> candidate = entries.next();
                if (hasAction(candidate.getValue())) {
                    next = candidate;
                }
            }
        }
    }

    private static final class ActionKeyIterator extends FilteredActionIterator<CavePosition> {
        private ActionKeyIterator(Iterator<Map.Entry<CavePosition, Byte>> entries) {
            super(entries);
        }

        @Override
        protected CavePosition map(Map.Entry<CavePosition, Byte> entry) {
            return entry.getKey();
        }
    }

    private static final class ActionEntryIterator
            extends FilteredActionIterator<Entry<CavePosition, HydrologyCaveAction>> {
        private ActionEntryIterator(Iterator<Map.Entry<CavePosition, Byte>> entries) {
            super(entries);
        }

        @Override
        protected Entry<CavePosition, HydrologyCaveAction> map(Map.Entry<CavePosition, Byte> entry) {
            return Map.entry(entry.getKey(), decodeAction(entry.getValue()));
        }
    }

    private static final class ActionValueIterator extends FilteredActionIterator<HydrologyCaveAction> {
        private ActionValueIterator(Iterator<Map.Entry<CavePosition, Byte>> entries) {
            super(entries);
        }

        @Override
        protected HydrologyCaveAction map(Map.Entry<CavePosition, Byte> entry) {
            return decodeAction(entry.getValue());
        }
    }

    private static final class PreconditionEntryIterator
            implements Iterator<Entry<CavePosition, CaveVoxelPrecondition>> {
        private final Map<CavePosition, Byte> packed;
        private final Iterator<CavePosition> positions;

        private PreconditionEntryIterator(
                Map<CavePosition, Byte> packed,
                Iterator<CavePosition> positions
        ) {
            this.packed = packed;
            this.positions = positions;
        }

        @Override
        public boolean hasNext() {
            return positions.hasNext();
        }

        @Override
        public Entry<CavePosition, CaveVoxelPrecondition> next() {
            CavePosition position = positions.next();
            return Map.entry(position, decodePrecondition(packed.get(position)));
        }
    }

    private static final class PreconditionValueIterator implements Iterator<CaveVoxelPrecondition> {
        private final Map<CavePosition, Byte> packed;
        private final Iterator<CavePosition> positions;

        private PreconditionValueIterator(
                Map<CavePosition, Byte> packed,
                Iterator<CavePosition> positions
        ) {
            this.packed = packed;
            this.positions = positions;
        }

        @Override
        public boolean hasNext() {
            return positions.hasNext();
        }

        @Override
        public CaveVoxelPrecondition next() {
            return decodePrecondition(packed.get(positions.next()));
        }
    }
}
