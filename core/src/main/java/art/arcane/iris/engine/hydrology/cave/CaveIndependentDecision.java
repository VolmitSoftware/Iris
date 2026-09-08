package art.arcane.iris.engine.hydrology.cave;

import java.util.List;

record CaveIndependentDecision(
        HydrologyCaveRejection rejection,
        List<CavePosition> guards
) {
    private static CaveIndependentDecision accepted(List<CavePosition> guards) {
        return new CaveIndependentDecision(HydrologyCaveRejection.NONE, List.copyOf(guards));
    }

    private static CaveIndependentDecision rejected(HydrologyCaveRejection rejection) {
        return new CaveIndependentDecision(rejection, List.of());
    }
}
