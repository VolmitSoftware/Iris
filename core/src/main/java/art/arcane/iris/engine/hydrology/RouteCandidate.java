package art.arcane.iris.engine.hydrology;

record RouteCandidate(
        HydrologyPoint point,
        double continuousX,
        double continuousZ,
        double offset,
        double localScore,
        double terrainScore,
        RouteDirection tangent,
        boolean terrainResolved
) {
}
