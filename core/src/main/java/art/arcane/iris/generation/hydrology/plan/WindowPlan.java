package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record WindowPlan(WindowKey key, List<Reach> reaches, List<Pond> ponds, List<Grotto> grottos) {
    public WindowPlan {
        Objects.requireNonNull(key, "key");
        reaches = List.copyOf(reaches);
        ponds = List.copyOf(ponds);
        grottos = List.copyOf(grottos);
    }

    public WindowPlan(WindowKey key, List<Reach> reaches, List<Pond> ponds) {
        this(key, reaches, ponds, List.of());
    }
}
