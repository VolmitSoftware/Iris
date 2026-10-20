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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.ObjectPlaceMode;
import com.volmit.iris.engine.object.CarvingMode;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectTranslate;
import com.volmit.iris.engine.object.IrisStiltSettings;
import com.volmit.iris.engine.object.IrisObjectLimit;
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListBlockType;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.object.procedural.ProceduralObjectCatalog;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Snippet(ProceduralObjectCatalog.TREE_FOLDER)
@Desc("A single procedurally generated tree. Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file.")
public final class Tree {
    @Desc("A human readable name used in logs and as the variant load key.")
    private String name = "procedural-tree";

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per chunk for this tree to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Desc("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("How many distinct variants to pre-bake for this tree. Higher means more variety at a small memory cost.")
    private int variants = 8;

    @Desc("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Desc("If true (default) leaves are placed with vanilla distance-from-wood values and persistent=false so leaf decay behaves naturally and the tree reads as plausible. If false, leaves are forced persistent and never decay (a raw object dump).")
    private boolean plausible = true;

    @Desc("The placement mode used to anchor the tree to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Desc("Rotate this tree's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Desc("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Desc("Whether this tree may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Desc("If true, the tree anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Desc("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Desc("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Required
    @Desc("The trunk (log) block, e.g. minecraft:oak_log. Ignored when trunkPalette is set.")
    @RegistryListBlockType
    private String trunk = "minecraft:oak_log";

    @Desc("A noise-driven palette for the trunk. When set this overrides the single trunk block, letting the trunk mix blocks by noise.")
    private IrisMaterialPalette trunkPalette;

    @Required
    @Desc("The leaf block, e.g. minecraft:oak_leaves. Ignored when leavesPalette is set.")
    @RegistryListBlockType
    private String leaves = "minecraft:oak_leaves";

    @Desc("A noise-driven palette for the leaves. When set this overrides the single leaf block, letting the canopy mix leaf (or other) blocks by noise. Only blocks that are actually leaves get vanilla decay distances.")
    private IrisMaterialPalette leavesPalette;

    @Desc("The named canopy profile that drives default crown radii and shape.")
    private TreeProfile profile = TreeProfile.OAK;

    @MinNumber(1)
    @MaxNumber(ResolvedTree.TALLEST_TREE_BLOCKS)
    @Desc("Minimum trunk height in blocks.")
    private int heightMin = 8;

    @MinNumber(1)
    @MaxNumber(ResolvedTree.TALLEST_TREE_BLOCKS)
    @Desc("Maximum trunk height in blocks.")
    private int heightMax = 12;

    @MinNumber(1)
    @Desc("Base trunk width in blocks. 1 is a single column, 2 is a 2x2 trunk, etc.")
    private int trunkWidth = 1;

    @Desc("The function used to shape trunk width over height.")
    private TreeFunction trunkShape = TreeFunction.CONSTANT;

    @Desc("Width multiplier at the base for the LINEAR trunk shape.")
    private double shapeStart = 1;

    @Desc("Width multiplier at the top for the LINEAR trunk shape.")
    private double shapeEnd = 1;

    @Desc("Steepness for the SIGMOID trunk shape.")
    private double shapeSteepness = 5;

    @Desc("Base for the LOG trunk shape.")
    private double shapeBase = 2.718281828;

    @Desc("Period for the SINE trunk shape.")
    private double shapePeriod = 1;

    @Desc("Amplitude for the SINE trunk shape.")
    private double shapeAmplitude = 0.2;

    @Desc("Pinch position (0-1) for the PARABOLIC trunk shape.")
    private double shapePeakOffset = 0.5;

    @Desc("Minimum width fraction at the waist for the PARABOLIC trunk shape.")
    private double shapeFloor = 0.5;

    @Desc("Compass direction in degrees the trunk leans toward.")
    private double leanAzimuth = 0;

    @Desc("How far the trunk leans, in degrees from vertical. 0 is perfectly upright.")
    private double leanAngle = 0;

    @Desc("How the lean accumulates over height.")
    private TreeFunction trunkCurve = TreeFunction.LINEAR;

    @Desc("Steepness for the SIGMOID trunk curve.")
    private double curveSteepness = 8;

    @Desc("How the lean azimuth itself changes over height (for spiraling or wandering trunks).")
    private TreeAzimuthMode leanAzimuthMode = TreeAzimuthMode.CONSTANT;

    @Desc("Azimuth at the base for the LINEAR azimuth mode.")
    private double azimuthStart = 0;

    @Desc("Azimuth at the top for the LINEAR azimuth mode.")
    private double azimuthEnd = 0;

    @Desc("Number of full turns for the SPIRAL azimuth mode.")
    private double azimuthTurns = 1;

    @Desc("Amplitude for the SINE azimuth mode.")
    private double azimuthAmplitude = 90;

    @Desc("Period for the SINE azimuth mode.")
    private double azimuthPeriod = 1;

    @Desc("Offset for the SINE azimuth mode.")
    private double azimuthOffset = 0;

    @Desc("Scale for the NOISE azimuth mode.")
    private double azimuthScale = 1;

    @MinNumber(1)
    @Desc("Number of branches per whorl ring for the WHORL branch azimuth mode.")
    private int azimuthWhorlCount = 5;

    @Desc("Optional accent leaf block scattered through the canopy (blossoms, shroomlight).")
    @RegistryListBlockType
    private String secondaryLeaves;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The fraction of leaves replaced by the secondary leaf block(s).")
    private double secondaryLeafFraction = 0.35;

    @ArrayType(min = 1, type = TreeSecondaryLeaf.class)
    @Desc("Optional weighted set of accent leaf blocks. When set this overrides the single secondaryLeaves block.")
    private List<TreeSecondaryLeaf> weightedSecondaryLeaves = List.of();

    @Desc("A noise-driven palette for the secondary (accent) leaves. When set this overrides both secondaryLeaves and weightedSecondaryLeaves.")
    private IrisMaterialPalette secondaryLeavesPalette;

    @Desc("Optional secondary trunk block used over a height band (for color-banded trunks). Ignored when secondaryTrunkPalette is set.")
    @RegistryListBlockType
    private String secondaryTrunk;

    @Desc("A noise-driven palette for the secondary trunk band. When set this overrides the single secondaryTrunk block.")
    private IrisMaterialPalette secondaryTrunkPalette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The normalized height where the secondary trunk band starts.")
    private double secondaryTrunkStart = 0.5;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The normalized height where the secondary trunk band ends.")
    private double secondaryTrunkEnd = 1;

    @Desc("If true, a root system extends below (and around) the base so the tree connects to the ground on uneven terrain.")
    private boolean roots = true;

    @Desc("How the root system is built when roots is true.")
    private TreeRootStyle rootStyle = TreeRootStyle.BUTTRESS;

    @MinNumber(0)
    @Desc("Override the root depth/reach in blocks. 0 means auto (scales with tree height).")
    private int rootDepth = 0;

    @MinNumber(0)
    @Desc("Override the root flare radius in blocks. 0 means auto.")
    private double rootFlare = 0;

    @MinNumber(1)
    @MaxNumber(6)
    @Desc("How many separate trunks the tree splits into above forkHeight. 1 is a single trunk.")
    private int trunkForks = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The normalized height where the trunk splits into forks.")
    private double forkHeight = 0.5;

    @Desc("The outward lean angle in degrees of each fork.")
    private double forkAngle = 25;

    @Desc("The leaf crown configuration.")
    private TreeCanopy canopy = new TreeCanopy();

    @ArrayType(min = 1, type = TreeDecorator.class)
    @Desc("Accent decorators applied after the tree is built (fruit, vines, snow, glow).")
    private List<TreeDecorator> decorators = List.of();

    public Tree() {
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

    public boolean plausible() {
        return plausible;
    }

    public ObjectPlaceMode mode() {
        return mode;
    }

    public IrisObjectRotation rotation() {
        return rotation;
    }

    public IrisObjectLimit clamp() {
        return clamp;
    }

    public CarvingMode carvingSupport() {
        return carvingSupport;
    }

    public boolean underwater() {
        return underwater;
    }

    public IrisObjectTranslate translate() {
        return translate;
    }

    public Optional<IrisStiltSettings> stiltSettings() {
        return Optional.ofNullable(stiltSettings);
    }

    public String trunk() {
        return trunk;
    }

    public Optional<IrisMaterialPalette> trunkPalette() {
        return Optional.ofNullable(trunkPalette);
    }

    public String leaves() {
        return leaves;
    }

    public Optional<IrisMaterialPalette> leavesPalette() {
        return Optional.ofNullable(leavesPalette);
    }

    public TreeProfile profile() {
        return profile;
    }

    public int heightMin() {
        return heightMin;
    }

    public int heightMax() {
        return heightMax;
    }

    public int trunkWidth() {
        return trunkWidth;
    }

    public TreeFunction trunkShape() {
        return trunkShape;
    }

    public double shapeStart() {
        return shapeStart;
    }

    public double shapeEnd() {
        return shapeEnd;
    }

    public double shapeSteepness() {
        return shapeSteepness;
    }

    public double shapeBase() {
        return shapeBase;
    }

    public double shapePeriod() {
        return shapePeriod;
    }

    public double shapeAmplitude() {
        return shapeAmplitude;
    }

    public double shapePeakOffset() {
        return shapePeakOffset;
    }

    public double shapeFloor() {
        return shapeFloor;
    }

    public double leanAzimuth() {
        return leanAzimuth;
    }

    public double leanAngle() {
        return leanAngle;
    }

    public TreeFunction trunkCurve() {
        return trunkCurve;
    }

    public double curveSteepness() {
        return curveSteepness;
    }

    public TreeAzimuthMode leanAzimuthMode() {
        return leanAzimuthMode;
    }

    public double azimuthStart() {
        return azimuthStart;
    }

    public double azimuthEnd() {
        return azimuthEnd;
    }

    public double azimuthTurns() {
        return azimuthTurns;
    }

    public double azimuthAmplitude() {
        return azimuthAmplitude;
    }

    public double azimuthPeriod() {
        return azimuthPeriod;
    }

    public double azimuthOffset() {
        return azimuthOffset;
    }

    public double azimuthScale() {
        return azimuthScale;
    }

    public int azimuthWhorlCount() {
        return azimuthWhorlCount;
    }

    public Optional<String> secondaryLeaves() {
        return Optional.ofNullable(secondaryLeaves);
    }

    public double secondaryLeafFraction() {
        return secondaryLeafFraction;
    }

    public List<TreeSecondaryLeaf> weightedSecondaryLeaves() {
        return weightedSecondaryLeaves == null ? List.of() : Collections.unmodifiableList(weightedSecondaryLeaves);
    }

    public Optional<IrisMaterialPalette> secondaryLeavesPalette() {
        return Optional.ofNullable(secondaryLeavesPalette);
    }

    public Optional<String> secondaryTrunk() {
        return Optional.ofNullable(secondaryTrunk);
    }

    public Optional<IrisMaterialPalette> secondaryTrunkPalette() {
        return Optional.ofNullable(secondaryTrunkPalette);
    }

    public double secondaryTrunkStart() {
        return secondaryTrunkStart;
    }

    public double secondaryTrunkEnd() {
        return secondaryTrunkEnd;
    }

    public boolean roots() {
        return roots;
    }

    public TreeRootStyle rootStyle() {
        return rootStyle;
    }

    public int rootDepth() {
        return rootDepth;
    }

    public double rootFlare() {
        return rootFlare;
    }

    public int trunkForks() {
        return trunkForks;
    }

    public double forkHeight() {
        return forkHeight;
    }

    public double forkAngle() {
        return forkAngle;
    }

    public TreeCanopy canopy() {
        return canopy;
    }

    public List<TreeDecorator> decorators() {
        return decorators == null ? List.of() : Collections.unmodifiableList(decorators);
    }
}
