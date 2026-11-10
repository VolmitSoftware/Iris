package art.arcane.iris.core.service;

import java.util.Objects;
import java.util.UUID;

public record JigsawStudioGraphEvaluation(
        UUID requestId,
        long generation,
        long seed,
        JigsawStudioEvaluationState state,
        String selectedTheme,
        int pieceCount,
        String detail,
        JigsawStudioPreviewRenderer.PreviewBounds previewBounds
) {
    public JigsawStudioGraphEvaluation {
        requestId = Objects.requireNonNull(requestId, "Jigsaw Studio evaluation request ID");
        if (generation < 1L) {
            throw new IllegalArgumentException("Jigsaw Studio evaluation generation must be positive");
        }
        state = Objects.requireNonNull(state, "Jigsaw Studio evaluation state");
        selectedTheme = normalize(selectedTheme);
        if (pieceCount < 0) {
            throw new IllegalArgumentException("Jigsaw Studio evaluation piece count cannot be negative");
        }
        detail = normalize(detail);
        previewBounds = Objects.requireNonNull(previewBounds, "Jigsaw Studio evaluation preview bounds");
    }

    public JigsawStudioGraphEvaluation stale(String reason) {
        return new JigsawStudioGraphEvaluation(
                requestId,
                generation,
                seed,
                JigsawStudioEvaluationState.STALE,
                selectedTheme,
                pieceCount,
                reason,
                previewBounds);
    }

    public boolean successful() {
        return state == JigsawStudioEvaluationState.VALID
                || state == JigsawStudioEvaluationState.WARNING;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
