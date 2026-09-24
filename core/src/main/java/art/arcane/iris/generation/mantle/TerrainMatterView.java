package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.storage.PaletteOrHunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.Objects;
import java.util.Arrays;
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

    public static MatterCavern[] getComposedFace(MantleChunk<Matter> chunk, Face face, int height) {
        Objects.requireNonNull(face, "Terrain face");
        if (height < 0) {
            throw new IllegalArgumentException("Terrain face height must be nonnegative");
        }
        MatterCavern[] result = new MatterCavern[Math.multiplyExact(height, 16)];
        if (chunk == null || height == 0) {
            return result;
        }
        PreObjectMatterCell[] journal = new PreObjectMatterCell[256];
        HydrologyCaveCell[] hydrology = new HydrologyCaveCell[256];
        MatterCavern[] caverns = new MatterCavern[256];
        synchronized (chunk) {
            for (int baseY = 0; baseY < height; baseY += 16) {
                int section = baseY >> 4;
                if (!chunk.exists(section)) {
                    continue;
                }
                Matter matter = chunk.get(section);
                if (matter == null) {
                    continue;
                }
                copyFace(matter.getSlice(PreObjectMatterCell.class), face, journal);
                copyFace(matter.getSlice(HydrologyCaveCell.class), face, hydrology);
                copyFace(matter.getSlice(MatterCavern.class), face, caverns);
                int count = Math.min(16, height - baseY) * 16;
                for (int index = 0; index < count; index++) {
                    PreObjectMatterCell cell = journal[index];
                    HydrologyCaveCell effectiveHydrology = cell != null && cell.hydrologyCaptured()
                            ? cell.hydrology() : hydrology[index];
                    result[baseY * 16 + index] = effectiveHydrology != null
                            ? effectiveHydrology.asCavern()
                            : cell != null && cell.cavernCaptured() ? cell.cavern() : caverns[index];
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> void copyFace(MatterSlice<T> slice, Face face, T[] destination) {
        if (slice == null) {
            Arrays.fill(destination, null);
            return;
        }
        if (slice instanceof PaletteOrHunk<?> storage && storage.isPalette()
                && slice.getWidth() == 16 && slice.getHeight() == 16 && slice.getDepth() == 16) {
            DataContainer<T> palette = (DataContainer<T>) storage.palette();
            palette.copyTo(face.positions, destination);
            return;
        }
        for (int index = 0; index < destination.length; index++) {
            int position = face.positions[index];
            destination[index] = slice.get(position & 15, (position >> 4) & 15, position >> 8);
        }
    }

    public enum Face {
        WEST(true, 0),
        EAST(true, 15),
        NORTH(false, 0),
        SOUTH(false, 15);

        private final int[] positions = new int[256];

        Face(boolean fixedX, int coordinate) {
            for (int y = 0; y < 16; y++) {
                for (int offset = 0; offset < 16; offset++) {
                    int x = fixedX ? coordinate : offset;
                    int z = fixedX ? offset : coordinate;
                    positions[y * 16 + offset] = (z << 8) | (y << 4) | x;
                }
            }
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
        return type == MatterCavern.class || type == NativeBlockState.class || type == String.class
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
        MatterSlice<T> slice = matter.getSlice(type);
        return slice == null ? null : slice.get(x & 15, y & 15, z & 15);
    }
}
