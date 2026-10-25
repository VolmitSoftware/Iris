package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.LakeCandidate;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record RegionComputation(RegionPlan owned, List<Reach> allReaches, List<LakeCandidate> allLakes) {
    public RegionComputation {
        Objects.requireNonNull(owned, "owned");
        allReaches = List.copyOf(allReaches);
        allLakes = List.copyOf(allLakes);
    }
}
