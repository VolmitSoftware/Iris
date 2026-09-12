package art.arcane.iris.generation.hydrology;

record FootprintFeatureKey(
        long courseId,
        long segmentId,
        HydrologyFeatureType type,
        int role,
        int flowX,
        int flowZ
) {
}
