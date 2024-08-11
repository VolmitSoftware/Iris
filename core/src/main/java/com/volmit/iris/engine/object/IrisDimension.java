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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.core.nms.datapack.IDataFixer;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Represents a dimension")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisDimension extends IrisRegistrant {
    public static final BlockData STONE = Material.STONE.createBlockData();
    public static final BlockData WATER = Material.WATER.createBlockData();
    private static final String DP_OVERWORLD_DEFAULT = """
            {
              "ambient_light": 0.0,
              "bed_works": true,
              "coordinate_scale": 1.0,
              "effects": "minecraft:overworld",
              "has_ceiling": false,
              "has_raids": true,
              "has_skylight": true,
              "infiniburn": "#minecraft:infiniburn_overworld",
              "monster_spawn_block_light_limit": 0,
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              },
              "natural": true,
              "piglin_safe": false,
              "respawn_anchor_works": false,
              "ultrawarm": false
            }""";

    private static final String DP_NETHER_DEFAULT = """
            {
              "ambient_light": 0.1,
              "bed_works": false,
              "coordinate_scale": 8.0,
              "effects": "minecraft:the_nether",
              "fixed_time": 18000,
              "has_ceiling": true,
              "has_raids": false,
              "has_skylight": false,
              "infiniburn": "#minecraft:infiniburn_nether",
              "monster_spawn_block_light_limit": 15,
              "monster_spawn_light_level": 7,
              "natural": false,
              "piglin_safe": true,
              "respawn_anchor_works": true,
              "ultrawarm": true
            }""";

    private static final String DP_END_DEFAULT = """
            {
              "ambient_light": 0.0,
              "bed_works": false,
              "coordinate_scale": 1.0,
              "effects": "minecraft:the_end",
              "fixed_time": 6000,
              "has_ceiling": false,
              "has_raids": true,
              "has_skylight": false,
              "infiniburn": "#minecraft:infiniburn_end",
              "monster_spawn_block_light_limit": 0,
              "monster_spawn_light_level": {
                "type": "minecraft:uniform",
                "value": {
                  "max_inclusive": 7,
                  "min_inclusive": 0
                }
              },
              "natural": false,
              "piglin_safe": false,
              "respawn_anchor_works": false,
              "ultrawarm": false
            }""";
    private final transient AtomicCache<Position2> parallaxSize = new AtomicCache<>();
    private final transient AtomicCache<CNG> rockLayerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> fluidLayerGenerator = new AtomicCache<>();
    private final transient AtomicCache<CNG> coordFracture = new AtomicCache<>();
    private final transient AtomicCache<Double> sinr = new AtomicCache<>();
    private final transient AtomicCache<Double> cosr = new AtomicCache<>();
    private final transient AtomicCache<Double> rad = new AtomicCache<>();
    private final transient AtomicCache<Boolean> featuresUsed = new AtomicCache<>();
    private final transient AtomicCache<KList<Position2>> strongholdsCache = new AtomicCache<>();
    @MinNumber(2)
    @Required
    @Desc("The human readable name of this dimension")
    private String name = "A Dimension";
    @MinNumber(1)
    @MaxNumber(2032)
    @Desc("Maximum height at which players can be teleported to through gameplay.")
    private int logicalHeight = 256;
    @Desc("Maximum height at which players can be teleported to through gameplay.")
    private int logicalHeightEnd = 256;
    @Desc("Maximum height at which players can be teleported to through gameplay.")
    private int logicalHeightNether = 256;
    @RegistryListResource(IrisJigsawStructure.class)
    @Desc("If defined, Iris will place the given jigsaw structure where minecraft should place the overworld stronghold.")
    private String stronghold;
    @Desc("If set to true, Iris will remove chunks to allow visualizing cross sections of chunks easily")
    private boolean debugChunkCrossSections = false;
    @Desc("Vertically split up the biome palettes with 3 air blocks in between to visualize them")
    private boolean explodeBiomePalettes = false;
    @Desc("Studio Mode for testing different parts of the world")
    private StudioMode studioMode = StudioMode.NORMAL;
    @MinNumber(1)
    @MaxNumber(16)
    @Desc("Customize the palette height explosion")
    private int explodeBiomePaletteSize = 3;
    @MinNumber(2)
    @MaxNumber(16)
    @Desc("Every X/Z % debugCrossSectionsMod == 0 cuts the chunk")
    private int debugCrossSectionsMod = 3;
    @Desc("The average distance between strongholds")
    private int strongholdJumpDistance = 1280;
    @Desc("Define the maximum strongholds to place")
    private int maxStrongholds = 14;
    @Desc("Tree growth override settings")
    private IrisTreeSettings treeSettings = new IrisTreeSettings();
    @Desc("Spawn Entities in this dimension over time. Iris will continually replenish these mobs just like vanilla does.")
    @ArrayType(min = 1, type = String.class)
    @RegistryListResource(IrisSpawner.class)
    private KList<String> entitySpawners = new KList<>();
    @Desc("Reference loot tables in this area")
    private IrisLootReference loot = new IrisLootReference();
    @MinNumber(0)
    @Desc("The version of this dimension. Changing this will stop users from accidentally upgrading (and breaking their worlds).")
    private int version = 1;
    @ArrayType(min = 1, type = IrisBlockDrops.class)
    @Desc("Define custom block drops for this dimension")
    private KList<IrisBlockDrops> blockDrops = new KList<>();
    @Desc("Should bedrock be generated or not.")
    private boolean bedrock = true;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The land chance. Up to 1.0 for total land or 0.0 for total sea")
    private double landChance = 0.625;
    @Desc("The placement style of regions")
    private IrisGeneratorStyle regionStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of land/sea")
    private IrisGeneratorStyle continentalStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of biomes")
    private IrisGeneratorStyle landBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of biomes")
    private IrisGeneratorStyle shoreBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of biomes")
    private IrisGeneratorStyle seaBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("The placement style of biomes")
    private IrisGeneratorStyle caveBiomeStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @Desc("Instead of filling objects with air, fills them with cobweb so you can see them")
    private boolean debugSmartBore = false;
    @Desc("Generate decorations or not")
    private boolean decorate = true;
    @Desc("Use post processing or not")
    private boolean postProcessing = true;
    @Desc("Add slabs in post processing")
    private boolean postProcessingSlabs = true;
    @Desc("Add painted walls in post processing")
    private boolean postProcessingWalls = true;
    @Desc("Carving configuration for the dimension")
    private IrisCarving carving = new IrisCarving();
    @Desc("Configuration of fluid bodies such as rivers & lakes")
    private IrisFluidBodies fluidBodies = new IrisFluidBodies();
    @Desc("forceConvertTo320Height")
    private Boolean forceConvertTo320Height = false;
    @Desc("The world environment")
    private Environment environment = Environment.NORMAL;
    @RegistryListResource(IrisRegion.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("Define all of the regions to include in this dimension. Dimensions -> Regions -> Biomes -> Objects etc")
    private KList<String> regions = new KList<>();
    @ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
    @Desc("Jigsaw structures")
    private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();
    @Desc("The jigsaw structure divisor to use when generating missing jigsaw placement values")
    private double jigsawStructureDivisor = 18;
    @Required
    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The fluid height for this dimension")
    private int fluidHeight = 63;
    @Desc("Define the min and max Y bounds of this dimension. Please keep in mind that Iris internally generates from 0 to (max - min). \n\nFor example at -64 to 320, Iris is internally generating to 0 to 384, then on outputting chunks, it shifts it down by the min height (64 blocks). The default is -64 to 320. \n\nThe fluid height is placed at (fluid height + min height). So a fluid height of 63 would actually show up in the world at 1.")
    private IrisRange dimensionHeight = new IrisRange(-64, 320);
    @Desc("Define the min and max Y bounds of this dimension. Please keep in mind that Iris internally generates from 0 to (max - min). \n\nFor example at -64 to 320, Iris is internally generating to 0 to 384, then on outputting chunks, it shifts it down by the min height (64 blocks). The default is -64 to 320. \n\nThe fluid height is placed at (fluid height + min height). So a fluid height of 63 would actually show up in the world at 1.")
    private IrisRange dimensionHeightEnd = new IrisRange(-64, 320);
    @Desc("Define the min and max Y bounds of this dimension. Please keep in mind that Iris internally generates from 0 to (max - min). \n\nFor example at -64 to 320, Iris is internally generating to 0 to 384, then on outputting chunks, it shifts it down by the min height (64 blocks). The default is -64 to 320. \n\nThe fluid height is placed at (fluid height + min height). So a fluid height of 63 would actually show up in the world at 1.")
    private IrisRange dimensionHeightNether = new IrisRange(-64, 320);
    @Desc("Enable smart vanilla height")
    private boolean smartVanillaHeight = false;
    @RegistryListResource(IrisBiome.class)
    @Desc("Keep this either undefined or empty. Setting any biome name into this will force iris to only generate the specified biome. Great for testing.")
    private String focus = "";
    @RegistryListResource(IrisRegion.class)
    @Desc("Keep this either undefined or empty. Setting any region name into this will force iris to only generate the specified region. Great for testing.")
    private String focusRegion = "";
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Zoom in or out the biome size. Higher = bigger biomes")
    private double biomeZoom = 1D;
    @MinNumber(0)
    @MaxNumber(360)
    @Desc("You can rotate the input coordinates by an angle. This can make terrain appear more natural (less sharp corners and lines). This literally rotates the entire dimension by an angle. Hint: Try 12 degrees or something not on a 90 or 45 degree angle.")
    private double dimensionAngleDeg = 0;
    @Required
    @Desc("Define the mode of this dimension (required!)")
    private IrisDimensionMode mode = new IrisDimensionMode();
    @MinNumber(0)
    @MaxNumber(8192)
    @Desc("Coordinate fracturing applies noise to the input coordinates. This creates the 'iris swirls' and wavy features. The distance pushes these waves further into places they shouldnt be. This is a block value multiplier.")
    private double coordFractureDistance = 20;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Coordinate fracturing zoom. Higher = less frequent warping, Lower = more frequent and rapid warping / swirls.")
    private double coordFractureZoom = 8;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("This zooms in the land space")
    private double landZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("This zooms oceanic biomes")
    private double seaZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Zoom in continents")
    private double continentZoom = 1;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("Change the size of regions")
    private double regionZoom = 1;
    @Desc("Disable this to stop placing objects, entities, features & updates")
    private boolean useMantle = true;
    @Desc("Prevent Leaf decay as if placed in creative mode")
    private boolean preventLeafDecay = false;
    @ArrayType(min = 1, type = IrisDepositGenerator.class)
    @Desc("Define global deposit generators")
    private KList<IrisDepositGenerator> deposits = new KList<>();
    @ArrayType(min = 1, type = IrisShapedGeneratorStyle.class)
    @Desc("Overlay additional noise on top of the interoplated terrain.")
    private KList<IrisShapedGeneratorStyle> overlayNoise = new KList<>();
    @Desc("If true, the spawner system has infinite energy. This is NOT recommended because it would allow for mobs to keep spawning over and over without a rate limit")
    private boolean infiniteEnergy = false;
    @MinNumber(0)
    @MaxNumber(10000)
    @Desc("This is the maximum energy you can have in a dimension")
    private double maximumEnergy = 1000;
    @MinNumber(0.0001)
    @MaxNumber(512)
    @Desc("The rock zoom mostly for zooming in on a wispy palette")
    private double rockZoom = 5;
    @Desc("The palette of blocks for 'stone'")
    private IrisMaterialPalette rockPalette = new IrisMaterialPalette().qclear().qadd("stone");
    @Desc("The palette of blocks for 'water'")
    private IrisMaterialPalette fluidPalette = new IrisMaterialPalette().qclear().qadd("water");
    @Desc("Prevent cartographers to generate explorer maps (Iris worlds only)\nONLY TOUCH IF YOUR SERVER CRASHES WHILE GENERATING EXPLORER MAPS")
    private boolean disableExplorerMaps = false;
    @Desc("Collection of ores to be generated")
    @ArrayType(type = IrisOreGenerator.class, min = 1)
    private KList<IrisOreGenerator> ores = new KList<>();
    @MinNumber(0)
    @MaxNumber(318)
    @Desc("The Subterrain Fluid Layer Height")
    private int caveLavaHeight = 8;

    public int getMaxHeight() {
        return (int) getDimensionHeight().getMax();
    }

    public int getMinHeight() {
        return (int) getDimensionHeight().getMin();
    }

    public BlockData generateOres(int x, int y, int z, RNG rng, IrisData data) {
        if (ores.isEmpty()) {
            return null;
        }
        BlockData b = null;
        for (IrisOreGenerator i : ores) {

            b = i.generate(x, y, z, rng, data);
            if (b != null) {
                return b;
            }
        }
        return null;
    }

    public KList<Position2> getStrongholds(long seed) {
        return strongholdsCache.aquire(() -> {
            KList<Position2> pos = new KList<>();
            int jump = strongholdJumpDistance;
            RNG rng = new RNG((seed * 223) + 12945);
            for (int i = 0; i < maxStrongholds + 1; i++) {
                int m = i + 1;
                pos.add(new Position2(
                        (int) ((rng.i(jump * i) + (jump * i)) * (rng.b() ? -1D : 1D)),
                        (int) ((rng.i(jump * i) + (jump * i)) * (rng.b() ? -1D : 1D))
                ));
            }

            pos.remove(0);

            return pos;
        });
    }

    public int getFluidHeight() {
        return fluidHeight - (int) dimensionHeight.getMin();
    }

    public CNG getCoordFracture(RNG rng, int signature) {
        return coordFracture.aquire(() ->
        {
            CNG coordFracture = CNG.signature(rng.nextParallelRNG(signature));
            coordFracture.scale(0.012 / coordFractureZoom);
            return coordFracture;
        });
    }

    public double getDimensionAngle() {
        return rad.aquire(() -> Math.toRadians(dimensionAngleDeg));
    }

    public Environment getEnvironment() {
        return environment;
    }

    public boolean hasFocusRegion() {
        return !focusRegion.equals("");
    }

    public String getFocusRegion() {
        return focusRegion;
    }

    public double sinRotate() {
        return sinr.aquire(() -> Math.sin(getDimensionAngle()));
    }

    public double cosRotate() {
        return cosr.aquire(() -> Math.cos(getDimensionAngle()));
    }

    public KList<IrisRegion> getAllRegions(DataProvider g) {
        KList<IrisRegion> r = new KList<>();

        for (String i : getRegions()) {
            r.add(g.getData().getRegionLoader().load(i));
        }

        return r;
    }

    public KList<IrisRegion> getAllAnyRegions() {
        KList<IrisRegion> r = new KList<>();

        for (String i : getRegions()) {
            r.add(IrisData.loadAnyRegion(i));
        }

        return r;
    }

    public KList<IrisBiome> getAllBiomes(DataProvider g) {
        return g.getData().getBiomeLoader().loadAll(g.getData().getBiomeLoader().getPossibleKeys());
    }

    public KList<IrisBiome> getAllAnyBiomes() {
        KList<IrisBiome> r = new KList<>();

        for (IrisRegion i : getAllAnyRegions()) {
            if (i == null) {
                continue;
            }

            r.addAll(i.getAllAnyBiomes());
        }

        return r;
    }

    public IrisGeneratorStyle getBiomeStyle(InferredType type) {
        switch (type) {
            case CAVE:
                return caveBiomeStyle;
            case LAND:
                return landBiomeStyle;
            case SEA:
                return seaBiomeStyle;
            case SHORE:
                return shoreBiomeStyle;
            default:
                break;
        }

        return landBiomeStyle;
    }

    public boolean installDataPack(IDataFixer fixer, DataProvider data, File datapacks, double ultimateMaxHeight, double ultimateMinHeight) {
        boolean write = false;
        boolean changed = false;

        IO.delete(new File(datapacks, "iris/data/" + getLoadKey().toLowerCase()));

        for (IrisBiome i : getAllBiomes(data)) {
            if (i.isCustom()) {
                write = true;

                for (IrisBiomeCustom j : i.getCustomDerivitives()) {
                    File output = new File(datapacks, "iris/data/" + getLoadKey().toLowerCase() + "/worldgen/biome/" + j.getId() + ".json");

                    if (!output.exists()) {
                        changed = true;
                    }

                    Iris.verbose("    Installing Data Pack Biome: " + output.getPath());
                    output.getParentFile().mkdirs();
                    try {
                        IO.writeAll(output, j.generateJson(fixer));
                    } catch (IOException e) {
                        Iris.reportError(e);
                        e.printStackTrace();
                    }
                }
            }
        }

        if (!dimensionHeight.equals(new IrisRange(-64, 320)) && this.name.equalsIgnoreCase("overworld")) {
            Iris.verbose("    Installing Data Pack Dimension Types: \"minecraft:overworld\", \"minecraft:the_nether\", \"minecraft:the_end\"");
            dimensionHeight.setMax(ultimateMaxHeight);
            dimensionHeight.setMin(ultimateMinHeight);
            changed = writeDimensionType(fixer, changed, datapacks);
        }

        if (write) {
            File mcm = new File(datapacks, "iris/pack.mcmeta");
            try {
                IO.writeAll(mcm, """
                        {
                            "pack": {
                                "description": "Iris Data Pack. This pack contains all installed Iris Packs' resources.",
                                "pack_format": 10
                            }
                        }
                        """);
            } catch (IOException e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
            Iris.verbose("    Installing Data Pack MCMeta: " + mcm.getPath());
        }

        return changed;
    }

    @Override
    public String getFolderName() {
        return "dimensions";
    }

    @Override
    public String getTypeName() {
        return "Dimension";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }

    public boolean writeDimensionType(IDataFixer fixer, boolean changed, File datapacks) {
        File dimTypeOverworld = new File(datapacks, "iris/data/minecraft/dimension_type/overworld.json");
        if (!dimTypeOverworld.exists())
            changed = true;
        dimTypeOverworld.getParentFile().mkdirs();
        try {
            IO.writeAll(dimTypeOverworld, generateDatapackJsonOverworld(fixer));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }


        File dimTypeNether = new File(datapacks, "iris/data/minecraft/dimension_type/the_nether.json");
        if (!dimTypeNether.exists())
            changed = true;
        dimTypeNether.getParentFile().mkdirs();
        try {
            IO.writeAll(dimTypeNether, generateDatapackJsonNether(fixer));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }


        File dimTypeEnd = new File(datapacks, "iris/data/minecraft/dimension_type/the_end.json");
        if (!dimTypeEnd.exists())
            changed = true;
        dimTypeEnd.getParentFile().mkdirs();
        try {
            IO.writeAll(dimTypeEnd, generateDatapackJsonEnd(fixer));
        } catch (IOException e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return changed;
    }

    private String generateDatapackJsonOverworld(IDataFixer fixer) {
        JSONObject obj = new JSONObject(DP_OVERWORLD_DEFAULT);
        obj.put("min_y", dimensionHeight.getMin());
        obj.put("height", dimensionHeight.getMax() - dimensionHeight.getMin());
        obj.put("logical_height", logicalHeight);
        return fixer.fixDimension(obj).toString(4);
    }

    private String generateDatapackJsonNether(IDataFixer fixer) {
        JSONObject obj = new JSONObject(DP_NETHER_DEFAULT);
        obj.put("min_y", dimensionHeightNether.getMin());
        obj.put("height", dimensionHeightNether.getMax() - dimensionHeightNether.getMin());
        obj.put("logical_height", logicalHeightNether);
        return fixer.fixDimension(obj).toString(4);
    }

    private String generateDatapackJsonEnd(IDataFixer fixer) {
        JSONObject obj = new JSONObject(DP_END_DEFAULT);
        obj.put("min_y", dimensionHeightEnd.getMin());
        obj.put("height", dimensionHeightEnd.getMax() - dimensionHeightEnd.getMin());
        obj.put("logical_height", logicalHeightEnd);
        return fixer.fixDimension(obj).toString(4);
    }
}
