package art.arcane.iris.generation.hydrology;

record RefinedEdgeKey(
        long upstreamId,
        long downstreamId,
        int upstreamX,
        int upstreamZ,
        int downstreamX,
        int downstreamZ,
        int continuationX,
        int continuationZ,
        int transverseCandidates,
        boolean surface
) {
}
