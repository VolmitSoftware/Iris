package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class EngineMetricsStageTotalsTest {
    @Test
    public void stageTotalsAccumulatePastTheRollingWindow() {
        EngineMetrics metrics = new EngineMetrics(2);
        metrics.getTerrain().put(1.5D);
        metrics.getTerrain().put(2.5D);
        metrics.getTerrain().put(4D);

        EngineMetrics.StageTotal terrain = metrics.stageTotals().get("terrain");

        assertEquals(8D, terrain.millis(), 0D);
        assertEquals(3L, terrain.samples());
        assertEquals(3.25D, metrics.getTerrain().getAverage(), 0D);
    }

    @Test
    public void stageTotalsSinceAnEarlierSnapshotCoverOnlyTheLaterPuts() {
        EngineMetrics metrics = new EngineMetrics(4);
        metrics.getCarveResolve().put(3D);
        EngineMetrics.StageTotal before = metrics.stageTotals().get("carve.resolve");
        metrics.getCarveResolve().put(5D);
        metrics.getCarveResolve().put(7D);

        EngineMetrics.StageTotal window = metrics.stageTotals().get("carve.resolve").since(before);

        assertEquals(12D, window.millis(), 0D);
        assertEquals(2L, window.samples());
    }

    @Test
    public void stageTotalsNameEveryPulledTimer() {
        EngineMetrics metrics = new EngineMetrics(1);
        Map<String, EngineMetrics.StageTotal> totals = metrics.stageTotals();

        assertEquals(Set.copyOf(metrics.pull().keySet()), Set.copyOf(totals.keySet()));
        assertEquals(metrics.pull().size(), totals.size());
    }
}
