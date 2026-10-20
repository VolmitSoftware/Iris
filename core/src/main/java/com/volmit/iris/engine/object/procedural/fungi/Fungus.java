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

package com.volmit.iris.engine.object.procedural.fungi;

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

@Snippet(ProceduralObjectCatalog.SNIPPET_FOLDER)
@Desc("A single procedurally generated fungus (giant mushroom or shelf fungus). Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. The fungus is built as a stem column with a cap dome grown on top, mirroring the trunk-and-canopy model used by procedural trees.")
public final class Fungus {
    @Desc("A human readable name used in logs and as the variant load key.")
    private String name = "fungus";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per chunk for this fungus to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this fungus. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Desc("The placement mode used to anchor the fungus to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Desc("Rotate this fungus's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Desc("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Desc("Whether this fungus may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Desc("If true, the fungus anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Required
    @Desc("The stem block, e.g. minecraft:mushroom_stem. Ignored when stemPalette is set.")
    @RegistryListBlockType
    private String stem = "minecraft:mushroom_stem";

    @Desc("A noise-driven palette for the stem. When set this overrides the single stem block, letting the stem mix blocks by noise.")
    private IrisMaterialPalette stemPalette;

    @Required
    @Desc("The cap block, e.g. minecraft:red_mushroom_block. Ignored when capPalette is set.")
    @RegistryListBlockType
    private String cap = "minecraft:red_mushroom_block";

    @Desc("A noise-driven palette for the cap. When set this overrides the single cap block, letting the cap mix blocks by noise.")
    private IrisMaterialPalette capPalette;

    @MinNumber(1)
    @Desc("Minimum total stem height in blocks. Variants spread between this and stemHeightMax.")
    private int stemHeightMin = 5;

    @MinNumber(1)
    @Desc("Maximum total stem height in blocks. Variants spread between stemHeightMin and this.")
    private int stemHeightMax = 9;

    @MinNumber(1)
    @MaxNumber(3)
    @Desc("Base stem width in blocks. 1 is a single column, 2 is a 2x2 stem, 3 is a chunky 3x3 trunk.")
    private int stemWidth = 1;

    @Desc("How far the stem leans away from vertical, in degrees. 0 is perfectly upright; small values give an organic tilt.")
    private double stemCurve = 0;

    @Desc("Compass direction in degrees the stem leans toward when stemCurve is non-zero.")
    private double stemLeanAzimuth = 0;

    @MinNumber(0)
    @Desc("Amplitude in blocks of a gentle sideways wave applied up the stem so it is not a perfectly straight ruler.")
    private double stemWaveAmplitude = 0.4;

    @MinNumber(0.0001)
    @Desc("The number of full sine wobbles applied over the stem height for the stem wave.")
    private double stemWavePeriods = 1;

    @Desc("The silhouette of the cap (dome, flat umbrella, funnel, cone, or wide shallow slab).")
    private FungusCapShape capShape = FungusCapShape.DOME;

    @MinNumber(1)
    @Desc("Minimum cap radius in blocks (measured from the cap center to its rim).")
    private int capRadiusMin = 3;

    @MinNumber(1)
    @Desc("Maximum cap radius in blocks (measured from the cap center to its rim).")
    private int capRadiusMax = 5;

    @MinNumber(1)
    @MaxNumber(3)
    @Desc("How many blocks thick the cap shell is. 1 is a thin skin, 3 is a fleshy slab.")
    private int capThickness = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Vertical flatten factor for the cap. 0 leaves the cap at full height, 1 squashes it flat into a disc.")
    private double capSquish = 0.4;

    @MinNumber(0)
    @Desc("How far the cap rim droops downward, in degrees, curling the edge of the cap toward the ground.")
    private double capDroop = 20;

    @MinNumber(0)
    @Desc("How far the cap radius extends past the stem before the rim begins, in blocks. Larger values make the cap overhang the stem more.")
    private double capOverhang = 2;

    @Desc("Optional block forming the gill layer on the underside of the cap (gills or a glow layer), e.g. minecraft:brown_mushroom_block or minecraft:shroomlight. Ignored when gillPalette is set or left null.")
    @RegistryListBlockType
    private String gillBlock = null;

    @Desc("A noise-driven palette for the underside gill layer. When set this overrides the single gillBlock.")
    private IrisMaterialPalette gillPalette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The fraction of underside cap blocks replaced by the gill block when a gill block or palette is set.")
    private double gillChance = 0.85;

    @Desc("Optional block speckled across the top of the cap by noise (white toadstool dots, warts, glowing spots), e.g. minecraft:bone_block or minecraft:white_concrete. Ignored when spotPalette is set or left null.")
    @RegistryListBlockType
    private String spotBlock = null;

    @Desc("A noise-driven palette for the cap top spots. When set this overrides the single spotBlock.")
    private IrisMaterialPalette spotPalette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The fraction of top cap blocks replaced by the spot block, selected by value noise so spots cluster naturally.")
    private double spotChance = 0.18;

    @Desc("If true, generate a bracket / shelf fungus instead of an upright mushroom: a small flat fan that grows sideways off a very short or absent stem, like a polypore clinging to a trunk.")
    private boolean shelf = false;

    @MinNumber(1)
    @Desc("The radius in blocks of the sideways fan when shelf is true.")
    private int shelfRadius = 3;

    public Fungus() {
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

    public String stem() {
        return stem;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> stemPalette() {
        return Optional.ofNullable(stemPalette);
    }

    public String cap() {
        return cap;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> capPalette() {
        return Optional.ofNullable(capPalette);
    }

    public int stemHeightMin() {
        return stemHeightMin;
    }

    public int stemHeightMax() {
        return stemHeightMax;
    }

    public int stemWidth() {
        return stemWidth;
    }

    public double stemCurve() {
        return stemCurve;
    }

    public double stemLeanAzimuth() {
        return stemLeanAzimuth;
    }

    public double stemWaveAmplitude() {
        return stemWaveAmplitude;
    }

    public double stemWavePeriods() {
        return stemWavePeriods;
    }

    public FungusCapShape capShape() {
        return capShape == null ? FungusCapShape.DOME : capShape;
    }

    public int capRadiusMin() {
        return capRadiusMin;
    }

    public int capRadiusMax() {
        return capRadiusMax;
    }

    public int capThickness() {
        return capThickness;
    }

    public double capSquish() {
        return capSquish;
    }

    public double capDroop() {
        return capDroop;
    }

    public double capOverhang() {
        return capOverhang;
    }

    /** Empty where the pack sets none. */
    public Optional<String> gillBlock() {
        return Optional.ofNullable(gillBlock);
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> gillPalette() {
        return Optional.ofNullable(gillPalette);
    }

    public double gillChance() {
        return gillChance;
    }

    /** Empty where the pack sets none. */
    public Optional<String> spotBlock() {
        return Optional.ofNullable(spotBlock);
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> spotPalette() {
        return Optional.ofNullable(spotPalette);
    }

    public double spotChance() {
        return spotChance;
    }

    public boolean shelf() {
        return shelf;
    }

    public int shelfRadius() {
        return shelfRadius;
    }
}
