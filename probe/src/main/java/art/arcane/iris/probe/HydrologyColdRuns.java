package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.engine.HydrologyPlanners;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.plan.WindowPlanner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

final class HydrologyColdRuns {
    private HydrologyColdRuns() {
    }

    record ColdRun(long nanos, long naturalSamples, long baseSamples, int depth) {
        static final ColdRun FAILED = new ColdRun(-1L, 0L, 0L, 0);

        double millis() {
            return nanos / 1_000_000D;
        }
    }

    static ColdRun cold(HydrologyProbe.Inputs inputs, WindowKey key) {
        HydrologySettings settings = inputs.settings();
        HydrologyPlanHarness.CountingTerrain terrain = new HydrologyPlanHarness.CountingTerrain(inputs.terrain());
        HydrologyPlanners fresh = HydrologyPlanners.create(settings, terrain);
        int chunkX = key.wx() * HydrologyWindows.CHUNKS + HydrologyWindows.CHUNKS / 2;
        int chunkZ = key.wz() * HydrologyWindows.CHUNKS + HydrologyWindows.CHUNKS / 2;
        long[] demandedSamples = new long[2];
        PlanGauge gauge = new PlanGauge(new HashSet<>(HydrologyWindows.neighbourhood(key)), () -> {
            demandedSamples[0] = terrain.natural();
            demandedSamples[1] = terrain.base();
        });
        HydrologyPlanCache cache = new HydrologyPlanCache(settings, gauge.region(fresh.regions()::plan), gauge.window(fresh.windows()::plan),
                inputs.planningPool(), () -> false);
        HydrologyRuntime runtime = new HydrologyRuntime(settings, cache, inputs.naturalHeight(), () -> false, System::nanoTime);
        long elapsed;
        try {
            long started = System.nanoTime();
            runtime.raster(chunkX, chunkZ);
            elapsed = System.nanoTime() - started;
        } finally {
            runtime.close();
            gauge.awaitIdle(inputs.planningPool());
        }
        return new ColdRun(elapsed, demandedSamples[0], demandedSamples[1], gauge.deepest());
    }

    static final class PlanGauge {
        private final Map<Object, Integer> planning = new ConcurrentHashMap<>();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger deepest = new AtomicInteger();
        private final Set<WindowKey> demanded;
        private final AtomicInteger demandedLeft;
        private final Runnable demandedPlanned;

        PlanGauge(Set<WindowKey> demanded, Runnable demandedPlanned) {
            this.demanded = Set.copyOf(demanded);
            this.demandedLeft = new AtomicInteger(demanded.size());
            this.demandedPlanned = demandedPlanned;
        }

        Function<RegionKey, RegionPlan> region(Function<RegionKey, RegionPlan> planner) {
            return key -> run(key, () -> planner.apply(key));
        }

        BiFunction<WindowKey, List<RegionPlan>, WindowPlan> window(BiFunction<WindowKey, List<RegionPlan>, WindowPlan> planner) {
            return (key, plans) -> run(key, () -> planner.apply(key, plans));
        }

        private <T> T run(Object key, Supplier<T> plan) {
            if (awaitedByRunningPlan(key)) {
                deepest.accumulateAndGet(1, Math::max);
            }
            planning.merge(key, 1, Integer::sum);
            active.incrementAndGet();
            try {
                T result = plan.get();
                if (key instanceof WindowKey window && demanded.contains(window) && demandedLeft.decrementAndGet() == 0) {
                    demandedPlanned.run();
                }
                return result;
            } finally {
                planning.computeIfPresent(key, (ignored, count) -> count == 1 ? null : count - 1);
                active.decrementAndGet();
            }
        }

        private boolean awaitedByRunningPlan(Object key) {
            if (!(key instanceof RegionKey region)) {
                return false;
            }
            for (Object running : planning.keySet()) {
                if (running instanceof WindowKey window && WindowPlanner.lakeRegions(window).contains(region)) {
                    return true;
                }
            }
            return false;
        }

        int deepest() {
            return deepest.get();
        }

        void awaitIdle(ForkJoinPool pool) {
            while (active.get() > 0 || !pool.awaitQuiescence(50L, TimeUnit.MILLISECONDS)) {
                Thread.onSpinWait();
            }
        }
    }
}
