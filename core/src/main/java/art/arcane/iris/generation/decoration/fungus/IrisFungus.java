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

package art.arcane.iris.generation.decoration.fungus;

import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.IrisVacuumSettings;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectLimit;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.ObjectPlaceMode;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("fungus")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A single procedurally generated fungus (giant mushroom or shelf fungus). Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. The fungus is built as a stem column with a cap dome grown on top, mirroring the trunk-and-canopy model used by procedural trees.")
@Data
public class IrisFungus implements IrisProceduralPlacement {
    private final transient AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    @Description("A human readable name used in logs and as the variant load key.")
    private String name = "fungus";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per chunk for this fungus to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Description("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("How many distinct variants to pre-bake for this fungus. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Description("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Description("The placement mode used to anchor the fungus to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Description("Rotate this fungus's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Description("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Description("Whether this fungus may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Description("If true, the fungus anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Description("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Description("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Description("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private IrisVacuumSettings vacuumSettings;

    @Required
    @Description("The stem block, e.g. minecraft:mushroom_stem. Ignored when stemPalette is set.")
    @RegistryListBlockType
    private String stem = "minecraft:mushroom_stem";

    @Description("A noise-driven palette for the stem. When set this overrides the single stem block, letting the stem mix blocks by noise.")
    private IrisMaterialPalette stemPalette = null;

    @Required
    @Description("The cap block, e.g. minecraft:red_mushroom_block. Ignored when capPalette is set.")
    @RegistryListBlockType
    private String cap = "minecraft:red_mushroom_block";

    @Description("A noise-driven palette for the cap. When set this overrides the single cap block, letting the cap mix blocks by noise.")
    private IrisMaterialPalette capPalette = null;

    @MinNumber(1)
    @Description("Minimum total stem height in blocks. Variants spread between this and stemHeightMax.")
    private int stemHeightMin = 5;

    @MinNumber(1)
    @Description("Maximum total stem height in blocks. Variants spread between stemHeightMin and this.")
    private int stemHeightMax = 9;

    @MinNumber(1)
    @MaxNumber(3)
    @Description("Base stem width in blocks. 1 is a single column, 2 is a 2x2 stem, 3 is a chunky 3x3 trunk.")
    private int stemWidth = 1;

    @Description("How far the stem leans away from vertical, in degrees. 0 is perfectly upright; small values give an organic tilt.")
    private double stemCurve = 0;

    @Description("Compass direction in degrees the stem leans toward when stemCurve is non-zero.")
    private double stemLeanAzimuth = 0;

    @MinNumber(0)
    @Description("Amplitude in blocks of a gentle sideways wave applied up the stem so it is not a perfectly straight ruler.")
    private double stemWaveAmplitude = 0.4;

    @MinNumber(0.0001)
    @Description("The number of full sine wobbles applied over the stem height for the stem wave.")
    private double stemWavePeriods = 1;

    @Description("The silhouette of the cap (dome, flat umbrella, funnel, cone, or wide shallow slab).")
    private IrisFungusCapShape capShape = IrisFungusCapShape.DOME;

    @MinNumber(1)
    @Description("Minimum cap radius in blocks (measured from the cap center to its rim).")
    private int capRadiusMin = 3;

    @MinNumber(1)
    @Description("Maximum cap radius in blocks (measured from the cap center to its rim).")
    private int capRadiusMax = 5;

    @MinNumber(1)
    @MaxNumber(3)
    @Description("How many blocks thick the cap shell is. 1 is a thin skin, 3 is a fleshy slab.")
    private int capThickness = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Vertical flatten factor for the cap. 0 leaves the cap at full height, 1 squashes it flat into a disc.")
    private double capSquish = 0.4;

    @MinNumber(0)
    @Description("How far the cap rim droops downward, in degrees, curling the edge of the cap toward the ground.")
    private double capDroop = 20;

    @MinNumber(0)
    @Description("How far the cap radius extends past the stem before the rim begins, in blocks. Larger values make the cap overhang the stem more.")
    private double capOverhang = 2;

    @Description("Optional block forming the gill layer on the underside of the cap (gills or a glow layer), e.g. minecraft:brown_mushroom_block or minecraft:shroomlight. Ignored when gillPalette is set or left null.")
    @RegistryListBlockType
    private String gillBlock = null;

    @Description("A noise-driven palette for the underside gill layer. When set this overrides the single gillBlock.")
    private IrisMaterialPalette gillPalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The fraction of underside cap blocks replaced by the gill block when a gill block or palette is set.")
    private double gillChance = 0.85;

    @Description("Optional block speckled across the top of the cap by noise (white toadstool dots, warts, glowing spots), e.g. minecraft:bone_block or minecraft:white_concrete. Ignored when spotPalette is set or left null.")
    @RegistryListBlockType
    private String spotBlock = null;

    @Description("A noise-driven palette for the cap top spots. When set this overrides the single spotBlock.")
    private IrisMaterialPalette spotPalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The fraction of top cap blocks replaced by the spot block, selected by value noise so spots cluster naturally.")
    private double spotChance = 0.18;

    @Description("If true, generate a bracket / shelf fungus instead of an upright mushroom: a small flat fan that grows sideways off a very short or absent stem, like a polypore clinging to a trunk.")
    private boolean shelf = false;

    @MinNumber(1)
    @Description("The radius in blocks of the sideways fan when shelf is true.")
    private int shelfRadius = 3;

    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> {
            KList<IrisObject> baked = new KList<>();
            int count = Math.max(1, variants);
            int lo = Math.min(stemHeightMin, stemHeightMax);
            int hi = Math.max(stemHeightMin, stemHeightMax);
            RNG heightRng = new RNG(seed);

            for (int i = 0; i < count; i++) {
                int height;
                if (count == 1 || lo == hi) {
                    height = heightRng.i(lo, hi + 1);
                } else {
                    double step = (hi - lo) / (double) (count - 1);
                    double base = lo + step * i;
                    double jitter = heightRng.d(-step * 0.3, step * 0.3);
                    height = (int) Math.round(Math.max(lo, Math.min(hi, base + jitter)));
                }

                IrisObject object = FungusGenerator.generate(this, Math.max(1, height), new RNG(seed + (i * 7919L)), data);
                if (object == null || object.getBlocks().isEmpty()) {
                    continue;
                }
                object.setLoadKey("procedural/" + name + "#" + i);
                object.setLoader(data);
                baked.add(object);
            }

            return baked;
        });
    }

    public IrisObject getVariantObject(IrisData data, RNG rng) {
        KList<IrisObject> baked = getVariantObjects(data);
        if (baked == null || baked.isEmpty()) {
            return null;
        }
        return baked.get(rng.i(baked.size()));
    }

    public IrisObjectPlacement asPlacement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(mode);
        placement.setRotation(rotation);
        placement.setClamp(clamp);
        placement.setCarvingSupport(carvingSupport);
        placement.setUnderwater(underwater);
        placement.setTranslate(translate);
        placement.setStiltSettings(stiltSettings);
        placement.setVacuumSettings(vacuumSettings);
        placement.setChance(chance);
        placement.setDensity(density);
        return placement;
    }

    public boolean isPlausible() {
        return false;
    }
}
