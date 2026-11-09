package com.volmit.tooling.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Styled text a tool shows a player: item names and lore, menu titles, sidebars, the action bar and chat. It is a list of
 * spans, each a piece of text with its own colour and style, so the text carries no formatting codes and a platform shows
 * it with its own text components. A value is immutable.
 *
 * <p>Build it with {@link #builder()}, or use {@link #plain(String)} and {@link #colored(TextColor, String)} for one span.
 * Text of a span is shown as it is: a formatting sign in it is just a character.
 *
 * @param spans the pieces in order
 */
public record ToolText(List<Span> spans) {
    /** Nothing at all. */
    public static final ToolText EMPTY = new ToolText(List.of());

    public ToolText {
        spans = List.copyOf(spans);
    }

    /**
     * A piece of text in one style.
     *
     * @param text          what is shown, never empty
     * @param color         the colour; null leaves the platform's default colour for that kind of text
     * @param bold          whether the text is bold
     * @param italic        whether the text is italic
     * @param strikethrough whether the text has a line through it
     */
    public record Span(String text, TextColor color, boolean bold, boolean italic, boolean strikethrough) {
        public Span {
            Objects.requireNonNull(text, "text");
            if (text.isEmpty()) {
                throw new IllegalArgumentException("A span has text");
            }
        }

        /** A span in {@code color} and no other style. */
        public static Span of(TextColor color, String text) {
            return new Span(text, color, false, false, false);
        }

        boolean sameStyle(Span other) {
            return color == other.color && bold == other.bold && italic == other.italic
                    && strikethrough == other.strikethrough;
        }
    }

    /** One span in the platform's default colour. */
    public static ToolText plain(String text) {
        return builder().append(text).build();
    }

    /** One span in {@code color}. */
    public static ToolText colored(TextColor color, String text) {
        return builder().append(color, text).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The text without any style, as one string. */
    public String plainText() {
        StringBuilder text = new StringBuilder();
        for (Span span : spans) {
            text.append(span.text());
        }
        return text.toString();
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }

    /** Builds a {@link ToolText} span by span. Empty text is skipped, and neighbours of one style become one span. */
    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        private final List<Span> spans = new ArrayList<>();

        private Builder() {
        }

        /** Adds {@code text} in the platform's default colour; empty text adds nothing. */
        public Builder append(String text) {
            Objects.requireNonNull(text, "text");
            return text.isEmpty() ? this : append(new Span(text, null, false, false, false));
        }

        /** Adds {@code text} in {@code color}; empty text adds nothing. */
        public Builder append(TextColor color, String text) {
            Objects.requireNonNull(color, "color");
            Objects.requireNonNull(text, "text");
            return text.isEmpty() ? this : append(Span.of(color, text));
        }

        /** Adds {@code span}, joined to the span before it when they share a style. */
        public Builder append(Span span) {
            Objects.requireNonNull(span, "span");
            if (!spans.isEmpty() && spans.getLast().sameStyle(span)) {
                Span last = spans.removeLast();
                spans.add(new Span(last.text() + span.text(), last.color(), last.bold(), last.italic(),
                        last.strikethrough()));
                return this;
            }
            spans.add(span);
            return this;
        }

        public Builder append(ToolText text) {
            Objects.requireNonNull(text, "text");
            text.spans().forEach(this::append);
            return this;
        }

        public Builder black(String text) {
            return append(TextColor.BLACK, text);
        }

        public Builder darkBlue(String text) {
            return append(TextColor.DARK_BLUE, text);
        }

        public Builder darkGreen(String text) {
            return append(TextColor.DARK_GREEN, text);
        }

        public Builder darkAqua(String text) {
            return append(TextColor.DARK_AQUA, text);
        }

        public Builder darkRed(String text) {
            return append(TextColor.DARK_RED, text);
        }

        public Builder darkPurple(String text) {
            return append(TextColor.DARK_PURPLE, text);
        }

        public Builder gold(String text) {
            return append(TextColor.GOLD, text);
        }

        public Builder gray(String text) {
            return append(TextColor.GRAY, text);
        }

        public Builder darkGray(String text) {
            return append(TextColor.DARK_GRAY, text);
        }

        public Builder blue(String text) {
            return append(TextColor.BLUE, text);
        }

        public Builder green(String text) {
            return append(TextColor.GREEN, text);
        }

        public Builder aqua(String text) {
            return append(TextColor.AQUA, text);
        }

        public Builder red(String text) {
            return append(TextColor.RED, text);
        }

        public Builder lightPurple(String text) {
            return append(TextColor.LIGHT_PURPLE, text);
        }

        public Builder yellow(String text) {
            return append(TextColor.YELLOW, text);
        }

        public Builder white(String text) {
            return append(TextColor.WHITE, text);
        }

        public ToolText build() {
            return new ToolText(spans);
        }
    }
}
