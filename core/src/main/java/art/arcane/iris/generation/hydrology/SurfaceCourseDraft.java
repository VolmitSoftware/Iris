package art.arcane.iris.generation.hydrology;

record SurfaceCourseDraft(
        HydrologyGridNode source,
        long courseId,
        String profileKey,
        HydrologyCoursePath path
) {
}
