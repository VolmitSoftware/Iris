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

package com.volmit.iris.engine.object.procedural.formation;

import com.volmit.iris.engine.object.ObjectPlaceMode;
import com.volmit.iris.engine.object.CarvingMode;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectTranslate;
import com.volmit.iris.engine.object.IrisStiltSettings;
import com.volmit.iris.engine.object.IrisObjectLimit;
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.procedural.ProceduralObjectCatalog;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBlockType;
import com.volmit.iris.engine.object.annotations.Required;

import java.util.Optional;

@Snippet(ProceduralObjectCatalog.FORMATION_FOLDER)
@Desc("A single procedurally generated natural formation, including rock landmarks and magical glacial silhouettes. Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file.")
public final class Formation {
    @Desc("A human readable name used in logs and as the variant load key.")
    private String name = "formation";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per chunk for this formation to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.02;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this formation. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Desc("The placement mode used to anchor the formation to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Desc("Rotate this formation's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Desc("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Desc("Whether this formation may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("Horizontal buffer, in blocks, beyond each lowest solid foundation block that must also be solid, un-carved ground.")
    private int surfaceSupportBuffer = 3;

    @Desc("If true, the formation anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Desc("The overall silhouette of the formation. Drives which sculpting routine the generator uses.")
    private FormationForm form = FormationForm.SPIRE;

    @Required
    @Desc("The main rock block, e.g. minecraft:stone. Ignored when blockPalette is set.")
    @RegistryListBlockType
    private String block = "minecraft:stone";

    @Desc("A noise-driven palette for the main rock body. When set this overrides the single block, letting the body mix blocks by noise.")
    private IrisMaterialPalette blockPalette;

    @Desc("Optional caprock block placed on the top crown of the formation (and the wide overhanging cap for HOODOO). Ignored when capPalette is set. When null and capPalette is unset, the formation uses its main rock everywhere.")
    @RegistryListBlockType
    private String capBlock = null;

    @Desc("A noise-driven palette for the caprock. When set this overrides the single capBlock.")
    private IrisMaterialPalette capPalette;

    @Desc("Optional palette of strata (horizontal colored bands) blended into the rock body by y level. When set, every strataThickness blocks of height switches to the next strata block, producing the signature banded look. Falls back to the main block where unset.")
    private IrisMaterialPalette strataPalette;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("The thickness in blocks of each horizontal strata band when strataPalette is set.")
    private int strataThickness = 3;

    @MinNumber(1)
    @Desc("Minimum total height of the formation in blocks.")
    private int heightMin = 14;

    @MinNumber(1)
    @Desc("Maximum total height of the formation in blocks.")
    private int heightMax = 26;

    @MinNumber(1)
    @Desc("Minimum base radius (half-width) of the formation in blocks.")
    private int baseWidthMin = 3;

    @MinNumber(1)
    @Desc("Maximum base radius (half-width) of the formation in blocks.")
    private int baseWidthMax = 6;

    @MinNumber(0)
    @Desc("The target radius (half-width) at the very top of the formation in blocks, before the profile is applied. 0 means taper to a point.")
    private int topWidth = 0;

    @Desc("The function used to shape the formation radius over its height.")
    private FormationProfile profile = FormationProfile.TAPER;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The normalized height (0 base, 1 top) of the pinched waist for the PARABOLIC profile (used by HOODOO).")
    private double profileWaist = 0.55;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The minimum radius fraction at the waist for the PARABOLIC profile. Lower values pinch the waist tighter.")
    private double profileWaistFloor = 0.35;

    @MinNumber(0)
    @Desc("How far the formation leans, in degrees from vertical. 0 is perfectly upright. The whole body is sheared by this amount over its height.")
    private double lean = 0;

    @Desc("Compass direction in degrees the formation leans toward when lean is non-zero.")
    private double leanAzimuth = 0;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Surface roughness from 0 (a clean smooth solid) to 1 (heavily eroded and pitted). Driven by 3D value noise that perturbs the radius so the formation never reads as a clean cylinder.")
    private double roughness = 0.3;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Per-block surface jitter from 0 (none) to 1 (maximum). Randomly carves and adds isolated edge blocks to break up the silhouette.")
    private double jitter = 0.15;

    @MinNumber(0)
    @Desc("Extra wide cap radius in blocks added on top for the HOODOO form, producing the overhanging mushroom caprock. 0 disables the overhang.")
    private int hoodooCapRadius = 3;

    @MinNumber(1)
    @MaxNumber(6)
    @Desc("The height in blocks of the HOODOO overhanging caprock slab.")
    private int hoodooCapHeight = 3;

    @MinNumber(0)
    @Desc("How wide the ARCH opening is in blocks (the gap between the two legs). The arch span is rasterized over this width.")
    private int archSpan = 10;

    @MinNumber(1)
    @Desc("The thickness in blocks of the ARCH legs and spanning curve.")
    private int archThickness = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How strongly an ARCH varies its leg steepness, crown position, depth bow and tube width between deterministic variants. 0 is symmetric and 1 is highly organic.")
    private double archAsymmetry = 0.35;

    @MinNumber(2)
    @MaxNumber(12)
    @Desc("How many separate columns make up a BASALT_COLUMN cluster. Each column gets a randomized height around the formation height range.")
    private int basaltColumns = 5;

    @MinNumber(1)
    @Desc("The radius (half-width) in blocks of each individual column in a BASALT_COLUMN cluster.")
    private int basaltColumnRadius = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("How much the heights of individual BASALT_COLUMN columns vary from each other, from 0 (all equal) to 1 (highly varied).")
    private double basaltHeightVariance = 0.45;

    @MinNumber(1)
    @MaxNumber(12)
    @Desc("How many tapered summits crown an ICEBERG formation.")
    private int icebergPeaks = 3;

    @MinNumber(2)
    @MaxNumber(8)
    @Desc("How many separated shards form a FISSURE.")
    private int fractureCount = 3;

    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Open-air separation in blocks between the shards of a FISSURE.")
    private int fractureSeparation = 2;

    @MinNumber(0.25)
    @MaxNumber(6)
    @Desc("How many complete turns a SPIRAL makes from base to tip.")
    private double spiralTurns = 1.5;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("The starting distance in blocks between a SPIRAL and its open center.")
    private int spiralRadius = 4;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("The radius in blocks of the tube swept along a SPIRAL.")
    private int spiralThickness = 2;

    @MinNumber(1)
    @MaxNumber(32)
    @Desc("How far the hooked arm of an OVERHANG projects from its base.")
    private int overhangReach = 8;

    @MinNumber(0)
    @MaxNumber(16)
    @Desc("How many blocks the tip of an OVERHANG curls downward.")
    private int overhangDrop = 3;

    public Formation() {
    }

    public String name() {
        return name;
    }

    public double chance() {
        return chance;
    }

    public int density() {
        return density;
    }

    public int variants() {
        return variants;
    }

    public long seed() {
        return seed;
    }

    public ObjectPlaceMode mode() {
        return mode == null ? ObjectPlaceMode.CENTER_HEIGHT : mode;
    }

    public IrisObjectRotation rotation() {
        return rotation;
    }

    public IrisObjectLimit clamp() {
        return clamp;
    }

    public CarvingMode carvingSupport() {
        return carvingSupport == null ? CarvingMode.SURFACE_ONLY : carvingSupport;
    }

    public int surfaceSupportBuffer() {
        return surfaceSupportBuffer;
    }

    public boolean underwater() {
        return underwater;
    }

    public IrisObjectTranslate translate() {
        return translate;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisStiltSettings> stiltSettings() {
        return Optional.ofNullable(stiltSettings);
    }

    /** Empty where the pack sets none. */

    public FormationForm form() {
        return form == null ? FormationForm.SPIRE : form;
    }

    public String block() {
        return block;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> blockPalette() {
        return Optional.ofNullable(blockPalette);
    }

    /** Empty where the pack sets none. */
    public Optional<String> capBlock() {
        return Optional.ofNullable(capBlock);
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> capPalette() {
        return Optional.ofNullable(capPalette);
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> strataPalette() {
        return Optional.ofNullable(strataPalette);
    }

    public int strataThickness() {
        return strataThickness;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public int baseWidthMin() {
        return baseWidthMin;
    }

    public int baseWidthMax() {
        return baseWidthMax;
    }

    public int topWidth() {
        return topWidth;
    }

    public FormationProfile profile() {
        return profile == null ? FormationProfile.TAPER : profile;
    }

    public double profileWaist() {
        return profileWaist;
    }

    public double profileWaistFloor() {
        return profileWaistFloor;
    }

    public double lean() {
        return lean;
    }

    public double leanAzimuth() {
        return leanAzimuth;
    }

    public double roughness() {
        return roughness;
    }

    public double jitter() {
        return jitter;
    }

    public int hoodooCapRadius() {
        return hoodooCapRadius;
    }

    public int hoodooCapHeight() {
        return hoodooCapHeight;
    }

    public int archSpan() {
        return archSpan;
    }

    public int archThickness() {
        return archThickness;
    }

    public double archAsymmetry() {
        return archAsymmetry;
    }

    public int basaltColumns() {
        return basaltColumns;
    }

    public int basaltColumnRadius() {
        return basaltColumnRadius;
    }

    public double basaltHeightVariance() {
        return basaltHeightVariance;
    }

    public int icebergPeaks() {
        return icebergPeaks;
    }

    public int fractureCount() {
        return fractureCount;
    }

    public int fractureSeparation() {
        return fractureSeparation;
    }

    public double spiralTurns() {
        return spiralTurns;
    }

    public int spiralRadius() {
        return spiralRadius;
    }

    public int spiralThickness() {
        return spiralThickness;
    }

    public int overhangReach() {
        return overhangReach;
    }

    public int overhangDrop() {
        return overhangDrop;
    }
}
