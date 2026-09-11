package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyHash;

import java.util.HashMap;

public final class SurfaceFeatureRefs {
    private static final int ROLE_CHANNEL = 1;
    private static final int ROLE_SHORE = 2;
    private static final int ROLE_GRADING = 3;
    private static final int ROLE_SOURCE = 6;

    private final long courseId;
    private final HashMap<Key, HydrologyFeatureRef> features;

    public SurfaceFeatureRefs(long courseId) {
        this.courseId = courseId;
        this.features = new HashMap<>();
    }

    public HydrologyFeatureRef feature(
            HydraulicSegment segment,
            SurfaceRole role,
            boolean source,
            int flowX,
            int flowZ
    ) {
        int roleCode = source ? ROLE_SOURCE : switch (role) {
            case CHANNEL -> ROLE_CHANNEL;
            case SHORE -> ROLE_SHORE;
            case BANK -> ROLE_GRADING;
        };
        int featureFlowX = Integer.compare(flowX, 0);
        int featureFlowZ = Integer.compare(flowZ, 0);
        Key key = new Key(segment.id(), segment.type(), roleCode, featureFlowX, featureFlowZ);
        HydrologyFeatureRef existing = features.get(key);
        if (existing != null) {
            return existing;
        }
        HydrologyFeatureRef created = new HydrologyFeatureRef(
                HydrologyHash.mix(courseId, segment.id(), segment.type().ordinal(), roleCode, featureFlowX, featureFlowZ),
                segment.type(),
                courseId,
                segment.id(),
                segment.start().x(),
                segment.start().y(),
                segment.start().z(),
                featureFlowX,
                featureFlowZ,
                source
        );
        features.put(key, created);
        return created;
    }

    private record Key(long segmentId, HydrologyFeatureType type, int role, int flowX, int flowZ) {
    }
}
