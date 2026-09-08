package art.arcane.iris.engine.hydrology.cave;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

record CaveBoundaryResult(Set<CavePosition> sealGuards, HydrologyCaveRejection rejection) {
    static CaveBoundaryResult accepted(Set<CavePosition> sealGuards) {
        return new CaveBoundaryResult(
                Collections.unmodifiableSet(new LinkedHashSet<>(sealGuards)),
                HydrologyCaveRejection.NONE
        );
    }

    static CaveBoundaryResult rejected(HydrologyCaveRejection rejection) {
        return new CaveBoundaryResult(Set.of(), rejection);
    }
}
