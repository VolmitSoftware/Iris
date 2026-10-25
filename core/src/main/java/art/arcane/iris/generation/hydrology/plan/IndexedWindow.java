package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.raster.ReachIndex;

import java.util.Objects;

public record IndexedWindow(WindowPlan plan, ReachIndex index) {
    public IndexedWindow {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(index, "index");
    }
}
