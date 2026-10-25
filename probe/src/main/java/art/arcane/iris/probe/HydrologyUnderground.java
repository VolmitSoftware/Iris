package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.runtime.Engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class HydrologyUnderground {
    private HydrologyUnderground() {
    }

    @FunctionalInterface
    interface Source {
        Counts measure(WindowKey key, WindowPlan plan) throws Exception;
    }

    record Counts(int inlets, long inletFailures, int cavePools, long cavePoolFailures, List<String> examples) {
        static final Counts NONE = new Counts(0, 0L, 0, 0L, List.of());
    }

    static Source generated(Engine engine, ExecutorService workers) {
        return (key, plan) -> {
            SeaInletProbe.Checks inlets = SeaInletProbe.check(engine, plan.inlets(), workers);
            int minChunkX = key.wx() * HydrologyWindows.CHUNKS;
            int minChunkZ = key.wz() * HydrologyWindows.CHUNKS;
            CavePoolProbe.Counts pools = CavePoolProbe.check(engine, new CavePoolProbe.Area(minChunkX, minChunkX + HydrologyWindows.CHUNKS - 1,
                    minChunkZ, minChunkZ + HydrologyWindows.CHUNKS - 1), workers);
            List<String> examples = new ArrayList<>();
            for (String example : inlets.examples()) {
                examples.add("inlet " + example);
            }
            for (String example : pools.examples()) {
                examples.add("pool " + example);
            }
            return new Counts(inlets.inlets(), inlets.failures(), pools.pools(), pools.failures(), examples);
        };
    }
}
