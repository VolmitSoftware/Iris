package art.arcane.iris.localization;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.localization.MessageArgument;

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

    public record Line(String text, String command) {
        public boolean clickable() {
            return command != null && !command.isBlank();
        }
    }

    private final Tone tone;
    private final String title;
    private final List<String> items;
    private final int itemLimit;
    private final String hint;
    private final String hintCommand;

    private ChatReport(Builder builder) {
        this.tone = builder.tone;
        this.title = builder.title;
        this.items = List.copyOf(builder.items);
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

    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(tone.paint(title), null));
        int shown = Math.min(itemLimit, items.size());
        for (int index = 0; index < shown; index++) {
            lines.add(new Line(C.DARK_GRAY + ITEM_BULLET + C.GRAY + items.get(index), null));
        }
        int hidden = items.size() - shown;
        if (hidden > 0) {
            lines.add(new Line(C.DARK_GRAY + CONTINUATION_INDENT
                    + clean(IrisLanguage.plain(RuntimeUiMessages.REPORT_MORE, MessageArgument.trusted("count", hidden))), null));
        }
        if (hint != null) {
            lines.add(new Line(C.GRAY + hint, hintCommand));
        }
        return lines;
    }

    public void log() {
        for (Line line : lines()) {
            logLine(line.text());
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
        private final List<String> items = new ArrayList<>();
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
                items.add(cleaned);
            }
            return this;
        }

        public Builder styledItem(String styledText) {
            if (styledText != null && !styledText.isBlank()) {
                items.add(styledText);
            }
            return this;
        }

        public Builder items(Collection<String> texts) {
            for (String text : texts) {
                item(text);
            }
            return this;
        }

        public Builder entry(String label, Object value) {
            items.add(label(label, String.valueOf(value)));
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
            String cleaned = clean(text);
            this.hint = command == null ? cleaned : cleaned.replace(command, highlight(command));
            this.hintCommand = command;
            return this;
        }

        public ChatReport build() {
            return new ChatReport(this);
        }
    }
}
