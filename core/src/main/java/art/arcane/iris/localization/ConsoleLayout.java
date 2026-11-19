package art.arcane.iris.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The console's shared row layout. Every line sits on a two-column gutter: column 0 holds a status glyph or a space
 * and content starts at column 2. Label rows put the value at column 18, and a next step puts its command on its own
 * line, indented 4, so it can be copied as is. Log records carry their own level, so the layout never emits blank lines.
 */
public final class ConsoleLayout {
    public static final int GUTTER_WIDTH = 2;
    public static final int LABEL_WIDTH = 16;
    public static final int COMMAND_INDENT = 4;
    public static final int CONTINUATION_INDENT = 2;

    private static final String BLANK_GUTTER = " ".repeat(GUTTER_WIDTH);
    private static final String LABEL_GAP = " ".repeat(LABEL_WIDTH);
    private static final C COMMAND_COLOR = C.AQUA;
    private static final Pattern STYLING = Pattern.compile("(?i)§[0-9A-FK-ORX]|<[/#!?]?[a-z0-9_:#-]+(?::[^>]*)?>");
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private ConsoleLayout() {
    }

    public static String row(String text) {
        return BLANK_GUTTER + C.RESET + text;
    }

    public static String continuation(String text) {
        return row(" ".repeat(CONTINUATION_INDENT) + text);
    }

    public static List<String> labelRows(String label, String value) {
        if (label.length() < LABEL_WIDTH) {
            return List.of(row(padRight(label, LABEL_WIDTH) + value));
        }
        return List.of(row(label), row(LABEL_GAP + value));
    }

    /** An instruction ending in a colon, then the exact command on its own line; an empty instruction leaves only the command. */
    public static List<String> nextStep(String instruction, String command) {
        List<String> lines = new ArrayList<>(2);
        String trimmed = instruction == null ? "" : instruction.strip();
        if (!trimmed.isEmpty()) {
            lines.add(row(trimmed.endsWith(":") ? trimmed : trimmed + ":"));
        }
        lines.add(row(" ".repeat(COMMAND_INDENT - GUTTER_WIDTH) + COMMAND_COLOR + command));
        return lines;
    }

    /** Puts a log line on the gutter, marked for its tone, unless it already starts with a gutter of its own. */
    public static String onGutter(ChatReport.Tone tone, String styledLine) {
        if (styledLine == null || hasGutter(styledLine)) {
            return styledLine;
        }
        return tone.marked(styledLine);
    }

    public static String withoutColors(String styledText) {
        return styledText == null ? "" : LEGACY_COLOR.matcher(styledText).replaceAll("");
    }

    static boolean hasGutter(String styledLine) {
        String plain = STYLING.matcher(styledLine).replaceAll("");
        if (plain.isBlank() || plain.startsWith(BLANK_GUTTER)) {
            return true;
        }
        for (ChatReport.Tone tone : ChatReport.Tone.values()) {
            if (tone.hasMark() && plain.startsWith(tone.mark() + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String padRight(String text, int width) {
        return text + " ".repeat(Math.max(0, width - text.length()));
    }
}
