package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.framework.PlacedStructurePiece;

import java.util.List;
import java.util.Objects;

public record StructureAssemblyResult(
        StructureAssemblyStatus status,
        List<PlacedStructurePiece> pieces,
        String selectedTheme,
        String detail
) {
    public StructureAssemblyResult {
        status = Objects.requireNonNull(status, "Structure assembly status");
        pieces = List.copyOf(Objects.requireNonNull(pieces, "Structure assembly pieces"));
        selectedTheme = normalize(selectedTheme);
        detail = normalize(detail);
        if (status == StructureAssemblyStatus.COMPLETE && pieces.isEmpty()) {
            throw new IllegalArgumentException("A complete structure assembly must contain at least one piece");
        }
        if (status == StructureAssemblyStatus.INTENTIONAL_EMPTY && !pieces.isEmpty()) {
            throw new IllegalArgumentException("An intentionally empty structure assembly cannot contain pieces");
        }
        if (status.isFailure() && detail.isEmpty()) {
            throw new IllegalArgumentException("A failed structure assembly requires failure detail");
        }
    }

    public static StructureAssemblyResult complete(
            List<PlacedStructurePiece> pieces,
            String selectedTheme
    ) {
        return new StructureAssemblyResult(
                StructureAssemblyStatus.COMPLETE,
                pieces,
                selectedTheme,
                "");
    }

    public static StructureAssemblyResult intentionalEmpty(
            String selectedTheme,
            String detail
    ) {
        return new StructureAssemblyResult(
                StructureAssemblyStatus.INTENTIONAL_EMPTY,
                List.of(),
                selectedTheme,
                detail);
    }

    public static StructureAssemblyResult failed(
            StructureAssemblyStatus status,
            List<PlacedStructurePiece> pieces,
            String selectedTheme,
            String detail
    ) {
        if (Objects.requireNonNull(status, "Structure assembly failure status").isComplete()) {
            throw new IllegalArgumentException("A failed structure assembly requires a failure status");
        }
        return new StructureAssemblyResult(status, pieces, selectedTheme, detail);
    }

    public boolean hasOutput() {
        return status == StructureAssemblyStatus.COMPLETE && !pieces.isEmpty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
