package com.volmit.noise;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Snippet;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import java.util.Objects;
import java.util.Optional;

/** How noise is shaped, as packs write it: {@code {"style": "SIMPLEX", "zoom": 4}}, or a snippet under
 * {@code snippet/style/}. The style names one of the host's noise generators ({@link Reference#NOISE_STYLE}); an
 * expression or image map of the pack replaces it. A zoom stretches the noise, an exponent bends it, a fracture
 * distorts its coordinates and a cellular frequency turns it into cells.
 *
 * <p>A style is a description, not noise: the host builds the noise ({@link NoiseFactory#sampler}). It is a value
 * the host decodes as every settings class (see DESIGN.md, "Decoding contract"); nothing changes it afterwards.
 */
@Snippet("style")
@Desc("A noise style: one of the host's noise generators, or an expression or image map of the pack, shaped by zoom, exponent, fracture and cells")
public final class NoiseStyle {
    /** The style without noise, the default. */
    public static final String FLAT = "FLAT";

    @Ref(Reference.NOISE_STYLE)
    @Desc("The noise generator. Used when neither expression nor imageMap is set, and when an expression fails.")
    private String style = FLAT;

    @Desc("Above 0, the noise is turned into cells of this frequency.")
    private double cellularFrequency = 0;

    @Desc("How far the cells are stretched.")
    private double cellularZoom = 1;

    @MinNumber(0.00001)
    @Desc("How far the noise is stretched: above 1 wider, below 1 tighter.")
    private double zoom = 1;

    @Ref(kind = PackExpression.class)
    @Desc("An expression of the pack that gives the noise instead of the style.")
    private ResourceId expression;

    @Ref(kind = PackImageMap.class)
    @Desc("An image map of the pack that gives the noise instead of the style, when there is no expression.")
    private ResourceId imageMap;

    @MinNumber(0.00001)
    @Desc("When this style moves coordinates, as another style's fracture or as a warp: up to half this many blocks either way.")
    private double multiplier = 1;

    @Desc("A style whose noise moves the coordinates this style is sampled at, distorting it.")
    private NoiseStyle fracture;

    @MinNumber(0.01562)
    @MaxNumber(64)
    @Desc("The power the noise is raised to: above 1 favours low values, below 1 high ones.")
    private double exponent = 1;

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
        return style;
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

    /** The pack expression that replaces the base style, by the id the pack gives its key; see {@link PackResources#EXPRESSIONS}. */
    public Optional<ResourceId> expression() {
        return Optional.ofNullable(expression);
    }

    /** The pack image map that replaces the base style when there is no expression; see {@link PackResources#IMAGE_MAPS}. */
    public Optional<ResourceId> imageMap() {
        return Optional.ofNullable(imageMap);
    }

    /** How far this style moves coordinates when it moves them, as another style's {@link #fracture} or as a warp: up
     * to half this many blocks either way.
     */
    public double multiplier() {
        return multiplier;
    }

    /** The style whose noise moves the coordinates this style is sampled at, by its own {@link #multiplier}. */
    public Optional<NoiseStyle> fracture() {
        return Optional.ofNullable(fracture);
    }

    public double exponent() {
        return exponent;
    }

    /** Whether the style gives no noise: the {@link #FLAT} base style without an expression or image map. */
    public boolean isFlat() {
        return FLAT.equals(style) && expression == null && imageMap == null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NoiseStyle that && style.equals(that.style)
                && Double.compare(cellularFrequency, that.cellularFrequency) == 0
                && Double.compare(cellularZoom, that.cellularZoom) == 0 && Double.compare(zoom, that.zoom) == 0
                && Objects.equals(expression, that.expression) && Objects.equals(imageMap, that.imageMap)
                && Double.compare(multiplier, that.multiplier) == 0 && Objects.equals(fracture, that.fracture)
                && Double.compare(exponent, that.exponent) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(style, cellularFrequency, cellularZoom, zoom, expression, imageMap, multiplier, fracture, exponent);
    }

    @Override
    public String toString() {
        return "NoiseStyle[" + style + expression().map(key -> ", expression " + key).orElse("")
                + imageMap().map(key -> ", image map " + key).orElse("") + ", zoom " + zoom + "]";
    }
}
