package art.arcane.iris.core.service;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;

import java.util.Objects;
import java.util.UUID;

/** What one open Jigsaw Studio was asked to edit, and who owns it. */
public record JigsawStudioRequest(
        UUID requestId,
        String packKey,
        String structureKey,
        JigsawStudioMode mode,
        JigsawStudioCompatibilityTarget compatibilityTarget,
        JigsawStudioCellDimensions cellDimensions,
        IrisData source,
        UUID ownerId
) {
    public JigsawStudioRequest {
        requestId = Objects.requireNonNull(requestId, "Jigsaw Studio request ID");
        packKey = requireKey(packKey, "pack");
        structureKey = requireKey(structureKey, "structure");
        mode = Objects.requireNonNull(mode, "Jigsaw Studio mode");
        compatibilityTarget = Objects.requireNonNull(
                compatibilityTarget,
                "Jigsaw Studio compatibility target"
        );
        cellDimensions = Objects.requireNonNull(cellDimensions, "Jigsaw Studio cell dimensions");
        source = Objects.requireNonNull(source, "Jigsaw Studio source data");
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
