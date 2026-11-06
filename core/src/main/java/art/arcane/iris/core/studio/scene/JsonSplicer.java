/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.scene;

import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Edits of a {@link JsonText} as splices of its text, so an edit rewrites only the value it changes and the rest of
 * the file keeps the layout its author gave it.
 */
public final class JsonSplicer {
    private final JsonText source;

    public JsonSplicer(JsonText source) {
        this.source = Objects.requireNonNull(source, "source");
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

    /** The value at {@code path}; empty when the file has none there. */
    public Optional<JsonText.Value> at(JsonPath path) {
        return source.at(path);
    }

    /** The text of {@code value}, its later lines indented as if it started a line with no indent. */
    public String detachedTextOf(JsonText.Value value) {
        return reindented(source.textOf(value), indentOfLine(value.start()), "");
    }

    public Splice replace(JsonText.Value value, String literal) {
        return new Splice(value.start(), value.end(), literal);
    }

    /** Sets the member, written after the last one in the object's own style when it is new. */
    public Splice put(JsonText.Obj object, String key, String literal) {
        Optional<JsonText.Member> existing = object.member(key);
        if (existing.isPresent()) {
            return replace(existing.get().value(), literal);
        }
        if (object.members().isEmpty()) {
            return replace(object, "{" + quote(key) + ": " + reindented(literal, "", indentOfLine(object.start())) + "}");
        }
        JsonText.Member last = object.members().getLast();
        String indent = indentOfLine(last.keyStart());
        String entry = quote(key) + ": " + reindented(literal, "", indent);
        String separator = isMultiline(object) ? ",\n" + indent : ", ";
        return new Splice(last.value().end(), last.value().end(), separator + entry);
    }

    /** @throws IllegalArgumentException when the object has no member {@code key} */
    public Splice remove(JsonText.Obj object, String key) {
        List<JsonText.Member> members = object.members();
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
    public Splice append(JsonText.Arr array, String literal) {
        if (array.elements().isEmpty()) {
            return replace(array, "[" + reindented(literal, "", indentOfLine(array.start())) + "]");
        }
        JsonText.Value last = array.elements().getLast();
        String indent = indentOfLine(last.start());
        String separator = isMultiline(array) ? ",\n" + indent : ", ";
        return new Splice(last.end(), last.end(), separator + reindented(literal, "", indent));
    }

    public Splice removeElement(JsonText.Arr array, int index) {
        List<JsonText.Value> elements = array.elements();
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
     * The text with {@code splices} made, each placed by where it was in the source text.
     *
     * @throws IllegalArgumentException when two splices overlap
     */
    public String apply(List<Splice> splices) {
        List<Splice> fromLast = new ArrayList<>(splices);
        fromLast.sort(Comparator.comparingInt(Splice::start).reversed());
        StringBuilder edited = new StringBuilder(source.text());
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

    private boolean isMultiline(JsonText.Value value) {
        return source.textOf(value).indexOf('\n') >= 0;
    }

    // The blanks before the first character on the line holding position.
    private String indentOfLine(int position) {
        String text = source.text();
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

    /** Replaces the text from {@code start} up to {@code end} with {@code replacement}. */
    public record Splice(int start, int end, String replacement) {
        public Splice {
            Objects.requireNonNull(replacement, "replacement");
            if (start < 0 || end < start) {
                throw new IllegalArgumentException("A splice runs forwards from 0, not " + start + " to " + end);
            }
        }
    }
}
