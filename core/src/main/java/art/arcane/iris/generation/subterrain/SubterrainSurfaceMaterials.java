package art.arcane.iris.generation.subterrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SubterrainSurfaceMaterials {
    private final SubterrainPlanner planner;
    private final List<SubterrainPlan> plans;
    private final IrisDimension dimension;
    private final IrisComplex complex;
    private final IrisData data;
    private final RNG random;
    private final int minimumY;
    private final Map<String, IrisBiome> biomes = new HashMap<>();
    private final Map<String, Set<String>> floorSupports = new HashMap<>();

    public SubterrainSurfaceMaterials(SubterrainPlanner planner, List<SubterrainPlan> plans,
                                     IrisDimension dimension, IrisComplex complex, IrisData data,
                                     RNG random, int minimumY) {
        this.planner = planner;
        this.plans = plans;
        this.dimension = dimension;
        this.complex = complex;
        this.data = data;
        this.random = random;
        this.minimumY = minimumY;
    }

    public NativeBlockState state(int x, int worldY, int z, SubterrainCell cell) {
        NativeBlockState fallback = SubterrainRasterizer.state(cell);
        if (!cell.solid() || cell.room().biome().isEmpty()) {
            return fallback;
        }
        Surface surface = surface(x, worldY, z, cell);
        if (surface == Surface.INTERIOR) {
            return fallback;
        }
        IrisBiome biome = biome(cell.room().biome());
        if (biome == null) {
            return fallback;
        }
        NativeBlockState candidate;
        if (surface == Surface.WALL) {
            candidate = biome.getWall().get(random, x, worldY - minimumY, z, data);
        } else {
            KList<NativeBlockState> layers = surface == Surface.FLOOR
                    ? biome.generateLayers(dimension, x, z, random, 1, worldY + 1 - minimumY, data, complex)
                    : biome.generateCeilingLayers(dimension, x, z, random, 1, worldY - 1 - minimumY, data, complex);
            candidate = layers.isEmpty() ? null : layers.getFirst();
        }
        return SubterrainRasterizer.isRetainedSolid(candidate) ? candidate : fallback;
    }

    public boolean retainsFloorSupport(int x, int worldY, int z, SubterrainCell cell, NativeBlockState existing) {
        if (!cell.solid() || cell.room().biome().isEmpty() || !SubterrainRasterizer.isRetainedSolid(existing)) {
            return false;
        }
        String key = cell.room().biome();
        Set<String> supports = floorSupports.get(key);
        if (supports == null) {
            supports = new HashSet<>();
            IrisBiome biome = biome(key);
            if (biome != null) {
                for (IrisDecorator decorator : biome.getDecoratorBucket(IrisDecorationPart.NONE)) {
                    if (decorator.getForceBlock() == null) {
                        continue;
                    }
                    NativeBlockState support = decorator.getForceBlock().getBlockData(data);
                    if (SubterrainRasterizer.isRetainedSolid(support)) {
                        supports.add(support.key());
                    }
                }
            }
            floorSupports.put(key, supports);
        }
        return supports.contains(existing.key()) && surface(x, worldY, z, cell) == Surface.FLOOR;
    }

    public Surface surface(int x, int worldY, int z, SubterrainCell cell) {
        if (!cell.solid()) {
            return Surface.INTERIOR;
        }
        if (sameRoom(planner.sample(plans, x, worldY + 1, z), cell)) {
            return Surface.FLOOR;
        }
        if (sameRoom(planner.sample(plans, x, worldY - 1, z), cell)) {
            return Surface.CEILING;
        }
        if (sameRoom(planner.sample(plans, x - 1, worldY, z), cell)
                || sameRoom(planner.sample(plans, x + 1, worldY, z), cell)
                || sameRoom(planner.sample(plans, x, worldY, z - 1), cell)
                || sameRoom(planner.sample(plans, x, worldY, z + 1), cell)) {
            return Surface.WALL;
        }
        return Surface.INTERIOR;
    }

    private static boolean sameRoom(SubterrainCell neighbor, SubterrainCell cell) {
        return neighbor.occupied() && neighbor.room().featureId().equals(cell.room().featureId());
    }

    private IrisBiome biome(String key) {
        if (!biomes.containsKey(key)) {
            biomes.put(key, data.getBiomeLoader().load(key));
        }
        return biomes.get(key);
    }

    public enum Surface {
        FLOOR,
        CEILING,
        WALL,
        INTERIOR
    }
}
