package art.arcane.iris.engine.hydrology;

record SurfaceCourseDraft(
        HydrologyGridNode source,
        long courseId,
        String profileKey,
        HydrologyCoursePath path
) {
}
