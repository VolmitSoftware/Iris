package art.arcane.iris.engine.platform.studio;

import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.common.data.DataProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BiomeBuffetLayout {
    private final List<Cell> cells;
    private final int width;
    private final int biomeSizeChunks;

    public BiomeBuffetLayout(IrisDimension dimension, DataProvider provider) {
        biomeSizeChunks = dimension.getStudioMode().biomeSizeChunks();
        if (biomeSizeChunks == 0) {
            throw new IllegalArgumentException("The dimension does not select a biome buffet.");
        }
        List<IrisBiome> biomes = new ArrayList<>(dimension.getAllBiomes(provider));
        biomes.removeIf(IrisBiome::isCompatExcluded);
        biomes.sort(Comparator.comparing(IrisBiome::getLoadKey));
        List<Cell> prepared = new ArrayList<>(biomes.size());
        for (IrisBiome biome : biomes) {
            IrisBiome focused = biome.withInferredType(InferredType.LAND);
            prepared.add(new Cell(focused, dimension.resolveFocusRegion(focused, provider)));
        }
        cells = List.copyOf(prepared);
        width = Math.max((int) Math.sqrt(cells.size()), 1);
    }

    public List<Cell> cells() {
        return cells;
    }

    public Cell chunk(int chunkX, int chunkZ) {
        int x = Math.floorDiv(chunkX, biomeSizeChunks);
        int z = Math.floorDiv(chunkZ, biomeSizeChunks);
        if (x < 0 || x >= width || z < 0) {
            return null;
        }
        long index = (long) z * width + x;
        return index < cells.size() ? cells.get((int) index) : null;
    }

    public Cell terrain(double blockX, double blockZ) {
        Cell cell = chunk(Math.floorDiv((int) Math.floor(blockX), 16),
                Math.floorDiv((int) Math.floor(blockZ), 16));
        return cell == null && !cells.isEmpty() ? cells.getFirst() : cell;
    }

    public record Cell(IrisBiome biome, IrisRegion region) {
    }
}
