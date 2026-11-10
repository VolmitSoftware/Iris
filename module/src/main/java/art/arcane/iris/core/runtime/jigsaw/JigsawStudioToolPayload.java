package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;
import java.util.UUID;

public record JigsawStudioToolPayload(
        int schemaVersion,
        JigsawStudioToolAction action,
        UUID requestId,
        String workcellId,
        String pieceKey,
        String poolKey,
        int entryIndex,
        int amount
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int NO_ENTRY_INDEX = -1;

    private static final int MAX_FIELD_LENGTH = 512;

    public JigsawStudioToolPayload {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Jigsaw Studio tool schema version must be positive");
        }
        action = Objects.requireNonNull(action, "Jigsaw Studio tool action");
        requestId = Objects.requireNonNull(requestId, "Jigsaw Studio tool request ID");
        workcellId = normalize(workcellId, "workcell ID");
        pieceKey = normalize(pieceKey, "piece key");
        poolKey = normalize(poolKey, "pool key");
        if (entryIndex < NO_ENTRY_INDEX) {
            throw new IllegalArgumentException("Jigsaw Studio tool entry index cannot be lower than -1");
        }
    }

    public static JigsawStudioToolPayload request(
            JigsawStudioToolAction action,
            UUID requestId
    ) {
        return new JigsawStudioToolPayload(
                CURRENT_SCHEMA_VERSION,
                action,
                requestId,
                "",
                "",
                "",
                NO_ENTRY_INDEX,
                0);
    }

    public static JigsawStudioToolPayload workcell(
            JigsawStudioToolAction action,
            UUID requestId,
            String workcellId
    ) {
        return new JigsawStudioToolPayload(
                CURRENT_SCHEMA_VERSION,
                action,
                requestId,
                workcellId,
                "",
                "",
                NO_ENTRY_INDEX,
                0);
    }

    public static JigsawStudioToolPayload variant(
            JigsawStudioToolAction action,
            UUID requestId,
            String workcellId,
            String pieceKey
    ) {
        return new JigsawStudioToolPayload(
                CURRENT_SCHEMA_VERSION,
                action,
                requestId,
                workcellId,
                pieceKey,
                "",
                NO_ENTRY_INDEX,
                0);
    }

    public static JigsawStudioToolPayload membership(
            JigsawStudioToolAction action,
            UUID requestId,
            String workcellId,
            String pieceKey,
            String poolKey,
            int entryIndex,
            int amount
    ) {
        return new JigsawStudioToolPayload(
                CURRENT_SCHEMA_VERSION,
                action,
                requestId,
                workcellId,
                pieceKey,
                poolKey,
                entryIndex,
                amount);
    }

    private static String normalize(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException("Jigsaw Studio tool " + fieldName
                    + " cannot exceed " + MAX_FIELD_LENGTH + " characters");
        }
        return normalized;
    }
}
