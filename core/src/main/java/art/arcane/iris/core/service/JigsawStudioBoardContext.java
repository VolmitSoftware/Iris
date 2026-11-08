package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;

import java.util.ArrayList;
import java.util.List;
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
    private static final String SIDEBAR_TITLE = "Iris";
    private static final String SEPARATOR = "-------------------";
    private static final int SIDEBAR_LINE_CAPACITY = 11;

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

    public String sidebarTitle() {
        return SIDEBAR_TITLE;
    }

    /** The sidebar lines for this context, top to bottom, as plain text. */
    public List<String> sidebarLines() {
        List<String> lines = new ArrayList<>(SIDEBAR_LINE_CAPACITY);
        lines.add(SEPARATOR);
        lines.add("Jigsaw Studio");
        lines.add("Structure: " + structureKey);
        if (!insideWorkcell()) {
            lines.add("Mode: " + modeDisplayName());
            lines.add(SEPARATOR);
            lines.add("Walk into a workcell");
            if (!controlHint.isEmpty()) {
                lines.add(controlHint);
            }
            lines.add(SEPARATOR);
            return List.copyOf(lines);
        }
        lines.add("Workcell: " + workcellName);
        if (!workcellRole.isEmpty() && !workcellRole.equals(workcellName)) {
            lines.add("Role: " + workcellRole);
        }
        lines.add("Variant: " + (variantName.isEmpty() ? "None" : variantName));
        lines.add("State: " + state.displayName());
        lines.add(SEPARATOR);
        if (!controlHint.isEmpty()) {
            lines.add(controlHint);
        }
        lines.add(SEPARATOR);
        return List.copyOf(lines);
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
