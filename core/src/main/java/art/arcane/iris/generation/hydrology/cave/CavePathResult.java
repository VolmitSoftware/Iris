package art.arcane.iris.generation.hydrology.cave;

import java.util.List;

record CavePathResult(List<CavePosition> positions, HydrologyCaveRejection rejection) {
    static CavePathResult accepted(List<CavePosition> positions) {
        return new CavePathResult(List.copyOf(positions), HydrologyCaveRejection.NONE);
    }

    static CavePathResult rejected(HydrologyCaveRejection rejection) {
        return new CavePathResult(List.of(), rejection);
    }
}
