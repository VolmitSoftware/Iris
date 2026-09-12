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

package art.arcane.iris.generation.decoration.tree;

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
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("procedural-tree")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A single procedurally generated tree. Iris bakes a pool of deterministic variants from these settings and scatters them at world-gen time, exactly like an object placement but generated from scratch instead of loaded from an iob file.")
@Data
public class IrisProceduralTree implements IrisProceduralPlacement {
    private final transient AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    @Description("A human readable name used in logs and as the variant load key.")
    private String name = "procedural-tree";

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The chance per chunk for this tree to attempt placement. Use density for multiple per chunk.")
    private double chance = 0.4;

    @MinNumber(1)
    @Description("If the chance check passes, attempt this many placements in the chunk.")
    private int density = 1;

    @MinNumber(1)
    @MaxNumber(64)
    @Description("How many distinct variants to pre-bake for this tree. Higher means more variety at a small memory cost.")
    private int variants = 8;

    @Description("The base seed for deterministic generation. The same seed and settings always bake the same variants.")
    private long seed = 1337;

    @Description("If true (default) leaves are placed with vanilla distance-from-wood values and persistent=false so leaf decay behaves naturally and the tree reads as plausible. If false, leaves are forced persistent and never decay (a raw object dump).")
    private boolean plausible = true;

    @Description("The placement mode used to anchor the tree to the terrain.")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;

    @Description("Rotate this tree's placement.")
    private IrisObjectRotation rotation = new IrisObjectRotation();

    @Description("Limit the max or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();

    @Description("Whether this tree may place on the terrain surface, under carvings, or both.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;

    @Description("If true, the tree anchors on the terrain height ignoring the water surface.")
    private boolean underwater = false;

    @Description("Translate (offset) this placement along each axis; for example set a negative y to sink it into the ground.")
    private IrisObjectTranslate translate = new IrisObjectTranslate();

    @Description("Settings for the stilt place modes (STILT, MIN_STILT, FAST_STILT, CENTER_STILT, ERODE_STILT, ORGANIC_STILT).")
    private IrisStiltSettings stiltSettings;

    @Description("Settings for the vacuum place modes (VACUUM, VACUUM_HIGH, VACUUM_FAST, VACUUM_ORGANIC).")
    private IrisVacuumSettings vacuumSettings;

    @Required
    @Description("The trunk (log) block, e.g. minecraft:oak_log. Ignored when trunkPalette is set.")
    @RegistryListBlockType
    private String trunk = "minecraft:oak_log";

    @Description("A noise-driven palette for the trunk. When set this overrides the single trunk block, letting the trunk mix blocks by noise.")
    private IrisMaterialPalette trunkPalette = null;

    @Required
    @Description("The leaf block, e.g. minecraft:oak_leaves. Ignored when leavesPalette is set.")
    @RegistryListBlockType
    private String leaves = "minecraft:oak_leaves";

    @Description("A noise-driven palette for the leaves. When set this overrides the single leaf block, letting the canopy mix leaf (or other) blocks by noise. Only blocks that are actually leaves get vanilla decay distances.")
    private IrisMaterialPalette leavesPalette = null;

    @Description("The named canopy profile that drives default crown radii and shape.")
    private IrisTreeProfile profile = IrisTreeProfile.OAK;

    @MinNumber(1)
    @Description("Minimum trunk height in blocks.")
    private int heightMin = 8;

    @MinNumber(1)
    @Description("Maximum trunk height in blocks.")
    private int heightMax = 12;

    @MinNumber(1)
    @Description("Base trunk width in blocks. 1 is a single column, 2 is a 2x2 trunk, etc.")
    private int trunkWidth = 1;

    @Description("The function used to shape trunk width over height.")
    private IrisTreeFunction trunkShape = IrisTreeFunction.CONSTANT;

    @Description("Width multiplier at the base for the LINEAR trunk shape.")
    private double shapeStart = 1;

    @Description("Width multiplier at the top for the LINEAR trunk shape.")
    private double shapeEnd = 1;

    @Description("Steepness for the SIGMOID trunk shape.")
    private double shapeSteepness = 5;

    @Description("Base for the LOG trunk shape.")
    private double shapeBase = 2.718281828;

    @Description("Period for the SINE trunk shape.")
    private double shapePeriod = 1;

    @Description("Amplitude for the SINE trunk shape.")
    private double shapeAmplitude = 0.2;

    @Description("Pinch position (0-1) for the PARABOLIC trunk shape.")
    private double shapePeakOffset = 0.5;

    @Description("Minimum width fraction at the waist for the PARABOLIC trunk shape.")
    private double shapeFloor = 0.5;

    @Description("Compass direction in degrees the trunk leans toward.")
    private double leanAzimuth = 0;

    @Description("How far the trunk leans, in degrees from vertical. 0 is perfectly upright.")
    private double leanAngle = 0;

    @Description("How the lean accumulates over height.")
    private IrisTreeFunction trunkCurve = IrisTreeFunction.LINEAR;

    @Description("Steepness for the SIGMOID trunk curve.")
    private double curveSteepness = 8;

    @Description("How the lean azimuth itself changes over height (for spiraling or wandering trunks).")
    private IrisTreeAzimuthMode leanAzimuthMode = IrisTreeAzimuthMode.CONSTANT;

    @Description("Azimuth at the base for the LINEAR azimuth mode.")
    private double azimuthStart = 0;

    @Description("Azimuth at the top for the LINEAR azimuth mode.")
    private double azimuthEnd = 0;

    @Description("Number of full turns for the SPIRAL azimuth mode.")
    private double azimuthTurns = 1;

    @Description("Amplitude for the SINE azimuth mode.")
    private double azimuthAmplitude = 90;

    @Description("Period for the SINE azimuth mode.")
    private double azimuthPeriod = 1;

    @Description("Offset for the SINE azimuth mode.")
    private double azimuthOffset = 0;

    @Description("Scale for the NOISE azimuth mode.")
    private double azimuthScale = 1;

    @MinNumber(1)
    @Description("Number of branches per whorl ring for the WHORL branch azimuth mode.")
    private int azimuthWhorlCount = 5;

    @Description("Optional accent leaf block scattered through the canopy (blossoms, shroomlight).")
    @RegistryListBlockType
    private String secondaryLeaves = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The fraction of leaves replaced by the secondary leaf block(s).")
    private double secondaryLeafFraction = 0.35;

    @ArrayType(min = 1, type = IrisTreeSecondaryLeaf.class)
    @Description("Optional weighted set of accent leaf blocks. When set this overrides the single secondaryLeaves block.")
    private KList<IrisTreeSecondaryLeaf> weightedSecondaryLeaves = new KList<>();

    @Description("A noise-driven palette for the secondary (accent) leaves. When set this overrides both secondaryLeaves and weightedSecondaryLeaves.")
    private IrisMaterialPalette secondaryLeavesPalette = null;

    @Description("Optional secondary trunk block used over a height band (for color-banded trunks). Ignored when secondaryTrunkPalette is set.")
    @RegistryListBlockType
    private String secondaryTrunk = null;

    @Description("A noise-driven palette for the secondary trunk band. When set this overrides the single secondaryTrunk block.")
    private IrisMaterialPalette secondaryTrunkPalette = null;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The normalized height where the secondary trunk band starts.")
    private double secondaryTrunkStart = 0.5;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The normalized height where the secondary trunk band ends.")
    private double secondaryTrunkEnd = 1;

    @Description("If true, a root system extends below (and around) the base so the tree connects to the ground on uneven terrain.")
    private boolean roots = true;

    @Description("How the root system is built when roots is true.")
    private IrisTreeRootStyle rootStyle = IrisTreeRootStyle.BUTTRESS;

    @MinNumber(0)
    @Description("Override the root depth/reach in blocks. 0 means auto (scales with tree height).")
    private int rootDepth = 0;

    @MinNumber(0)
    @Description("Override the root flare radius in blocks. 0 means auto.")
    private double rootFlare = 0;

    @MinNumber(1)
    @MaxNumber(6)
    @Description("How many separate trunks the tree splits into above forkHeight. 1 is a single trunk.")
    private int trunkForks = 1;

    @MinNumber(0)
    @MaxNumber(1)
    @Description("The normalized height where the trunk splits into forks.")
    private double forkHeight = 0.5;

    @Description("The outward lean angle in degrees of each fork.")
    private double forkAngle = 25;

    @Description("The leaf crown configuration.")
    private IrisTreeCanopy canopy = new IrisTreeCanopy();

    @ArrayType(min = 1, type = IrisTreeDecorator.class)
    @Description("Accent decorators applied after the tree is built (fruit, vines, snow, glow).")
    private KList<IrisTreeDecorator> decorators = new KList<>();

    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> {
            KList<IrisObject> baked = new KList<>();
            int count = Math.max(1, variants);
            int lo = Math.min(heightMin, heightMax);
            int hi = Math.max(heightMin, heightMax);
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

                IrisObject object = ProceduralTreeGenerator.generate(this, Math.max(2, height), new RNG(seed + (i * 7919L)), data);
                if (object == null || object.getBlocks().isEmpty()) {
                    continue;
                }
                object.setLoadKey(getVariantLoadKey(i));
                object.setLoader(data);
                baked.add(object);
            }

            if (new java.io.File("plugins/Iris/goldendebug.txt").isFile()) {
                for (IrisObject object : baked) {
                    long[] digest = new long[]{0L};
                    object.getBlocks().forEach((vector, state) -> {
                        long position = ((long) vector.getBlockX() << 40) ^ ((long) vector.getBlockY() << 20) ^ vector.getBlockZ();
                        digest[0] ^= Long.rotateLeft(position * 0x9E3779B97F4A7C15L ^ state.key().hashCode(), (int) (position & 63));
                    });
                    IrisLogging.debug("Goldendebug bake: " + object.getLoadKey() + " blocks=" + object.getBlocks().size() + " digest=" + Long.toHexString(digest[0]));
                }
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

    public String getVariantLoadKey(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        return "procedural/tree/" + name + "#" + index;
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
}
