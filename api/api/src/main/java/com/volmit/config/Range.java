package com.volmit.config;

import com.volmit.random.WorldRandom;
import java.util.Objects;

/** A span of numbers from {@code min} to {@code max}, both included, as packs write it: {@code {"min": 0, "max": 15}},
 * or a snippet under {@code snippet/range/}. An omitted bound keeps its default, so give a settings field its own
 * default with {@link #of}. A value the host decodes as every settings class (see DESIGN.md, "Decoding contract").
 */
@Snippet("range")
@Desc("A span of numbers from min to max, both included")
public final class Range {
    @Desc("The lowest value.")
    private double min = 16;

    @Desc("The highest value.")
    private double max = 32;

    private Range() {
    }

    private Range(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public static Range of(double min, double max) {
        return new Range(min, max);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** Whether {@code value} lies between the bounds, both included. */
    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    /** A number between the bounds drawn from {@code random}: {@code min + nextDouble() * (max - min)}, one draw,
     * or {@code min} without a draw when the bounds are equal.
     */
    public double pick(WorldRandom random) {
        if (min == max) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Range that && Double.compare(min, that.min) == 0 && Double.compare(max, that.max) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return "Range[" + min + ".." + max + "]";
    }
}
