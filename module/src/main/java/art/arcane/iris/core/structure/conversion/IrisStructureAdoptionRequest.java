package art.arcane.iris.core.structure.conversion;

import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;

import java.util.Objects;
import java.util.Optional;

public record IrisStructureAdoptionRequest(
        String sourceStructure,
        Optional<StructureKey> requestedTarget,
        IrisStructureAdoptionStrategy strategy,
        IrisStructureAdoptionInputKind inputKind
) {
    public IrisStructureAdoptionRequest {
        sourceStructure = requireInternalKey(sourceStructure);
        requestedTarget = Objects.requireNonNull(requestedTarget, "requestedTarget");
        strategy = Objects.requireNonNull(strategy, "strategy");
        inputKind = Objects.requireNonNull(inputKind, "inputKind");
    }

    public static IrisStructureAdoptionRequest unowned(String sourceStructure) {
        return new IrisStructureAdoptionRequest(
                sourceStructure,
                Optional.empty(),
                IrisStructureAdoptionStrategy.AUTO,
                IrisStructureAdoptionInputKind.UNOWNED_IRIS
        );
    }

    public static IrisStructureAdoptionRequest cloneTo(String sourceStructure, StructureKey target) {
        return new IrisStructureAdoptionRequest(
                sourceStructure,
                Optional.of(Objects.requireNonNull(target, "target")),
                IrisStructureAdoptionStrategy.CLONE,
                IrisStructureAdoptionInputKind.UNOWNED_IRIS
        );
    }

    public StructureKey sourceOwnershipKey() {
        return new StructureKey("iris", sourceStructure);
    }

    private static String requireInternalKey(String value) {
        Objects.requireNonNull(value, "sourceStructure");
        String normalized = value.trim();
        if (!normalized.equals(value)) {
            throw new IllegalArgumentException("Source structure key cannot contain surrounding whitespace");
        }
        StructureResourceBundle.validateRelativePath("structures/" + normalized + ".json");
        new StructureKey("iris", normalized);
        return normalized;
    }
}
