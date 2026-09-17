package art.arcane.iris.util.project.context;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.parallel.MultiBurst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkContext {
    private final int x;
    private final int z;
    private final IrisComplex complex;
    private final long generationSessionId;
    private final ChunkedDoubleDataCache height;
    private final int[] roundedHeight;
    private final ChunkedDataCache<IrisBiome> biome;
    private final ChunkedDataCache<IrisBiome> cave;
    private final ChunkedDataCache<PlatformBlockState> rock;
    private final ChunkedDataCache<PlatformBlockState> fluid;
    private final ChunkedDataCache<IrisRegion> region;
    private final FloatingIslandBoundarySampler floatingIslandBoundarySampler;

    public ChunkContext(int x, int z, IrisComplex complex) {
        this(x, z, complex, 0L, true, PrefillPlan.NO_CAVE, null);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache) {
        this(x, z, complex, 0L, cache, PrefillPlan.NO_CAVE, null);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache, EngineMetrics metrics) {
        this(x, z, complex, 0L, cache, PrefillPlan.NO_CAVE, metrics);
    }

    public ChunkContext(int x, int z, IrisComplex complex, boolean cache, PrefillPlan prefillPlan, EngineMetrics metrics) {
        this(x, z, complex, 0L, cache, prefillPlan, metrics);
    }

    public ChunkContext(int x, int z, IrisComplex complex, long generationSessionId, boolean cache, PrefillPlan prefillPlan, EngineMetrics metrics) {
        this.x = x;
        this.z = z;
        this.complex = complex;
        this.generationSessionId = generationSessionId;
        this.height = new ChunkedDoubleDataCache(complex.getHeightStream(), x, z, cache);
        this.roundedHeight = new int[cache ? 256 : 0];
        this.biome = new ChunkedDataCache<>(complex.getTrueBiomeStream(), x, z, cache);
        this.cave = new ChunkedDataCache<>(complex.getCaveBiomeStream(), x, z, cache);
        this.rock = new ChunkedDataCache<>(complex.getRockStream(), x, z, cache);
        this.fluid = new ChunkedDataCache<>(complex.getFluidStream(), x, z, cache);
        this.region = new ChunkedDataCache<>(complex.getRegionStream(), x, z, cache);
        this.floatingIslandBoundarySampler = new FloatingIslandBoundarySampler((wx, wz) -> complex.getTrueBiomeStream().get(wx, wz));

        if (cache) {
            PrefillPlan resolvedPlan = prefillPlan == null ? PrefillPlan.NO_CAVE : prefillPlan;
            boolean capturePrefillMetric = metrics != null;
            long totalStartNanos = capturePrefillMetric ? System.nanoTime() : 0L;
            List<Runnable> fillTasks = new ArrayList<>(6);
            if (resolvedPlan.height) {
                fillTasks.add(() -> height.fillRounded(roundedHeight));
            }
            if (resolvedPlan.biome) {
                fillTasks.add(new PrefillFillTask(biome));
            }
            if (resolvedPlan.rock) {
                fillTasks.add(new PrefillFillTask(rock));
            }
            if (resolvedPlan.fluid) {
                fillTasks.add(new PrefillFillTask(fluid));
            }
            if (resolvedPlan.region) {
                fillTasks.add(new PrefillFillTask(region));
            }
            if (resolvedPlan.cave) {
                fillTasks.add(new PrefillFillTask(cave));
            }

            if (!shouldPrefillAsync(fillTasks.size())) {
                for (Runnable fillTask : fillTasks) {
                    fillTask.run();
                }
            } else {
                List<CompletableFuture<Void>> futures = new ArrayList<>(fillTasks.size());
                for (Runnable fillTask : fillTasks) {
                    futures.add(CompletableFuture.runAsync(fillTask, MultiBurst.burst));
                }
                for (CompletableFuture<Void> future : futures) {
                    future.join();
                }
            }
            if (capturePrefillMetric) {
                metrics.getContextPrefill().put((System.nanoTime() - totalStartNanos) / 1_000_000D);
            }
        }
    }

    static boolean shouldPrefillAsync(int fillTaskCount) {
        if (fillTaskCount <= 1 || !IrisPlatforms.isBound()) {
            return false;
        }

        return !MultiBurst.burst.ownsCurrentThread();
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public IrisComplex getComplex() {
        return complex;
    }

    public FloatingIslandBoundarySampler getFloatingIslandBoundarySampler() {
        return floatingIslandBoundarySampler;
    }

    public long getGenerationSessionId() {
        return generationSessionId;
    }

    public ChunkedDoubleDataCache getHeight() {
        return height;
    }

    public int getRoundedHeight(int x, int z) {
        if (roundedHeight.length == 0) {
            return (int) Math.round(height.getDouble(x, z));
        }

        return roundedHeight[(z << 4) + x];
    }

    public ChunkedDataCache<IrisBiome> getBiome() {
        return biome;
    }

    public ChunkedDataCache<IrisBiome> getCave() {
        return cave;
    }

    public ChunkedDataCache<PlatformBlockState> getRock() {
        return rock;
    }

    public ChunkedDataCache<PlatformBlockState> getFluid() {
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

    private static final class PrefillFillTask implements Runnable {
        private final ChunkedDataCache<?> dataCache;

        private PrefillFillTask(ChunkedDataCache<?> dataCache) {
            this.dataCache = dataCache;
        }

        @Override
        public void run() {
            dataCache.fill();
        }
    }
}
