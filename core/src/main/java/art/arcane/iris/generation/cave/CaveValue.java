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

package art.arcane.iris.generation.cave;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.schema.annotation.AcceptsPlainNumber;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.documentation.Description;

import java.util.Optional;

/**
 * A number that shapes how a cave looks, which may vary across the world: {@code 5} everywhere,
 * {@code {"min": 3, "max": 9}} picked by a smooth noise the cave chooses for the key, or
 * {@code {"min": 3, "max": 9, "style": {...}}} picked by that style, its zoom as written. The noise's low end gives
 * min and its high end max.
 */
@AcceptsPlainNumber
@Description("A number, or {min, max} that varies across the world; add a style to decide where.")
public final class CaveValue {
    @Required
    @Description("The value where the noise is lowest.")
    private double min;

    @Required
    @Description("The value where the noise is highest.")
    private double max;

    @Description("The noise that decides where the value is low or high, its zoom as written. Leave out and the cave picks a smooth noise for this key.")
    private IrisGeneratorStyle style;

    public CaveValue() {
    }

    private CaveValue(double min, double max, IrisGeneratorStyle style) {
        this.min = min;
        this.max = max;
        this.style = style;
    }

    public static CaveValue of(double value) {
        return new CaveValue(value, value, null);
    }

    public static CaveValue between(double min, double max) {
        return new CaveValue(min, max, null);
    }

    public static CaveValue between(double min, double max, IrisGeneratorStyle style) {
        return new CaveValue(min, max, style);
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
    public Optional<IrisGeneratorStyle> style() {
        return Optional.ofNullable(style);
    }

    boolean isWrittenAsPlainNumber() {
        return style == null && min == max;
    }

    @Override
    public String toString() {
        return isWrittenAsPlainNumber() ? Double.toString(min) : "{min " + min + ", max " + max + (style == null ? "" : ", style " + style) + "}";
    }
}
