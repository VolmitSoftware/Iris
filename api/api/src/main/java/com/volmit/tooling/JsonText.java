package com.volmit.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A JSON file that remembers where each value is written, so the studio can edit one value and leave the rest of the
 * file as its author laid it out. Comments are passed over, as the pack reader allows them.
 */
public final class JsonText {
    private final String text;
    private final Value root;

    private JsonText(String text, Value root) {
        this.text = text;
        this.root = root;
    }

    /** @throws IllegalArgumentException naming the line, when {@code text} is not one JSON value */
    public static JsonText parse(String text) {
        Objects.requireNonNull(text, "text");
        Parser parser = new Parser(text);
        Value root = parser.value();
        parser.skipBlank();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected text after the end");
        }
        return new JsonText(text, root);
    }

    /** Whether {@code literal} is one JSON value. */
    public static boolean isValue(String literal) {
        try {
            parse(literal);
            return true;
        } catch (IllegalArgumentException notJson) {
            return false;
        }
    }

    public String text() {
        return text;
    }

    public Value root() {
        return root;
    }

    /** The value at {@code path}; empty when the file has none there. */
    public Optional<Value> at(JsonPath path) {
        Value value = root;
        for (JsonPath.Step step : path.steps()) {
            Optional<Value> inside = stepInto(value, step);
            if (inside.isEmpty()) {
                return Optional.empty();
            }
            value = inside.get();
        }
        return Optional.of(value);
    }

    /** The value as it is written. */
    public String textOf(Value value) {
        return text.substring(value.start(), value.end());
    }

    private static Optional<Value> stepInto(Value value, JsonPath.Step step) {
        if (step instanceof JsonPath.Key(String name) && value instanceof Obj object) {
            return object.get(name);
        }
        if (step instanceof JsonPath.Index(int position) && value instanceof Arr array && position < array.elements().size()) {
            return Optional.of(array.elements().get(position));
        }
        return Optional.empty();
    }

    /** A value of the file, from {@code start} up to {@code end} in its text. */
    public sealed interface Value permits Obj, Arr, Literal {
        int start();

        int end();
    }

    /** One member of an object; {@code keyStart} is where its quoted name begins. */
    public record Member(String key, int keyStart, Value value) {
    }

    public record Obj(int start, int end, List<Member> members) implements Value {
        public Obj {
            members = List.copyOf(members);
        }

        public Optional<Member> member(String key) {
            return members.stream().filter(member -> member.key().equals(key)).findFirst();
        }

        public Optional<Value> get(String key) {
            return member(key).map(Member::value);
        }
    }

    public record Arr(int start, int end, List<Value> elements) implements Value {
        public Arr {
            elements = List.copyOf(elements);
        }
    }

    /** A string, number, true, false or null; {@code string} holds a string's decoded text, and is empty for the rest. */
    public record Literal(int start, int end, Optional<String> string) implements Value {
        public Literal {
            Objects.requireNonNull(string, "string");
        }
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return position >= text.length();
        }

        Value value() {
            skipBlank();
            if (atEnd()) {
                throw error("Expected a value");
            }
            return switch (text.charAt(position)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> {
                    int start = position;
                    String string = string();
                    yield new Literal(start, position, Optional.of(string));
                }
                default -> bareLiteral();
            };
        }

        private Obj object() {
            int start = position++;
            List<Member> members = new ArrayList<>();
            skipBlank();
            if (peek() == '}') {
                position++;
                return new Obj(start, position, List.of());
            }
            while (true) {
                skipBlank();
                if (peek() != '"') {
                    throw error("Expected a quoted name");
                }
                int keyStart = position;
                String key = string();
                skipBlank();
                expect(':');
                members.add(new Member(key, keyStart, value()));
                skipBlank();
                if (peek() == ',') {
                    position++;
                } else {
                    expect('}');
                    return new Obj(start, position, members);
                }
            }
        }

        private Arr array() {
            int start = position++;
            List<Value> elements = new ArrayList<>();
            skipBlank();
            if (peek() == ']') {
                position++;
                return new Arr(start, position, List.of());
            }
            while (true) {
                elements.add(value());
                skipBlank();
                if (peek() == ',') {
                    position++;
                } else {
                    expect(']');
                    return new Arr(start, position, elements);
                }
            }
        }

        private String string() {
            position++;
            StringBuilder decoded = new StringBuilder();
            while (!atEnd()) {
                char character = text.charAt(position++);
                if (character == '"') {
                    return decoded.toString();
                }
                if (character != '\\') {
                    decoded.append(character);
                    continue;
                }
                if (atEnd()) {
                    break;
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case 'n' -> decoded.append('\n');
                    case 't' -> decoded.append('\t');
                    case 'r' -> decoded.append('\r');
                    case 'b' -> decoded.append('\b');
                    case 'f' -> decoded.append('\f');
                    case 'u' -> {
                        if (position + 4 > text.length()) {
                            throw error("Unfinished \\u escape");
                        }
                        decoded.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> decoded.append(escaped);
                }
            }
            throw error("Unclosed string");
        }

        private Literal bareLiteral() {
            int start = position;
            while (!atEnd() && "{}[],:\"".indexOf(text.charAt(position)) < 0 && !Character.isWhitespace(text.charAt(position))) {
                position++;
            }
            String word = text.substring(start, position);
            if (!word.equals("true") && !word.equals("false") && !word.equals("null") && !isNumber(word)) {
                position = start;
                throw error(word.isEmpty() ? "Expected a value" : "Not a value: " + word);
            }
            return new Literal(start, position, Optional.empty());
        }

        private static boolean isNumber(String word) {
            return word.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");
        }

        void skipBlank() {
            while (!atEnd()) {
                char character = text.charAt(position);
                if (Character.isWhitespace(character)) {
                    position++;
                } else if (text.startsWith("//", position)) {
                    int lineEnd = text.indexOf('\n', position);
                    position = lineEnd < 0 ? text.length() : lineEnd;
                } else if (text.startsWith("/*", position)) {
                    int commentEnd = text.indexOf("*/", position + 2);
                    position = commentEnd < 0 ? text.length() : commentEnd + 2;
                } else {
                    return;
                }
            }
        }

        private char peek() {
            return atEnd() ? '\0' : text.charAt(position);
        }

        private void expect(char wanted) {
            if (peek() != wanted) {
                throw error("Expected " + wanted);
            }
            position++;
        }

        IllegalArgumentException error(String problem) {
            int line = 1;
            for (int index = 0; index < Math.min(position, text.length()); index++) {
                if (text.charAt(index) == '\n') {
                    line++;
                }
            }
            return new IllegalArgumentException(problem + " on line " + line);
        }
    }
}
