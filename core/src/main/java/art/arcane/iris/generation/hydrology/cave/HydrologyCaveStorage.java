package art.arcane.iris.generation.hydrology.cave;

import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.TectonicPlate;
import art.arcane.volmlib.util.matter.Matter;

public final class HydrologyCaveStorage {
    private HydrologyCaveStorage() {
    }

    public static HydrologyCaveCell getIfPresent(Mantle<Matter> mantle, int x, int y, int z) {
        if (y < 0 || y >= mantle.getWorldHeight()) {
            return null;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        TectonicPlate<Matter> plate = mantle.getLoadedRegions().get(Mantle.key(chunkX >> 5, chunkZ >> 5));
        if (plate == null || plate.isClosed()) {
            return null;
        }
        MantleChunk<Matter> chunk = plate.get(chunkX & 31, chunkZ & 31);
        return getIfPresent(chunk, x, y, z);
    }

    public static HydrologyCaveCell getIfPresent(MantleChunk<Matter> chunk, int x, int y, int z) {
        if (chunk == null || y < 0) {
            return null;
        }
        int section = y >> 4;
        if (!chunk.exists(section)) {
            return null;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(HydrologyCaveCell.class)) {
            return null;
        }
        return matter.<HydrologyCaveCell>getSlice(HydrologyCaveCell.class)
                .get(x & 15, y & 15, z & 15);
    }
}
