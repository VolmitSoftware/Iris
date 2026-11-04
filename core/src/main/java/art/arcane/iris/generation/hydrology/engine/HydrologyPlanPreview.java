package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.underground.Grotto;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.List;
import java.util.Objects;

public record HydrologyPlanPreview(List<Reach> reaches, List<Pond> ponds, List<Pond> lakes, List<Grotto> grottos, boolean complete) {
    public static final HydrologyPlanPreview EMPTY = new HydrologyPlanPreview(List.of(), List.of(), List.of(), true);
    public static final HydrologyPlanPreview PENDING = new HydrologyPlanPreview(List.of(), List.of(), List.of(), false);

    public HydrologyPlanPreview {
        reaches = List.copyOf(Objects.requireNonNull(reaches, "reaches"));
        ponds = List.copyOf(Objects.requireNonNull(ponds, "ponds"));
        lakes = List.copyOf(Objects.requireNonNull(lakes, "lakes"));
        grottos = List.copyOf(Objects.requireNonNull(grottos, "grottos"));
    }

    public HydrologyPlanPreview(List<Reach> reaches, List<Pond> ponds, List<Pond> lakes, boolean complete) {
        this(reaches, ponds, lakes, List.of(), complete);
    }
}
