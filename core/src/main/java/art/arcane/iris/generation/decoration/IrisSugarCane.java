package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Optional;

final class IrisSugarCane {
    private static final int[][] NEIGHBORS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private IrisSugarCane() {
    }

    static boolean isSugarCane(PlatformBlockState state) {
        if (state == null) {
            return false;
        }
        String key = state.key();
        return key != null && (key.equals("minecraft:sugar_cane") || key.startsWith("minecraft:sugar_cane["));
    }

    static boolean canPlace(PlatformBlockState block, Hunk<PlatformBlockState> data,
                            int x, int y, int z, int worldX, int worldZ, Engine engine) {
        if (!isSugarCane(block)) {
            return true;
        }
        if (y <= 0 || y >= data.getHeight()) {
            return false;
        }
        int baseY = y - 1;
        PlatformBlockState support = data.get(x, baseY, z);
        PlatformBlockState target = data.get(x, y, z);
        if (support == null || !block.canPlaceOnto(support) || target != null && !target.isAir()) {
            return false;
        }
        while (isSugarCane(support) && baseY > 0) {
            support = data.get(x, --baseY, z);
        }
        if (support == null || isSugarCane(support) || !block.canPlaceOnto(support)) {
            return false;
        }
        for (int[] offset : NEIGHBORS) {
            int adjacentX = x + offset[0];
            int adjacentZ = z + offset[1];
            if (adjacentX >= 0 && adjacentX < data.getWidth()
                    && adjacentZ >= 0 && adjacentZ < data.getDepth()) {
                if (supportsAdjacent(data.get(adjacentX, baseY, adjacentZ))) {
                    return true;
                }
            } else if (engine != null && plannedWater(engine, worldX + offset[0], baseY, worldZ + offset[1])) {
                return true;
            }
        }
        return false;
    }

    static boolean supportsAdjacent(PlatformBlockState state) {
        if (state == null) {
            return false;
        }
        String key = state.key();
        return state.isWater() || state.isWaterLogged() || key != null
                && (key.equals("minecraft:frosted_ice") || key.startsWith("minecraft:frosted_ice["));
    }

    private static boolean plannedWater(Engine engine, int x, int y, int z) {
        IrisComplex complex = engine.getComplex();
        Optional<TerrainBoundarySignature> resolved = complex.resolvedTerrainColumn(x, z);
        if (resolved.isPresent()) {
            String key = resolved.get().geometry().voxelAt(y + engine.getMinHeight()).stateKey();
            return supportsAdjacent(IrisPlatforms.get().registries().blockOrNull(key));
        }
        HydrologyColumnSample sample = complex.sampleHydrologyColumn(x, z);
        HydrologyColumnLayer fluid = sample == null ? null : sample.primarySurfaceFluidLayerOrNull();
        if (fluid != null) {
            return y > fluid.bedY() && y <= fluid.fluidHeadY()
                    && supportsAdjacent(complex.resolveHydrologyFluid(fluid.profileKey(), x, z));
        }
        int terrainHeight = (int) Math.round(complex.getHeightStream().getDouble(x, z));
        int fluidHeight = (int) Math.round(complex.getRiverWaterSurfaceStream().getDouble(x, z));
        if (y <= terrainHeight || y > fluidHeight) {
            return false;
        }
        IrisBiome biome = complex.getTrueBiomeStream().get(x, z);
        KList<PlatformBlockState> layers = biome.generateSeaLayers(x, z,
                new RNG(engine.getSeedManager().getTerrain()), fluidHeight - terrainHeight, engine.getData());
        int depth = fluidHeight - y;
        return supportsAdjacent(layers.hasIndex(depth) ? layers.get(depth) : complex.resolveSurfaceFluid(x, z));
    }
}
