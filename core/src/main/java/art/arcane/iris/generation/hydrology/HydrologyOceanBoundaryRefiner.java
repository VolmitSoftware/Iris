package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Objects;

final class HydrologyOceanBoundaryRefiner {
    private HydrologyOceanBoundaryRefiner() {
    }

    static Result refine(
            List<HydrologyPoint> crossing,
            HydrologyTerrainSampler terrainSampler,
            HydrologyRoutingTerrainSampler routingSampler,
            int seaLevel
    ) {
        Objects.requireNonNull(crossing, "crossing");
        Objects.requireNonNull(terrainSampler, "terrainSampler");
        Objects.requireNonNull(routingSampler, "routingSampler");
        if (crossing.size() < 2) {
            return null;
        }
        for (int index = 1; index < crossing.size(); index++) {
            HydrologyPoint point = crossing.get(index);
            HydrologyRoutingTerrainSampler.NaturalClassification classification =
                    routingSampler.classifyNatural(point.x(), point.z());
            if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.UNAVAILABLE) {
                return null;
            }
            if (classification == HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN) {
                for (int landward = index - 1; landward >= 0; landward--) {
                    HydrologyPoint previous = crossing.get(landward);
                    HydrologyTerrainSample landwardTerrain = terrainSampler.sample(previous.x(), previous.z());
                    if (landwardTerrain == null || landwardTerrain.ocean()) {
                        return null;
                    }
                    if (landwardTerrain.naturalHeight() >= seaLevel) {
                        return new Result(previous, point, landwardTerrain);
                    }
                }
                return null;
            }
        }
        return null;
    }

    record Result(
            HydrologyPoint landwardPoint,
            HydrologyPoint oceanPoint,
            HydrologyTerrainSample landwardTerrain
    ) {
    }
}
