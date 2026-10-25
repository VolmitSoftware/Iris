package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record RegionPlan(RegionKey key, List<Reach> majorReaches, List<Pond> lakes) {
    public RegionPlan {
        Objects.requireNonNull(key, "key");
        majorReaches = List.copyOf(majorReaches);
        lakes = List.copyOf(lakes);
    }
}
