package com.volmit.noise;

import com.volmit.config.Desc;
import com.volmit.config.Snippet;
import java.util.Objects;

/** A number between {@code min} and {@code max} that a noise style picks per column, as packs write it:
 * {@code {"min": 4, "max": 12, "style": {"style": "SIMPLEX"}}}, or a snippet under {@code snippet/style-range/}.
 * A value the host decodes as every settings class (see DESIGN.md, "Decoding contract").
 */
@Snippet("style-range")
@Desc("A number between min and max that noise picks per column")
public final class StyledRange {
    private static final String DEFAULT_STYLE = "STATIC";

    @Desc("The lowest value.")
    private double min = 16;

    @Desc("The highest value.")
    private double max = 32;

    @Desc("The noise that picks the value in each column.")
    private NoiseStyle style = NoiseStyle.of(DEFAULT_STYLE);

    private StyledRange() {
    }

    private StyledRange(double min, double max, NoiseStyle style) {
        this.min = min;
        this.max = max;
        this.style = Objects.requireNonNull(style, "style");
    }

    public static StyledRange of(double min, double max, NoiseStyle style) {
        return new StyledRange(min, max, style);
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** The style; {@code STATIC} where the pack wrote none. */
    public NoiseStyle style() {
        return style;
    }

    /** Whether every column gets the same value: the bounds are equal or the style is flat. */
    public boolean isFlat() {
        return min == max || style.isFlat();
    }

    /** The value at column {@code worldX}, {@code worldZ}: {@code min} when the bounds are equal, halfway between
     * them when the style is flat, otherwise {@code min + (max - min) * noise} of the style's noise seeded with
     * {@code seed}.
     */
    public double at(NoiseFactory noise, long seed, double worldX, double worldZ) {
        if (min == max) {
            return min;
        }
        if (style.isFlat()) {
            return (min + max) / 2;
        }
        return min + (max - min) * noise.sampler(style, seed).sample(worldX, worldZ);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StyledRange that && Double.compare(min, that.min) == 0
                && Double.compare(max, that.max) == 0 && style.equals(that.style);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, style);
    }

    @Override
    public String toString() {
        return "StyledRange[" + min + ".." + max + ", " + style + "]";
    }
}
