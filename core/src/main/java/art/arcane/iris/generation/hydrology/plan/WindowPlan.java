package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record WindowPlan(WindowKey key, List<Reach> reaches, List<Pond> ponds, List<SeaInlet> inlets) {
    public WindowPlan {
        Objects.requireNonNull(key, "key");
        reaches = List.copyOf(reaches);
        ponds = List.copyOf(ponds);
        inlets = List.copyOf(inlets);
    }
}
