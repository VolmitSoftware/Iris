package art.arcane.iris.generation.hydrology.cave;

import java.util.Map;

record CaveClaimGroup(
        HydrologyCaveCandidate candidate,
        Map<CavePosition, HydrologyCaveAction> actions
) {
}
