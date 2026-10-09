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

package art.arcane.iris.builtin.floating;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.terrain.IrisBiomePaletteLayer;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Snippet;
import com.volmit.noise.NoiseStyle;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A floating island above a biome's terrain, an entry of the biome's {@code floatingChildBiomes}: where its footprint
 * is, how high it floats, how its top, underside and walls are shaped, and which objects, decorators and water it
 * gets. Noise and options are IrisApi types; the object placements and palette layers the island hosts are
 * Iris's own classes, which the host describes itself, so this class leaves the pack module jar only when those
 * become settings of modules.
 */
@Snippet("floating-child-biome")
@Desc("Declares a floating biome layer above this biome's terrain. A 2D footprint noise decides which columns are part of the island (threshold + style control blankets, swirls, scattered blobs). The top profile is driven by the target biome's own terrain generators (so a mountains biome produces real peaks, desert produces real dunes). The bottom tail hanging below is a separately configurable noise (pick VASCULAR for drippy roots, FRACTAL_RM_SIMPLEX for crystalline spikes, PERLIN for smooth rounded bowls).")
public final class FloatingChildBiome {
    public static final int DEFAULT_EDGE_TAPER_WIDTH = 10;
    public static final double DEFAULT_EDGE_TAPER_EXPONENT = 1.0D;
    private static final NoiseStyle DEFAULT_BOTTOM_STYLE = NoiseStyle.of("SIMPLEX");
    private static final NoiseStyle DEFAULT_TOP_SHAPE_STYLE = NoiseStyle.of("SIMPLEX");
    private static final NoiseStyle DEFAULT_ALTITUDE_STYLE = NoiseStyle.of("SIMPLEX");
    private static final NoiseStyle DEFAULT_PICKER_STYLE = NoiseStyle.of("SIMPLEX");
    private static final NoiseStyle DEFAULT_EDGE_TAPER_VARIATION_STYLE = NoiseStyle.of("SIMPLEX").withZoom(0.18D);
    private static final NoiseStyle DEFAULT_FOOTPRINT_STYLE = NoiseStyle.of("SIMPLEX");

    @Ref(Reference.PACK_BIOME)
    @Desc("The target biome whose visual design (layers, palette, decorators, surface objects, derivative, and — when topShapeMode=BIOME — generator profile) drives the floating island. Leave empty to reuse the parent biome (self).")
    private String biome = "";

    @MinNumber(1)
    @MaxNumber(512)
    @Desc("Selection rarity when multiple floating child entries are defined on one parent biome. Lower is more common.")
    private int rarity = 1;

    @Desc("2D noise that decides which columns are part of this island. Set feature size via the style's own zoom field (e.g. {\"style\":\"CELLULAR\",\"zoom\":0.3} for ~30-block shards, {\"style\":\"SIMPLEX\",\"zoom\":1.0} for ~100-block blobs). Pick SIMPLEX for smooth blobs, CELLULAR for angular shards, VASCULAR for vein/branch strips, FRACTAL_FBM_SIMPLEX for large irregular blanket regions. Fracture (domain warp) this to get swirly silhouettes.")
    private NoiseStyle footprintStyle = DEFAULT_FOOTPRINT_STYLE;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Coverage threshold (0..1). Roughly the fraction of the world that is NOT island: 0.0 = every column becomes island, 0.5 ≈ 50% of columns, 0.8 ≈ sparse scattered ~20% coverage, 1.0 = no islands at all. Values near 0.5 feel most natural.")
    private double footprintThreshold = 0.5;

    @MinNumber(2)
    @MaxNumber(32)
    @Desc("Width in blocks of the rounded transition from the floating island contour to its full thickness. Smaller values make a tighter edge; larger values make a broader rounded underside.")
    private int edgeTaperWidth = DEFAULT_EDGE_TAPER_WIDTH;

    @MinNumber(0.25)
    @MaxNumber(4)
    @Desc("Shape exponent for the rounded edge. Values below 1 fill into a softer, fuller curve; values above 1 keep the rim thinner before rising into the island interior.")
    private double edgeTaperExponent = DEFAULT_EDGE_TAPER_EXPONENT;

    @Desc("Broad 2D noise that organically varies the rounded edge without changing the island footprint. Low-frequency SIMPLEX or PERLIN styles give coherent natural contours; high-frequency or cellular styles can look speckled.")
    private NoiseStyle edgeTaperVariationStyle = DEFAULT_EDGE_TAPER_VARIATION_STYLE;

    @MinNumber(0)
    @MaxNumber(8)
    @Desc("Maximum number of blocks by which edgeTaperVariationStyle locally widens or narrows the rounded transition. 0 disables variation. Runtime keeps the resulting local width between 2 and 32 blocks so every protected rim column remains connected.")
    private double edgeTaperVariationAmplitude = 0.0D;

    @Desc("Picker noise used when multiple floating child entries exist. Samples once per column to deterministically choose which entry's footprint is tested there. Use a large style zoom (e.g. zoom: 4 for ~400-block regions) so each entry owns broad coherent areas.")
    private NoiseStyle pickerStyle = DEFAULT_PICKER_STYLE;

    @Desc("Altitude noise — varies the base platform Y across one island so it isn't a flat plane. Use a large style zoom (e.g. zoom: 2 for ~200-block altitude patches) so an island sits at roughly one altitude.")
    private NoiseStyle altitudeStyle = DEFAULT_ALTITUDE_STYLE;

    @MinNumber(0)
    @MaxNumber(2032)
    @Desc("Minimum absolute world Y where the island base can sit. Island altitude is independent of the parent biome's terrain height; altitudeStyle noise varies the base between min and max per column.")
    private int minHeightAboveSurface = 160;

    @MinNumber(0)
    @MaxNumber(2032)
    @Desc("Maximum absolute world Y where the island base can sit. Island altitude is independent of the parent biome's terrain height; altitudeStyle noise varies the base between min and max per column.")
    private int maxHeightAboveSurface = 210;

    @Desc("Optional absolute minimum world Y for the island base. When set, baseY is clamped upward so the tail bottom stays above this value.")
    private Integer minAbsoluteY = null;

    @Desc("Optional absolute maximum world Y for the island top. When set, the top is clamped downward.")
    private Integer maxAbsoluteY = null;

    @Desc("How the top profile of the island is shaped. BIOME = evaluate target biome's own generators (mountains biome -> real mountains). NOISE = use topShapeStyle as a user-controlled heightmap. FLAT = constant maxTopHeight slab.")
    private TopShapeMode topShapeMode = TopShapeMode.BIOME;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("Maximum top profile height in blocks above the island base. Caps how tall the biome terrain can grow on top.")
    private int maxTopHeight = 40;

    @Desc("Used only when topShapeMode=NOISE. 2D noise driving the top heightmap. Set feature scale via the style's zoom field (small zoom = rugged peaks, large zoom = broad rolling shapes).")
    private NoiseStyle topShapeStyle = DEFAULT_TOP_SHAPE_STYLE;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Amplitude multiplier applied to the NOISE top profile. 0 = no top (flat at base), 1 = full maxTopHeight range.")
    private double topShapeAmp = 1.0;

    @Desc("2D noise driving the bottom tail hanging below the island base. VASCULAR = drippy organic roots. FRACTAL_RM_SIMPLEX = crystalline spikes. CELLULAR = jagged chunks. PERLIN = smooth rounded bowl. SIMPLEX = gentle lobes. Set feature scale via the style's zoom field.")
    private NoiseStyle bottomStyle = DEFAULT_BOTTOM_STYLE;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("Minimum blocks below the base where the tail extends.")
    private int bottomDepthMin = 4;

    @MinNumber(0)
    @MaxNumber(512)
    @Desc("Maximum blocks below the base where the tail extends.")
    private int bottomDepthMax = 20;

    @MinNumber(0.1)
    @MaxNumber(8)
    @Desc("Power curve applied to the bottom noise before mapping to depth. >1 = most columns shallow with occasional deeper spikes (sparse roots). <1 = most columns deep with occasional shallow spots (dense curtains). 1.0 = linear.")
    private double bottomExponent = 1.0;

    @Desc("Controls the material palette near the island underside. DEPTH keeps the old top-down depth behavior. MIRROR_TOP uses the target biome's shallow/top palette from the underside upward. CUSTOM uses bottomPalette near the underside while keeping the target biome palette near the top.")
    private FloatingBottomPaletteMode bottomPaletteMode = FloatingBottomPaletteMode.DEPTH;

    @MinItems(1)
    @Desc("Custom palette layers used near the underside when bottomPaletteMode=CUSTOM. The layer format is the same as normal biome layers.")
    private List<IrisBiomePaletteLayer> bottomPalette = List.of();

    @MinNumber(1)
    @MaxNumber(512)
    @Desc("Hard cap on the total Y-extent (top minus bottom) of a single island column. Safety limit.")
    private int maxThickness = 96;

    @Desc("Optional 3D noise that shifts the footprint's XZ sample position per Y layer — naturalizes the walls so they stop looking like a straight extrusion of the 2D footprint. Leave null to disable and keep straight vertical walls. Good defaults: {\"style\":\"SIMPLEX\",\"zoom\":0.25} for gentle undulation, {\"style\":\"FRACTAL_FBM_SIMPLEX\",\"zoom\":0.4} for craggier walls.")
    private NoiseStyle wallWarpStyle;

    @MinNumber(0)
    @MaxNumber(64)
    @Desc("Amplitude in blocks of the per-layer XZ shift applied when wallWarpStyle is set. 0 = no warp (straight walls). 4..8 = gentle naturalization. 16+ = heavily meandering walls. Ignored when wallWarpStyle is null.")
    private double wallWarpAmplitude = 6.0;

    @Desc("Optional 3D noise that swiss-cheeses the island interior by marking individual blocks as air when the noise exceeds carveThreshold. Leave null to keep the island solid. Good defaults: {\"style\":\"CELLULAR\",\"zoom\":0.3} for bubble pockets, {\"style\":\"VASCULAR\",\"zoom\":0.25} for wormy tunnels.")
    private NoiseStyle carveStyle;

    @Desc("Optional carving biome key or dimension carving entry id whose caveProfile drives floating-island internal air pockets. Dimension carving ids resolve first; biome keys such as carving/mushroom resolve second. When set, this overrides carveStyle while carveThreshold remains a final tuning bias.")
    private String carving = "";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Threshold (0..1) above which carveStyle noise carves air pockets. 1.0 = no direct-noise carving. 0.75 = sparse pockets. 0.55 = heavy swiss-cheese. 0.4 = shredded lattice. When carving is set, lower values add a final positive threshold bias to the referenced caveProfile.")
    private double carveThreshold = 1.0;

    @Desc("Optional water surface height above the island base, in blocks. null = no internal water. Positive = water fills any dip in the top profile up to baseY + localFluidHeight (forms lakes/ponds in concavities of the biome-top heightmap).")
    private Integer localFluidHeight = null;

    @Desc("Block used for the internal water pool when localFluidHeight is positive.")
    @Ref(Reference.BLOCK)
    private String fluidBlock = "minecraft:water";

    @Desc("When true, the target biome's decorators apply to the island's top surface.")
    private boolean inheritDecorators = true;

    @Desc("When true, the target biome's surface objects are placed on the island's top surface instead of the parent terrain.")
    private boolean inheritObjects = true;

    @MinNumber(0.01)
    @MaxNumber(1)
    @Desc("Uniform shrink factor applied to every object placed on this floating island (inherited, extra, and free-floating). 1.0 = native size, 0.5 = half size, 0.25 = quarter. Useful for making small floating biomes feel believable.")
    private double objectShrinkFactor = 1.0;

    @MinItems(1)
    @Desc("Additional object placements anchored to the island top.")
    private List<IrisObjectPlacement> extraObjects = List.of();

    @MinItems(1)
    @Desc("Additional object placements that float freely in air, independent of the island. Forced to ObjectPlaceMode.FLOATING.")
    private List<IrisObjectPlacement> floatingObjects = List.of();

    @Desc("Visualization color for this floating child in Iris Studio.")
    private String color = null;

    @Desc("Controls how topObjectOverrides are combined with the inherited surface objects from the target biome. INHERIT_ONLY (default) = behaves identically to before this field was added. MERGE = appends overrides after inherited objects. REPLACE = uses only overrides, ignoring all inherited objects.")
    private OverrideMode topObjectMode = OverrideMode.INHERIT_ONLY;

    @Desc("Controls how bottomObjectOverrides are combined. INHERIT_ONLY (default) = no bottom objects placed (there is no inherited bottom set). MERGE = same as REPLACE for bottom (no inherited source). REPLACE = uses bottomObjectOverrides list only.")
    private OverrideMode bottomObjectMode = OverrideMode.INHERIT_ONLY;

    @MinItems(1)
    @Desc("Object placements that override or supplement the inherited surface objects on the island TOP. Behaviour depends on topObjectMode. INHERIT_ONLY = this list is ignored. MERGE = appended after inherited. REPLACE = used instead of inherited.")
    private List<IrisObjectPlacement> topObjectOverrides = List.of();

    @MinItems(1)
    @Desc("Object placements anchored to the island BOTTOM face. Each entry is auto-inverted 180 degrees around the X axis and placed flush against the lowest solid face of the island, so objects appear to hang upside-down from the underside. WARNING: directional blocks (stairs, doors, slabs) will not render correctly when flipped — use non-directional content (logs, leaves, stone, mycelium, ice, glass) for bottom placements.")
    private List<IrisObjectPlacement> bottomObjectOverrides = List.of();

    public FloatingChildBiome() {
    }

    public String biome() {
        return biome == null ? "" : biome;
    }

    public int rarity() {
        return rarity;
    }

    public NoiseStyle footprintStyle() {
        return footprintStyle == null ? DEFAULT_FOOTPRINT_STYLE : footprintStyle;
    }

    public double footprintThreshold() {
        return footprintThreshold;
    }

    public int edgeTaperWidth() {
        return edgeTaperWidth;
    }

    public double edgeTaperExponent() {
        return edgeTaperExponent;
    }

    public NoiseStyle edgeTaperVariationStyle() {
        return edgeTaperVariationStyle == null ? DEFAULT_EDGE_TAPER_VARIATION_STYLE : edgeTaperVariationStyle;
    }

    public double edgeTaperVariationAmplitude() {
        return edgeTaperVariationAmplitude;
    }

    public NoiseStyle pickerStyle() {
        return pickerStyle == null ? DEFAULT_PICKER_STYLE : pickerStyle;
    }

    public NoiseStyle altitudeStyle() {
        return altitudeStyle == null ? DEFAULT_ALTITUDE_STYLE : altitudeStyle;
    }

    public int minHeightAboveSurface() {
        return minHeightAboveSurface;
    }

    public int maxHeightAboveSurface() {
        return maxHeightAboveSurface;
    }

    /** Empty where the pack sets none. */
    public OptionalInt minAbsoluteY() {
        return minAbsoluteY == null ? OptionalInt.empty() : OptionalInt.of(minAbsoluteY);
    }

    /** Empty where the pack sets none. */
    public OptionalInt maxAbsoluteY() {
        return maxAbsoluteY == null ? OptionalInt.empty() : OptionalInt.of(maxAbsoluteY);
    }

    public TopShapeMode topShapeMode() {
        return topShapeMode == null ? TopShapeMode.BIOME : topShapeMode;
    }

    public int maxTopHeight() {
        return maxTopHeight;
    }

    public NoiseStyle topShapeStyle() {
        return topShapeStyle == null ? DEFAULT_TOP_SHAPE_STYLE : topShapeStyle;
    }

    public double topShapeAmp() {
        return topShapeAmp;
    }

    public NoiseStyle bottomStyle() {
        return bottomStyle == null ? DEFAULT_BOTTOM_STYLE : bottomStyle;
    }

    public int bottomDepthMin() {
        return bottomDepthMin;
    }

    public int bottomDepthMax() {
        return bottomDepthMax;
    }

    public double bottomExponent() {
        return bottomExponent;
    }

    public FloatingBottomPaletteMode bottomPaletteMode() {
        return bottomPaletteMode == null ? FloatingBottomPaletteMode.DEPTH : bottomPaletteMode;
    }

    public List<IrisBiomePaletteLayer> bottomPalette() {
        return bottomPalette == null ? List.of() : Collections.unmodifiableList(bottomPalette);
    }

    public int maxThickness() {
        return maxThickness;
    }

    /** Empty where the pack sets none. */
    public Optional<NoiseStyle> wallWarpStyle() {
        return Optional.ofNullable(wallWarpStyle);
    }

    public double wallWarpAmplitude() {
        return wallWarpAmplitude;
    }

    /** Empty where the pack sets none. */
    public Optional<NoiseStyle> carveStyle() {
        return Optional.ofNullable(carveStyle);
    }

    public String carving() {
        return carving == null ? "" : carving;
    }

    public double carveThreshold() {
        return carveThreshold;
    }

    /** Empty where the pack sets none. */
    public OptionalInt localFluidHeight() {
        return localFluidHeight == null ? OptionalInt.empty() : OptionalInt.of(localFluidHeight);
    }

    public String fluidBlock() {
        return fluidBlock == null ? "" : fluidBlock;
    }

    public boolean inheritDecorators() {
        return inheritDecorators;
    }

    public boolean inheritObjects() {
        return inheritObjects;
    }

    public double objectShrinkFactor() {
        return objectShrinkFactor;
    }

    public List<IrisObjectPlacement> extraObjects() {
        return extraObjects == null ? List.of() : Collections.unmodifiableList(extraObjects);
    }

    public List<IrisObjectPlacement> floatingObjects() {
        return floatingObjects == null ? List.of() : Collections.unmodifiableList(floatingObjects);
    }

    public String color() {
        return color == null ? "" : color;
    }

    public OverrideMode topObjectMode() {
        return topObjectMode == null ? OverrideMode.INHERIT_ONLY : topObjectMode;
    }

    public OverrideMode bottomObjectMode() {
        return bottomObjectMode == null ? OverrideMode.INHERIT_ONLY : bottomObjectMode;
    }

    public List<IrisObjectPlacement> topObjectOverrides() {
        return topObjectOverrides == null ? List.of() : Collections.unmodifiableList(topObjectOverrides);
    }

    public List<IrisObjectPlacement> bottomObjectOverrides() {
        return bottomObjectOverrides == null ? List.of() : Collections.unmodifiableList(bottomObjectOverrides);
    }
}
