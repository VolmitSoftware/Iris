package art.arcane.iris.engine.hydrology;

record UndergroundSegmentCapKey(
        long courseId,
        String profileKey,
        int startX,
        int startZ,
        int endX,
        int endZ,
        int radius,
        int minimumHeadroom,
        int maximumHeadroom
) {
}
