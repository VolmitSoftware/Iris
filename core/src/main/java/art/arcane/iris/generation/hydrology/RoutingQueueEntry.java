package art.arcane.iris.generation.hydrology;

record RoutingQueueEntry(
        int nodeIndex,
        double potential,
        int outletIndex
)
        implements Comparable<RoutingQueueEntry> {
    @Override
    public int compareTo(RoutingQueueEntry other) {
        int potentialComparison = Double.compare(potential, other.potential);
        if (potentialComparison != 0) {
            return potentialComparison;
        }
        int nodeComparison = Integer.compare(nodeIndex, other.nodeIndex);
        return nodeComparison != 0 ? nodeComparison : Integer.compare(outletIndex, other.outletIndex);
    }
}
