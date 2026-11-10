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

package art.arcane.iris.core.pack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Positions in the text of a JSON file as Iris reads it, with comments and trailing commas, so a file can be edited
 * in place without rewriting the formatting around the edit.
 */
final class PackJsonText {
    private PackJsonText() {
    }

    /** A member of an object: the key and where its value starts and ends in the text. */
    record Member(String key, int keyStart, int valueStart, int valueEnd) {
    }

    /** A value inside an array: where it starts and ends in the text. */
    record Item(int start, int end) {
    }

    /** A replacement of the text from {@code start} up to {@code end}. */
    record Edit(int start, int end, String replacement) {
    }

    static List<Member> members(String text, int objectStart) {
        if (text.charAt(objectStart) != '{') {
            throw new IllegalArgumentException("An object was expected at " + objectStart);
        }

        List<Member> members = new ArrayList<>();
        int index = skipBlank(text, objectStart + 1);
        while (index < text.length() && text.charAt(index) != '}') {
            if (text.charAt(index) == ',') {
                index = skipBlank(text, index + 1);
                continue;
            }

            if (text.charAt(index) != '"') {
                throw new IllegalArgumentException("A key was expected at " + index);
            }

            int keyEnd = stringEnd(text, index);
            int colon = skipBlank(text, keyEnd + 1);
            int valueStart = skipBlank(text, colon + 1);
            int valueEnd = valueEnd(text, valueStart);
            members.add(new Member(text.substring(index + 1, keyEnd), index, valueStart, valueEnd));
            index = skipBlank(text, valueEnd);
        }
        return members;
    }

    static Member member(List<Member> members, String key) {
        return members.stream().filter(member -> member.key().equals(key)).findFirst().orElse(null);
    }

    static List<Item> items(String text, int arrayStart) {
        if (text.charAt(arrayStart) != '[') {
            throw new IllegalArgumentException("An array was expected at " + arrayStart);
        }

        List<Item> items = new ArrayList<>();
        int index = skipBlank(text, arrayStart + 1);
        while (index < text.length() && text.charAt(index) != ']') {
            if (text.charAt(index) == ',') {
                index = skipBlank(text, index + 1);
                continue;
            }

            int end = valueEnd(text, index);
            items.add(new Item(index, end));
            index = skipBlank(text, end);
        }
        return items;
    }

    /** The text of the file with every edit applied; the edits must not overlap. */
    static String apply(String text, List<Edit> edits) {
        StringBuilder edited = new StringBuilder(text);
        edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed())
                .forEach(edit -> edited.replace(edit.start(), edit.end(), edit.replacement()));
        return edited.toString();
    }

    /** The edit that takes the member out, together with its comma and, when it stands alone there, its lines. */
    static Edit removal(String text, Member member) {
        int start = member.keyStart();
        int end = member.valueEnd();
        int after = skipBlank(text, end);
        if (after < text.length() && text.charAt(after) == ',') {
            end = after + 1;
        } else {
            int before = start - 1;
            while (before >= 0 && Character.isWhitespace(text.charAt(before))) {
                before--;
            }
            if (before >= 0 && text.charAt(before) == ',') {
                start = before;
            }
        }

        int lineStart = text.lastIndexOf('\n', start - 1) + 1;
        int lineEnd = text.indexOf('\n', end);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }

        boolean alone = text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank();
        if (!alone) {
            return new Edit(start, end, "");
        }
        return new Edit(lineStart, Math.min(lineEnd + 1, text.length()), "");
    }

    /** The whitespace the line holding {@code position} starts with. */
    static String indentAt(String text, int position) {
        int lineStart = text.lastIndexOf('\n', position - 1) + 1;
        int index = lineStart;
        while (index < position && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
        return text.substring(lineStart, index);
    }

    static int skipBlank(String text, int index) {
        int position = index;
        while (position < text.length()) {
            if (Character.isWhitespace(text.charAt(position))) {
                position++;
            } else if (isComment(text, position)) {
                position = commentEnd(text, position);
            } else {
                break;
            }
        }
        return position;
    }

    static int valueEnd(String text, int start) {
        char first = text.charAt(start);
        if (first == '"') {
            return stringEnd(text, start) + 1;
        }

        if (first != '[' && first != '{') {
            return literalEnd(text, start);
        }

        int depth = 0;
        int index = start;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '"') {
                index = stringEnd(text, index);
            } else if (isComment(text, index)) {
                index = commentEnd(text, index) - 1;
            } else if (current == '[' || current == '{') {
                depth++;
            } else if (current == ']' || current == '}') {
                depth--;
                if (depth == 0) {
                    return index + 1;
                }
            }
            index++;
        }
        throw new IllegalArgumentException("The value at " + start + " is not closed");
    }

    private static int literalEnd(String text, int start) {
        int index = start;
        while (index < text.length() && ",]} \t\r\n".indexOf(text.charAt(index)) < 0 && !isComment(text, index)) {
            index++;
        }
        return index;
    }

    private static int stringEnd(String text, int quote) {
        int index = quote + 1;
        while (index < text.length() && text.charAt(index) != '"') {
            index += text.charAt(index) == '\\' ? 2 : 1;
        }
        if (index >= text.length()) {
            throw new IllegalArgumentException("The string at " + quote + " is not closed");
        }
        return index;
    }

    private static boolean isComment(String text, int index) {
        return text.startsWith("//", index) || text.startsWith("/*", index);
    }

    private static int commentEnd(String text, int start) {
        if (text.startsWith("//", start)) {
            int lineEnd = text.indexOf('\n', start);
            return lineEnd < 0 ? text.length() : lineEnd;
        }

        int close = text.indexOf("*/", start + 2);
        if (close < 0) {
            throw new IllegalArgumentException("The comment at " + start + " is not closed");
        }
        return close + 2;
    }
}
