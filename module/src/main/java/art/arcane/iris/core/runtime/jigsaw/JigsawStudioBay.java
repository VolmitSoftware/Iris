package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;
import java.util.Optional;

public record JigsawStudioBay(
        String stableId,
        JigsawStudioBayKind kind,
        Optional<JigsawStudioWorkcellSpec> workcellSpec,
        String authorDisplayName,
        JigsawStudioBounds bounds
) {
    public JigsawStudioBay {
        stableId = requireStableId(stableId);
        kind = Objects.requireNonNull(kind, "Jigsaw Studio bay kind");
        workcellSpec = Objects.requireNonNull(workcellSpec, "Jigsaw Studio workcell specification");
        authorDisplayName = authorDisplayName == null ? "" : authorDisplayName.trim();
        bounds = Objects.requireNonNull(bounds, "Jigsaw Studio bay bounds");
        if (kind == JigsawStudioBayKind.PLANAR_WORKCELL && workcellSpec.isEmpty()) {
            throw new IllegalArgumentException("Planar Jigsaw Studio workcells require an archetype");
        }
        if (kind == JigsawStudioBayKind.SPATIAL_WORKCELL && workcellSpec.isPresent()) {
            throw new IllegalArgumentException("Spatial Jigsaw Studio workcells cannot declare an archetype");
        }
        if (workcellSpec.isPresent() && !workcellSpec.get().dimensions().equals(bounds.dimensions())) {
            throw new IllegalArgumentException("Jigsaw Studio workcell specification dimensions do not match bounds");
        }
    }

    public Optional<JigsawPlanarArchetype> archetype() {
        return workcellSpec.map(JigsawStudioWorkcellSpec::archetype);
    }

    public boolean enabled() {
        return workcellSpec.map(JigsawStudioWorkcellSpec::enabled).orElse(true);
    }

    public String canonicalDisplayName() {
        return archetype().map(JigsawPlanarArchetype::displayName).orElse("Spatial");
    }

    public String displayName() {
        return authorDisplayName.isEmpty() ? canonicalDisplayName() : authorDisplayName;
    }

    public JigsawStudioCellDimensions capacity() {
        return bounds.dimensions();
    }

    public Optional<JigsawPlanarTopology> topology() {
        return archetype().map(JigsawPlanarArchetype::canonicalTopology);
    }

    private static String requireStableId(String value) {
        Objects.requireNonNull(value, "Jigsaw Studio bay stable ID");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio bay stable ID cannot be blank");
        }
        return normalized;
    }
}
