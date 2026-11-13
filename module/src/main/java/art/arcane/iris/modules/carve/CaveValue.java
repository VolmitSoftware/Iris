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

package art.arcane.iris.modules.carve;

import com.volmit.config.Desc;
import com.volmit.config.JsonForms;
import com.volmit.noise.NoiseStyle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A number that shapes how a cave looks, which may vary across the world: {@code 5} everywhere,
 * {@code {"min": 3, "max": 9}} picked by a smooth noise the cave chooses for the key, or
 * {@code {"min": 3, "max": 9, "style": {...}}} picked by that style, its zoom as written. The noise's low end gives
 * min and its high end max.
 */
@Desc("A number, or {min, max} that varies across the world; add a style to decide where.")
@JsonForms(CaveValue.Forms.class)
public final class CaveValue {
    private final Object written;
    private final double min;
    private final double max;
    private final NoiseStyle style;

    private CaveValue(Object written, double min, double max, NoiseStyle style) {
        this.written = written;
        this.min = min;
        this.max = max;
        this.style = style;
    }

    public static CaveValue of(double value) {
        return new CaveValue(value, value, value, null);
    }

    public static CaveValue between(double min, double max) {
        return new CaveValue(Map.of(Forms.MIN, min, Forms.MAX, max), min, max, null);
    }

    /** Whether the value differs from place to place; a plain number, or a min equal to max, does not. */
    public boolean varies() {
        return min != max;
    }

    /** The value where the noise is lowest; the value everywhere when it does not vary. */
    public double min() {
        return min;
    }

    /** The value where the noise is highest. */
    public double max() {
        return max;
    }

    public double middle() {
        return (min + max) / 2D;
    }

    public double lowest() {
        return Math.min(min, max);
    }

    public double highest() {
        return Math.max(min, max);
    }

    /** The noise the pack chose to pick the value; empty where it wrote none and the cave picks a smooth one. */
    public Optional<NoiseStyle> style() {
        return Optional.ofNullable(style);
    }

    /** Adds to {@code problems} each end of the value outside {@code bounds}, naming it {@code path} as written. */
    void requireWithin(Bounds bounds, String path, List<String> problems) {
        if (!varies()) {
            requireWithin(bounds, path, min, problems);
            return;
        }
        requireWithin(bounds, path + "." + Forms.MIN, min, problems);
        requireWithin(bounds, path + "." + Forms.MAX, max, problems);
    }

    private static void requireWithin(Bounds bounds, String path, double value, List<String> problems) {
        if (value < bounds.least()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at least " + shortNumber(bounds.least()));
        } else if (value > bounds.most()) {
            problems.add(path + " is " + shortNumber(value) + "; it must be at most " + shortNumber(bounds.most()));
        }
    }

    private static String shortNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    @Override
    public String toString() {
        return String.valueOf(written);
    }

    /** The numbers a key allows, both included. */
    record Bounds(double least, double most) {
    }

    /** Reads the forms a pack writes and writes a value back exactly as it was read. */
    static final class Forms implements JsonForms.Codec<CaveValue> {
        static final String MIN = "min";
        static final String MAX = "max";
        static final String STYLE = "style";
        private static final Set<String> MEMBERS = Set.of(MIN, MAX, STYLE);

        @Override
        public CaveValue read(Object json, JsonForms.Parts parts) {
            return switch (json) {
                case Number number -> new CaveValue(json, number.doubleValue(), number.doubleValue(), null);
                case Map<?, ?> object -> varying(object, parts);
                default -> throw new IllegalArgumentException("A cave value is a number or {min, max} with an optional style, not " + json);
            };
        }

        @Override
        public Object write(CaveValue value) {
            return value.written;
        }

        private static CaveValue varying(Map<?, ?> object, JsonForms.Parts parts) {
            for (Object member : object.keySet()) {
                if (!MEMBERS.contains(member)) {
                    throw new IllegalArgumentException("A varying cave value has min, max and style, not " + member);
                }
            }
            if (!object.containsKey(MIN) || !object.containsKey(MAX)) {
                throw new IllegalArgumentException("A varying cave value needs both min and max: " + object);
            }
            Object writtenStyle = object.get(STYLE);
            NoiseStyle style = writtenStyle == null ? null : parts.read(writtenStyle, NoiseStyle.class);
            return new CaveValue(object, number(object.get(MIN), MIN), number(object.get(MAX), MAX), style);
        }

        private static double number(Object written, String name) {
            if (written instanceof Number number) {
                return number.doubleValue();
            }
            throw new IllegalArgumentException(name + " needs a number, not " + written);
        }
    }
}
