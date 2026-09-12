package art.arcane.iris.generation.mantle;

import java.util.List;

final class ObjectSourcePlan {
    private final List<ObjectDestinationTransaction.Mutation> mutations;
    private final int mutationWeight;

    ObjectSourcePlan(List<ObjectDestinationTransaction.Mutation> mutations) {
        this.mutations = List.copyOf(mutations);
        long weight = 1L;
        for (ObjectDestinationTransaction.Mutation mutation : this.mutations) {
            weight += mutation.weight();
        }
        this.mutationWeight = weight >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) weight;
    }

    List<ObjectDestinationTransaction.Mutation> mutations() {
        return mutations;
    }

    int mutationWeight() {
        return mutationWeight;
    }
}
