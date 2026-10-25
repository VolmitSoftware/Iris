package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPermission;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.generation.BiomeBuffetLayout;
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

public final class IrisHydrologyTerrainSource implements HydrologyTerrainSource {
    private static final int MINIMUM_SLICE = 256;
    private static final int POLICY_CAPACITY = 4096;

    private final IrisComplex complex;
    private final IrisDimension dimension;
    private final HydrologySettings settings;
    private final ExecutorService pool;
    private final int parallelism;
    private final ProceduralStream<IrisRegion> regions;
    private final ProceduralStream<IrisBiome> biomes;
    private final ProceduralStream<InferredType> continents;
    private final BiomeBuffetLayout buffet;
    private final IrisBiome focus;
    private final HydrologyBinding binding;
    private final ConcurrentHashMap<PolicyKey, Policy> policies = new ConcurrentHashMap<>();

    public IrisHydrologyTerrainSource(IrisComplex complex, IrisDimension dimension, HydrologySettings settings, ExecutorService pool,
                                      int parallelism, HydrologyBinding binding) {
        this.complex = Objects.requireNonNull(complex, "complex");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.pool = pool;
        this.parallelism = Math.max(1, parallelism);
        this.regions = Objects.requireNonNull(complex.getRegionStream(), "region stream");
        this.biomes = Objects.requireNonNull(complex.getBaseBiomeStream(), "base biome stream");
        this.continents = Objects.requireNonNull(complex.getBridgeStream(), "continent stream");
        this.buffet = complex.getBiomeBuffet();
        this.focus = complex.getFocusBiome();
        this.binding = Objects.requireNonNull(binding, "binding");
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

    public int naturalHeight(int x, int z) {
        return complex.naturalTrueHeight(x, z);
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
        Policy policy = policy(biomes.get(x, z), regions.get(x, z));
        out.height()[index] = (float) height;
        out.ocean()[index] = StrictMath.round(height) < settings.seaLevel() && oceanIntent(x, z);
        out.rainfall()[index] = policy.rainfall();
        out.pondDensity()[index] = policy.pondDensity();
        out.permission()[index] = policy.permission();
        out.fluidProfile()[index] = policy.fluid();
    }

    private boolean oceanIntent(int x, int z) {
        BiomeBuffetLayout.Cell cell = buffet == null ? null : buffet.terrain(x, z);
        IrisBiome focused = cell == null ? focus : cell.biome();
        InferredType type = focused == null ? continents.get(x, z) : focused.getInferredType();
        return type == InferredType.SEA;
    }

    private Policy policy(IrisBiome biome, IrisRegion region) {
        PolicyKey key = new PolicyKey(biome, region);
        Policy known = policies.get(key);
        if (known != null) {
            return known;
        }
        Policy resolved = resolve(biome, region);
        if (policies.size() >= POLICY_CAPACITY) {
            policies.clear();
        }
        Policy raced = policies.putIfAbsent(key, resolved);
        return raced == null ? resolved : raced;
    }

    private Policy resolve(IrisBiome biome, IrisRegion region) {
        double rainfall = inherited(biome, region, IrisHydrologyPolicy::getRainfall, 1D);
        double pondDensity = inherited(biome, region, IrisHydrologyPolicy::getPondDensity, 1D);
        IrisHydrologyPermission streams = inherited(biome, region, IrisHydrologyPolicy::getStreams, IrisHydrologyPermission.ALLOW);
        IrisHydrologyPermission ponds = inherited(biome, region, IrisHydrologyPolicy::getPonds, IrisHydrologyPermission.ALLOW);
        String fluid = inherited(biome, region, IrisHydrologyPolicy::getFluid, null);
        requireMultiplier("rainfall", rainfall, biome, region);
        requireMultiplier("pondDensity", pondDensity, biome, region);
        if (fluid != null && !settings.fluids().containsKey(fluid)) {
            throw new IllegalArgumentException("Hydrology policy for " + where(biome, region) + " names fluid '" + fluid
                    + "', which is not one of the hydrology fluids " + settings.fluids().keySet());
        }
        return new Policy((float) rainfall, (float) pondDensity, permission(streams, ponds), fluid);
    }

    private <T> T inherited(IrisBiome biome, IrisRegion region, Function<IrisHydrologyPolicy, T> field, T fallback) {
        T value = IrisHydrologyPolicy.inherited(biome, region, dimension, field);
        return value == null ? fallback : value;
    }

    private static byte permission(IrisHydrologyPermission streams, IrisHydrologyPermission ponds) {
        int bits = (streams == IrisHydrologyPermission.ALLOW ? HydrologySampleBuffer.ALLOW_STREAMS : 0)
                | (ponds == IrisHydrologyPermission.ALLOW ? HydrologySampleBuffer.ALLOW_PONDS : 0);
        return (byte) bits;
    }

    private static void requireMultiplier(String field, double value, IrisBiome biome, IrisRegion region) {
        if (!Double.isFinite(value) || value < 0D) {
            throw new IllegalArgumentException("Hydrology policy " + field + " for " + where(biome, region) + " must be a finite value of 0 or more, got "
                    + value);
        }
    }

    private static String where(IrisBiome biome, IrisRegion region) {
        return "biome " + (biome == null ? "none" : biome.getLoadKey()) + " in region " + (region == null ? "none" : region.getLoadKey());
    }

    private record Policy(float rainfall, float pondDensity, byte permission, String fluid) {
    }

    private record PolicyKey(IrisBiome biome, IrisRegion region) {
        @Override
        public boolean equals(Object other) {
            return other instanceof PolicyKey key && key.biome == biome && key.region == region;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(biome) + System.identityHashCode(region);
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
