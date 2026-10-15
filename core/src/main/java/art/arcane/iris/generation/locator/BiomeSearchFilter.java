package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.Objects;
import java.util.Set;

public final class BiomeSearchFilter {
    private static final Column ALL = y -> true;
    private static final Column NONE = y -> false;

    private final Engine engine;
    private final IrisComplex complex;
    private final Set<String> biomes;
    private final boolean unfiltered;
    private final boolean hydrology;
    private final HintedLocator.CoarseSample surface;
    private final IrisImageMapRuntime imageMaps;
    private final boolean caves;
    private final boolean carving;
    private final boolean subterrain;
    private final boolean transitions;
    private final int minHeight;
    private final int caveSwitchY;

    private BiomeSearchFilter(Engine engine, Set<String> biomes) {
        this.engine = engine;
        this.complex = engine.getComplex();
        this.biomes = Set.copyOf(biomes);
        IrisDimension dimension = engine.getDimension();
        unfiltered = engine.getDimensionStackContext() != null || complex.getFocusBiome() != null
                || complex.getFocusRegion() != null
                || complex.getBiomeBuffet() != null && !complex.getBiomeBuffet().cells().isEmpty();
        hydrology = complex.getHydrologyRuntime() != null && hostsRiverBiome(dimension);
        surface = this.biomes.isEmpty() ? null : HintedLocator.surfaceCoarse(engine, this.biomes);
        IrisImageMapRuntime maps = complex.getImageMapRuntime();
        imageMaps = maps != null && maps.has(IrisImageMapApplication.BIOME) ? maps : null;
        caves = hostsCaveBiome(dimension);
        carving = carvesBiome(dimension);
        subterrain = hostsSubterrainBiome(dimension);
        transitions = complex.getTransitionGenerationPlan() != null;
        minHeight = engine.getMinHeight();
        caveSwitchY = Math.max(-8 - minHeight, 40);
    }

    public static BiomeSearchFilter of(Engine engine, Set<String> biomeKeys) {
        return new BiomeSearchFilter(Objects.requireNonNull(engine, "engine"),
                Objects.requireNonNull(biomeKeys, "biome keys"));
    }

    public Column column(int x, int z) {
        if (unfiltered || hydrology && !engine.answersFromNaturalTerrain(x, z)) {
            return ALL;
        }
        if (surfaceAdmits(x, z)) {
            return ALL;
        }
        boolean cave = caves && target(complex.getCaveBiomeStream().get(x, z));
        if (!cave && !carving && !subterrain && !transitions) {
            return NONE;
        }
        return y -> admits(x, y, z, cave);
    }

    private boolean surfaceAdmits(int x, int z) {
        IrisBiome mapped = imageMaps == null ? null : imageMaps.sampleBiome(x, z);
        if (mapped != null) {
            return target(mapped);
        }
        return surface != null && surface.test(x, z);
    }

    private boolean admits(int x, int y, int z, boolean cave) {
        if (transitions && complex.historicalPhysicalBiomeKeyAt(x, y + minHeight, z).isPresent()) {
            return true;
        }
        if (subterrain && target(subterrainBiome(x, y + minHeight, z))) {
            return true;
        }
        if (y > caveSwitchY) {
            return false;
        }
        return cave || carving && target(IrisDimensionCarvingResolver.resolveBiome(engine, x, y + minHeight, z, null));
    }

    private String subterrainBiome(int x, int worldY, int z) {
        SubterrainPlanner planner = complex.getSubterrainPlanner();
        SubterrainCell cell = planner == null ? SubterrainCell.OUTSIDE : planner.sample(x, worldY, z);
        return cell.occupied() ? cell.room().biome() : null;
    }

    private boolean target(IrisBiome biome) {
        return biome != null && target(biome.getLoadKey());
    }

    private boolean target(String key) {
        return key != null && biomes.contains(key.trim());
    }

    private boolean hostsRiverBiome(IrisDimension dimension) {
        if (placesTarget(dimension.getRiverPolicy())) {
            return true;
        }
        for (IrisRegion region : dimension.getAllRegions(engine)) {
            if (region != null && placesTarget(region.getRiverPolicy())) {
                return true;
            }
        }
        for (IrisBiome biome : dimension.getReachableBiomes(engine)) {
            if (biome != null && placesTarget(biome.getRiverPolicy())) {
                return true;
            }
        }
        return false;
    }

    private boolean placesTarget(IrisRiverPolicy policy) {
        if (policy == null) {
            return false;
        }
        for (String key : policy.getAllBiomeIds()) {
            if (HintedLocator.closureContains(engine, key, biomes)) {
                return true;
            }
        }
        return false;
    }

    private boolean hostsCaveBiome(IrisDimension dimension) {
        for (IrisRegion region : dimension.getAllRegions(engine)) {
            if (region == null) {
                continue;
            }
            for (String key : region.getCaveBiomes()) {
                if (target(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean carvesBiome(IrisDimension dimension) {
        if (dimension.getCarving() == null) {
            return false;
        }
        for (IrisDimensionCarvingEntry entry : dimension.getCarving()) {
            if (entry != null && entry.isEnabled() && target(entry.getBiome())) {
                return true;
            }
        }
        return false;
    }

    private boolean hostsSubterrainBiome(IrisDimension dimension) {
        if (dimension.getSubterrainFeatures() == null) {
            return false;
        }
        for (IrisSubterrainFeature feature : dimension.getSubterrainFeatures()) {
            if (feature != null && feature.isEnabled() && target(feature.getBiome())) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface Column {
        boolean admits(int y);
    }
}
