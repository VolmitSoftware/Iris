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

package com.volmit.iris.util.nbt.tag;

import com.volmit.iris.engine.data.io.MaxDepthReachedException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all NBT tags.
 *
 * <h1>Nesting</h1>
 * <p>All methods serializing instances or deserializing data track the nesting levels to prevent
 * circular references or malicious data which could, when deserialized, result in thousands
 * of instances causing a denial of service.</p>
 *
 * <p>These methods have a parameter for the maximum nesting depth they are allowed to traverse. A
 * value of {@code 0} means that only the object itself, but no nested objects may be processed.
 * If an instance is nested further than allowed, a {@link MaxDepthReachedException} will be thrown.
 * Providing a negative maximum nesting depth will cause an {@code IllegalArgumentException}
 * to be thrown.</p>
 *
 * <p>Some methods do not provide a parameter to specify the maximum nesting depth, but instead use
 * {@link #DEFAULT_MAX_DEPTH}, which is also the maximum used by Minecraft. This is documented for
 * the respective methods.</p>
 *
 * <p>If custom NBT tags contain objects other than NBT tags, which can be nested as well, then there
 * is no guarantee that {@code MaxDepthReachedException}s are thrown for them. The respective class
 * will document this behavior accordingly.</p>
 *
 * @param <T> The type of the contained value
 */
public abstract class Tag<T> implements Cloneable {

    /**
     * The default maximum depth of the NBT structure.
     */
    public static final int DEFAULT_MAX_DEPTH = 512;

    private static final Map<String, String> ESCAPE_CHARACTERS;
    private static final Pattern ESCAPE_PATTERN = Pattern.compile("[\\\\\n\t\r\"]");
    private static final Pattern NON_QUOTE_PATTERN = Pattern.compile("[a-zA-Z0-9_\\-+]+");

    static {
        final Map<String, String> temp = new HashMap<>();
        temp.put("\\", "\\\\\\\\");
        temp.put("\n", "\\\\n");
        temp.put("\t", "\\\\t");
        temp.put("\r", "\\\\r");
        temp.put("\"", "\\\\\"");
        //noinspection Java9CollectionFactory
        ESCAPE_CHARACTERS = Collections.unmodifiableMap(temp);
    }

    private T value;

    /**
     * Initializes this Tag with some value. If the value is {@code null}, it will
     * throw a {@code NullPointerException}
     *
     * @param value The value to be set for this Tag.
     */
    public Tag(T value) {
        setValue(value);
    }

    /**
     * Escapes a string to fit into a JSON-like string representation for Minecraft
     * or to create the JSON string representation of a Tag returned from {@link Tag#toString()}
     *
     * @param s       The string to be escaped.
     * @param lenient {@code true} if it should force double quotes ({@code "}) at the start and
     *                the end of the string.
     * @return The escaped string.
     */
    @SuppressWarnings("StringBufferMayBeStringBuilder")
    protected static String escapeString(String s, @SuppressWarnings("SameParameterValue") boolean lenient) {
        StringBuffer sb = new StringBuffer();
        Matcher m = ESCAPE_PATTERN.matcher(s);
        while (m.find()) {
            m.appendReplacement(sb, ESCAPE_CHARACTERS.get(m.group()));
        }
        m.appendTail(sb);
        m = NON_QUOTE_PATTERN.matcher(s);
        if (!lenient || !m.matches()) {
            sb.insert(0, "\"").append("\"");
        }
        return sb.toString();
    }

    /**
     * @return This Tag's ID, usually used for serialization and deserialization.
     */
    public abstract byte getID();

    /**
     * @return The value of this Tag.
     */
    public T getValue() {
        return value;
    }

    /**
     * Sets the value for this Tag directly.
     *
     * @param value The value to be set.
     * @throws NullPointerException If the value is null
     */
    protected void setValue(T value) {
        this.value = checkValue(value);
    }

    /**
     * Checks if the value {@code value} is {@code null}.
     *
     * @param value The value to check
     * @return The parameter {@code value}
     * @throws NullPointerException If {@code value} was {@code null}
     */
    protected T checkValue(T value) {
        return Objects.requireNonNull(value);
    }

    /**
     * Calls {@link Tag#toString(int)} with an initial depth of {@code 0}.
     *
     * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
     * @see Tag#toString(int)
     */
    @Override
    public final String toString() {
        return toString(DEFAULT_MAX_DEPTH);
    }

    /**
     * Creates a string representation of this Tag in a valid JSON format.
     *
     * @param maxDepth The maximum nesting depth.
     * @return The string representation of this Tag.
     * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
     */
    public String toString(int maxDepth) {
        return "{\"type\":\"" + getClass().getSimpleName() + "\"," +
                "\"value\":" + valueToString(maxDepth) + "}";
    }

    /**
     * Calls {@link Tag#valueToString(int)} with {@link Tag#DEFAULT_MAX_DEPTH}.
     *
     * @return The string representation of the value of this Tag.
     * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
     */
    public String valueToString() {
        return valueToString(DEFAULT_MAX_DEPTH);
    }

    /**
     * Returns a JSON representation of the value of this Tag.
     *
     * @param maxDepth The maximum nesting depth.
     * @return The string representation of the value of this Tag.
     * @throws MaxDepthReachedException If the maximum nesting depth is exceeded.
     */
    public abstract String valueToString(int maxDepth);

    /**
     * Returns whether this Tag and some other Tag are equal.
     * They are equal if {@code other} is not {@code null} and they are of the same class.
     * Custom Tag implementations should overwrite this but check the result
     * of this {@code super}-method while comparing.
     *
     * @param other The Tag to compare to.
     * @return {@code true} if they are equal based on the conditions mentioned above.
     */
    @Override
    public boolean equals(Object other) {
        return other != null && getClass() == other.getClass();
    }

    /**
     * Calculates the hash code of this Tag. Tags which are equal according to {@link Tag#equals(Object)}
     * must return an equal hash code.
     *
     * @return The hash code of this Tag.
     */
    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /**
     * Creates a clone of this Tag.
     *
     * @return A clone of this Tag.
     */
    public abstract Tag<T> clone();
}
