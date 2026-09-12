package art.arcane.iris.generation.context;

import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.biome.FloatingIslandBoundarySampler;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.generation.concurrent.MultiBurst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkContext {
    private boolean naturalTerrain = true;
    private boolean speculativeTerrain;
    private Hunk<PlatformBiome> terrainBiomes;
    private FloatingBiomeOverlay floatingBiomes;
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
    private final DimensionStackContext dimensionStackContext;
    private final DimensionStackLayout[] dimensionStackLayouts;

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
        this(x, z, complex, generationSessionId, cache, prefillPlan, metrics, null);
    }

    public ChunkContext(
            int x,
            int z,
            IrisComplex complex,
            long generationSessionId,
            boolean cache,
            PrefillPlan prefillPlan,
            EngineMetrics metrics,
            DimensionStackContext dimensionStackContext
    ) {
        this.x = x;
        this.z = z;
        this.complex = complex;
        this.generationSessionId = generationSessionId;
        this.height = new ChunkedDoubleDataCache(complex.getRawHeightStream(), x, z, cache);
        this.roundedHeight = new int[cache ? 256 : 0];
        this.biome = new ChunkedDataCache<>(complex.getTrueBiomeStream(), x, z, cache);
        this.cave = new ChunkedDataCache<>(complex.getCaveBiomeStream(), x, z, cache);
        this.rock = new ChunkedDataCache<>(complex.getRockStream(), x, z, cache);
        this.fluid = new ChunkedDataCache<>(complex.getFluidStream(), x, z, cache);
        this.region = new ChunkedDataCache<>(complex.getRegionStream(), x, z, cache);
        this.floatingIslandBoundarySampler = new FloatingIslandBoundarySampler((wx, wz) -> complex.getTrueBiomeStream().get(wx, wz));
        this.dimensionStackContext = dimensionStackContext;
        this.dimensionStackLayouts = dimensionStackContext == null ? null : new DimensionStackLayout[256];

        if (cache) {
            PrefillPlan resolvedPlan = prefillPlan == null ? PrefillPlan.NO_CAVE : prefillPlan;
            boolean capturePrefillMetric = metrics != null;
            long totalStartNanos = capturePrefillMetric ? System.nanoTime() : 0L;
            IrisHydrologyRuntime hydrologyRuntime = complex.getHydrologyRuntime();
            if (hydrologyRuntime != null && (resolvedPlan.height || resolvedPlan.biome)) {
                hydrologyRuntime.prepareChunkColumns(x, z);
            }
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

            if (!shouldPrefillAsync(resolvedPlan, fillTasks.size())) {
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

    static boolean shouldPrefillAsync(PrefillPlan prefillPlan, int fillTaskCount) {
        if (prefillPlan == PrefillPlan.NATURAL_TERRAIN || fillTaskCount <= 1 || !IrisPlatforms.isBound()) {
            return false;
        }

        return !MultiBurst.burst.ownsCurrentThread();
    }

    public void setTerrainBiomeOutput(Hunk<PlatformBiome> terrainBiomes) {
        this.terrainBiomes = terrainBiomes;
    }

    public FloatingBiomeOverlay getFloatingBiomes() {
        return floatingBiomes;
    }

    public void setFloatingBiomes(FloatingBiomeOverlay floatingBiomes) {
        this.floatingBiomes = floatingBiomes;
    }

    public FloatingBiomeOverlay floatingBiomes(int height) {
        if (floatingBiomes == null) {
            floatingBiomes = new FloatingBiomeOverlay(height);
        }
        return floatingBiomes;
    }

    public void setNaturalBiome(int localX, int y, int localZ, PlatformBiome biome) {
        if (terrainBiomes != null) {
            terrainBiomes.setRaw(localX, y, localZ, biome);
        }
    }

    public boolean isSpeculativeTerrain() {
        return speculativeTerrain;
    }

    public void beginSpeculativeTerrain() {
        speculativeTerrain = true;
    }

    public boolean isNaturalTerrain() {
        return naturalTerrain;
    }

    public void beginContent() {
        naturalTerrain = false;
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

    public void setTerrainHeight(int localX, int localZ, int terrainHeight) {
        height.setDouble(localX, localZ, terrainHeight);
        if (roundedHeight.length > 0) {
            roundedHeight[(localZ << 4) + localX] = terrainHeight;
        }
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

    public DimensionStackLayout getDimensionStackLayout(int localX, int localZ) {
        if (dimensionStackContext == null) {
            return null;
        }
        int index = (localZ << 4) | localX;
        DimensionStackLayout layout = dimensionStackLayouts[index];
        if (layout == null) {
            layout = dimensionStackContext.sample(x + localX, z + localZ);
            dimensionStackLayouts[index] = layout;
        }
        return layout;
    }

    public enum PrefillPlan {
        ALL(true, true, true, true, true, true),
        NO_CAVE(true, true, false, true, true, true),
        NATURAL_TERRAIN(true, true, false, true, true, true),
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
