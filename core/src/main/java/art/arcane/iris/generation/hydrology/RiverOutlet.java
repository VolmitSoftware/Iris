package art.arcane.iris.generation.hydrology;

import java.util.Objects;

public record RiverOutlet(
        long id,
        HydrologyFeatureType type,
        long drainageNodeId,
        HydrologyPoint landwardPoint,
        HydrologyPoint connectionPoint,
        int seaLevel,
        boolean directOcean
) {
    public RiverOutlet {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(landwardPoint, "landwardPoint");
        Objects.requireNonNull(connectionPoint, "connectionPoint");
        if (type != HydrologyFeatureType.MOUTH
                && type != HydrologyFeatureType.COASTAL_GROTTO
                && type != HydrologyFeatureType.INLAND_GROTTO) {
            throw new IllegalArgumentException("Unsupported outlet feature type: " + type);
        }
        if (directOcean && type == HydrologyFeatureType.INLAND_GROTTO) {
            throw new IllegalArgumentException("An inland grotto cannot be a direct ocean outlet.");
        }
        if (directOcean && connectionPoint.y() != seaLevel) {
            throw new IllegalArgumentException("A direct ocean connection must be at sea level.");
        }
    }
}
