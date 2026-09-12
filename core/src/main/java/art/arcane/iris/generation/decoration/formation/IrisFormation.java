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

package art.arcane.iris.generation.decoration.formation;

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

@Snippet("formation")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A single procedurally generated natural formation, including rock landmarks and magical glacial silhouettes. Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file.")
@Data
public class IrisFormation implements IrisProceduralPlacement {
    private final transient AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    @Description("A human readable name used in logs and as the variant load key.")
    private String name = "formation";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per chunk for this formation to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.02;

    @MinNumber(1)
    @Description("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("How many distinct variants to pre-bake for this formation. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Description("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Description("The placement mode used to anchor the formation to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Description("Rotate this formation's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Description("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Description("Whether this formation may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("Horizontal buffer, in blocks, beyond each lowest solid foundation block that must also be solid, un-carved ground.")
    private int surfaceSupportBuffer = 3;

    @Description("If true, the formation anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Description("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Description("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Description("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private IrisVacuumSettings vacuumSettings;

    @Description("The overall silhouette of the formation. Drives which sculpting routine the generator uses.")
    private IrisFormationForm form = IrisFormationForm.SPIRE;

    @Required
    @Description("The main rock block, e.g. minecraft:stone. Ignored when blockPalette is set.")
    @RegistryListBlockType
    private String block = "minecraft:stone";

    @Description("A noise-driven palette for the main rock body. When set this overrides the single block, letting the body mix blocks by noise.")
    private IrisMaterialPalette blockPalette = null;

    @Description("Optional caprock block placed on the top crown of the formation (and the wide overhanging cap for HOODOO). Ignored when capPalette is set. When null and capPalette is unset, the formation uses its main rock everywhere.")
    @RegistryListBlockType
    private String capBlock = null;

    @Description("A noise-driven palette for the caprock. When set this overrides the single capBlock.")
    private IrisMaterialPalette capPalette = null;

    @Description("Optional palette of strata (horizontal colored bands) blended into the rock body by y level. When set, every strataThickness blocks of height switches to the next strata block, producing the signature banded look. Falls back to the main block where unset.")
    private IrisMaterialPalette strataPalette = null;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("The thickness in blocks of each horizontal strata band when strataPalette is set.")
    private int strataThickness = 3;

    @MinNumber(1)
    @Description("Minimum total height of the formation in blocks.")
    private int heightMin = 14;

    @MinNumber(1)
    @Description("Maximum total height of the formation in blocks.")
    private int heightMax = 26;

    @MinNumber(1)
    @Description("Minimum base radius (half-width) of the formation in blocks.")
    private int baseWidthMin = 3;

    @MinNumber(1)
    @Description("Maximum base radius (half-width) of the formation in blocks.")
    private int baseWidthMax = 6;

    @MinNumber(0)
    @Description("The target radius (half-width) at the very top of the formation in blocks, before the profile is applied. 0 means taper to a point.")
    private int topWidth = 0;

    @Description("The function used to shape the formation radius over its height.")
    private IrisFormationProfile profile = IrisFormationProfile.TAPER;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The normalized height (0 base, 1 top) of the pinched waist for the PARABOLIC profile (used by HOODOO).")
    private double profileWaist = 0.55;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The minimum radius fraction at the waist for the PARABOLIC profile. Lower values pinch the waist tighter.")
    private double profileWaistFloor = 0.35;

    @MinNumber(0)
    @Description("How far the formation leans, in degrees from vertical. 0 is perfectly upright. The whole body is sheared by this amount over its height.")
    private double lean = 0;

    @Description("Compass direction in degrees the formation leans toward when lean is non-zero.")
    private double leanAzimuth = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Surface roughness from 0 (a clean smooth solid) to 1 (heavily eroded and pitted). Driven by 3D value noise that perturbs the radius so the formation never reads as a clean cylinder.")
    private double roughness = 0.3;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("Per-block surface jitter from 0 (none) to 1 (maximum). Randomly carves and adds isolated edge blocks to break up the silhouette.")
    private double jitter = 0.15;

    @MinNumber(0)
    @Description("Extra wide cap radius in blocks added on top for the HOODOO form, producing the overhanging mushroom caprock. 0 disables the overhang.")
    private int hoodooCapRadius = 3;

    @MinNumber(1)
    @MaxNumber(6)
    @Description("The height in blocks of the HOODOO overhanging caprock slab.")
    private int hoodooCapHeight = 3;

    @MinNumber(0)
    @Description("How wide the ARCH opening is in blocks (the gap between the two legs). The arch span is rasterized over this width.")
    private int archSpan = 10;

    @MinNumber(1)
    @Description("The thickness in blocks of the ARCH legs and spanning curve.")
    private int archThickness = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How strongly an ARCH varies its leg steepness, crown position, depth bow and tube width between deterministic variants. 0 is symmetric and 1 is highly organic.")
    private double archAsymmetry = 0.35;

    @MinNumber(2)
    @MaxNumber(12)
    @Description("How many separate columns make up a BASALT_COLUMN cluster. Each column gets a randomized height around the formation height range.")
    private int basaltColumns = 5;

    @MinNumber(1)
    @Description("The radius (half-width) in blocks of each individual column in a BASALT_COLUMN cluster.")
    private int basaltColumnRadius = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("How much the heights of individual BASALT_COLUMN columns vary from each other, from 0 (all equal) to 1 (highly varied).")
    private double basaltHeightVariance = 0.45;

    @MinNumber(1)
    @MaxNumber(12)
    @Description("How many tapered summits crown an ICEBERG formation.")
    private int icebergPeaks = 3;

    @MinNumber(2)
    @MaxNumber(8)
    @Description("How many separated shards form a FISSURE.")
    private int fractureCount = 3;

    @MinNumber(1)
    @MaxNumber(16)
    @Description("Open-air separation in blocks between the shards of a FISSURE.")
    private int fractureSeparation = 2;

    @MinNumber(0.25)
    @MaxNumber(6)
    @Description("How many complete turns a SPIRAL makes from base to tip.")
    private double spiralTurns = 1.5;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("The starting distance in blocks between a SPIRAL and its open center.")
    private int spiralRadius = 4;

    @MinNumber(1)
    @MaxNumber(8)
    @Description("The radius in blocks of the tube swept along a SPIRAL.")
    private int spiralThickness = 2;

    @MinNumber(1)
    @MaxNumber(32)
    @Description("How far the hooked arm of an OVERHANG projects from its base.")
    private int overhangReach = 8;

    @MinNumber(0)
    @MaxNumber(16)
    @Description("How many blocks the tip of an OVERHANG curls downward.")
    private int overhangDrop = 3;

    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> {
            KList<IrisObject> baked = new KList<>();
            int count = Math.max(1, variants);

            for (int i = 0; i < count; i++) {
                IrisObject object = FormationGenerator.generate(this, i, new RNG(seed + (i * 7919L)), data);
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
        placement.setSurfaceSupportBuffer(surfaceSupportBuffer);
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
