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

package com.volmit.iris.engine.object.procedural.coral;

import com.volmit.iris.engine.object.ObjectPlaceMode;
import com.volmit.iris.engine.object.CarvingMode;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectTranslate;
import com.volmit.iris.engine.object.IrisStiltSettings;
import com.volmit.iris.engine.object.IrisObjectLimit;
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBlockType;
import com.volmit.iris.engine.object.annotations.Required;

import java.util.Optional;

@Snippet("coral")
@Desc("A single procedurally generated coral reef structure. Iris bakes a pool of deterministic variants from these settings and scatters them across the seafloor at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file. Mirrors the procedural tree system but tuned for underwater reefs: structural blocks are waterlogged so the coral stays alive, and placement defaults to anchoring on the terrain beneath the water surface.")
public final class Coral {
    @Desc("A human readable name used in logs and as the variant load key.")
    private String name = "coral";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per chunk for this coral to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this coral. Higher means more variety at a small memory cost.")
    private int variants = 6;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Desc("The placement mode used to anchor the coral to the seafloor.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Desc("Rotate this coral's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Desc("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Desc("Whether this coral may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Desc("If true (default for coral), the coral anchors on the terrain height beneath the water, so it grows up from the seafloor rather than floating at the water surface.")
    private boolean underwater = true;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the seafloor.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Desc("If true (default), every resolved coral block that is waterloggable is forced waterlogged so the coral does not die in water (coral fans, sea pickles and kelp also waterlog). Set false to place dead/dry coral.")
    private boolean waterlogged = true;

    @Desc("The overall silhouette this coral grows into (branching, fan, brain, pillar, tendril). Each form runs a different generation routine.")
    private CoralForm form = CoralForm.BRANCHING;

    @Required
    @Desc("The structural coral block (the living wood of the reef), e.g. minecraft:tube_coral_block. Ignored when blockPalette is set.")
    @RegistryListBlockType
    private String block = "minecraft:tube_coral_block";

    @Desc("A noise-driven palette for the structural coral block. When set this overrides the single block, letting the reef mix tube/brain/bubble/fire/horn coral_block tones across its body.")
    private IrisMaterialPalette blockPalette;

    @Desc("Optional tip block placed at branch tips and at the very top of the structure, e.g. coral fans or minecraft:sea_pickle. Ignored when tipPalette is set. Null disables tips.")
    @RegistryListBlockType
    private String tipBlock = null;

    @Desc("A noise-driven palette for the tip block. When set this overrides the single tipBlock, letting tips mix fan / pickle decorations.")
    private IrisMaterialPalette tipPalette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per eligible tip position to place a tip block (coral fan / sea pickle).")
    private double tipChance = 0.6;

    @MinNumber(1)
    @Desc("Minimum overall height of the coral in blocks.")
    private int heightMin = 4;

    @MinNumber(1)
    @Desc("Maximum overall height of the coral in blocks.")
    private int heightMax = 8;

    @MinNumber(0)
    @Desc("Horizontal spread of the structure in blocks. For BRANCHING this is how far arms reach out, for BRAIN/PILLAR/FAN it widens the base footprint.")
    private double spread = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Lateral noise wobble (0-1) applied to branches and tendrils so they are not ruler-straight. 0 is perfectly straight, 1 is heavily wandering.")
    private double sway = 0.5;

    @MinNumber(1)
    @MaxNumber(12)
    @Desc("BRANCHING only: how many arms sprout from the central stalk.")
    private int branchCount = 4;

    @MinNumber(1)
    @Desc("BRANCHING only: the length of each arm in blocks before reaching its tip.")
    private double branchLength = 3;

    @MinNumber(0)
    @MaxNumber(90)
    @Desc("BRANCHING only: the upward elevation in degrees of each arm from horizontal. 90 is straight up, 0 is flat out.")
    private double branchElevation = 55;

    @Desc("BRANCHING only: how the arms are distributed around the stalk by azimuth.")
    private CoralBranchAzimuth branchAzimuth = CoralBranchAzimuth.GOLDEN_ANGLE;

    @Desc("BRANCHING only: if true, each arm splits once into a small fan of sub-arms for a bushier reef.")
    private boolean subBranches = true;

    @MinNumber(1)
    @MaxNumber(5)
    @Desc("BRANCHING only: how many sub-arms each arm splits into when subBranches is true.")
    private int subBranchCount = 2;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("BRANCHING only: the length of each sub-arm as a fraction of its parent arm.")
    private double subBranchScale = 0.5;

    @MinNumber(0)
    @MaxNumber(4)
    @Desc("BRANCHING and PILLAR only: the radius of the small coral tip cluster grown at each tip.")
    private int tipClusterRadius = 1;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("BRAIN only: the horizontal radius of the brain-coral blob in blocks.")
    private int brainRadius = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("BRAIN only: how strongly 3D value noise perturbs the ellipsoid surface, for a wrinkled organic blob. 0 is a smooth dome.")
    private double brainRoughness = 0.35;

    @MinNumber(1)
    @MaxNumber(12)
    @Desc("PILLAR only: the radius of the stout vertical column in blocks.")
    private int pillarRadius = 1;

    @MinNumber(1)
    @MaxNumber(8)
    @Desc("FAN only: the half-width of the upright fan plane in blocks (the disc widens toward its mid-height).")
    private int fanWidth = 3;

    @MinNumber(1)
    @MaxNumber(12)
    @Desc("TENDRIL only: how many thin wavy stalks rise from the base.")
    private int tendrilCount = 4;

    public Coral() {
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

    public boolean waterlogged() {
        return waterlogged;
    }

    public CoralForm form() {
        return form == null ? CoralForm.BRANCHING : form;
    }

    public String block() {
        return block;
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> blockPalette() {
        return Optional.ofNullable(blockPalette);
    }

    /** Empty where the pack sets none. */
    public Optional<String> tipBlock() {
        return Optional.ofNullable(tipBlock);
    }

    /** Empty where the pack sets none. */
    public Optional<IrisMaterialPalette> tipPalette() {
        return Optional.ofNullable(tipPalette);
    }

    public double tipChance() {
        return tipChance;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public double spread() {
        return spread;
    }

    public double sway() {
        return sway;
    }

    public int branchCount() {
        return branchCount;
    }

    public double branchLength() {
        return branchLength;
    }

    public double branchElevation() {
        return branchElevation;
    }

    public CoralBranchAzimuth branchAzimuth() {
        return branchAzimuth == null ? CoralBranchAzimuth.GOLDEN_ANGLE : branchAzimuth;
    }

    public boolean subBranches() {
        return subBranches;
    }

    public int subBranchCount() {
        return subBranchCount;
    }

    public double subBranchScale() {
        return subBranchScale;
    }

    public int tipClusterRadius() {
        return tipClusterRadius;
    }

    public int brainRadius() {
        return brainRadius;
    }

    public double brainRoughness() {
        return brainRoughness;
    }

    public int pillarRadius() {
        return pillarRadius;
    }

    public int fanWidth() {
        return fanWidth;
    }

    public int tendrilCount() {
        return tendrilCount;
    }
}
