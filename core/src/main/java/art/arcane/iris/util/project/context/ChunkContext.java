package art.arcane.iris.util.project.context;

import art.arcane.iris.Iris;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.util.common.parallel.MultiBurst;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkContext {
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
            boolean capturePrefillMetric = metrics != null;
            long totalStartNanos = capturePrefillMetric ? System.nanoTime() : 0L;
            List<PrefillFillTask> fillTasks = new ArrayList<>(6);
            if (resolvedPlan.height) {
                fillTasks.add(new PrefillFillTask(height));
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

            if (fillTasks.size() <= 1 || Iris.instance == null) {
                for (PrefillFillTask fillTask : fillTasks) {
                    fillTask.run();
                }
            } else {
                List<CompletableFuture<Void>> futures = new ArrayList<>(fillTasks.size());
                for (PrefillFillTask fillTask : fillTasks) {
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
