package art.arcane.iris.engine.mantle;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.util.project.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public final class TerrainMatterView {
    private TerrainMatterView() {
    }

    public static <T> T get(Mantle<Matter> mantle, int x, int y, int z, Class<T> type) {
        if (!hasJournal(type)) {
            return mantle.get(x, y, z, type);
        }
        MantleChunk<Matter> chunk = mantle.getChunk(x >> 4, z >> 4);
        synchronized (chunk) {
            PreObjectMatterCell cell = mantle.get(x, y, z, PreObjectMatterCell.class);
            return cell != null && cell.captures(type) ? cell.original(type) : mantle.get(x, y, z, type);
        }
    }

    public static <T> T get(MantleChunk<Matter> chunk, int x, int y, int z, Class<T> type) {
        Objects.requireNonNull(type, "Terrain matter type");
        if (chunk == null || y < 0) {
            return null;
        }
        if (!hasJournal(type)) {
            return raw(chunk, x, y, z, type);
        }
        synchronized (chunk) {
            PreObjectMatterCell cell = raw(chunk, x, y, z, PreObjectMatterCell.class);
            return cell != null && cell.captures(type) ? cell.original(type) : raw(chunk, x, y, z, type);
        }
    }

    public static MatterCavern getComposedCavern(MantleChunk<Matter> chunk, int x, int y, int z) {
        if (chunk == null || y < 0) {
            return null;
        }
        synchronized (chunk) {
            int section = y >> 4;
            if (!chunk.exists(section)) {
                return null;
            }
            Matter matter = chunk.get(section);
            if (matter == null) {
                return null;
            }
            PreObjectMatterCell cell = raw(matter, x, y, z, PreObjectMatterCell.class);
            HydrologyCaveCell hydrology = cell != null && cell.hydrologyCaptured()
                    ? cell.hydrology() : raw(matter, x, y, z, HydrologyCaveCell.class);
            if (hydrology != null) {
                return hydrology.asCavern();
            }
            return cell != null && cell.cavernCaptured()
                    ? cell.cavern() : raw(matter, x, y, z, MatterCavern.class);
        }
    }

    public static <T> void iterate(MantleChunk<Matter> chunk, Class<T> type,
                                    Consumer4<Integer, Integer, Integer, T> consumer) {
        Objects.requireNonNull(chunk, "Terrain mantle chunk");
        Objects.requireNonNull(type, "Terrain matter type");
        Objects.requireNonNull(consumer, "Terrain matter consumer");
        if (!hasJournal(type)) {
            chunk.iterate(type, consumer);
            return;
        }
        List<TerrainEntry<T>> entries = new ArrayList<>();
        synchronized (chunk) {
            chunk.iterate(type, (x, y, z, value) -> {
                PreObjectMatterCell cell = raw(chunk, x, y, z, PreObjectMatterCell.class);
                T original = cell != null && cell.captures(type) ? cell.original(type) : value;
                if (original != null) {
                    entries.add(new TerrainEntry<>(x, y, z, original));
                }
            });
            chunk.iterate(PreObjectMatterCell.class, (x, y, z, cell) -> {
                if (cell == null || !cell.captures(type) || raw(chunk, x, y, z, type) != null) {
                    return;
                }
                T original = cell.original(type);
                if (original != null) {
                    entries.add(new TerrainEntry<>(x, y, z, original));
                }
            });
        }
        for (TerrainEntry<T> entry : entries) {
            consumer.accept(entry.x(), entry.y(), entry.z(), entry.value());
        }
    }

    private record TerrainEntry<T>(int x, int y, int z, T value) {
    }

    private static boolean hasJournal(Class<?> type) {
        return type == MatterCavern.class || type == PlatformBlockState.class || type == String.class
                || type == HydrologyCaveCell.class;
    }

    private static <T> T raw(MantleChunk<Matter> chunk, int x, int y, int z, Class<T> type) {
        int section = y >> 4;
        if (!chunk.exists(section)) {
            return null;
        }
        Matter matter = chunk.get(section);
        return matter == null ? null : raw(matter, x, y, z, type);
    }

    private static <T> T raw(Matter matter, int x, int y, int z, Class<T> type) {
        return !matter.hasSlice(type) ? null : matter.<T>getSlice(type).get(x & 15, y & 15, z & 15);
    }
}
