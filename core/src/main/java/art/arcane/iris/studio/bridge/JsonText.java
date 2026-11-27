/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.bridge;

import art.arcane.iris.studio.scene.JsonPath;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A JSON file's text read into values that know where they are written, so an edit can change one value and leave the
 * rest of the text, its layout and its comments as they were. Line and block comments are read as blanks.
 */
final class JsonText {
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NULL = "null";

    private final String text;
    private final Value root;

    private JsonText(String text, Value root) {
        this.text = text;
        this.root = root;
    }

    /** @throws IllegalArgumentException naming the line, when {@code text} is not one JSON value */
    static JsonText parse(String text) {
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
    static boolean isValue(String literal) {
        try {
            parse(literal);
            return true;
        } catch (IllegalArgumentException notJson) {
            return false;
        }
    }

    String text() {
        return text;
    }

    Value root() {
        return root;
    }

    /** The value at {@code path}; empty when the file has none there. */
    Optional<Value> at(JsonPath path) {
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
    String textOf(Value value) {
        return text.substring(value.start(), value.end());
    }

    /** The whole file as Gson reads it, so what the pack reads and what is edited come from one reading of the text. */
    JsonElement toGson() {
        return gsonOf(root);
    }

    private JsonElement gsonOf(Value value) {
        return switch (value) {
            case Obj object -> {
                JsonObject members = new JsonObject();
                object.members().forEach(member -> members.add(member.key(), gsonOf(member.value())));
                yield members;
            }
            case Arr array -> {
                JsonArray elements = new JsonArray();
                array.elements().forEach(element -> elements.add(gsonOf(element)));
                yield elements;
            }
            case Literal literal -> gsonOf(literal);
        };
    }

    private JsonElement gsonOf(Literal literal) {
        if (literal.string().isPresent()) {
            return new JsonPrimitive(literal.string().get());
        }
        String written = textOf(literal);
        return switch (written) {
            case TRUE -> new JsonPrimitive(true);
            case FALSE -> new JsonPrimitive(false);
            case NULL -> JsonNull.INSTANCE;
            default -> new JsonPrimitive(new LazilyParsedNumber(written));
        };
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
    sealed interface Value permits Obj, Arr, Literal {
        int start();

        int end();
    }

    /** One member of an object; {@code keyStart} is where its quoted name begins. */
    record Member(String key, int keyStart, Value value) {
    }

    record Obj(int start, int end, List<Member> members) implements Value {
        Obj {
            members = List.copyOf(members);
        }

        Optional<Member> member(String key) {
            return members.stream().filter(member -> member.key().equals(key)).findFirst();
        }

        Optional<Value> get(String key) {
            return member(key).map(Member::value);
        }
    }

    record Arr(int start, int end, List<Value> elements) implements Value {
        Arr {
            elements = List.copyOf(elements);
        }
    }

    /** A string, number, true, false or null; {@code string} holds a string's decoded text, and is empty for the rest. */
    record Literal(int start, int end, Optional<String> string) implements Value {
        Literal {
            Objects.requireNonNull(string, "string");
        }
    }

    private static final class Parser {
        private static final String STRUCTURE = "{}[],:\"";
        private static final int UNICODE_ESCAPE_DIGITS = 4;
        private static final int HEX = 16;

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
                if (peek() != ',') {
                    expect('}');
                    return new Obj(start, position, members);
                }
                position++;
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
                if (peek() != ',') {
                    expect(']');
                    return new Arr(start, position, elements);
                }
                position++;
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
                decoded.append(escaped(text.charAt(position++)));
            }
            throw error("Unclosed string");
        }

        private char escaped(char escape) {
            return switch (escape) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case 'r' -> '\r';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'u' -> unicodeEscape();
                default -> escape;
            };
        }

        private char unicodeEscape() {
            if (position + UNICODE_ESCAPE_DIGITS > text.length()) {
                throw error("Unfinished \\u escape");
            }
            try {
                char decoded = (char) Integer.parseInt(text.substring(position, position + UNICODE_ESCAPE_DIGITS), HEX);
                position += UNICODE_ESCAPE_DIGITS;
                return decoded;
            } catch (NumberFormatException notHex) {
                throw error("Malformed \\u escape");
            }
        }

        private Literal bareLiteral() {
            int start = position;
            while (!atEnd() && STRUCTURE.indexOf(text.charAt(position)) < 0 && !Character.isWhitespace(text.charAt(position))) {
                position++;
            }
            String word = text.substring(start, position);
            if (!word.equals(TRUE) && !word.equals(FALSE) && !word.equals(NULL) && !NUMBER.matcher(word).matches()) {
                position = start;
                throw error(word.isEmpty() ? "Expected a value" : "Not a value: " + word);
            }
            return new Literal(start, position, Optional.empty());
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
