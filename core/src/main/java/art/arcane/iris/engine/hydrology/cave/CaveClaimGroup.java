package art.arcane.iris.engine.hydrology.cave;

import java.util.Map;

record CaveClaimGroup(
        HydrologyCaveCandidate candidate,
        Map<CavePosition, HydrologyCaveAction> actions
) {
}
