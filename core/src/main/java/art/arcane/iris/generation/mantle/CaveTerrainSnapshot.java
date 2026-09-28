package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.world.storage.matter.PreObjectMatterCell;
import art.arcane.volmlib.util.hunk.bits.DataContainer;
import art.arcane.volmlib.util.hunk.storage.MappedHunk;
import art.arcane.volmlib.util.hunk.storage.PaletteOrHunk;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The terrain view of one mantle chunk's cavern and hydrology cells (journaled pre-object values win),
 * copied once under the chunk monitor. Terrain components finish before any content write reaches a
 * chunk and every later write journals the value it replaces, so this view never goes stale while the
 * chunk generates.
 */
public final class CaveTerrainSnapshot {
    private static final int SECTION_VOLUME = 4096;

    private final int chunkX;
    private final int chunkZ;
    private final MatterCavern[][] caverns;
    private final HydrologyCaveCell[][] hydrology;
    private boolean hydrologyPresent;

    private CaveTerrainSnapshot(int chunkX, int chunkZ, int sections) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        caverns = new MatterCavern[sections][];
        hydrology = new HydrologyCaveCell[sections][];
    }

    public static CaveTerrainSnapshot capture(MantleChunk<Matter> chunk, int chunkX, int chunkZ) {
        Objects.requireNonNull(chunk, "Terrain mantle chunk");
        synchronized (chunk) {
            int sections = chunk.sectionCount();
            CaveTerrainSnapshot snapshot = new CaveTerrainSnapshot(chunkX, chunkZ, sections);
            for (int section = 0; section < sections; section++) {
                if (!chunk.exists(section)) {
                    continue;
                }
                Matter matter = chunk.get(section);
                if (matter != null) {
                    snapshot.captureSection(section, matter);
                }
            }
            return snapshot;
        }
    }

    public boolean covers(int chunkX, int chunkZ) {
        return this.chunkX == chunkX && this.chunkZ == chunkZ;
    }

    public boolean hasHydrology() {
        return hydrologyPresent;
    }

    public MatterCavern cavern(int x, int y, int z) {
        if (y < 0) {
            return null;
        }
        int section = y >> 4;
        if (section >= caverns.length) {
            return null;
        }
        MatterCavern[] values = caverns[section];
        return values == null ? null : values[index(x, y, z)];
    }

    public HydrologyCaveCell hydrology(int x, int y, int z) {
        if (!hydrologyPresent || y < 0) {
            return null;
        }
        int section = y >> 4;
        if (section >= hydrology.length) {
            return null;
        }
        HydrologyCaveCell[] values = hydrology[section];
        return values == null ? null : values[index(x, y, z)];
    }

    public MatterCavern composedCavern(int x, int y, int z) {
        HydrologyCaveCell cell = hydrology(x, y, z);
        return cell != null ? cell.asCavern() : cavern(x, y, z);
    }

    /**
     * Visits every cell holding a cavern or a hydrology cell, section by section in slice position order.
     */
    public void forEachCell(CellConsumer consumer) {
        for (int section = 0; section < caverns.length; section++) {
            MatterCavern[] sectionCaverns = caverns[section];
            HydrologyCaveCell[] sectionHydrology = hydrologyPresent ? hydrology[section] : null;
            if (sectionCaverns == null && sectionHydrology == null) {
                continue;
            }
            int baseY = section << 4;
            for (int position = 0; position < SECTION_VOLUME; position++) {
                MatterCavern cavern = sectionCaverns == null ? null : sectionCaverns[position];
                HydrologyCaveCell cell = sectionHydrology == null ? null : sectionHydrology[position];
                if (cavern != null || cell != null) {
                    consumer.accept(position & 15, baseY + ((position >> 4) & 15), position >> 8, cavern, cell);
                }
            }
        }
    }

    public MatterCavern[] composedFace(TerrainMatterView.Face face, int height) {
        Objects.requireNonNull(face, "Terrain face");
        if (height < 0) {
            throw new IllegalArgumentException("Terrain face height must be nonnegative");
        }
        MatterCavern[] result = new MatterCavern[Math.multiplyExact(height, 16)];
        for (int y = 0; y < height; y++) {
            for (int offset = 0; offset < 16; offset++) {
                result[y * 16 + offset] = composedCavern(face.x(offset), y, face.z(offset));
            }
        }
        return result;
    }

    private void captureSection(int section, Matter matter) {
        MatterCavern[] sectionCaverns = copy(matter.getSlice(MatterCavern.class), MatterCavern[]::new);
        HydrologyCaveCell[] sectionHydrology = copy(matter.getSlice(HydrologyCaveCell.class), HydrologyCaveCell[]::new);
        MatterSlice<PreObjectMatterCell> journal = matter.getSlice(PreObjectMatterCell.class);
        if (journal instanceof MappedHunk<?> mapped) {
            @SuppressWarnings("unchecked")
            Map<Integer, PreObjectMatterCell> entries = (Map<Integer, PreObjectMatterCell>) mapped.getData();
            for (Map.Entry<Integer, PreObjectMatterCell> entry : entries.entrySet()) {
                int position = entry.getKey();
                PreObjectMatterCell cell = entry.getValue();
                if (cell.cavernCaptured() && (sectionCaverns != null || cell.cavern() != null)) {
                    if (sectionCaverns == null) {
                        sectionCaverns = new MatterCavern[SECTION_VOLUME];
                    }
                    sectionCaverns[position] = cell.cavern();
                }
                if (cell.hydrologyCaptured() && (sectionHydrology != null || cell.hydrology() != null)) {
                    if (sectionHydrology == null) {
                        sectionHydrology = new HydrologyCaveCell[SECTION_VOLUME];
                    }
                    sectionHydrology[position] = cell.hydrology();
                }
            }
        } else if (journal != null) {
            for (int position = 0; position < SECTION_VOLUME; position++) {
                PreObjectMatterCell cell = journal.get(position & 15, (position >> 4) & 15, position >> 8);
                if (cell == null) {
                    continue;
                }
                if (cell.cavernCaptured() && (sectionCaverns != null || cell.cavern() != null)) {
                    if (sectionCaverns == null) {
                        sectionCaverns = new MatterCavern[SECTION_VOLUME];
                    }
                    sectionCaverns[position] = cell.cavern();
                }
                if (cell.hydrologyCaptured() && (sectionHydrology != null || cell.hydrology() != null)) {
                    if (sectionHydrology == null) {
                        sectionHydrology = new HydrologyCaveCell[SECTION_VOLUME];
                    }
                    sectionHydrology[position] = cell.hydrology();
                }
            }
        }
        caverns[section] = sectionCaverns;
        if (sectionHydrology != null && containsAny(sectionHydrology)) {
            hydrology[section] = sectionHydrology;
            hydrologyPresent = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] copy(MatterSlice<T> slice, IntFunction<T[]> allocator) {
        if (slice == null) {
            return null;
        }
        T[] values = allocator.apply(SECTION_VOLUME);
        if (slice instanceof PaletteOrHunk<?> storage && storage.isPalette()
                && slice.getWidth() == 16 && slice.getHeight() == 16 && slice.getDepth() == 16) {
            ((DataContainer<T>) storage.palette()).copyAll(values);
            return values;
        }
        int width = Math.min(16, slice.getWidth());
        int height = Math.min(16, slice.getHeight());
        int depth = Math.min(16, slice.getDepth());
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    values[index(x, y, z)] = slice.get(x, y, z);
                }
            }
        }
        return values;
    }

    private static boolean containsAny(Object[] values) {
        for (Object value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private static int index(int x, int y, int z) {
        return ((z & 15) << 8) | ((y & 15) << 4) | (x & 15);
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int x, int y, int z, MatterCavern cavern, HydrologyCaveCell hydrology);
    }
}
