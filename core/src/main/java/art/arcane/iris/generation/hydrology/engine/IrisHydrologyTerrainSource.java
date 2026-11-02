package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPermission;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyWetness;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.raster.ChannelFluids;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.generation.BiomeBuffetLayout;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class IrisHydrologyTerrainSource implements HydrologyTerrainSource, ChannelFluids {
    private static final int MINIMUM_SLICE = 256;
    private static final int POLICY_CAPACITY = 4096;
    private static final long SALT_CLIMATE = 0x434c494d415445L;

    private final IrisComplex complex;
    private final IrisDimension dimension;
    private final HydrologySettings settings;
    private final ExecutorService pool;
    private final int parallelism;
    private final ProceduralStream<IrisRegion> regions;
    private final ProceduralStream<InferredType> continents;
    private final BiomeBuffetLayout buffet;
    private final IrisBiome focus;
    private final HydrologyBinding binding;
    private final HydrologySettings.Climate climate;
    private final CNG wetness;
    private final ConcurrentHashMap<PolicyKey, Policy> policies = new ConcurrentHashMap<>();

    public IrisHydrologyTerrainSource(IrisComplex complex, IrisDimension dimension, HydrologySettings settings, ExecutorService pool,
                                      int parallelism, HydrologyBinding binding) {
        this.complex = Objects.requireNonNull(complex, "complex");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.pool = pool;
        this.parallelism = Math.max(1, parallelism);
        this.regions = Objects.requireNonNull(complex.getRegionStream(), "region stream");
        this.continents = Objects.requireNonNull(complex.getBridgeStream(), "continent stream");
        this.buffet = complex.getBiomeBuffet();
        this.focus = complex.getFocusBiome();
        this.binding = Objects.requireNonNull(binding, "binding");
        this.climate = settings.climate();
        this.wetness = climate.enabled() ? climate.style().create(settings.seed() ^ SALT_CLIMATE, complex.getData()) : null;
    }

    @Override
    public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
        if (xs.length != zs.length) {
            throw new IllegalArgumentException("Hydrology terrain sample has " + xs.length + " x and " + zs.length + " z coordinates");
        }
        if (out.height().length < xs.length) {
            throw new IllegalArgumentException("Sample buffer capacity " + out.height().length + " is below " + xs.length);
        }
        int slices = pool == null ? 1 : Math.min(parallelism, Math.ceilDiv(xs.length, MINIMUM_SLICE));
        if (slices < 2) {
            sampleRange(xs, zs, out, baseOnly, 0, xs.length);
        } else {
            sampleParallel(xs, zs, out, baseOnly, slices);
        }
    }

    public double naturalSurface(int x, int z) {
        return complex.naturalHeight(x, z);
    }

    @Override
    public String channelFluid(int x, int z) {
        ChunkBakeCache.enterSparse();
        try {
            return policy(complex.baseBiomeLineage(x, z), regions.get(x, z)).fluid();
        } finally {
            ChunkBakeCache.exitSparse();
        }
    }

    @Override
    public int seaLevel() {
        return settings.seaLevel();
    }

    @Override
    public int minY() {
        return settings.minY();
    }

    @Override
    public int maxY() {
        return settings.maxY();
    }

    private void sampleParallel(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly, int slices) {
        int size = Math.ceilDiv(xs.length, slices);
        List<Slice> parts = new ArrayList<>(slices);
        for (int from = 0; from < xs.length; from += size) {
            parts.add(new Slice(xs, zs, out, baseOnly, from, Math.min(xs.length, from + size)));
        }
        for (int index = 1; index < parts.size(); index++) {
            try {
                pool.execute(parts.get(index));
            } catch (RejectedExecutionException rejected) {
                break;
            }
        }
        for (Slice part : parts) {
            part.run();
        }
        Throwable failure = null;
        for (Slice part : parts) {
            Throwable failed = part.await();
            if (failure == null) {
                failure = failed;
            } else if (failed != null && failed != failure) {
                failure.addSuppressed(failed);
            }
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException("Hydrology terrain sampling failed.", failure);
        }
    }

    private void sampleRange(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly, int from, int to) {
        double[] heights = new double[to - from];
        try (HydrologyBinding.Scope ignored = binding.open()) {
            ChunkBakeCache.enterSparse();
            try {
                complex.sampleHydrologyHeights(xs, zs, from, to, baseOnly, heights);
                for (int index = from; index < to; index++) {
                    sampleColumn(xs[index], zs[index], heights[index - from], out, baseOnly, index);
                }
            } finally {
                ChunkBakeCache.exitSparse();
            }
        }
    }

    private void sampleColumn(int x, int z, double height, HydrologySampleBuffer out, boolean baseOnly, int index) {
        if (!Double.isFinite(height)) {
            throw new IllegalStateException("Hydrology " + (baseOnly ? "base" : "natural") + " height at " + x + "," + z + " is " + height);
        }
        Policy policy = policy(complex.baseBiomeLineage(x, z), regions.get(x, z));
        out.height()[index] = (float) height;
        out.ocean()[index] = StrictMath.round(height) < settings.seaLevel() && oceanIntent(x, z);
        boolean zoned = wetness != null && policy.climateZones();
        float wet = zoned ? (float) Math.clamp(climate.wetness(wetness.noise(x, z)), policy.minWetness(), policy.maxWetness()) : 1f;
        out.rainfall()[index] = policy.rainfall() * wet;
        out.pondDensity()[index] = policy.pondDensity() * wet;
        out.wetness()[index] = wet;
        out.climateZoned()[index] = zoned;
        out.permission()[index] = policy.permission();
        out.fluidProfile()[index] = policy.fluid();
    }

    private boolean oceanIntent(int x, int z) {
        BiomeBuffetLayout.Cell cell = buffet == null ? null : buffet.terrain(x, z);
        IrisBiome focused = cell == null ? focus : cell.biome();
        InferredType type = focused == null ? continents.get(x, z) : focused.getInferredType();
        return type == InferredType.SEA;
    }

    private Policy policy(List<IrisBiome> biomes, IrisRegion region) {
        PolicyKey key = new PolicyKey(biomes, region);
        Policy known = policies.get(key);
        if (known != null) {
            return known;
        }
        Policy resolved = resolve(biomes, region);
        if (policies.size() >= POLICY_CAPACITY) {
            policies.clear();
        }
        Policy raced = policies.putIfAbsent(key, resolved);
        return raced == null ? resolved : raced;
    }

    private Policy resolve(List<IrisBiome> biomes, IrisRegion region) {
        double rainfall = inherited(biomes, region, IrisHydrologyPolicy::getRainfall, 1D);
        double pondDensity = inherited(biomes, region, IrisHydrologyPolicy::getPondDensity, 1D);
        IrisHydrologyPermission streams = inherited(biomes, region, IrisHydrologyPolicy::getStreams, IrisHydrologyPermission.ALLOW);
        IrisHydrologyPermission ponds = inherited(biomes, region, IrisHydrologyPolicy::getPonds, IrisHydrologyPermission.ALLOW);
        String fluid = inherited(biomes, region, IrisHydrologyPolicy::getFluid, null);
        boolean climateZones = inherited(biomes, region, IrisHydrologyPolicy::getClimateZones, Boolean.TRUE);
        IrisHydrologyWetness wetness = inherited(biomes, region, IrisHydrologyPolicy::getWetness, null);
        requireMultiplier("rainfall", rainfall, biomes, region);
        requireMultiplier("pondDensity", pondDensity, biomes, region);
        if (fluid != null) {
            settings.requireFillingFluid(fluid, "the hydrology policy for " + where(biomes, region));
        }
        double minWetness = wetness == null ? 0D : wetness.getMin();
        double maxWetness = wetness == null ? 1D : wetness.getMax();
        requireWetness(minWetness, maxWetness, biomes, region);
        return new Policy((float) rainfall, (float) pondDensity, permission(streams, ponds), fluid, climateZones, (float) minWetness,
                (float) maxWetness);
    }

    private <T> T inherited(List<IrisBiome> biomes, IrisRegion region, Function<IrisHydrologyPolicy, T> field, T fallback) {
        T value = IrisHydrologyPolicy.inherited(biomes, region, dimension, field);
        return value == null ? fallback : value;
    }

    private static byte permission(IrisHydrologyPermission streams, IrisHydrologyPermission ponds) {
        int bits = (streams == IrisHydrologyPermission.ALLOW ? HydrologySampleBuffer.ALLOW_STREAMS : 0)
                | (ponds == IrisHydrologyPermission.ALLOW ? HydrologySampleBuffer.ALLOW_PONDS : 0);
        return (byte) bits;
    }

    private static void requireMultiplier(String field, double value, List<IrisBiome> biomes, IrisRegion region) {
        if (!IrisHydrologyPolicy.validMultiplier(value)) {
            throw new IllegalArgumentException("Hydrology policy " + field + " for " + where(biomes, region) + " must be a finite value from 0 to "
                    + (int) IrisHydrologyPolicy.MAX_MULTIPLIER + ", got " + value);
        }
    }

    private static void requireWetness(double min, double max, List<IrisBiome> biomes, IrisRegion region) {
        if (!(min >= 0D && min <= max && max <= 1D)) {
            throw new IllegalArgumentException("Hydrology policy wetness for " + where(biomes, region) + " must have 0 <= min <= max <= 1, got min "
                    + min + " and max " + max);
        }
    }

    private static String where(List<IrisBiome> biomes, IrisRegion region) {
        StringBuilder where = new StringBuilder("biome ").append(biomes.isEmpty() ? "none" : biomes.getFirst().getLoadKey());
        for (int index = 1; index < biomes.size(); index++) {
            where.append(" inside ").append(biomes.get(index).getLoadKey());
        }
        return where.append(" in region ").append(region == null ? "none" : region.getLoadKey()).toString();
    }

    private record Policy(float rainfall, float pondDensity, byte permission, String fluid, boolean climateZones, float minWetness,
                          float maxWetness) {
    }

    private record PolicyKey(List<IrisBiome> biomes, IrisRegion region) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PolicyKey key && key.biomes == biomes && key.region == region;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(biomes) + System.identityHashCode(region);
        }
    }

    private final class Slice implements Runnable {
        private final int[] xs;
        private final int[] zs;
        private final HydrologySampleBuffer out;
        private final boolean baseOnly;
        private final int from;
        private final int to;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile Throwable failure;

        private Slice(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly, int from, int to) {
            this.xs = xs;
            this.zs = zs;
            this.out = out;
            this.baseOnly = baseOnly;
            this.from = from;
            this.to = to;
        }

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                sampleRange(xs, zs, out, baseOnly, from, to);
            } catch (Throwable failed) {
                failure = failed;
            } finally {
                done.countDown();
            }
        }

        private Throwable await() {
            try {
                done.await();
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                CancellationException cancelled = new CancellationException("Hydrology terrain sampling was interrupted.");
                cancelled.initCause(interruption);
                throw cancelled;
            }
            return failure;
        }
    }
}
