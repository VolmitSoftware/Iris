package art.arcane.iris.util.project.context;

import art.arcane.iris.core.IrisHotPathMetricsMode;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.volmlib.util.atomics.AtomicRollingSequence;
import org.bukkit.block.data.BlockData;

import java.util.IdentityHashMap;

public class ChunkContext {
    private static final int PREFILL_METRICS_FLUSH_SIZE = 64;
    private static final ThreadLocal<PrefillMetricsState> PREFILL_METRICS = ThreadLocal.withInitial(PrefillMetricsState::new);
    private final int x;
    private final int z;
    private final ChunkedDataCache<Double> height;
    private final ChunkedDataCache<IrisBiome> biome;
    private final ChunkedDataCache<IrisBiome> cave;
    private final ChunkedDataCache<BlockData> rock;
    private final ChunkedDataCache<BlockData> fluid;
    private final ChunkedDataCache<IrisRegion> region;

    public ChunkContext(int x, int z, IrisComplex complex) {
        this(x, z, complex, true, PrefillPlan.NO_CAVE, null);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache) {
        this(x, z, complex, cache, PrefillPlan.NO_CAVE, null);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache, EngineMetrics metrics) {
        this(x, z, complex, cache, PrefillPlan.NO_CAVE, metrics);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache, PrefillPlan prefillPlan, EngineMetrics metrics) {
        this.x = x;
        this.z = z;
        this.height = new ChunkedDataCache<>(complex.getHeightStream(), x, z, cache);
        this.biome = new ChunkedDataCache<>(complex.getTrueBiomeStream(), x, z, cache);
        this.cave = new ChunkedDataCache<>(complex.getCaveBiomeStream(), x, z, cache);
        this.rock = new ChunkedDataCache<>(complex.getRockStream(), x, z, cache);
        this.fluid = new ChunkedDataCache<>(complex.getFluidStream(), x, z, cache);
        this.region = new ChunkedDataCache<>(complex.getRegionStream(), x, z, cache);

        if (cache) {
            PrefillPlan resolvedPlan = prefillPlan == null ? PrefillPlan.NO_CAVE : prefillPlan;
            PrefillMetricsState metricsState = PREFILL_METRICS.get();
            IrisSettings.IrisSettingsPregen pregen = IrisSettings.get().getPregen();
            IrisHotPathMetricsMode metricsMode = pregen.getHotPathMetricsMode();
            boolean sampleMetrics = metricsMode != IrisHotPathMetricsMode.DISABLED
                    && metricsState.shouldSample(metricsMode, pregen.getHotPathMetricsSampleStride());
            long totalStartNanos = sampleMetrics ? System.nanoTime() : 0L;
            if (resolvedPlan.height) {
                fill(height, metrics == null ? null : metrics.getContextPrefillHeight(), sampleMetrics, metricsState);
            }
            if (resolvedPlan.biome) {
                fill(biome, metrics == null ? null : metrics.getContextPrefillBiome(), sampleMetrics, metricsState);
            }
            if (resolvedPlan.rock) {
                fill(rock, metrics == null ? null : metrics.getContextPrefillRock(), sampleMetrics, metricsState);
            }
            if (resolvedPlan.fluid) {
                fill(fluid, metrics == null ? null : metrics.getContextPrefillFluid(), sampleMetrics, metricsState);
            }
            if (resolvedPlan.region) {
                fill(region, metrics == null ? null : metrics.getContextPrefillRegion(), sampleMetrics, metricsState);
            }
            if (resolvedPlan.cave) {
                fill(cave, metrics == null ? null : metrics.getContextPrefillCave(), sampleMetrics, metricsState);
            }
            if (metrics != null && sampleMetrics) {
                metricsState.record(metrics.getContextPrefill(), System.nanoTime() - totalStartNanos);
            }
        }
    }

    private void fill(ChunkedDataCache<?> dataCache, AtomicRollingSequence metrics, boolean sampleMetrics, PrefillMetricsState metricsState) {
        long startNanos = sampleMetrics ? System.nanoTime() : 0L;
        dataCache.fill();
        if (metrics != null && sampleMetrics) {
            metricsState.record(metrics, System.nanoTime() - startNanos);
        }
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public ChunkedDataCache<Double> getHeight() {
        return height;
    }

    public ChunkedDataCache<IrisBiome> getBiome() {
        return biome;
    }

    public ChunkedDataCache<IrisBiome> getCave() {
        return cave;
    }

    public ChunkedDataCache<BlockData> getRock() {
        return rock;
    }

    public ChunkedDataCache<BlockData> getFluid() {
        return fluid;
    }

    public ChunkedDataCache<IrisRegion> getRegion() {
        return region;
    }

    public enum PrefillPlan {
        ALL(true, true, true, true, true, true),
        NO_CAVE(true, true, false, true, true, true),
        NONE(false, false, false, false, false, false);

        private final boolean height;
        private final boolean biome;
        private final boolean cave;
        private final boolean rock;
        private final boolean fluid;
        private final boolean region;

        PrefillPlan(boolean height, boolean biome, boolean cave, boolean rock, boolean fluid, boolean region) {
            this.height = height;
            this.biome = biome;
            this.cave = cave;
            this.rock = rock;
            this.fluid = fluid;
            this.region = region;
        }
    }

    private static final class PrefillMetricsState {
        private long callCounter;
        private final IdentityHashMap<AtomicRollingSequence, MetricBucket> buckets = new IdentityHashMap<>();

        private boolean shouldSample(IrisHotPathMetricsMode mode, int sampleStride) {
            if (mode == IrisHotPathMetricsMode.EXACT) {
                return true;
            }

            long current = callCounter++;
            return (current & (sampleStride - 1L)) == 0L;
        }

        private void record(AtomicRollingSequence sequence, long nanos) {
            if (sequence == null || nanos < 0L) {
                return;
            }

            MetricBucket bucket = buckets.get(sequence);
            if (bucket == null) {
                bucket = new MetricBucket();
                buckets.put(sequence, bucket);
            }

            bucket.nanos += nanos;
            bucket.samples++;
            if (bucket.samples >= PREFILL_METRICS_FLUSH_SIZE) {
                double averageMs = (bucket.nanos / (double) bucket.samples) / 1_000_000D;
                sequence.put(averageMs);
                bucket.nanos = 0L;
                bucket.samples = 0;
            }
        }
    }

    private static final class MetricBucket {
        private long nanos;
        private int samples;
    }
}
