package art.arcane.iris.generation.mantle;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.jetbrains.annotations.Nullable;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ObjectDestinationTransaction implements ObjectPassPlacer {
    private static final Object CLEARED = new Object();
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final MantleWriter writer;
    private final int destinationChunkX;
    private final int destinationChunkZ;
    private final int worldHeight;
    private final MutationLog log = new MutationLog();
    private final List<PlacementRange> placements = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<OverlayCell>> overlay;
    private BlockPosition canopyPosition;
    private boolean subterrainKnown;
    private int subterrainX;
    private int subterrainY;
    private int subterrainZ;
    private boolean subterrainProtects;
    private boolean hydrologyKnown;
    private HydrologyRuntime hydrology;

    ObjectDestinationTransaction(MantleWriter writer, int destinationChunkX, int destinationChunkZ) {
        this.writer = writer;
        this.destinationChunkX = destinationChunkX;
        this.destinationChunkZ = destinationChunkZ;
        this.worldHeight = writer.getMantle().getWorldHeight();
        this.overlay = new Long2ObjectOpenHashMap<>();
    }

    void commit() {
        writer.withChunkFence(destinationChunkX, destinationChunkZ, this::commitFenced);
    }

    int mutationCheckpoint() {
        return log.size();
    }

    ObjectSourcePlan sourcePlanSince(int checkpoint) {
        if (checkpoint < 0 || checkpoint > log.size()) {
            throw new IllegalArgumentException("Mutation checkpoint is outside the transaction");
        }
        ArrayList<PlacementRange> accepted = new ArrayList<>();
        for (PlacementRange placement : placements) {
            if (placement.start() >= checkpoint) {
                accepted.add(new PlacementRange(placement.start() - checkpoint, placement.end() - checkpoint));
            }
        }
        return new ObjectSourcePlan(log, checkpoint, accepted);
    }

    @Override
    public int beginObjectPlacement() {
        return log.size();
    }

    @Override
    public void endObjectPlacement(int checkpoint) {
        if (checkpoint >= 0 && checkpoint < log.size()) {
            placements.add(new PlacementRange(checkpoint, log.size()));
        }
    }

    void apply(ObjectSourcePlan plan) {
        int slot = plan.destinationSlot(destinationChunkX, destinationChunkZ);
        if (slot < 0) {
            return;
        }
        int baseX = destinationChunkX << 4;
        int baseZ = destinationChunkZ << 4;
        boolean skipCanopy = false;
        int end = plan.slotEnd(slot);
        for (int index = plan.slotStart(slot); index < end; index++) {
            int cell = plan.cellAt(index);
            int x = baseX | ObjectSourcePlan.cellX(cell);
            int y = ObjectSourcePlan.cellY(cell);
            int z = baseZ | ObjectSourcePlan.cellZ(cell);
            Object value = plan.valueAt(index);
            boolean custom = ObjectSourcePlan.isCustom(cell);
            boolean canopy = ObjectSourcePlan.isCanopy(cell);
            if (custom || value instanceof NativeBlockState) {
                NativeBlockState existing = canopy ? get(x, y, z) : null;
                skipCanopy = existing != null && existing.isSolid();
            }
            if (canopy && skipCanopy) {
                continue;
            }
            if (custom) {
                set(x, y, z, (NativeBlockState) value, true, canopy);
            } else {
                setData(x, y, z, value, true, canopy);
            }
        }
    }

    private void commitFenced() {
        CommitJournal journal = new CommitJournal();
        try {
            for (int position = 0; position < log.size(); position++) {
                int x = log.x(position);
                int z = log.z(position);
                if (!isDestination(x, z)) {
                    continue;
                }
                int y = log.y(position);
                Object value = log.value(position);
                boolean custom = log.custom(position);
                Class<?> type = log.type(position);
                if (!journal.captured(x, y, z, type)) {
                    journal.capture(x, y, z, type, publishedOrCleared(x, y, z, type));
                }
                if (type == NativeBlockState.class) {
                    if (!journal.captured(x, y, z, TileWrapper.class)) {
                        TileWrapper tile = writer.getDataIfPresent(x, y, z, TileWrapper.class);
                        if (tile != null) {
                            journal.capture(x, y, z, TileWrapper.class, tile);
                        }
                    }
                    if (!journal.captured(x, y, z, Identifier.class)) {
                        Identifier identifier = writer.getDataIfPresent(x, y, z, Identifier.class);
                        if (identifier != null || custom) {
                            journal.capture(x, y, z, Identifier.class, identifier == null ? CLEARED : identifier);
                        }
                    }
                }
                if (custom) {
                    writer.set(x, y, z, (NativeBlockState) value);
                } else {
                    writer.setData(x, y, z, value);
                }
            }
        } catch (Throwable failure) {
            journal.rollback(failure);
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Object destination publication failed", failure);
        }
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return writer.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return writer.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, NativeBlockState state) {
        set(x, y, z, state, false, false);
    }

    private void set(int x, int y, int z, NativeBlockState state, boolean replayed, boolean replayedCanopy) {
        if (state == null) {
            return;
        }
        String placementKey = state.deferredPlacementKey();
        NativeBlockState baseState = state.placementBaseState();
        if (state.isCustom() && placementKey != null && baseState != null) {
            if (!canSetBlock(x, y, z) || hydrologyKeeps(x, y, z, baseState.isSolid())) {
                return;
            }
            Identifier identifier = Identifier.fromString(placementKey);
            OverlayCell cell = writableCell(x, y, z);
            cell.block = baseState;
            cell.tile = CLEARED;
            cell.identifier = identifier;
            log.add(x, y, z, state, true, replayed ? replayedCanopy : isCanopy(x, y, z));
            return;
        }
        setData(x, y, z, state);
    }

    @Override
    public boolean beginTreeCanopy(BlockPosition position) {
        if (!ObjectPassPlacer.super.beginTreeCanopy(position)) {
            return false;
        }
        canopyPosition = position;
        return true;
    }

    @Override
    public void endTreeCanopy() {
        canopyPosition = null;
    }

    private boolean isCanopy(int x, int y, int z) {
        return canopyPosition != null && canopyPosition.getX() == x
                && canopyPosition.getY() == y && canopyPosition.getZ() == z;
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        OverlayCell cell = overlayCell(x, y, z);
        Object value = cell == null ? null : cell.block;
        if (value == CLEARED) {
            return EngineMantle.AIR.get();
        }
        return value instanceof NativeBlockState state
                ? state
                : writer.getPrerequisiteBlock(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return writer.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        OverlayCell cell = overlayCell(x, y, z);
        if (cell == null) {
            return writer.isPrerequisiteCarved(x, y, z);
        }
        Object block = cell.block;
        if (block instanceof NativeBlockState state && !state.isAir() && !state.isFluid()) {
            return false;
        }
        Object hydrologyValue = cell.hydrology;
        if (hydrologyValue instanceof HydrologyCaveCell hydrology) {
            return hydrology.carves();
        }
        Object cavern = cell.cavern;
        return cavern == null ? writer.isPrerequisiteCarved(x, y, z) : cavern != CLEARED;
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return writer.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return writer.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return writer.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return writer.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (tile != null) {
            setData(x, y, z, new TileWrapper(tile));
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        setData(x, y, z, data, false, false);
    }

    private void setData(int x, int y, int z, Object data, boolean replayed, boolean replayedCanopy) {
        if (data == null || y < 0 || y >= worldHeight
                || protectsSubterrain(x, y, z)) {
            return;
        }
        if (data instanceof NativeBlockState state && (!canSetBlock(x, y, z) || hydrologyKeeps(x, y, z, state.isSolid()))) {
            return;
        }
        if (data instanceof MatterCavern && (hasProtectedHydrology(x, y, z) || hydrologyKeeps(x, y, z, false))) {
            return;
        }
        Class<?> type = data instanceof NativeBlockState ? NativeBlockState.class : data.getClass();
        OverlayCell cell = writableCell(x, y, z);
        if (data instanceof NativeBlockState) {
            cell.block = data;
            cell.tile = CLEARED;
            cell.identifier = CLEARED;
        } else {
            cell.put(type, data);
        }
        log.add(x, y, z, data, false, replayed ? replayedCanopy : isCanopy(x, y, z));
    }

    @Override
    public <T> @Nullable T getDataIfPresent(int x, int y, int z, Class<T> type) {
        OverlayCell cell = overlayCell(x, y, z);
        Object value = cell == null ? null : cell.get(type);
        if (value == CLEARED) {
            return null;
        }
        if (value != null) {
            return type.cast(value);
        }
        return writer.getPrerequisiteDataIfPresent(x, y, z, type);
    }

    @Override
    public byte[] getCarvedColumn(int x, int z, int height) {
        int cappedHeight = Math.min(Math.max(height, 0), worldHeight);
        byte[] carved = writer.getPrerequisiteCarvedColumn(x, z, cappedHeight);
        if (carved.length != cappedHeight) {
            byte[] resized = new byte[cappedHeight];
            System.arraycopy(carved, 0, resized, 0, Math.min(carved.length, cappedHeight));
            carved = resized;
        } else {
            carved = carved.clone();
        }
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(columnKey(x, z));
        if (column == null) {
            return carved;
        }
        for (Int2ObjectMap.Entry<OverlayCell> entry : column.int2ObjectEntrySet()) {
            int y = entry.getIntKey();
            if (y >= cappedHeight) {
                continue;
            }
            OverlayCell cell = entry.getValue();
            if (cell.block instanceof NativeBlockState state && !state.isAir() && !state.isFluid()) {
                carved[y] = 0;
            } else if (cell.hydrology instanceof HydrologyCaveCell hydrology) {
                carved[y] = hydrology.carves() ? (byte) 1 : 0;
            } else if (cell.cavern != null) {
                carved[y] = cell.cavern == CLEARED ? (byte) 0 : (byte) 1;
            }
        }
        return carved;
    }

    @Override
    public Engine getEngine() {
        return writer.getEngine();
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private OverlayCell overlayCell(int x, int y, int z) {
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(columnKey(x, z));
        return column == null ? null : column.get(y);
    }

    private OverlayCell writableCell(int x, int y, int z) {
        long coordinate = columnKey(x, z);
        Int2ObjectOpenHashMap<OverlayCell> column = overlay.get(coordinate);
        if (column == null) {
            column = new Int2ObjectOpenHashMap<>(4);
            overlay.put(coordinate, column);
        }
        OverlayCell cell = column.get(y);
        if (cell == null) {
            cell = new OverlayCell();
            column.put(y, cell);
        }
        return cell;
    }

    private Object publishedOrCleared(int x, int y, int z, Class<?> type) {
        Object value = writer.getDataIfPresent(x, y, z, type);
        return value == null ? CLEARED : value;
    }

    private boolean isDestination(int x, int z) {
        return (x >> 4) == destinationChunkX && (z >> 4) == destinationChunkZ;
    }

    private boolean canSetBlock(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) {
            return false;
        }
        if (y == 0 && writer.getEngine().getDimension().isBedrock()) {
            return false;
        }
        return !hasProtectedHydrology(x, y, z)
                && !protectsSubterrain(x, y, z);
    }

    private boolean hydrologyKeeps(int x, int y, int z, boolean holdsWater) {
        HydrologyRuntime runtime = hydrology();
        if (runtime == null) {
            return false;
        }
        ChunkWaterRaster raster = runtime.raster(x >> 4, z >> 4);
        if (raster.empty()) {
            return false;
        }
        WaterRole role = raster.role(x & 15, z & 15);
        if (role == WaterRole.INLET && y >= raster.waterHead(x & 15, z & 15) && y <= getEngine().getComplex().naturalTrueHeight(x, z)) {
            return true;
        }
        if (role.wet()) {
            int surface = raster.waterHead(x & 15, z & 15);
            int fallTop = highestHead(runtime, raster, x, z);
            if (fallTop > surface && y >= surface && y <= fallTop) {
                return true;
            }
            return !holdsWater && y >= raster.terrainHeight(x & 15, z & 15) && y <= surface;
        }
        if (holdsWater || role == WaterRole.NONE) {
            return false;
        }
        int head = Integer.MIN_VALUE;
        int bed = Integer.MAX_VALUE;
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            ChunkWaterRaster neighbour = neighbour(runtime, raster, x, z, nx, nz);
            if (neighbour.role(nx & 15, nz & 15).wet()) {
                head = Math.max(head, neighbour.waterHead(nx & 15, nz & 15));
                bed = Math.min(bed, neighbour.terrainHeight(nx & 15, nz & 15));
            }
        }
        return y > bed && y <= head;
    }

    private static int highestHead(HydrologyRuntime runtime, ChunkWaterRaster raster, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            top = Math.max(top, neighbour(runtime, raster, x, z, nx, nz).waterHead(nx & 15, nz & 15));
        }
        return top;
    }

    private static ChunkWaterRaster neighbour(HydrologyRuntime runtime, ChunkWaterRaster raster, int x, int z, int nx, int nz) {
        return (nx >> 4) == (x >> 4) && (nz >> 4) == (z >> 4) ? raster : runtime.raster(nx >> 4, nz >> 4);
    }

    private HydrologyRuntime hydrology() {
        if (!hydrologyKnown) {
            Engine engine = getEngine();
            IrisComplex complex = engine == null ? null : engine.getComplex();
            HydrologyRuntime runtime = complex == null ? null : complex.getHydrologyRuntime();
            hydrology = runtime != null && runtime.enabled() ? runtime : null;
            hydrologyKnown = true;
        }
        return hydrology;
    }

    private boolean protectsSubterrain(int x, int y, int z) {
        if (subterrainKnown && x == subterrainX && y == subterrainY && z == subterrainZ) {
            return subterrainProtects;
        }
        Engine engine = getEngine();
        boolean protects = engine != null && SubterrainRasterizer.protectsPlacement(engine.getSubterrainCell(x, y, z));
        subterrainX = x;
        subterrainY = y;
        subterrainZ = z;
        subterrainProtects = protects;
        subterrainKnown = true;
        return protects;
    }

    private boolean hasProtectedHydrology(int x, int y, int z) {
        HydrologyCaveCell hydrology = getDataIfPresent(x, y, z, HydrologyCaveCell.class);
        return hydrology != null && hydrology.protectsPlacement();
    }

    private final class CommitJournal {
        private final Reference2IntOpenHashMap<Class<?>> typeIds = new Reference2IntOpenHashMap<>();
        private final LongOpenHashSet keys = new LongOpenHashSet();
        private final ArrayList<Original> originals = new ArrayList<>();

        private long key(int x, int y, int z, Class<?> type) {
            int typeId = typeIds.computeIfAbsent(type, (Class<?> ignored) -> typeIds.size());
            return ((long) typeId << 32) | ((long) y << 8) | ((long) (z & 15) << 4) | (x & 15);
        }

        private boolean captured(int x, int y, int z, Class<?> type) {
            return keys.contains(key(x, y, z, type));
        }

        private void capture(int x, int y, int z, Class<?> type, Object value) {
            if (keys.add(key(x, y, z, type))) {
                originals.add(new Original(x, y, z, type, value));
            }
        }

        private void rollback(Throwable failure) {
            for (int index = originals.size() - 1; index >= 0; index--) {
                Original original = originals.get(index);
                try {
                    writer.restoreData(original.x(), original.y(), original.z(), original.type(),
                            original.value() == CLEARED ? null : original.value());
                } catch (Throwable rollbackFailure) {
                    if (rollbackFailure != failure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
        }
    }

    private record Original(int x, int y, int z, Class<?> type, Object value) {
    }

    private static final class OverlayCell {
        private Object block;
        private Object hydrology;
        private Object cavern;
        private Object identifier;
        private Object tile;
        private Object string;
        private Object treeMaterial;
        private Map<Class<?>, Object> other;

        private Object get(Class<?> type) {
            if (type == NativeBlockState.class) {
                return block;
            }
            if (type == HydrologyCaveCell.class) {
                return hydrology;
            }
            if (type == MatterCavern.class) {
                return cavern;
            }
            if (type == Identifier.class) {
                return identifier;
            }
            if (type == TileWrapper.class) {
                return tile;
            }
            if (type == String.class) {
                return string;
            }
            if (type == TreeBlockMaterial.class) {
                return treeMaterial;
            }
            return other == null ? null : other.get(type);
        }

        private void put(Class<?> type, Object value) {
            if (type == HydrologyCaveCell.class) {
                hydrology = value;
            } else if (type == MatterCavern.class) {
                cavern = value;
            } else if (type == Identifier.class) {
                identifier = value;
            } else if (type == TileWrapper.class) {
                tile = value;
            } else if (type == String.class) {
                string = value;
            } else if (type == TreeBlockMaterial.class) {
                treeMaterial = value;
            } else {
                if (other == null) {
                    other = new HashMap<>();
                }
                other.put(type, value);
            }
        }
    }

    record PlacementRange(int start, int end) {
    }

    record DataKey(int x, int y, int z, Class<?> type) {
    }

    sealed interface Mutation permits SetMutation, CustomBlockMutation {
        int x();

        int z();

        DataKey key();

        boolean canopy();
    }

    record SetMutation(DataKey key, Object value, boolean canopy) implements Mutation {
        @Override
        public int x() {
            return key.x();
        }

        @Override
        public int z() {
            return key.z();
        }
    }

    record CustomBlockMutation(DataKey key, NativeBlockState state, boolean canopy) implements Mutation {
        @Override
        public int x() {
            return key.x();
        }

        @Override
        public int z() {
            return key.z();
        }
    }
}
