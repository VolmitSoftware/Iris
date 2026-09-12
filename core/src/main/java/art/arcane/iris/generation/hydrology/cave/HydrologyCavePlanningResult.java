package art.arcane.iris.generation.hydrology.cave;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record HydrologyCavePlanningResult(
        List<HydrologyCavePlan> plans,
        Map<CavePosition, HydrologyCaveAction> actions,
        Map<CavePosition, CaveVoxelPrecondition> baselinePreconditions
) {
    public HydrologyCavePlanningResult {
        plans = List.copyOf(Objects.requireNonNull(plans));
        actions = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(actions)));
        baselinePreconditions = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(baselinePreconditions))
        );
    }
}
