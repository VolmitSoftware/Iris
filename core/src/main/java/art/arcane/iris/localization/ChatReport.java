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
        SUCCESS(C.IRIS, ConsoleGlyphs.CHECK, C.GREEN),
        INFO(C.AQUA, "", C.RESET),
        WARNING(C.GOLD, ConsoleGlyphs.WARNING, C.YELLOW),
        ERROR(C.RED, ConsoleGlyphs.CROSS, C.RED);

        private final C color;
        private final String mark;
        private final C markColor;

        Tone(C color, String mark, C markColor) {
            this.color = color;
            this.mark = mark;
            this.markColor = markColor;
        }

        /** Puts already styled console text on the gutter behind this tone's status glyph, or a blank one for info. */
        public String marked(String styledText) {
            if (!hasMark()) {
                return ConsoleLayout.row(styledText);
            }
            return markColor + mark + " " + C.RESET + styledText;
        }

        public boolean hasMark() {
            return !mark.isEmpty();
        }

        public String mark() {
            return mark;
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

    /** A suggested command and what running it gets the reader. */
    private record NextStep(String instruction, String command) {
    }

    /**
     * One report entry. Chat shows its styled lines; the console shows the same words on the gutter in normal
     * foreground, and a labelled entry as an aligned label row.
     */
    private record Entry(List<String> styledLines, String label, String value) {
        static Entry styled(List<String> styledLines) {
            return new Entry(List.copyOf(styledLines), null, null);
        }

        static Entry labelled(String label, String value) {
            return new Entry(List.of(ChatReport.label(label, value)), clean(label), clean(value));
        }

        List<String> consoleLines() {
            if (label != null) {
                return ConsoleLayout.labelRows(label, value);
            }
            List<String> lines = new ArrayList<>(styledLines.size());
            lines.add(ConsoleLayout.row(ConsoleLayout.withoutColors(styledLines.getFirst())));
            for (int index = 1; index < styledLines.size(); index++) {
                lines.add(ConsoleLayout.continuation(ConsoleLayout.withoutColors(styledLines.get(index)).stripLeading()));
            }
            return lines;
        }
    }

    private final Tone tone;
    private final String title;
    private final List<Entry> entries;
    private final int itemLimit;
    private final String hint;
    private final String hintShownCommand;
    private final String hintClickCommand;
    private final List<NextStep> nextSteps;

    private ChatReport(Builder builder) {
        this.tone = builder.tone;
        this.title = builder.title;
        this.entries = List.copyOf(builder.entries);
        this.itemLimit = builder.itemLimit;
        this.hint = builder.hint;
        this.hintShownCommand = builder.hintShownCommand;
        this.hintClickCommand = builder.hintClickCommand;
        this.nextSteps = List.copyOf(builder.nextSteps);
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
        for (Entry entry : shownEntries()) {
            addChatEntryLines(lines, entry);
        }
        int hidden = hiddenEntryCount();
        if (hidden > 0) {
            lines.add(new Line(C.DARK_GRAY + CONTINUATION_INDENT + ReportText.moreLabel(hidden), null));
        }
        if (hint == null && nextSteps.isEmpty()) {
            return lines;
        }
        if (spaced && !lines.isEmpty()) {
            lines.add(SPACER);
        }
        if (hint != null) {
            lines.add(new Line(C.GRAY + chatHint(), hintClickCommand));
        }
        for (NextStep step : nextSteps) {
            lines.add(new Line(C.GRAY + step.instruction() + ": " + highlight(step.command()), step.command()));
        }
        return lines;
    }

    private String chatHint() {
        return hintShownCommand == null ? hint : hint.replace(hintShownCommand, highlight(hintShownCommand));
    }

    private void addChatEntryLines(List<Line> lines, Entry entry) {
        List<String> styled = entry.styledLines();
        lines.add(new Line(tone.color() + ITEM_BULLET + C.GRAY + styled.getFirst(), null));
        for (int index = 1; index < styled.size(); index++) {
            lines.add(new Line(styled.get(index), null));
        }
    }

    /** The console layout: the title behind the tone's status glyph, then the body, hint and next steps on the gutter. */
    public List<String> consoleLines() {
        List<String> lines = new ArrayList<>();
        lines.add(tone.marked(consoleTitle()));
        for (Entry entry : shownEntries()) {
            lines.addAll(entry.consoleLines());
        }
        int hidden = hiddenEntryCount();
        if (hidden > 0) {
            lines.add(ConsoleLayout.row(ReportText.moreLabel(hidden)));
        }
        if (hint != null) {
            lines.addAll(consoleHintLines());
        }
        for (NextStep step : nextSteps) {
            lines.addAll(ConsoleLayout.nextStep(step.instruction(), step.command()));
        }
        return lines;
    }

    private String consoleTitle() {
        String plainTitle = clean(title);
        return tone == Tone.SUCCESS ? C.BOLD + plainTitle : plainTitle;
    }

    /**
     * A hint that ends in its command reads as a next step; any other hint stays one sentence. The console can't
     * click, so the step shows the full command a click would run.
     */
    private List<String> consoleHintLines() {
        if (hintShownCommand != null && hint.endsWith(hintShownCommand)) {
            String instruction = hint.substring(0, hint.length() - hintShownCommand.length());
            String runnable = hintClickCommand == null ? hintShownCommand : clean(hintClickCommand);
            return ConsoleLayout.nextStep(instruction, runnable);
        }
        return List.of(ConsoleLayout.row(hint));
    }

    private List<Entry> shownEntries() {
        return entries.subList(0, Math.min(itemLimit, entries.size()));
    }

    private int hiddenEntryCount() {
        return entries.size() - shownEntries().size();
    }

    public void log() {
        for (String line : consoleLines()) {
            logLine(line);
        }
    }

    private void logLine(String text) {
        switch (tone) {
            case ERROR -> IrisLogging.error(text);
            case WARNING -> IrisLogging.warn(text);
            default -> IrisLogging.info(text);
        }
    }

    public static final class Builder {
        private final Tone tone;
        private final String title;
        private final List<Entry> entries = new ArrayList<>();
        private final List<NextStep> nextSteps = new ArrayList<>();
        private int itemLimit = DEFAULT_ITEM_LIMIT;
        private String hint;
        private String hintShownCommand;
        private String hintClickCommand;

        private Builder(Tone tone, String title) {
            this.tone = tone;
            this.title = title;
        }

        public Builder item(String text) {
            String cleaned = clean(text);
            if (!cleaned.isEmpty()) {
                entries.add(Entry.styled(List.of(ReportText.body(cleaned))));
            }
            return this;
        }

        public Builder styledItem(String styledText) {
            if (styledText != null && !styledText.isBlank()) {
                entries.add(Entry.styled(List.of(styledText)));
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
            ReportText.groupedBodies(cleaned, CONTINUATION_INDENT).forEach(lines -> entries.add(Entry.styled(lines)));
            return this;
        }

        public Builder entry(String label, Object value) {
            entries.add(Entry.labelled(label, String.valueOf(value)));
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
            this.hint = clean(text);
            this.hintShownCommand = shownCommand == null ? null : clean(shownCommand);
            this.hintClickCommand = clickCommand;
            return this;
        }

        /** Suggests a command after the body; chat shows it as one clickable line, the console on its own line. */
        public Builder nextStep(String instruction, String command) {
            nextSteps.add(new NextStep(clean(instruction), clean(command)));
            return this;
        }

        public ChatReport build() {
            return new ChatReport(this);
        }
    }
}
