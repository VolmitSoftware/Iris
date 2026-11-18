package art.arcane.iris.localization;

import art.arcane.iris.spi.IrisLogging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public final class ChatReport {
    public static final int DEFAULT_ITEM_LIMIT = 8;
    private static final String ITEM_BULLET = "  » ";
    private static final String CONTINUATION_INDENT = "     ";
    private static final Pattern LEGACY_COLOR = Pattern.compile("(?i)[§&][0-9A-FK-ORX]");

    public enum Tone {
        SUCCESS(C.IRIS),
        INFO(C.AQUA),
        WARNING(C.GOLD),
        ERROR(C.RED);

        private final C color;

        Tone(C color) {
            this.color = color;
        }

        public C color() {
            return color;
        }

        public String paint(String text) {
            return color + clean(text);
        }
    }

    /** A blank chat line. A single space, because some clients drop fully empty messages. */
    public static final Line SPACER = new Line(" ", null);

    public record Line(String text, String command) {
        public boolean clickable() {
            return command != null && !command.isBlank();
        }
    }

    private final Tone tone;
    private final String title;
    private final List<List<String>> entries;
    private final int itemLimit;
    private final String hint;
    private final String hintCommand;

    private ChatReport(Builder builder) {
        this.tone = builder.tone;
        this.title = builder.title;
        this.entries = List.copyOf(builder.entries);
        this.itemLimit = builder.itemLimit;
        this.hint = builder.hint;
        this.hintCommand = builder.hintCommand;
    }

    public static Builder of(Tone tone, String title) {
        return new Builder(tone, title);
    }

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY_COLOR.matcher(text).replaceAll("")
                .replace("<", "‹")
                .replace(">", "›")
                .strip();
    }

    public static String label(String label, String value) {
        return C.GRAY + clean(label) + C.DARK_GRAY + ": " + C.IRIS + clean(value);
    }

    public static String highlight(String text) {
        return C.IRIS + clean(text) + C.GRAY;
    }

    public Tone tone() {
        return tone;
    }

    /** The compact layout: title first, no spacing. Used for logs and other plain output. */
    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        lines.add(titleLine());
        lines.addAll(bodyLines(false));
        return lines;
    }

    public Line titleLine() {
        return new Line(ReportText.title(tone, clean(title)), null);
    }

    /** Every line after the title; {@code spaced} adds a blank line between the list and the hint, for chat. */
    public List<Line> bodyLines(boolean spaced) {
        List<Line> lines = new ArrayList<>();
        int shown = Math.min(itemLimit, entries.size());
        for (int index = 0; index < shown; index++) {
            addEntryLines(lines, entries.get(index));
        }
        int hidden = entries.size() - shown;
        if (hidden > 0) {
            lines.add(new Line(C.DARK_GRAY + CONTINUATION_INDENT + ReportText.moreLabel(hidden), null));
        }
        if (hint != null) {
            if (spaced && !lines.isEmpty()) {
                lines.add(SPACER);
            }
            lines.add(new Line(C.GRAY + hint, hintCommand));
        }
        return lines;
    }

    private void addEntryLines(List<Line> lines, List<String> entry) {
        lines.add(new Line(tone.color() + ITEM_BULLET + C.GRAY + entry.getFirst(), null));
        for (int index = 1; index < entry.size(); index++) {
            lines.add(new Line(entry.get(index), null));
        }
    }

    public void log() {
        for (Line line : lines()) {
            logLine(line.text());
        }
    }

    private void logLine(String styledText) {
        String text = IrisLogging.clean(styledText);
        switch (tone) {
            case ERROR -> IrisLogging.error(text);
            case WARNING -> IrisLogging.warn(text);
            default -> IrisLogging.info(text);
        }
    }

    public static final class Builder {
        private final Tone tone;
        private final String title;
        private final List<List<String>> entries = new ArrayList<>();
        private int itemLimit = DEFAULT_ITEM_LIMIT;
        private String hint;
        private String hintCommand;

        private Builder(Tone tone, String title) {
            this.tone = tone;
            this.title = title;
        }

        public Builder item(String text) {
            String cleaned = clean(text);
            if (!cleaned.isEmpty()) {
                entries.add(List.of(ReportText.body(cleaned)));
            }
            return this;
        }

        public Builder styledItem(String styledText) {
            if (styledText != null && !styledText.isBlank()) {
                entries.add(List.of(styledText));
            }
            return this;
        }

        /** Adds reasons, collapsing ones that differ only in their quoted subject into a single entry. */
        public Builder items(Collection<String> texts) {
            List<String> cleaned = new ArrayList<>(texts.size());
            for (String text : texts) {
                String reason = clean(text);
                if (!reason.isEmpty()) {
                    cleaned.add(reason);
                }
            }
            entries.addAll(ReportText.groupedBodies(cleaned, CONTINUATION_INDENT));
            return this;
        }

        public Builder entry(String label, Object value) {
            entries.add(List.of(label(label, String.valueOf(value))));
            return this;
        }

        public Builder limit(int maxItems) {
            this.itemLimit = Math.max(0, maxItems);
            return this;
        }

        public Builder unlimited() {
            this.itemLimit = Integer.MAX_VALUE;
            return this;
        }

        public Builder hint(String text, String command) {
            return hint(text, command, command);
        }

        /** Shows {@code shownCommand} in the hint text while a click runs {@code clickCommand}. */
        public Builder hint(String text, String shownCommand, String clickCommand) {
            String cleaned = clean(text);
            this.hint = shownCommand == null ? cleaned : cleaned.replace(shownCommand, highlight(shownCommand));
            this.hintCommand = clickCommand;
            return this;
        }

        public ChatReport build() {
            return new ChatReport(this);
        }
    }
}
