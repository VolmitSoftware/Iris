package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.Objects;

public final class SubterrainChunkCells {
    private final Engine engine;
    private final boolean delegated;
    private final SubterrainPlanner.ChunkSampler sampler;
    private final int minimumY;

    private SubterrainChunkCells(Engine engine, boolean delegated, SubterrainPlanner.ChunkSampler sampler, int minimumY) {
        this.engine = Objects.requireNonNull(engine);
        this.delegated = delegated;
        this.sampler = sampler;
        this.minimumY = minimumY;
    }

    public static SubterrainChunkCells of(Engine engine, int chunkX, int chunkZ) {
        return engine instanceof IrisEngine irisEngine
                ? irisEngine.subterrainChunkCells(chunkX, chunkZ)
                : delegated(engine);
    }

    public static SubterrainChunkCells delegated(Engine engine) {
        return new SubterrainChunkCells(engine, true, null, 0);
    }

    static SubterrainChunkCells planned(Engine engine, SubterrainPlanner planner, int chunkX, int chunkZ, int minimumY) {
        return new SubterrainChunkCells(engine, false, planner == null ? null : planner.chunkSampler(chunkX, chunkZ), minimumY);
    }

    static SubterrainPlanner activePlanner(IrisComplex complex, IrisDimension dimension) {
        if (complex == null || dimension == null || !admits(dimension)) {
            return null;
        }
        return complex.getSubterrainPlanner();
    }

    static boolean admits(IrisDimension dimension) {
        return dimension.allowsSubterrainFeatures()
                && dimension.getSubterrainFeatures() != null && !dimension.getSubterrainFeatures().isEmpty();
    }

    public SubterrainCell cell(int x, int y, int z) {
        if (delegated) {
            SubterrainCell cell = engine.getSubterrainCell(x, y, z);
            return cell == null ? SubterrainCell.OUTSIDE : cell;
        }
        return sampler == null ? SubterrainCell.OUTSIDE : sampler.sample(x, y + minimumY, z);
    }

    public IrisBiome biome(int x, int y, int z) {
        if (delegated) {
            return engine.getSubterrainBiome(x, y, z);
        }
        SubterrainCell cell = cell(x, y, z);
        if (!cell.occupied() || cell.room().biome().isEmpty()) {
            return null;
        }
        return engine.getSubterrainBiome(x, y, z);
    }
}
