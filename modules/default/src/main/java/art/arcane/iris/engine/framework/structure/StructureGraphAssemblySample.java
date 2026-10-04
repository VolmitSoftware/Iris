package art.arcane.iris.engine.framework.structure;

import java.util.List;
import java.util.Objects;

public record StructureGraphAssemblySample(long seed, Outcome outcome) {
    public record Outcome(
            List<String> pieceKeys,
            StructureAssemblyStatus status,
            String detail
    ) {
        public Outcome {
            pieceKeys = List.copyOf(Objects.requireNonNull(pieceKeys));
            status = Objects.requireNonNull(status);
            detail = Objects.requireNonNull(detail);
        }

        public boolean pieceCapReached() {
            return status == StructureAssemblyStatus.HARD_CAP;
        }

        public boolean intentionalEmpty() {
            return status == StructureAssemblyStatus.INTENTIONAL_EMPTY;
        }

        public boolean isComplete() {
            return status.isComplete();
        }
    }
}
