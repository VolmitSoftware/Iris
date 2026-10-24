package com.volmit.block;

import com.volmit.config.Desc;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A palette of blocks as packs write it in settings, such as
 * {@code {"style": "SIMPLEX", "zoom": 8, "palette": [{"block": "stone"}, {"block": "andesite", "weight": 2}]}}, or a
 * snippet under {@code snippet/palette/}. The blocks are picked by noise: the host picks the entry the style's noise
 * at the position over {@code zoom} selects, each block counting {@link BlockSpec#weight() weight} times.
 *
 * <p>A palette is a description, not a picker: the host builds the picker
 * ({@link BlockRegistry#palette(PaletteSpec, long)}). It is a value the host creates with the no-argument
 * constructor and reads the JSON into; nothing changes it afterwards.
 */
@Snippet("palette")
@Desc("A palette of materials")
public final class PaletteSpec {
    private static final String DEFAULT_STYLE = "STATIC";
    private static final double DEFAULT_ZOOM = 5;
    private static final String DEFAULT_BLOCK = "stone";

    @Desc("The style of noise")
    private NoiseStyle style = NoiseStyle.of(DEFAULT_STYLE);

    @MinNumber(0.0001)
    @Desc("The terrain zoom mostly for zooming in on a wispy palette")
    private double zoom = DEFAULT_ZOOM;

    @Required
    @MinItems(1)
    @Desc("The palette of blocks to be used in this layer")
    private List<BlockSpec> palette = List.of(BlockSpec.of(DEFAULT_BLOCK));

    private PaletteSpec() {
    }

    private PaletteSpec(NoiseStyle style, double zoom, List<BlockSpec> palette) {
        this.style = Objects.requireNonNull(style, "style");
        this.zoom = zoom;
        this.palette = List.copyOf(palette);
    }

    /** A palette of {@code blocks} with the default style and zoom. */
    public static PaletteSpec of(List<BlockSpec> blocks) {
        return new PaletteSpec(NoiseStyle.of(DEFAULT_STYLE), DEFAULT_ZOOM, blocks);
    }

    public PaletteSpec withStyle(NoiseStyle style) {
        return new PaletteSpec(style, zoom, palette());
    }

    /** This palette with zoom {@code zoom}: above 1 stretches the noise that picks the blocks, below 1 squeezes it. */
    public PaletteSpec withZoom(double zoom) {
        return new PaletteSpec(style(), zoom, palette());
    }

    /** The noise that picks the blocks; the default {@code STATIC} style where the pack wrote none. */
    public NoiseStyle style() {
        return style == null ? NoiseStyle.of(DEFAULT_STYLE) : style;
    }

    /** The factor the position is divided by before it is looked up in the noise. */
    public double zoom() {
        return zoom;
    }

    /** The blocks to pick from, as the pack wrote them. */
    public List<BlockSpec> palette() {
        return palette == null ? List.of() : Collections.unmodifiableList(palette);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PaletteSpec spec && style().equals(spec.style())
                && Double.compare(zoom, spec.zoom) == 0 && palette().equals(spec.palette());
    }

    @Override
    public int hashCode() {
        return Objects.hash(style(), zoom, palette());
    }

    @Override
    public String toString() {
        return "PaletteSpec[" + palette() + ", " + style() + ", zoom " + zoom + "]";
    }
}
