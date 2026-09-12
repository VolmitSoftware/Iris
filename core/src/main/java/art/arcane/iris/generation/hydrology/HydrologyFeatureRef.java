package art.arcane.iris.generation.hydrology;

import java.util.Objects;

public record HydrologyFeatureRef(
        long id,
        HydrologyFeatureType type,
        long courseId,
        long segmentId,
        int x,
        int y,
        int z,
        int flowDeltaX,
        int flowDeltaZ,
        boolean source
) {
    public HydrologyFeatureRef {
        Objects.requireNonNull(type, "type");
        if (flowDeltaX < -1 || flowDeltaX > 1 || flowDeltaZ < -1 || flowDeltaZ > 1) {
            throw new IllegalArgumentException("Flow direction components must be between -1 and 1.");
        }
    }

    public HydrologyPoint point() {
        return new HydrologyPoint(x, y, z);
    }
}
