package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.nativelib.terrain.NativeBlockChangeSet;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.MantleClosedException;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class DeferredPlacementRecords {
    private static final Set<MantleFlag> RETAINED_MARGIN_FLAGS = Set.of(
            MantleFlag.TILE, MantleFlag.CUSTOM, MantleFlag.UPDATE, MantleFlag.ETCHED,
            MantleFlag.INITIAL_SPAWNED, MantleFlag.INITIAL_SPAWNED_MARKER, MantleFlag.DISCOVERED);

    private DeferredPlacementRecords() {
    }

    public static void resetMantleChunk(Mantle<Matter> mantle, int chunkX, int chunkZ, boolean target) {
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                for (int index = 0; index < chunk.sectionCount(); index++) {
                    Matter section = chunk.get(index);
                    if (section == null) {
                        continue;
                    }
                    section.getSliceTypes().removeIf(type -> type != CustomBlockOwnership.class);
                    if (section.isEmpty()) {
                        chunk.delete(index);
                    }
                }
                for (ReservedFlag flag : ReservedFlag.values()) {
                    if (target || !RETAINED_MARGIN_FLAGS.contains(flag)) {
                        chunk.flag(flag, false);
                    }
                }
                for (int ordinal = MantleFlag.MIN_ORDINAL; ordinal <= MantleFlag.MAX_ORDINAL; ordinal++) {
                    chunk.flag(MantleFlag.of(ordinal), false);
                }
            }
        } finally {
            chunk.release();
        }
    }

    public static void invalidateCustomBlockOwnership(MantleChunk<Matter> chunk) {
        synchronized (chunk) {
            List<Pending<CustomBlockOwnership>> records =
                    snapshot(chunk, CustomBlockOwnership.class);
            for (Pending<CustomBlockOwnership> record : records) {
                NativeBlockPoint position = record.position();
                chunk.get(position.y() >> 4).slice(CustomBlockOwnership.class)
                        .set(position.x() & 15, position.y() & 15, position.z() & 15, record.value().invalidate());
            }
        }
    }

    public static <T> List<Pending<T>> snapshot(MantleChunk<Matter> chunk, Class<T> type) {
        List<Pending<T>> records = new ArrayList<>();
        synchronized (chunk) {
            chunk.iterate(type, (x, y, z, value) -> records.add(new Pending<>(new NativeBlockPoint(x, y, z), value)));
        }
        return records;
    }

    public static <T> boolean isPending(MantleChunk<Matter> chunk, Class<T> type, Pending<T> record) {
        NativeBlockPoint position = record.position();
        synchronized (chunk) {
            return Objects.equals(record.value(), chunk.get(position.x(), position.y(), position.z(), type));
        }
    }

    public static NativeBlockChangeSet track(Engine engine, NativeBlockChangeSet.Bounds bounds) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
            for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                if (hasRecords(mantle, chunkX, chunkZ)) {
                    return NativeBlockChangeSet.create(bounds);
                }
            }
        }
        return NativeBlockChangeSet.disabled();
    }

    public static void invalidate(Engine engine, NativeBlockChangeSet changes) {
        if (changes.isEmpty()) {
            return;
        }
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int minHeight = engine.getMinHeight();
        changes.forEachChunk((chunkX, chunkZ) -> {
            MantleChunk<Matter> chunk = invalidateChunk(mantle, changes, chunkX, chunkZ, minHeight);
            if (chunk != null) {
                scheduleRemoval(engine, chunk, minHeight);
            }
        });
    }

    public static NativeBlockChangeSet positions(Engine engine, NativeBlockChangeSet.Bounds bounds) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        NativeBlockChangeSet positions = null;
        int minHeight = engine.getMinHeight();
        for (int chunkX = bounds.minChunkX(); chunkX <= bounds.maxChunkX(); chunkX++) {
            for (int chunkZ = bounds.minChunkZ(); chunkZ <= bounds.maxChunkZ(); chunkZ++) {
                MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
                try {
                    synchronized (chunk) {
                        if (!hasRecords(chunk)) {
                            continue;
                        }
                        if (positions == null) {
                            positions = NativeBlockChangeSet.create(bounds);
                        }
                        collectPositions(chunk, positions, Identifier.class, minHeight);
                        collectPositions(chunk, positions, TileWrapper.class, minHeight);
                        collectPositions(chunk, positions, CustomBlockOwnership.class, minHeight);
                    }
                } finally {
                    chunk.release();
                }
            }
        }
        return positions == null || positions.isEmpty() ? NativeBlockChangeSet.disabled() : positions;
    }

    public static void remember(Engine engine, MantleChunk<Matter> chunk, NativeBlockPoint position,
                                CustomBlockOwnership.Entry entry) {
        boolean invalidated;
        synchronized (chunk) {
            Matter section = chunk.getOrCreate(position.y() >> 4);
            MatterSlice<Identifier> deferred = section.getSlice(Identifier.class);
            invalidated = entry.invalidated() || deferred == null
                    || deferred.get(position.x() & 15, position.y() & 15, position.z() & 15) == null;
            CustomBlockOwnership.Entry completed = invalidated
                    ? new CustomBlockOwnership.Entry(entry.identifier(), entry.token(), true) : entry;
            MatterSlice<CustomBlockOwnership> slice = section.slice(CustomBlockOwnership.class);
            CustomBlockOwnership existing = slice.get(position.x() & 15, position.y() & 15, position.z() & 15);
            CustomBlockOwnership updated = existing == null
                    ? new CustomBlockOwnership(List.of(completed)) : existing.add(completed);
            slice.set(position.x() & 15, position.y() & 15, position.z() & 15, updated);
        }
        if (invalidated) {
            scheduleRemoval(engine, chunk, engine.getMinHeight());
        }
    }

    public static void removeInvalidated(Engine engine, int chunkX, int chunkZ) {
        removeInvalidated(engine, engine.getMantle().getMantle(), chunkX, chunkZ);
    }

    public static void removeInvalidated(Engine engine, Mantle<Matter> mantle, int chunkX, int chunkZ) {
        NativeWorld world = engine.getWorld().platformWorld();
        if (world == null || engine.isClosed() || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        if (mantle.isClosed()) {
            return;
        }
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            removeInvalidated(engine, chunk, engine.getMinHeight());
        } finally {
            chunk.release();
        }
    }

    private static void scheduleRemoval(Engine engine, MantleChunk<Matter> chunk, int minHeight) {
        NativeWorld world = engine.getWorld().platformWorld();
        if (world != null && IrisPlatforms.isBound()) {
            IrisPlatforms.get().scheduler().region(world, chunk.getX(), chunk.getZ(),
                    () -> removeScheduled(new ScheduledRemoval(engine, world, chunk, minHeight)));
        }
    }

    private static void removeScheduled(ScheduledRemoval removal) {
        MantleChunk<Matter> chunk = removal.chunk();
        if (removal.engine().isClosed() || chunk.isClosed()
                || !removal.world().isChunkLoaded(chunk.getX(), chunk.getZ())) {
            return;
        }
        try {
            chunk.use();
        } catch (MantleClosedException closed) {
            IrisLogging.reportError("Custom content cleanup deferred after mantle chunk "
                    + chunk.getX() + "," + chunk.getZ() + " closed.", closed);
            return;
        }
        try {
            removeInvalidated(removal.engine(), chunk, removal.minHeight());
        } finally {
            chunk.release();
        }
    }

    private static void removeInvalidated(Engine engine, MantleChunk<Matter> chunk, int minHeight) {
        EnginePlatformHooks hooks = IrisServices.getOrNull(EnginePlatformHooks.class);
        if (hooks == null) {
            return;
        }
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        List<Removal> removals = new ArrayList<>();
        synchronized (chunk) {
            chunk.iterate(CustomBlockOwnership.class, (x, y, z, ownership) -> {
                for (CustomBlockOwnership.Entry entry : ownership.entries()) {
                    if (entry.invalidated()) {
                        removals.add(new Removal(new NativeBlockPoint(baseX + (x & 15),
                                y + minHeight, baseZ + (z & 15)), entry));
                    }
                }
            });
        }
        for (Removal removal : removals) {
            try {
                if (hooks.removeCustomBlock(engine, removal.position(), removal.entry())) {
                    forgetRemoval(chunk, removal, minHeight);
                }
            } catch (RuntimeException | LinkageError error) {
                IrisLogging.reportError("Failed to remove generated custom content at " + removal.position() + ".", error);
            }
        }
    }

    private static <T> void collectPositions(MantleChunk<Matter> chunk, NativeBlockChangeSet positions,
                                            Class<T> type, int minHeight) {
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        chunk.iterate(type, (x, y, z, value) -> positions.add(baseX + (x & 15), y + minHeight, baseZ + (z & 15)));
    }

    private static void forgetRemoval(MantleChunk<Matter> chunk, Removal removal, int minHeight) {
        NativeBlockPoint position = removal.position();
        int y = position.y() - minHeight;
        synchronized (chunk) {
            Matter section = chunk.get(y >> 4);
            if (section == null) {
                return;
            }
            MatterSlice<CustomBlockOwnership> slice = section.getSlice(CustomBlockOwnership.class);
            if (slice == null) {
                return;
            }
            CustomBlockOwnership ownership = slice.get(position.x() & 15, y & 15, position.z() & 15);
            if (ownership != null) {
                slice.set(position.x() & 15, y & 15, position.z() & 15, ownership.remove(removal.entry()));
            }
        }
    }

    private static boolean hasRecords(Mantle<Matter> mantle, int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                return hasRecords(chunk);
            }
        } finally {
            chunk.release();
        }
    }

    private static boolean hasRecords(MantleChunk<Matter> chunk) {
        for (int section = 0; section < chunk.sectionCount(); section++) {
            Matter matter = chunk.get(section);
            if (matter != null && (matter.hasSlice(Identifier.class) || matter.hasSlice(TileWrapper.class)
                    || matter.hasSlice(CustomBlockOwnership.class))) {
                return true;
            }
        }
        return false;
    }

    private static MantleChunk<Matter> invalidateChunk(Mantle<Matter> mantle, NativeBlockChangeSet changes,
                                        int chunkX, int chunkZ, int minHeight) {
        MantleChunk<Matter> chunk = mantle.useChunk(chunkX, chunkZ);
        try {
            synchronized (chunk) {
                if (!hasRecords(chunk)) {
                    return null;
                }
                boolean[] cleanup = new boolean[1];
                changes.forEachPosition(chunkX, chunkZ, (x, y, z) ->
                        cleanup[0] |= clearRecords(chunk, x & 15, y - minHeight, z & 15));
                return cleanup[0] ? chunk : null;
            }
        } finally {
            chunk.release();
        }
    }

    private static boolean clearRecords(MantleChunk<Matter> chunk, int x, int y, int z) {
        if (y < 0 || (y >> 4) >= chunk.sectionCount()) {
            return false;
        }
        Matter section = chunk.get(y >> 4);
        if (section == null) {
            return false;
        }
        clearRecord(section.getSlice(Identifier.class), x, y & 15, z);
        clearRecord(section.getSlice(TileWrapper.class), x, y & 15, z);
        MatterSlice<CustomBlockOwnership> ownership = section.getSlice(CustomBlockOwnership.class);
        if (ownership != null) {
            CustomBlockOwnership current = ownership.get(x, y & 15, z);
            if (current != null) {
                ownership.set(x, y & 15, z, current.invalidate());
                return true;
            }
        }
        return false;
    }

    private static void clearRecord(MatterSlice<?> slice, int x, int y, int z) {
        if (slice != null) {
            slice.set(x, y, z, null);
        }
    }

    private record Removal(NativeBlockPoint position, CustomBlockOwnership.Entry entry) {
    }

    public record Pending<T>(NativeBlockPoint position, T value) {
    }

    private record ScheduledRemoval(Engine engine, NativeWorld world, MantleChunk<Matter> chunk, int minHeight) {
    }
}
