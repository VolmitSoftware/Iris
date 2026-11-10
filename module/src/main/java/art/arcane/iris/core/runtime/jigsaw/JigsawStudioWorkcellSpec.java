package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;

public record JigsawStudioWorkcellSpec(
        JigsawPlanarArchetype archetype,
        String displayName,
        JigsawStudioCellDimensions dimensions,
        boolean enabled
) {
    public JigsawStudioWorkcellSpec {
        archetype = Objects.requireNonNull(archetype, "Jigsaw Studio workcell archetype");
        displayName = displayName == null ? "" : displayName.trim();
        dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio workcell dimensions");
    }

    public String resolvedDisplayName() {
        return displayName.isEmpty() ? archetype.displayName() : displayName;
    }
}
