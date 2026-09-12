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

package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cave.IrisCaveProfileSampler;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.pack.value.OverrideMode;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectScale;

import art.arcane.volmlib.util.math.Rarity;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("floating-child-biome")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Description("Declares a floating biome layer above this biome's terrain. A 2D footprint noise decides which columns are part of the island (threshold + style control blankets, swirls, scattered blobs). The top profile is driven by the target biome's own terrain generators (so a mountains biome produces real peaks, desert produces real dunes). The bottom tail hanging below is a separately configurable noise (pick VASCULAR for drippy roots, FRACTAL_RM_SIMPLEX for crystalline spikes, PERLIN for smooth rounded bowls).")
@Data
public class IrisFloatingChildBiomes implements Rarity {
    private final transient AtomicCache<IrisBiome> resolvedBiome = new AtomicCache<>();
    private final transient AtomicCache<CNG> footprintCache = new AtomicCache<>();
    private final transient AtomicCache<FloatingIslandEdgeProfile> edgeTaperProfileCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> pickerCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> altitudeCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> topShapeCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> bottomCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> wallWarpCache = new AtomicCache<>();
    private final transient AtomicCache<CNG> carveCache = new AtomicCache<>();
    private final transient AtomicCache<IrisCaveProfileSampler> carvingProfileSamplerCache = new AtomicCache<>(true);
    private final transient AtomicCache<IrisObjectScale> shrinkScaleCache = new AtomicCache<>();

    public CNG getFootprintCng(long baseSeed, IrisData data) {
        return footprintCache.aquire(() -> getFootprintStyle().create(new RNG(baseSeed ^ 0xF007B17DL), data));
    }

    public FloatingIslandEdgeProfile getEdgeTaperProfile(long baseSeed, IrisData data) {
        return edgeTaperProfileCache.aquire(() -> {
            int width = FloatingIslandEdgeProfile.clampWidth(getEdgeTaperWidth());
            double amplitude = FloatingIslandEdgeProfile.clampVariationAmplitude(
                    getEdgeTaperVariationAmplitude(), width);
            IrisGeneratorStyle style = getEdgeTaperVariationStyle();
            CNG variation = amplitude > 0.0D && style != null
                    ? style.create(new RNG(baseSeed ^ 0xED6E7A9E5L), data)
                    : null;
            return new FloatingIslandEdgeProfile(width, getEdgeTaperExponent(), amplitude, variation);
        });
    }

    public CNG getPickerCng(long baseSeed, IrisData data) {
        return pickerCache.aquire(() -> getPickerStyle().create(new RNG(baseSeed ^ 0x91C4E72DL), data));
    }

    public CNG getAltitudeCng(long baseSeed, IrisData data) {
        return altitudeCache.aquire(() -> getAltitudeStyle().create(new RNG(baseSeed ^ 0xA17DEBBL), data));
    }

    public CNG getTopShapeCng(long baseSeed, IrisData data) {
        return topShapeCache.aquire(() -> getTopShapeStyle().create(new RNG(baseSeed ^ 0x70970601DEFL), data));
    }

    public CNG getBottomCng(long baseSeed, IrisData data) {
        return bottomCache.aquire(() -> getBottomStyle().create(new RNG(baseSeed ^ 0xB0770075CAFEL), data));
    }

    public CNG getWallWarpCng(long baseSeed, IrisData data) {
        IrisGeneratorStyle style = getWallWarpStyle();
        if (style == null) {
            return null;
        }
        return wallWarpCache.aquire(() -> style.create(new RNG(baseSeed ^ 0xA117BA17E0FL), data));
    }

    public CNG getCarveCng(long baseSeed, IrisData data) {
        IrisGeneratorStyle style = getCarveStyle();
        if (style == null) {
            return null;
        }
        return carveCache.aquire(() -> style.create(new RNG(baseSeed ^ 0xCA5EC1EE5EL), data));
    }

    public IrisCaveProfileSampler getCarvingProfileSampler(Engine engine, IrisData data) {
        if (!hasCarvingReference()) {
            return null;
        }

        return carvingProfileSamplerCache.aquire(() -> {
            IrisBiome resolved = resolveCarvingBiome(carving, engine, data);
            if (resolved == null || resolved.getCaveProfile() == null || !resolved.getCaveProfile().isEnabled()) {
                return null;
            }

            return new IrisCaveProfileSampler(engine, resolved.getCaveProfile());
        });
    }

    public boolean hasCarvingReference() {
        return carving != null && !carving.isBlank();
    }

    static IrisBiome resolveCarvingBiome(String carving, Engine engine, IrisData data) {
        if (carving == null || carving.isBlank() || engine == null || data == null) {
            return null;
        }

        IrisDimension dimension = engine.getDimension();
        if (dimension != null && dimension.getCarvingEntryIndex() != null) {
            IrisDimensionCarvingEntry entry = dimension.getCarvingEntryIndex().get(carving);
            if (entry != null) {
                return IrisDimensionCarvingResolver.resolveEntryBiome(engine, entry);
            }
        }

        if (data.getBiomeLoader() == null) {
            return null;
        }

        return data.getBiomeLoader().load(carving);
    }

    @RegistryListResource(IrisBiome.class)
    @Description("The target biome whose visual design (layers, palette, decorators, surface objects, derivative, and — when topShapeMode=BIOME — generator profile) drives the floating island. Leave empty to reuse the parent biome (self).")
    private String biome = "";

    @MinNumber(1)
    @MaxNumber(512)
    @Description("Selection rarity when multiple floating child entries are defined on one parent biome. Lower is more common.")
    private int rarity = 1;

    @Description("2D noise that decides which columns are part of this island. Set feature size via the style's own zoom field (e.g. {\"style\":\"CELLULAR\",\"zoom\":0.3} for ~30-block shards, {\"style\":\"SIMPLEX\",\"zoom\":1.0} for ~100-block blobs). Pick SIMPLEX for smooth blobs, CELLULAR for angular shards, VASCULAR for vein/branch strips, FRACTAL_FBM_SIMPLEX for large irregular blanket regions. Fracture (domain warp) this to get swirly silhouettes.")
    private IrisGeneratorStyle footprintStyle = NoiseStyle.SIMPLEX.style();

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Coverage threshold (0..1). Roughly the fraction of the world that is NOT island: 0.0 = every column becomes island, 0.5 ≈ 50% of columns, 0.8 ≈ sparse scattered ~20% coverage, 1.0 = no islands at all. Values near 0.5 feel most natural.")
    private double footprintThreshold = 0.5;

    @MinNumber(2)
    @MaxNumber(32)
    @Description("Width in blocks of the rounded transition from the floating island contour to its full thickness. Smaller values make a tighter edge; larger values make a broader rounded underside.")
    private int edgeTaperWidth = FloatingIslandEdgeProfile.DEFAULT_WIDTH;

    @MinNumber(0.25)
    @MaxNumber(4)
    @Description("Shape exponent for the rounded edge. Values below 1 fill into a softer, fuller curve; values above 1 keep the rim thinner before rising into the island interior.")
    private double edgeTaperExponent = FloatingIslandEdgeProfile.DEFAULT_EXPONENT;

    @Description("Broad 2D noise that organically varies the rounded edge without changing the island footprint. Low-frequency SIMPLEX or PERLIN styles give coherent natural contours; high-frequency or cellular styles can look speckled.")
    private IrisGeneratorStyle edgeTaperVariationStyle = NoiseStyle.SIMPLEX.style().zoomed(0.18D);

    @MinNumber(0)
    @MaxNumber(8)
    @Description("Maximum number of blocks by which edgeTaperVariationStyle locally widens or narrows the rounded transition. 0 disables variation. Runtime keeps the resulting local width between 2 and 32 blocks so every protected rim column remains connected.")
    private double edgeTaperVariationAmplitude = 0.0D;

    @Description("Picker noise used when multiple floating child entries exist. Samples once per column to deterministically choose which entry's footprint is tested there. Use a large style zoom (e.g. zoom: 4 for ~400-block regions) so each entry owns broad coherent areas.")
    private IrisGeneratorStyle pickerStyle = NoiseStyle.SIMPLEX.style();

    @Description("Altitude noise — varies the base platform Y across one island so it isn't a flat plane. Use a large style zoom (e.g. zoom: 2 for ~200-block altitude patches) so an island sits at roughly one altitude.")
    private IrisGeneratorStyle altitudeStyle = NoiseStyle.SIMPLEX.style();

    @MinNumber(0)
    @MaxNumber(2032)
    @Description("Minimum absolute world Y where the island base can sit. Island altitude is independent of the parent biome's terrain height; altitudeStyle noise varies the base between min and max per column.")
    private int minHeightAboveSurface = 160;

    @MinNumber(0)
    @MaxNumber(2032)
    @Description("Maximum absolute world Y where the island base can sit. Island altitude is independent of the parent biome's terrain height; altitudeStyle noise varies the base between min and max per column.")
    private int maxHeightAboveSurface = 210;

    @Description("Optional absolute minimum world Y for the island base. When set, baseY is clamped upward so the tail bottom stays above this value.")
    private Integer minAbsoluteY = null;

    @Description("Optional absolute maximum world Y for the island top. When set, the top is clamped downward.")
    private Integer maxAbsoluteY = null;

    @Description("How the top profile of the island is shaped. BIOME = evaluate target biome's own generators (mountains biome -> real mountains). NOISE = use topShapeStyle as a user-controlled heightmap. FLAT = constant maxTopHeight slab.")
    private TopShapeMode topShapeMode = TopShapeMode.BIOME;

    @MinNumber(0)
    @MaxNumber(512)
    @Description("Maximum top profile height in blocks above the island base. Caps how tall the biome terrain can grow on top.")
    private int maxTopHeight = 40;

    @Description("Used only when topShapeMode=NOISE. 2D noise driving the top heightmap. Set feature scale via the style's zoom field (small zoom = rugged peaks, large zoom = broad rolling shapes).")
    private IrisGeneratorStyle topShapeStyle = NoiseStyle.SIMPLEX.style();

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Amplitude multiplier applied to the NOISE top profile. 0 = no top (flat at base), 1 = full maxTopHeight range.")
    private double topShapeAmp = 1.0;

    @Description("2D noise driving the bottom tail hanging below the island base. VASCULAR = drippy organic roots. FRACTAL_RM_SIMPLEX = crystalline spikes. CELLULAR = jagged chunks. PERLIN = smooth rounded bowl. SIMPLEX = gentle lobes. Set feature scale via the style's zoom field.")
    private IrisGeneratorStyle bottomStyle = NoiseStyle.SIMPLEX.style();

    @MinNumber(0)
    @MaxNumber(512)
    @Description("Minimum blocks below the base where the tail extends.")
    private int bottomDepthMin = 4;

    @MinNumber(0)
    @MaxNumber(512)
    @Description("Maximum blocks below the base where the tail extends.")
    private int bottomDepthMax = 20;

    @MinNumber(0.1)
    @MaxNumber(8)
    @Description("Power curve applied to the bottom noise before mapping to depth. >1 = most columns shallow with occasional deeper spikes (sparse roots). <1 = most columns deep with occasional shallow spots (dense curtains). 1.0 = linear.")
    private double bottomExponent = 1.0;

    @Description("Controls the material palette near the island underside. DEPTH keeps the old top-down depth behavior. MIRROR_TOP uses the target biome's shallow/top palette from the underside upward. CUSTOM uses bottomPalette near the underside while keeping the target biome palette near the top.")
    private FloatingBottomPaletteMode bottomPaletteMode = FloatingBottomPaletteMode.DEPTH;

    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Description("Custom palette layers used near the underside when bottomPaletteMode=CUSTOM. The layer format is the same as normal biome layers.")
    private KList<IrisBiomePaletteLayer> bottomPalette = new KList<>();

    @MinNumber(1)
    @MaxNumber(512)
    @Description("Hard cap on the total Y-extent (top minus bottom) of a single island column. Safety limit.")
    private int maxThickness = 96;

    @Description("Optional 3D noise that shifts the footprint's XZ sample position per Y layer — naturalizes the walls so they stop looking like a straight extrusion of the 2D footprint. Leave null to disable and keep straight vertical walls. Good defaults: {\"style\":\"SIMPLEX\",\"zoom\":0.25} for gentle undulation, {\"style\":\"FRACTAL_FBM_SIMPLEX\",\"zoom\":0.4} for craggier walls.")
    private IrisGeneratorStyle wallWarpStyle = null;

    @MinNumber(0)
    @MaxNumber(64)
    @Description("Amplitude in blocks of the per-layer XZ shift applied when wallWarpStyle is set. 0 = no warp (straight walls). 4..8 = gentle naturalization. 16+ = heavily meandering walls. Ignored when wallWarpStyle is null.")
    private double wallWarpAmplitude = 6.0;

    @Description("Optional 3D noise that swiss-cheeses the island interior by marking individual blocks as air when the noise exceeds carveThreshold. Leave null to keep the island solid. Good defaults: {\"style\":\"CELLULAR\",\"zoom\":0.3} for bubble pockets, {\"style\":\"VASCULAR\",\"zoom\":0.25} for wormy tunnels.")
    private IrisGeneratorStyle carveStyle = null;

    @Description("Optional carving biome key or dimension carving entry id whose caveProfile drives floating-island internal air pockets. Dimension carving ids resolve first; biome keys such as carving/mushroom resolve second. When set, this overrides carveStyle while carveThreshold remains a final tuning bias.")
    private String carving = "";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Threshold (0..1) above which carveStyle noise carves air pockets. 1.0 = no direct-noise carving. 0.75 = sparse pockets. 0.55 = heavy swiss-cheese. 0.4 = shredded lattice. When carving is set, lower values add a final positive threshold bias to the referenced caveProfile.")
    private double carveThreshold = 1.0;

    @Description("Optional water surface height above the island base, in blocks. null = no internal water. Positive = water fills any dip in the top profile up to baseY + localFluidHeight (forms lakes/ponds in concavities of the biome-top heightmap).")
    private Integer localFluidHeight = null;

    @Description("Block used for the internal water pool when localFluidHeight is positive.")
    @RegistryListBlockType
    private String fluidBlock = "minecraft:water";

    @Description("When true, the target biome's decorators apply to the island's top surface.")
    private boolean inheritDecorators = true;

    @Description("When true, the target biome's surface objects are placed on the island's top surface instead of the parent terrain.")
    private boolean inheritObjects = true;

    @MinNumber(0.01)
    @MaxNumber(1)
    @Description("Uniform shrink factor applied to every object placed on this floating island (inherited, extra, and free-floating). 1.0 = native size, 0.5 = half size, 0.25 = quarter. Useful for making small floating biomes feel believable.")
    private double objectShrinkFactor = 1.0;

    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Description("Additional object placements anchored to the island top.")
    private KList<IrisObjectPlacement> extraObjects = new KList<>();

    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Description("Additional object placements that float freely in air, independent of the island. Forced to ObjectPlaceMode.FLOATING.")
    private KList<IrisObjectPlacement> floatingObjects = new KList<>();

    @Description("Visualization color for this floating child in Iris Studio.")
    private String color = null;

    @Description("Controls how topObjectOverrides are combined with the inherited surface objects from the target biome. INHERIT_ONLY (default) = behaves identically to before this field was added. MERGE = appends overrides after inherited objects. REPLACE = uses only overrides, ignoring all inherited objects.")
    private OverrideMode topObjectMode = OverrideMode.INHERIT_ONLY;

    @Description("Controls how bottomObjectOverrides are combined. INHERIT_ONLY (default) = no bottom objects placed (there is no inherited bottom set). MERGE = same as REPLACE for bottom (no inherited source). REPLACE = uses bottomObjectOverrides list only.")
    private OverrideMode bottomObjectMode = OverrideMode.INHERIT_ONLY;

    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Description("Object placements that override or supplement the inherited surface objects on the island TOP. Behaviour depends on topObjectMode. INHERIT_ONLY = this list is ignored. MERGE = appended after inherited. REPLACE = used instead of inherited.")
    private KList<IrisObjectPlacement> topObjectOverrides = new KList<>();

    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Description("Object placements anchored to the island BOTTOM face. Each entry is auto-inverted 180 degrees around the X axis and placed flush against the lowest solid face of the island, so objects appear to hang upside-down from the underside. WARNING: directional blocks (stairs, doors, slabs) will not render correctly when flipped — use non-directional content (logs, leaves, stone, mycelium, ice, glass) for bottom placements.")
    private KList<IrisObjectPlacement> bottomObjectOverrides = new KList<>();

    public KList<IrisObjectPlacement> resolveTopObjects(IrisBiome target) {
        KList<IrisObjectPlacement> surfaceObjects = (inheritObjects && target != null) ? target.getSurfaceObjects() : new KList<>();
        return resolveTopObjectsFromSurface(surfaceObjects);
    }

    KList<IrisObjectPlacement> resolveTopObjectsFromSurface(KList<IrisObjectPlacement> surfaceObjects) {
        return switch (topObjectMode) {
            case REPLACE -> new KList<>(topObjectOverrides);
            case MERGE -> {
                KList<IrisObjectPlacement> merged = new KList<>();
                merged.addAll(surfaceObjects);
                merged.addAll(topObjectOverrides);
                yield merged;
            }
            case INHERIT_ONLY -> surfaceObjects;
        };
    }

    public KList<IrisObjectPlacement> resolveBottomObjects(IrisBiome target) {
        return switch (bottomObjectMode) {
            case INHERIT_ONLY -> new KList<>();
            case MERGE, REPLACE -> bottomObjectOverrides;
        };
    }

    public boolean hasObjectShrink() {
        return objectShrinkFactor > 0 && objectShrinkFactor < 1.0;
    }

    public IrisObjectScale getShrinkScale() {
        return shrinkScaleCache.aquire(() -> {
            IrisObjectScale s = new IrisObjectScale();
            s.setSize(Math.max(0.01, Math.min(1.0, objectShrinkFactor)));
            return s;
        });
    }

    public IrisBiome getRealBiome(IrisBiome parent, IrisData data) {
        return resolvedBiome.aquire(() -> {
            if (biome == null || biome.isBlank() || biome.equals(parent.getLoadKey())) {
                return parent;
            }

            IrisBiome loaded = data.getBiomeLoader().load(biome);
            if (loaded == null) {
                return parent;
            }

            if (loaded.isCompatExcluded()) {
                CompatPools.drop(data, loaded, "floating child biome", parent.getLoadKey(), "biome " + biome, null);
                return parent;
            }

            return loaded;
        });
    }
}
