package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record WindowComputation(WindowPlan owned, List<Reach> allReaches, List<Pond> allPonds) {
    public WindowComputation {
        Objects.requireNonNull(owned, "owned");
        allReaches = List.copyOf(allReaches);
        allPonds = List.copyOf(allPonds);
    }
}
