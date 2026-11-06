package com.volmit.tooling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A JSON file that remembers where each value is written, so an edit rewrites only the value it changes and the rest
 * of the file keeps the layout its author gave it. Comments are passed over, as the pack reader allows them.
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

    /** {@code plain} as a JSON string literal. */
    public static String quote(String plain) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char character : plain.toCharArray()) {
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
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
        for (Object step : path.steps()) {
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

    /** The text of {@code value}, its later lines indented as if it started a line with no indent. */
    public String detachedTextOf(Value value) {
        return reindented(textOf(value), indentOfLine(value.start()), "");
    }

    public Splice replace(Value value, String literal) {
        return new Splice(value.start(), value.end(), literal);
    }

    /** Sets the member, written after the last one in the object's own style when it is new. */
    public Splice put(Obj object, String key, String literal) {
        Optional<Member> existing = object.member(key);
        if (existing.isPresent()) {
            return replace(existing.get().value(), literal);
        }
        if (object.members().isEmpty()) {
            return replace(object, "{" + quote(key) + ": " + reindented(literal, "", indentOfLine(object.start())) + "}");
        }
        Member last = object.members().getLast();
        String indent = indentOfLine(last.keyStart());
        String entry = quote(key) + ": " + reindented(literal, "", indent);
        String separator = isMultiline(object) ? ",\n" + indent : ", ";
        return new Splice(last.value().end(), last.value().end(), separator + entry);
    }

    /** @throws IllegalArgumentException when the object has no member {@code key} */
    public Splice remove(Obj object, String key) {
        List<Member> members = object.members();
        int index = members.indexOf(object.member(key).orElseThrow(() -> new IllegalArgumentException("No " + key + " to remove")));
        if (members.size() == 1) {
            return replace(object, "{}");
        }
        if (index > 0) {
            return new Splice(members.get(index - 1).value().end(), members.get(index).value().end(), "");
        }
        return new Splice(members.getFirst().keyStart(), members.get(1).keyStart(), "");
    }

    /** Adds an element after the last one, a multi-line one indented to match the array. */
    public Splice append(Arr array, String literal) {
        if (array.elements().isEmpty()) {
            return replace(array, "[" + reindented(literal, "", indentOfLine(array.start())) + "]");
        }
        Value last = array.elements().getLast();
        String indent = indentOfLine(last.start());
        String separator = isMultiline(array) ? ",\n" + indent : ", ";
        return new Splice(last.end(), last.end(), separator + reindented(literal, "", indent));
    }

    public Splice removeElement(Arr array, int index) {
        List<Value> elements = array.elements();
        Objects.checkIndex(index, elements.size());
        if (elements.size() == 1) {
            return replace(array, "[]");
        }
        if (index > 0) {
            return new Splice(elements.get(index - 1).end(), elements.get(index).end(), "");
        }
        return new Splice(elements.getFirst().start(), elements.get(1).start(), "");
    }

    /**
     * The text with {@code splices} made, each placed by where it was in this text.
     *
     * @throws IllegalArgumentException when two splices overlap
     */
    public String apply(List<Splice> splices) {
        List<Splice> fromLast = new ArrayList<>(splices);
        fromLast.sort(Comparator.comparingInt(Splice::start).reversed());
        StringBuilder edited = new StringBuilder(text);
        int earliestStart = Integer.MAX_VALUE;
        for (Splice splice : fromLast) {
            if (splice.end() > earliestStart) {
                throw new IllegalArgumentException("Overlapping edits");
            }
            edited.replace(splice.start(), splice.end(), splice.replacement());
            earliestStart = splice.start();
        }
        return edited.toString();
    }

    private static Optional<Value> stepInto(Value value, Object step) {
        if (step instanceof String key && value instanceof Obj object) {
            return object.get(key);
        }
        if (step instanceof Integer index && value instanceof Arr array && index < array.elements().size()) {
            return Optional.of(array.elements().get(index));
        }
        return Optional.empty();
    }

    private boolean isMultiline(Value value) {
        return textOf(value).indexOf('\n') >= 0;
    }

    // The blanks before the first character on the line holding position.
    private String indentOfLine(int position) {
        int lineStart = text.lastIndexOf('\n', position - 1) + 1;
        int indentEnd = lineStart;
        while (indentEnd < position && (text.charAt(indentEnd) == ' ' || text.charAt(indentEnd) == '\t')) {
            indentEnd++;
        }
        return text.substring(lineStart, indentEnd);
    }

    private static String reindented(String literal, String fromIndent, String toIndent) {
        String[] lines = literal.split("\n", -1);
        StringBuilder result = new StringBuilder(lines[0]);
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].startsWith(fromIndent) ? lines[index].substring(fromIndent.length()) : lines[index].stripLeading();
            result.append('\n').append(toIndent).append(line);
        }
        return result.toString();
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

    /** Replaces the text from {@code start} up to {@code end} with {@code replacement}. */
    public record Splice(int start, int end, String replacement) {
        public Splice {
            Objects.requireNonNull(replacement, "replacement");
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("A splice runs forwards from 0, not " + start + " to " + end);
            }
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
