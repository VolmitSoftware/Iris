package art.arcane.iris.probe;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.engine.HydrologyBinding;
import art.arcane.iris.generation.hydrology.engine.IrisHydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.function.IntBinaryOperator;

public final class HydrologyProbe {
    private static final int RASTER_CACHE = 8192;
    private static final int WARMUP_OFFSET = 8;
    private static final int MAXIMUM_WINDOWS = 256;

    private HydrologyProbe() {
    }

    record Configuration(HydrologyWindows.Target target, int threads) {
        Configuration {
            Objects.requireNonNull(target, "target");
            if (target.windows().count() > MAXIMUM_WINDOWS) {
                throw new IllegalArgumentException("The probe measures at most " + MAXIMUM_WINDOWS + " windows, got " + target.windows().count() + ".");
            }
            if (threads < 1) {
                throw new IllegalArgumentException("Threads must be positive.");
            }
        }

        static Configuration parse(String[] args) {
            if (args.length != 6) {
                throw new IllegalArgumentException("Expected <pack> <dimension|-> <seed> <x0..x1,z0..z1> <threads> <studio>.");
            }
            return new Configuration(HydrologyWindows.Target.parse(args[0], args[1], args[2], args[3], args[5]), Integer.parseInt(args[4]));
        }
    }

    record Inputs(HydrologySettings settings, HydrologyTerrainSource terrain, IntBinaryOperator naturalHeight, ForkJoinPool planningPool,
                  ExecutorService workers, HydrologyVoxels.ChunkSource blocks, HydrologyUnderground.Source underground, int worldHeight) {
        Inputs {
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(terrain, "terrain");
            Objects.requireNonNull(naturalHeight, "naturalHeight");
            Objects.requireNonNull(planningPool, "planningPool");
            Objects.requireNonNull(workers, "workers");
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(underground, "underground");
        }
    }

    public static void main(String[] args) {
        int exitCode = 2;
        try {
            HydrologyReport.Result result = run(Configuration.parse(args));
            exitCode = result.passes() ? 0 : 1;
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
        }
        System.exit(exitCode);
    }

    static HydrologyReport.Result run(Configuration configuration) throws Exception {
        StubPlatform.vanillaSolidity(true);
        HydrologyWindows.Target target = configuration.target();
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(target.pack(), target.dimension(), "[hydrology]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(target.seed(), target.studio(), "hydrology")) {
            Engine engine = session.engine();
            IrisDimension dimension = engine.getDimension();
            IrisHydrology config = dimension.getHydrology();
            if (config == null || !config.isEnabled()) {
                throw new IllegalStateException("The dimension " + target.dimension() + " must enable hydrology.");
            }
            HydrologySettings settings = HydrologySettings.compile(config, engine.getSeedManager().getBodies(), dimension.getFluidHeight(), 0,
                    engine.getHeight());
            IrisHydrologyTerrainSource terrain = new IrisHydrologyTerrainSource(engine.getComplex(), dimension, settings, MultiBurst.hydrology,
                    MultiBurst.hydrology.parallelism(), HydrologyBinding.UNBOUND);
            ForkJoinPool planningPool = MultiBurst.hydrology.submit((Callable<ForkJoinPool>) ForkJoinTask::getPool).get();
            ExecutorService workers = Executors.newFixedThreadPool(configuration.threads());
            try {
                Inputs inputs = new Inputs(settings, terrain, terrain::naturalHeight, planningPool, workers,
                        (chunkX, chunkZ) -> HydrologyVoxels.generate(engine, chunkX, chunkZ), HydrologyUnderground.generated(engine, workers),
                        engine.getTarget().getHeight());
                HydrologyReport.Result measured = measure(inputs, target.windows());
                List<Throwable> reports = RealPackProbeSupport.drainReported();
                RealPackProbeSupport.printReports("[hydrology]", "generation", reports);
                HydrologyReport.Result result = measured.withGenerationErrors(reports.size());
                for (String line : result.lines()) {
                    System.out.println(line);
                }
                return result;
            } finally {
                workers.shutdownNow();
            }
        }
    }

    static HydrologyReport.Result measure(Inputs inputs, HydrologyWindows.WindowRange range) throws Exception {
        return new Measurement(inputs, range).measure();
    }

    private static final class Measurement {
        private final Inputs inputs;
        private final HydrologyWindows.WindowRange range;
        private final HydrologyReport.Examples examples = new HydrologyReport.Examples();
        private final Set<String> failures = Collections.synchronizedSet(new LinkedHashSet<>());
        private HydrologyPlanHarness.Plans plans;
        private HydrologyPlanHarness.RasterCache rasters;

        private Measurement(Inputs inputs, HydrologyWindows.WindowRange range) {
            this.inputs = inputs;
            this.range = range;
        }

        private HydrologyReport.Result measure() throws Exception {
            double warmupMs = -1D;
            try {
                warmupMs = HydrologyColdRuns.cold(inputs, new WindowKey(range.minX() - WARMUP_OFFSET, range.minZ() - WARMUP_OFFSET)).millis();
            } catch (RuntimeException failure) {
                failures.add("warm-up cold run: " + HydrologyPlanHarness.cause(failure));
            }
            Map<WindowKey, HydrologyColdRuns.ColdRun> colds = new LinkedHashMap<>();
            for (WindowKey key : range.keys()) {
                try {
                    colds.put(key, HydrologyColdRuns.cold(inputs, key));
                } catch (RuntimeException failure) {
                    failures.add("cold window " + key.wx() + "," + key.wz() + ": " + HydrologyPlanHarness.cause(failure));
                    colds.put(key, HydrologyColdRuns.ColdRun.FAILED);
                }
            }
            plans = new HydrologyPlanHarness.Plans(inputs.settings(), inputs.terrain(), inputs.naturalHeight(), range);
            rasters = new HydrologyPlanHarness.RasterCache(RASTER_CACHE, plans::rasterize);
            Map<WindowKey, WindowPlan> windows = plans.windowPlans();
            HydrologyPlanMetrics planMetrics = new HydrologyPlanMetrics(inputs.settings(), windows, plans.lakes(), range, examples);
            HydrologyRasterMetrics rasterMetrics = new HydrologyRasterMetrics(inputs.settings(), planMetrics, rasters, inputs.naturalHeight(), range,
                    examples);
            HydrologyBlockMetrics blockMetrics = new HydrologyBlockMetrics(inputs, rasterMetrics, range, examples, failures::add);
            List<HydrologyReport.WindowMetrics> measured = new ArrayList<>();
            long generationErrors = 0L;
            for (WindowKey key : range.keys()) {
                WindowPlan plan = windows.get(key);
                if (plan == null) {
                    continue;
                }
                try {
                    prepareRasters(key);
                    HydrologyBlockMetrics.Counts blocks = blockMetrics.count(key);
                    generationErrors += blocks.errors();
                    HydrologyUnderground.Counts underground = underground(key, plan);
                    HydrologyColdRuns.ColdRun cold = colds.get(key);
                    measured.add(new HydrologyReport.WindowMetrics(key, cold.millis(), cold.naturalSamples(), cold.baseSamples(),
                            plans.windowCpu(key), plans.regionCpu(key.region()), cold.depth(), plans.landTiles(key), planMetrics.rivers(key),
                            planMetrics.streams(key), planMetrics.ponds(key), planMetrics.levels(key), planMetrics.perched(key),
                            blocks.scan().containmentLeaks, rasterMetrics.rising(key, plan), planMetrics.seams(key) + rasterMetrics.chunkSeams(key),
                            blocks.scan().tickConversionCells, blocks.scan().missingSurfaces, rasterMetrics.bermOverCap(key), underground.inlets(), underground.inletFailures(),
                            underground.cavePools(), underground.cavePoolFailures(), blocks.generated(), plans.digest(key)));
                } catch (RuntimeException failure) {
                    failures.add("window " + key.wx() + "," + key.wz() + ": " + HydrologyPlanHarness.cause(failure));
                }
            }
            failures.addAll(plans.failures());
            return new HydrologyReport.Result(measured, plans.meanRegionCpu(), plans.regionCount(), plans.windowCount(), plans.naturalSamples(),
                    plans.baseSamples(), warmupMs, generationErrors, plans.digest(), List.copyOf(failures), examples.values());
        }

        private HydrologyUnderground.Counts underground(WindowKey key, WindowPlan plan) {
            try {
                HydrologyUnderground.Counts counts = inputs.underground().measure(key, plan);
                for (String example : counts.examples()) {
                    examples.accept(example);
                }
                return counts;
            } catch (Exception failure) {
                failures.add("underground window " + key.wx() + "," + key.wz() + ": " + HydrologyPlanHarness.cause(failure));
                return HydrologyUnderground.Counts.NONE;
            }
        }

        private void prepareRasters(WindowKey key) throws InterruptedException {
            List<Callable<ChunkWaterRaster>> tasks = new ArrayList<>();
            List<String> chunks = new ArrayList<>();
            int minChunkX = key.wx() * HydrologyWindows.CHUNKS - 1;
            int minChunkZ = key.wz() * HydrologyWindows.CHUNKS - 1;
            for (int chunkZ = minChunkZ; chunkZ <= minChunkZ + HydrologyWindows.CHUNKS + 1; chunkZ++) {
                for (int chunkX = minChunkX; chunkX <= minChunkX + HydrologyWindows.CHUNKS + 1; chunkX++) {
                    if (range.containsChunk(chunkX, chunkZ)) {
                        int x = chunkX;
                        int z = chunkZ;
                        tasks.add(() -> rasters.get(x, z));
                        chunks.add(x + "," + z);
                    }
                }
            }
            List<Future<ChunkWaterRaster>> futures = inputs.workers().invokeAll(tasks);
            for (int index = 0; index < futures.size(); index++) {
                try {
                    futures.get(index).get();
                } catch (ExecutionException failure) {
                    failures.add("raster chunk " + chunks.get(index) + ": " + HydrologyPlanHarness.cause(failure));
                }
            }
        }
    }
}
