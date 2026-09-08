package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Objects;

record CrossTileBlockers(
        List<HydrologyCrossTileCaveAdmission.RankedClaim> caveClaims,
        List<HydrologyCrossTileSurfaceAdmission.RankedClaim> surfaceClaims
) {
    CrossTileBlockers {
        caveClaims = List.copyOf(Objects.requireNonNull(caveClaims, "caveClaims"));
        surfaceClaims = List.copyOf(Objects.requireNonNull(surfaceClaims, "surfaceClaims"));
    }
}
