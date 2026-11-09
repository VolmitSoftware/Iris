package art.arcane.iris.core.service;

import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;

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

    /** Empty: the sidebar keeps the platform's own title. */
    public ToolText sidebarTitle() {
        return ToolText.EMPTY;
    }

    /** The sidebar lines for this context, top to bottom. */
    public List<ToolText> sidebarLines() {
        List<ToolText> lines = new ArrayList<>(SIDEBAR_LINE_CAPACITY);
        lines.add(separator());
        lines.add(ToolText.colored(TextColor.LIGHT_PURPLE, "Jigsaw Studio"));
        lines.add(labelled("Structure", structureKey));
        if (!insideWorkcell()) {
            lines.add(labelled("Mode", modeDisplayName()));
            lines.add(separator());
            lines.add(ToolText.colored(TextColor.YELLOW, "Walk into a workcell"));
            if (!controlHint.isEmpty()) {
                lines.add(ToolText.colored(TextColor.GRAY, controlHint));
            }
            lines.add(separator());
            return List.copyOf(lines);
        }
        lines.add(labelled("Workcell", workcellName));
        if (!workcellRole.isEmpty() && !workcellRole.equals(workcellName)) {
            lines.add(labelled("Role", workcellRole));
        }
        lines.add(labelled("Variant", variantName.isEmpty() ? "None" : variantName));
        lines.add(labelled("State", state.displayName()));
        lines.add(separator());
        if (!controlHint.isEmpty()) {
            lines.add(ToolText.colored(TextColor.YELLOW, controlHint));
        }
        lines.add(separator());
        return List.copyOf(lines);
    }

    private static ToolText separator() {
        return ToolText.builder()
                .append(new ToolText.Span(SEPARATOR, TextColor.GRAY, false, false, true))
                .build();
    }

    private static ToolText labelled(String label, String value) {
        return ToolText.builder().aqua(label).gray(": " + value).build();
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
