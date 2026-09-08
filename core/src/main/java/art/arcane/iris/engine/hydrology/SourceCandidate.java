package art.arcane.iris.engine.hydrology;

record SourceCandidate(
        int nodeIndex,
        long stableId,
        double score,
        boolean required
) {
}
