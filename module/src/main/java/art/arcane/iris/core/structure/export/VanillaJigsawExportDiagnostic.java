package art.arcane.iris.core.structure.export;

import java.util.Objects;

public record VanillaJigsawExportDiagnostic(Severity severity, Code code, String resource, String message) {
    public VanillaJigsawExportDiagnostic {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(code);
        resource = resource == null ? "" : resource;
        Objects.requireNonNull(message);
    }

    public boolean isBlocking() {
        return severity == Severity.ERROR;
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum Code {
        SOURCE_STRUCTURE_MISSING,
        GRAPH_VALIDATION,
        INVALID_NAMESPACE,
        INVALID_RESOURCE_PATH,
        INVALID_BIOME,
        INVALID_SETTINGS,
        UNSUPPORTED_COMPATIBILITY,
        UNSUPPORTED_PLACE_MODE,
        UNSUPPORTED_EDIT,
        UNSUPPORTED_LOOT,
        UNSUPPORTED_THEME_METADATA,
        UNSUPPORTED_CHANCE,
        UNSUPPORTED_PIECE_RULES,
        UNSUPPORTED_REQUIRED_CAPS,
        UNSUPPORTED_CHANNEL,
        UNSUPPORTED_FIXED_ROTATION,
        UNSUPPORTED_NON_COLLIDABLE_PIECE,
        UNSUPPORTED_TILE_DATA,
        UNSUPPORTED_BLOCK_ENTITY,
        UNSUPPORTED_CUSTOM_BLOCK,
        UNSUPPORTED_MARKER_BLOCK,
        INVALID_MAX_DEPTH,
        INVALID_MAX_DISTANCE,
        INVALID_POOL_WEIGHT,
        INVALID_CONNECTOR,
        INVALID_CONNECTOR_ID,
        INVALID_CONNECTOR_ORIENTATION,
        INVALID_CONNECTOR_FINAL_STATE,
        DUPLICATE_CONNECTOR_POSITION,
        INVALID_BLOCK_STATE,
        OUTPUT_EXISTS,
        SERIALIZATION_FAILED,
        PUBLICATION_FAILED,
        CLEANUP_FAILED
    }
}
