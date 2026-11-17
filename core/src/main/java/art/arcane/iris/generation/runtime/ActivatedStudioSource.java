package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.PackTreeState;

import java.util.Optional;

/**
 * The Studio source state an activated epoch was snapshotted from. While that epoch is still active, source files
 * whose size and modification time match it are known to equal the epoch's published files.
 */
record ActivatedStudioSource(String epochId, PackTreeState sourceState) {
    Optional<PackTreeState> sourceStateOf(GenerationEpoch active) {
        return active.epochId().equals(epochId) ? Optional.of(sourceState) : Optional.empty();
    }
}
