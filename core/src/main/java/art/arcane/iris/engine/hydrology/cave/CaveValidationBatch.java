package art.arcane.iris.engine.hydrology.cave;

import java.util.List;
import java.util.Map;

record CaveValidationBatch(
        List<HydrologyCavePlan> plans,
        Map<CavePosition, HydrologyCaveAction> actions,
        Map<CavePosition, CaveVoxelPrecondition> baselinePreconditions
) {
}
