package art.arcane.iris.generation.hydrology.cave;

record CaveCachedValidation(
        HydrologyCavePlan plan,
        CaveViewObservations viewObservations,
        CaveSurfaceObservations surfaceObservations
) {
    long retainedPositions() {
        return Math.max(1L, (long) plan.actions().size()
                + plan.baselinePreconditions().size()
                + viewObservations.size()
                + surfaceObservations.observations().size());
    }
}
