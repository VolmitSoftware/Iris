package art.arcane.iris.generation.hydrology;

record SourceCandidate(
        int nodeIndex,
        long stableId,
        double score,
        boolean required
) {
}
