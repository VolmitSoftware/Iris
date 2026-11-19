package art.arcane.iris.localization;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The console's two-column gutter. Column 0 holds a status glyph, a tree rail or a space, and content starts at
 * column 2, so a report's rails, its title text and plain log lines all line up.
 */
public final class ConsoleLayout {
    public static final int GUTTER_WIDTH = 2;

    private static final String BLANK_GUTTER = " ".repeat(GUTTER_WIDTH);
    private static final List<String> RAILS = List.of(ConsoleGlyphs.BRANCH, ConsoleGlyphs.LAST_BRANCH, ConsoleGlyphs.BAR);
    private static final Pattern STYLING = Pattern.compile("(?i)§[0-9A-FK-ORX]|<[/#!?]?[a-z0-9_:#-]+(?::[^>]*)?>");

    private ConsoleLayout() {
    }

    public static String row(String text) {
        return BLANK_GUTTER + text;
    }

    /** Puts a log line on the gutter, marked for its tone, unless it already starts with a gutter of its own. */
    public static String onGutter(ChatReport.Tone tone, String styledLine) {
        if (styledLine == null || hasGutter(styledLine)) {
            return styledLine;
        }
        return tone.marked(styledLine);
    }

    static boolean hasGutter(String styledLine) {
        String plain = STYLING.matcher(styledLine).replaceAll("");
        if (plain.isBlank() || plain.startsWith(BLANK_GUTTER)) {
            return true;
        }
        for (String rail : RAILS) {
            if (plain.startsWith(rail + " ")) {
                return true;
            }
        }
        for (ChatReport.Tone tone : ChatReport.Tone.values()) {
            if (tone.hasMark() && plain.startsWith(tone.mark() + " ")) {
                return true;
            }
        }
        return false;
    }
}
