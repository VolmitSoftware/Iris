package art.arcane.iris.core.runtime.jigsaw;

import com.volmit.geometry.BlockPosition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record JigsawStudioVariant(
        String pieceKey,
        String objectKey,
        String displayName,
        Optional<JigsawStudioCellDimensions> dimensions,
        JigsawStudioMode mode,
        Optional<JigsawPlanarTopology> sourceTopology,
        boolean rotatable,
        boolean owned,
        List<String> themes,
        JigsawStudioPieceRules rules,
        List<JigsawStudioPoolMembership> memberships
) {
    public JigsawStudioVariant {
        pieceKey = requireKey(pieceKey, "piece");
        objectKey = requireKey(objectKey, "object");
        displayName = displayName == null ? "" : displayName.trim();
        dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio variant dimensions");
        mode = Objects.requireNonNull(mode, "Jigsaw Studio variant mode");
        sourceTopology = Objects.requireNonNull(sourceTopology, "Jigsaw Studio variant topology");
        themes = List.copyOf(Objects.requireNonNull(themes, "Jigsaw Studio variant themes"));
        rules = Objects.requireNonNull(rules, "Jigsaw Studio variant rules");
        memberships = List.copyOf(Objects.requireNonNull(memberships, "Jigsaw Studio pool memberships"));
        if (mode == JigsawStudioMode.PLANAR_JIGSAW && sourceTopology.isEmpty()) {
            throw new IllegalArgumentException("Planar Jigsaw Studio variants require a topology");
        }
        if (mode == JigsawStudioMode.SPATIAL_JIGSAW && sourceTopology.isPresent()) {
            throw new IllegalArgumentException("Spatial Jigsaw Studio variants cannot declare a planar topology");
        }
    }

    public String resolvedDisplayName() {
        if (!displayName.isEmpty()) {
            return displayName;
        }
        int separator = Math.max(pieceKey.lastIndexOf('/'), pieceKey.lastIndexOf(':'));
        return separator < 0 ? pieceKey : pieceKey.substring(separator + 1);
    }

    public Optional<JigsawPlanarArchetype> archetype() {
        return sourceTopology.map(JigsawPlanarArchetype::fromTopology);
    }

    public int sourceToCanonicalQuarterTurns() {
        JigsawPlanarTopology topology = sourceTopology.orElse(null);
        return topology == null ? 0 : JigsawPlanarArchetype.fromTopology(topology)
                .sourceToCanonicalQuarterTurns(topology);
    }

    public int canonicalToSourceQuarterTurns() {
        JigsawPlanarTopology topology = sourceTopology.orElse(null);
        return topology == null ? 0 : JigsawPlanarArchetype.fromTopology(topology)
                .canonicalToSourceQuarterTurns(topology);
    }

    public BlockPosition sourceToCanonicalPosition(
            BlockPosition sourcePosition,
            JigsawStudioCellDimensions sourceDimensions
    ) {
        return rotatePosition(
                sourcePosition,
                sourceDimensions,
                sourceToCanonicalQuarterTurns());
    }

    public BlockPosition canonicalToSourcePosition(
            BlockPosition canonicalPosition,
            JigsawStudioCellDimensions sourceDimensions
    ) {
        JigsawStudioCellDimensions canonicalDimensions = canonicalDimensions(sourceDimensions);
        return rotatePosition(
                canonicalPosition,
                canonicalDimensions,
                canonicalToSourceQuarterTurns());
    }

    public JigsawStudioCellDimensions canonicalDimensions(JigsawStudioCellDimensions sourceDimensions) {
        JigsawStudioCellDimensions dimensions = Objects.requireNonNull(
                sourceDimensions,
                "Jigsaw Studio source dimensions");
        return Math.floorMod(sourceToCanonicalQuarterTurns(), 2) == 0
                ? dimensions
                : new JigsawStudioCellDimensions(
                        dimensions.depth(),
                        dimensions.height(),
                        dimensions.width());
    }

    public boolean assigned() {
        return !memberships.isEmpty();
    }

    private static BlockPosition rotatePosition(
            BlockPosition position,
            JigsawStudioCellDimensions dimensions,
            int quarterTurns
    ) {
        BlockPosition source = Objects.requireNonNull(position, "Jigsaw Studio variant position");
        JigsawStudioCellDimensions bounds = Objects.requireNonNull(
                dimensions,
                "Jigsaw Studio variant position bounds");
        if (source.x() < 0 || source.x() >= bounds.width()
                || source.y() < 0 || source.y() >= bounds.height()
                || source.z() < 0 || source.z() >= bounds.depth()) {
            throw new IllegalArgumentException("Jigsaw Studio variant position is outside its object bounds");
        }
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> new BlockPosition(source.x(), source.y(), source.z());
            case 1 -> new BlockPosition(
                    bounds.depth() - 1 - source.z(),
                    source.y(),
                    source.x());
            case 2 -> new BlockPosition(
                    bounds.width() - 1 - source.x(),
                    source.y(),
                    bounds.depth() - 1 - source.z());
            case 3 -> new BlockPosition(
                    source.z(),
                    source.y(),
                    bounds.width() - 1 - source.x());
            default -> throw new IllegalStateException("Unreachable Jigsaw Studio rotation");
        };
    }

    private static String requireKey(String value, String name) {
        Objects.requireNonNull(value, "Jigsaw Studio " + name + " key");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio " + name + " key cannot be blank");
        }
        return normalized;
    }
}
