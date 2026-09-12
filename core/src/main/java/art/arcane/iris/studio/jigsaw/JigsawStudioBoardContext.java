package art.arcane.iris.studio.jigsaw;


import java.util.Objects;
import java.util.UUID;

public record JigsawStudioBoardContext(
        UUID worldId,
        UUID requestId,
        String structureKey,
        JigsawStudioMode mode,
        String workcellRole,
        String workcellName,
        String variantName,
        JigsawStudioBoardState state,
        String controlHint
) {
    public JigsawStudioBoardContext {
        worldId = Objects.requireNonNull(worldId, "Jigsaw Studio board world ID");
        requestId = Objects.requireNonNull(requestId, "Jigsaw Studio board request ID");
        structureKey = requireText(structureKey, "structure key");
        mode = Objects.requireNonNull(mode, "Jigsaw Studio board mode");
        workcellRole = optionalText(workcellRole);
        workcellName = optionalText(workcellName);
        variantName = optionalText(variantName);
        state = Objects.requireNonNull(state, "Jigsaw Studio board state");
        controlHint = optionalText(controlHint);
        if (workcellName.isEmpty() && (!workcellRole.isEmpty() || !variantName.isEmpty())) {
            throw new IllegalArgumentException("Jigsaw Studio board variants require a workcell");
        }
    }

    public boolean insideWorkcell() {
        return !workcellName.isEmpty();
    }

    public String modeDisplayName() {
        return switch (mode) {
            case PLANAR_JIGSAW -> "Planar";
            case SPATIAL_JIGSAW -> "Spatial";
        };
    }

    private static String requireText(String value, String name) {
        String normalized = optionalText(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Jigsaw Studio board " + name + " cannot be blank");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null ? "" : value.trim();
    }
}
