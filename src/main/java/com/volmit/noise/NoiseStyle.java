package com.volmit.noise;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Snippet;
import java.util.Objects;
import java.util.Optional;

/** How noise is shaped, as packs write it: {@code {"style": "SIMPLEX", "zoom": 4}}, or a snippet under
 * {@code snippet/style/}. The style names one of the host's noise generators ({@link Reference#NOISE_STYLE}); an
 * expression or image map of the pack replaces it. A zoom stretches the noise, an exponent bends it, a fracture
 * style distorts its coordinates and a cellular frequency turns it into cells.
 *
 * <p>A style is a description, not noise: the host builds the noise ({@link NoiseFactory#sampler}). It is a value
 * the host creates with the no-argument constructor and reads the JSON into; nothing changes it afterwards.
 */
@Snippet("style")
@Desc("A noise style: one of the host's noise generators, or an expression or image map of the pack, shaped by zoom, exponent, fracture and cells")
public final class NoiseStyle {
    /** The style without noise, the default. */
    public static final String FLAT = "FLAT";

    @Ref(Reference.NOISE_STYLE)
    @Desc("The base noise style. Used when neither expression nor imageMap is set; a failed expression also falls back to this style.")
    private String style = FLAT;

    @Desc("If set above 0, this style will be cellularized")
    private double cellularFrequency = 0;

    @Desc("Cell zooms")
    private double cellularZoom = 1;

    @MinNumber(0.00001)
    @Desc("The zoom of this style")
    private double zoom = 1;

    @Ref(Reference.PACK_EXPRESSION)
    @Desc("Instead of using the style property, use a custom expression to represent this style.")
    private String expression;

    @Ref(Reference.PACK_IMAGE_MAP)
    @Desc("Use an Image map instead of a generated value")
    private String imageMap;

    @MinNumber(0.00001)
    @Desc("The Output multiplier. Only used if parent is fracture.")
    private double multiplier = 1;

    @Desc("Apply a generator to the coordinate field fed into this parent generator. I.e. Distort your generator with another generator.")
    private NoiseStyle fracture;

    @MinNumber(0.01562)
    @MaxNumber(64)
    @Desc("The exponent")
    private double exponent = 1;

    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("If the cache size is set above 0, this generator will be cached")
    private int cacheSize = 0;

    private NoiseStyle() {
    }

    private NoiseStyle(NoiseStyle copy) {
        style = copy.style;
        cellularFrequency = copy.cellularFrequency;
        cellularZoom = copy.cellularZoom;
        zoom = copy.zoom;
        expression = copy.expression;
        imageMap = copy.imageMap;
        multiplier = copy.multiplier;
        fracture = copy.fracture;
        exponent = copy.exponent;
        cacheSize = copy.cacheSize;
    }

    /** The host's noise style {@code style}, such as {@code SIMPLEX}, with every other option at its default. */
    public static NoiseStyle of(String style) {
        var created = new NoiseStyle();
        created.style = Objects.requireNonNull(style, "style");
        return created;
    }

    /** This style with zoom {@code zoom}: above 1 stretches the noise, below 1 squeezes it. */
    public NoiseStyle withZoom(double zoom) {
        var changed = new NoiseStyle(this);
        changed.zoom = zoom;
        return changed;
    }

    /** The base style's name; {@link #FLAT} where the pack wrote none. */
    public String style() {
        return style == null ? FLAT : style;
    }

    public double cellularFrequency() {
        return cellularFrequency;
    }

    public double cellularZoom() {
        return cellularZoom;
    }

    public double zoom() {
        return zoom;
    }

    /** The key of the pack expression that replaces the base style. */
    public Optional<String> expression() {
        return Optional.ofNullable(expression);
    }

    /** The key of the pack image map that replaces the base style when there is no expression. */
    public Optional<String> imageMap() {
        return Optional.ofNullable(imageMap);
    }

    public double multiplier() {
        return multiplier;
    }

    public Optional<NoiseStyle> fracture() {
        return Optional.ofNullable(fracture);
    }

    public double exponent() {
        return exponent;
    }

    public int cacheSize() {
        return cacheSize;
    }

    /** Whether the style gives no noise: the {@link #FLAT} base style without an expression or image map. */
    public boolean isFlat() {
        return FLAT.equals(style()) && expression == null && imageMap == null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NoiseStyle that && style().equals(that.style())
                && Double.compare(cellularFrequency, that.cellularFrequency) == 0
                && Double.compare(cellularZoom, that.cellularZoom) == 0 && Double.compare(zoom, that.zoom) == 0
                && Objects.equals(expression, that.expression) && Objects.equals(imageMap, that.imageMap)
                && Double.compare(multiplier, that.multiplier) == 0 && Objects.equals(fracture, that.fracture)
                && Double.compare(exponent, that.exponent) == 0 && cacheSize == that.cacheSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(style(), cellularFrequency, cellularZoom, zoom, expression, imageMap, multiplier, fracture,
                exponent, cacheSize);
    }

    @Override
    public String toString() {
        return "NoiseStyle[" + style() + expression().map(key -> ", expression " + key).orElse("")
                + imageMap().map(key -> ", image map " + key).orElse("") + ", zoom " + zoom + "]";
    }
}
