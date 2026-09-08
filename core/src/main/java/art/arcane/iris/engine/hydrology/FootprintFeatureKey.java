package art.arcane.iris.engine.hydrology;

record FootprintFeatureKey(
        long courseId,
        long segmentId,
        HydrologyFeatureType type,
        int role,
        int flowX,
        int flowZ
) {
}
