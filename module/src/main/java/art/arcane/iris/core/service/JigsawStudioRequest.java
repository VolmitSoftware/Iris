package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCompatibilityTarget;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import com.volmit.tooling.ToolPack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * What one open Jigsaw Studio was asked to edit, and who owns it.
 *
 * @param source   the pack whose structure is edited
 * @param packRoot the pack's folder, which the graph editors write below, as {@link ToolPack#edit} hands it out
 */
public record JigsawStudioRequest(
        UUID requestId,
        String packKey,
        String structureKey,
        JigsawStudioMode mode,
        JigsawStudioCompatibilityTarget compatibilityTarget,
        JigsawStudioCellDimensions cellDimensions,
        ToolPack source,
        Path packRoot,
        UUID ownerId
) {
    /** The folder of {@code pack}. */
    public static Path rootOf(ToolPack pack) {
        try {
            return pack.edit(root -> root);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot reach the folder of the pack", failure);
        }
    }

    /** Makes {@code pack} read its files again after a tool changed them. */
    public static void reload(ToolPack pack) {
        try {
            pack.edit(root -> null);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot reload the pack", failure);
        }
    }

    /** Makes the pack read the files again after the studio changed them. */
    public void reloadSource() {
        reload(source);
    }

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
        source = Objects.requireNonNull(source, "Jigsaw Studio source pack");
        packRoot = Objects.requireNonNull(packRoot, "Jigsaw Studio pack folder");
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
