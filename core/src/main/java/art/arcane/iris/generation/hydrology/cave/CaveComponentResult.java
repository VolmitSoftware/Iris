package art.arcane.iris.generation.hydrology.cave;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

record CaveComponentResult(Set<CavePosition> positions, HydrologyCaveRejection rejection) {
    static CaveComponentResult accepted(Set<CavePosition> positions) {
        return new CaveComponentResult(
                Collections.unmodifiableSet(new LinkedHashSet<>(positions)),
                HydrologyCaveRejection.NONE
        );
    }

    static CaveComponentResult rejected(HydrologyCaveRejection rejection) {
        return new CaveComponentResult(Set.of(), rejection);
    }
}
