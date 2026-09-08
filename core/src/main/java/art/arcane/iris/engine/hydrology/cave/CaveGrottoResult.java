package art.arcane.iris.engine.hydrology.cave;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

record CaveGrottoResult(Set<CavePosition> positions, HydrologyCaveRejection rejection) {
    static CaveGrottoResult accepted(Set<CavePosition> positions) {
        return new CaveGrottoResult(
                Collections.unmodifiableSet(new LinkedHashSet<>(positions)),
                HydrologyCaveRejection.NONE
        );
    }

    static CaveGrottoResult rejected(HydrologyCaveRejection rejection) {
        return new CaveGrottoResult(Set.of(), rejection);
    }
}
