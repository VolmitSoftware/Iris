package art.arcane.iris.probe;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;

import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyPlanner;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.engine.hydrology.HydrologyTile;
import art.arcane.iris.engine.hydrology.HydrologyTileCache;
import art.arcane.iris.engine.hydrology.HydrologyTileKey;
import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class HydrologyPlannerProbe {
    private static final long[] DEFAULT_SEEDS = {1L, 19L, 52L, 77L, 331L, 501L, 712L, 992L};
    private static final long SURFACE_INLAND_SEED = 7012L;
    private static final long UNDERGROUND_INLAND_SEED = 7013L;
    private static final long COASTAL_SEED = 7014L;
    static final Set<HydrologyFeatureType> REQUIRED_FEATURE_TYPES =
            // The synthetic probe settings define no standing pools, so that type is not expected either.
            Set.copyOf(EnumSet.complementOf(EnumSet.of(HydrologyFeatureType.RIDGE_BORE, HydrologyFeatureType.STANDING_POOL)));
    private static final HydrologyTileKey ORIGIN = new HydrologyTileKey(0, 0);
    private static final int WARM_SAMPLES = 2_048;

    private HydrologyPlannerProbe() {
    }

    public static void main(String[] arguments) {
        long[] seeds = parseSeeds(arguments);
        long[] coldTimings = new long[seeds.length];
        long[] warmTimings = new long[seeds.length];
        long signature = 0xCBF29CE484222325L;
        EnumMap<HydrologyFeatureType, HydrologyFeatureRef> firstFeatures =
                new EnumMap<>(HydrologyFeatureType.class);

        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            long seed = seeds[seedIndex];
            HydrologyPlanner planner = new HydrologyPlanner(seed, settings(), terrain(seed));
            HydrologyTileCache cache = new HydrologyTileCache(planner, 32);
            long coldStart = System.nanoTime();
            HydrologyTile tile = cache.get(ORIGIN);
            coldTimings[seedIndex] = System.nanoTime() - coldStart;
            HydrologyTile repeated = new HydrologyPlanner(seed, settings(), terrain(seed)).plan(ORIGIN);
            if (!tile.equals(repeated)) {
                throw new IllegalStateException("Hydrology plan changed across deterministic replay for seed " + seed);
            }

            EnumMap<HydrologyFeatureType, Integer> counts = new EnumMap<>(HydrologyFeatureType.class);
            for (HydrologyFeatureRef feature : tile.features()) {
                counts.merge(feature.type(), 1, Integer::sum);
                signature = mix(signature, feature.id());
                signature = mix(signature, feature.type().ordinal());
                signature = mix(signature, feature.x());
                signature = mix(signature, feature.y());
                signature = mix(signature, feature.z());
            }
            collectAccepted(firstFeatures, tile);

            long warmStart = System.nanoTime();
            for (int sampleIndex = 0; sampleIndex < WARM_SAMPLES; sampleIndex++) {
                int x = Math.floorMod(sampleIndex * 1_229, 256);
                int z = Math.floorMod(sampleIndex * 811, 256);
                Optional<HydrologyColumnSample> sample = cache.columnAt(x, z);
                signature = mix(signature, sample.map(HydrologyColumnSample::layers).map(List::size).orElse(0));
            }
            warmTimings[seedIndex] = System.nanoTime() - warmStart;
            System.out.printf(
                    Locale.ROOT,
                    "IRIS_HYDROLOGY_SEED seed=%d cold_nanos=%d warm_nanos=%d courses=%d features=%d diagnostics=%d counts=%s%n",
                    seed,
                    coldTimings[seedIndex],
                    warmTimings[seedIndex],
                    tile.courses().size(),
                    tile.features().size(),
                    tile.diagnosticCandidates().size(),
                    counts
            );
            cache.close();
        }

        collectAccepted(firstFeatures, surfaceInlandTile());
        collectAccepted(firstFeatures, undergroundInlandTile());
        collectAccepted(firstFeatures, surfaceMouthTile());
        collectAccepted(firstFeatures, coastalGrottoTile());
        requireCompleteCoverage(firstFeatures.keySet());

        for (Map.Entry<HydrologyFeatureType, HydrologyFeatureRef> entry : firstFeatures.entrySet()) {
            HydrologyFeatureRef feature = entry.getValue();
            System.out.printf(
                    Locale.ROOT,
                    "IRIS_HYDROLOGY_COORDINATE type=%s x=%d y=%d z=%d id=%016x%n",
                    entry.getKey(),
                    feature.x(),
                    feature.y(),
                    feature.z(),
                    feature.id()
            );
        }

        long[] sortedCold = coldTimings.clone();
        long[] sortedWarm = warmTimings.clone();
        Arrays.sort(sortedCold);
        Arrays.sort(sortedWarm);
        System.out.printf(
                Locale.ROOT,
                "IRIS_HYDROLOGY_RESULT version=1 seeds=%d warm_samples=%d cold_median_nanos=%d cold_p95_nanos=%d warm_median_nanos=%d warm_p95_nanos=%d feature_types=%d signature=%016x%n",
                seeds.length,
                WARM_SAMPLES,
                percentile(sortedCold, 0.5D),
                percentile(sortedCold, 0.95D),
                percentile(sortedWarm, 0.5D),
                percentile(sortedWarm, 0.95D),
                firstFeatures.size(),
                signature
        );
    }

    static Map<HydrologyFeatureType, HydrologyFeatureRef> deterministicAcceptedCoverage() {
        EnumMap<HydrologyFeatureType, HydrologyFeatureRef> coverage = new EnumMap<>(HydrologyFeatureType.class);
        for (long seed : DEFAULT_SEEDS) {
            collectAccepted(
                    coverage,
                    new HydrologyPlanner(seed, settings(), terrain(seed)).plan(ORIGIN)
            );
        }
        collectAccepted(coverage, surfaceInlandTile());
        collectAccepted(coverage, undergroundInlandTile());
        collectAccepted(coverage, surfaceMouthTile());
        collectAccepted(coverage, coastalGrottoTile());
        requireCompleteCoverage(coverage.keySet());
        return Map.copyOf(coverage);
    }

    private static void collectAccepted(
            Map<HydrologyFeatureType, HydrologyFeatureRef> features,
            HydrologyTile tile
    ) {
        for (HydrologyFeatureRef feature : tile.features()) {
            features.putIfAbsent(feature.type(), feature);
        }
    }

    private static void requireCompleteCoverage(Set<HydrologyFeatureType> observed) {
        EnumSet<HydrologyFeatureType> missing = EnumSet.copyOf(REQUIRED_FEATURE_TYPES);
        missing.removeAll(observed);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Hydrology accepted-feature coverage is missing " + missing + ".");
        }
    }

    private static HydrologyTile surfaceInlandTile() {
        return new HydrologyPlanner(
                SURFACE_INLAND_SEED,
                inlandSettings(0D, 0D),
                inlandTerrain(true, false),
                solidCaveView()
        ).plan(ORIGIN);
    }

    private static HydrologyTile undergroundInlandTile() {
        return new HydrologyPlanner(
                UNDERGROUND_INLAND_SEED,
                inlandSettings(0D, 1D),
                inlandTerrain(false, true),
                solidCaveView()
        ).plan(ORIGIN);
    }

    private static HydrologyTile surfaceMouthTile() {
        return new HydrologyPlanner(
                COASTAL_SEED,
                settings(false),
                coastalTerrain(false)
        ).plan(ORIGIN);
    }

    private static HydrologyTile coastalGrottoTile() {
        return new HydrologyPlanner(
                COASTAL_SEED,
                settings(true),
                coastalTerrain(true),
                solidCaveView()
        ).plan(ORIGIN);
    }

    private static HydrologyPlannerSettings inlandSettings(
            double surfaceDensity,
            double undergroundDensity
    ) {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                surfaceDensity,
                80,
                0,
                6,
                24
        );
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true,
                undergroundDensity,
                Integer.MIN_VALUE,
                0,
                4,
                32
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(
                        128,
                        16,
                        512,
                        256,
                        32,
                        16,
                        0.5D,
                        12D,
                        0.5D,
                        0.1D,
                        1D,
                        0
                ),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        4,
                        18,
                        2,
                        4,
                        10,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()
                ),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        undergroundDensity > 0D,
                        undergroundSources,
                        68,
                        82,
                        4,
                        12,
                        2,
                        4,
                        5,
                        9,
                        true,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        false,
                        new HydrologyPlannerSettings.Grotto(false, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                        true,
                        12,
                        32,
                        2,
                        4,
                        4
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private static HydrologyTerrainSampler inlandTerrain(
            boolean surfaceSource,
            boolean undergroundSource
    ) {
        return (int x, int z) -> {
            boolean source = x == 0 && z == 0;
            boolean outlet = x == 64 && z == 64;
            int height = 120 - Math.floorDiv(x + z, 16);
            return new HydrologyTerrainSample(
                    height,
                    1D,
                    false,
                    true,
                    72,
                    74,
                    true,
                    outlet,
                    surfaceSource && source,
                    surfaceSource && source,
                    undergroundSource && source,
                    undergroundSource && source,
                    0D,
                    surfaceSource && source ? 1D : 0D,
                    undergroundSource && source ? 1D : 0D,
                    1D,
                    1D,
                    1D,
                    1D,
                    1D,
                    "parent",
                    "surface",
                    "mouth",
                    "shore",
                    "dry",
                    "flooded",
                    List.of("water"), List.of()
            ,
                Double.NaN,
                null, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
        };
    }

    private static HydrologyTerrainSampler coastalTerrain(boolean cliffCoast) {
        return (int x, int z) -> {
            if (x >= 208) {
                return oceanTerrain();
            }
            int height;
            double slope;
            if (x < 104) {
                height = 100 - Math.floorDiv(x, 8);
                slope = 1D;
            } else if (cliffCoast) {
                height = 84 - Math.floorDiv(x - 104, 26);
                slope = 1D;
            } else {
                height = 79 - Math.floorDiv(x - 104, 12);
                slope = x < 112 ? 4D : 1D;
            }
            boolean surfaceSource = x <= 40;
            boolean undergroundSource = x <= 64;
            return new HydrologyTerrainSample(
                    height,
                    slope,
                    false,
                    true,
                    height - 30,
                    height - 28,
                    true,
                    true,
                    surfaceSource,
                    surfaceSource,
                    undergroundSource,
                    undergroundSource,
                    0D,
                    surfaceSource ? 1D : 0D,
                    undergroundSource ? 1D : 0D,
                    1D,
                    1D,
                    1D,
                    1D,
                    1D,
                    "parent",
                    "surface",
                    "mouth",
                    "shore",
                    "bank",
                    "flooded",
                    List.of("water"), List.of()
            ,
                Double.NaN,
                null, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
        };
    }

    private static CaveVoxelView solidCaveView() {
        return new CaveVoxelView() {
            @Override
            public boolean isInWorld(CavePosition position) {
                return position.y() > -4096 && position.y() < 4096;
            }

            @Override
            public CaveVoxel voxelAt(CavePosition position) {
                return CaveVoxel.SOLID;
            }

            @Override
            public boolean isOpenToSurface(CavePosition position) {
                return false;
            }

            @Override
            public boolean isAboveTerrainSurface(CavePosition position) {
                return false;
            }
        };
    }

    private static HydrologyPlannerSettings settings() {
        return settings(true);
    }

    private static HydrologyPlannerSettings settings(boolean coastalGrottosEnabled) {
        HydrologyPlannerSettings.Source surfaceSources = new HydrologyPlannerSettings.Source(
                true,
                4D,
                80,
                1,
                6,
                32
        );
        HydrologyPlannerSettings.Source undergroundSources = new HydrologyPlannerSettings.Source(
                true,
                4D,
                Integer.MIN_VALUE,
                1,
                4,
                48
        );
        HydrologyPlannerSettings.DeepFluid deepFluid = new HydrologyPlannerSettings.DeepFluid(
                "deep_lava",
                true,
                3D,
                48,
                18,
                42,
                3,
                5,
                2,
                3,
                8,
                24,
                4,
                2,
                3,
                4096,
                4,
                true,
                true
        );
        return new HydrologyPlannerSettings(
                63,
                new HydrologyPlannerSettings.Routing(
                        256,
                        16,
                        1_089,
                        256,
                        32,
                        16,
                        0.5D,
                        12D,
                        0.5D,
                        0.2D,
                        1D,
                        0
                ),
                new HydrologyPlannerSettings.Surface(
                        true,
                        surfaceSources,
                        4,
                        20,
                        2,
                        5,
                        12,
                        1.5D,
                        HydrologyPlannerSettings.Banks.defaults()
                ),
                new HydrologyPlannerSettings.Hydraulics(4),
                HydrologyPlannerSettings.Underground.of(
                        true,
                        undergroundSources,
                        68,
                        82,
                        4,
                        14,
                        2,
                        5,
                        6,
                        12,
                        true,
                        0
                ),
                HydrologyPlannerSettings.Outlets.of(
                        true,
                        new HydrologyPlannerSettings.Grotto(coastalGrottosEnabled, 4, 3, 3, 4096),
                        new HydrologyPlannerSettings.Grotto(true, 4, 3, 3, 4096),
                        true,
                        12,
                        48,
                        2,
                        8,
                        8
                ),
                HydrologyPlannerSettings.Geometry.defaults(),
                List.of(deepFluid), List.of(),
                0D,
                HydrologyPlannerSettings.SeaCaves.disabled(),
                HydrologyPlannerSettings.SurfacePolicyBounds.NONE
        );
    }

    private static HydrologyTerrainSampler terrain(long seed) {
        return (int x, int z) -> {
            if (x >= 224) {
                return oceanTerrain();
            }
            double wave = StrictMath.sin((z + seed % 37L) / 19D) * 3D;
            double ridge = x >= 96 && x <= 128 ? 34D : 0D;
            double cliff = x >= 144 ? -24D : 0D;
            int height = 126 - Math.floorDiv(x, 13) + (int) StrictMath.round(wave + ridge + cliff);
            double slope = ridge > 0D || x >= 136 && x <= 144
                    ? 18D
                    : 1D + StrictMath.abs(wave) * 0.25D;
            boolean surfaceSource = x >= 0 && x <= 48;
            boolean undergroundSource = x >= 0 && x <= 64;
            return new HydrologyTerrainSample(
                    height,
                    slope,
                    false,
                    true,
                    70,
                    74 + Math.floorMod(x + z, 7),
                    true,
                    true,
                    surfaceSource,
                    surfaceSource,
                    undergroundSource,
                    undergroundSource,
                    0D,
                    surfaceSource ? 1D : 0D,
                    undergroundSource ? 1D : 0D,
                    1D,
                    1D,
                    1D,
                    1D,
                    1D,
                    "parent",
                    "surface",
                    "mouth",
                    "shore",
                    "dry",
                    "flooded",
                    List.of("water"), List.of(),
                    Double.NaN,
                    null,
                    Double.NaN,
                    true,
                    SurfaceRiverPolicy.INHERIT
            );
        };
    }

    private static HydrologyTerrainSample oceanTerrain() {
        return new HydrologyTerrainSample(
                54,
                0D,
                true,
                false,
                30,
                32,
                false,
                false,
                false,
                false,
                false,
                false,
                0D,
                0D,
                0D,
                1D,
                1D,
                1D,
                1D,
                1D,
                "ocean_parent",
                "surface",
                "mouth",
                "shore",
                "dry",
                "flooded",
                List.of("water"), List.of()
        ,
                Double.NaN,
                null, Double.NaN, true,
                SurfaceRiverPolicy.INHERIT
        );
    }

    private static long[] parseSeeds(String[] arguments) {
        if (arguments.length == 0) {
            return DEFAULT_SEEDS.clone();
        }
        long[] seeds = new long[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            seeds[index] = Long.parseLong(arguments[index]);
        }
        return seeds;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = Math.max(0, (int) StrictMath.ceil(sorted.length * percentile) - 1);
        return sorted[index];
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001B3L;
    }
}
