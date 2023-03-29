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
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.data.WeightedRandom;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.data.BlockData;

@Snippet("object-placer")
@EqualsAndHashCode()
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris object placer. It places objects.")
@Data
public class IrisObjectPlacement {
    private final transient AtomicCache<CNG> surfaceWarp = new AtomicCache<>();
    @RegistryListResource(IrisObject.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of objects to place")
    private KList<String> place = new KList<>();
    @Desc("Rotate this objects placement")
    private IrisObjectRotation rotation = new IrisObjectRotation();
    @Desc("Limit the max height or min height of placement.")
    private IrisObjectLimit clamp = new IrisObjectLimit();
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The maximum layer level of a snow filter overtop of this placement. Set to 0 to disable. Max of 1.")
    private double snow = 0;
    @Desc("Whether or not this object can be targeted by a dolphin.")
    private boolean isDolphinTarget = false;
    @Desc("The slope at which this object can be placed. Range from 0 to 10 by default. Calculated from a 3-block radius from the center of the object placement.")
    private IrisSlopeClip slopeCondition = new IrisSlopeClip();
    @Desc("Set to true to add the rotation of the direction of the slope of the terrain (wherever the slope is going down) to the y-axis rotation of the object." +
            "Rounded to 90 degrees. Adds the *min* rotation of the y axis as well (to still allow you to rotate objects nicely). Discards *max* and *interval* on *yaxis*")
    private boolean rotateTowardsSlope = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance for this to place in a chunk. If you need multiple per chunk, set this to 1 and use density.")
    private double chance = 1;
    @MinNumber(1)
    @Desc("If the chance check passes, place this many in a single chunk")
    private int density = 1;
    @Desc("If the chance check passes, and you specify this, it picks a number in the range based on noise, and 'density' is ignored.")
    private IrisStyledRange densityStyle = null;
    @Desc("When stilting is enabled, this object will define various properties related to it.")
    private IrisStiltSettings stiltSettings;
    @MaxNumber(64)
    @MinNumber(0)
    @Desc("When bore is enabled, expand max-y of the cuboid it removes")
    private int boreExtendMaxY = 0;
    @ArrayType(min = 1, type = IrisObjectMarker.class)
    @Desc("Add markers to blocks in this object")
    private KList<IrisObjectMarker> markers = new KList<>();
    @MaxNumber(64)
    @MinNumber(-1)
    @Desc("When bore is enabled, lower min-y of the cuboid it removes")
    private int boreExtendMinY = 0;
    @Desc("If set to true, objects will place on the terrain height, ignoring the water surface.")
    private boolean underwater = false;
    @Desc("If set to true, objects will place in carvings (such as underground) or under an overhang.")
    private CarvingMode carvingSupport = CarvingMode.SURFACE_ONLY;
    @Desc("If this is defined, this object wont place on the terrain heightmap, but instead on this virtual heightmap")
    private IrisNoiseGenerator heightmap;
    @Desc("If set to true, Iris will try to fill the insides of 'rooms' and 'pockets' where air should fit based off of raytrace checks. This prevents a village house placing in an area where a tree already exists, and instead replaces the parts of the tree where the interior of the structure is. \n\nThis operation does not affect warmed-up generation speed however it does slow down loading objects.")
    private boolean smartBore = false;
    @Desc("If set to true, Blocks placed underwater that could be waterlogged are waterlogged.")
    private boolean waterloggable = false;
    @Desc("If set to true, objects will place on the fluid height level Such as boats.")
    private boolean onwater = false;
    @Desc("If set to true, this object will only place parts of itself where blocks already exist. Warning: Melding is very performance intensive!")
    private boolean meld = false;
    @Desc("If set to true, this object will place from the ground up instead of height checks when not y locked to the surface. This is not compatable with X and Z axis rotations (it may look off)")
    private boolean bottom = false;
    @Desc("If set to true, air will be placed before the schematic places.")
    private boolean bore = false;
    @Desc("Use a generator to warp the field of coordinates. Using simplex for example would make a square placement warp like a flag")
    private IrisGeneratorStyle warp = new IrisGeneratorStyle(NoiseStyle.FLAT);
    @Desc("If the place mode is set to CENTER_HEIGHT_RIGID and you have an X/Z translation, Turning on translate center will also translate the center height check.")
    private boolean translateCenter = false;
    @Desc("The placement mode")
    private ObjectPlaceMode mode = ObjectPlaceMode.CENTER_HEIGHT;
    @ArrayType(min = 1, type = IrisObjectReplace.class)
    @Desc("Find and replace blocks")
    private KList<IrisObjectReplace> edit = new KList<>();
    @Desc("Translate this object's placement")
    private IrisObjectTranslate translate = new IrisObjectTranslate();
    @Desc("Scale Objects")
    private IrisObjectScale scale = new IrisObjectScale();
    @ArrayType(min = 1, type = IrisObjectLoot.class)
    @Desc("The loot tables to apply to these objects")
    private KList<IrisObjectLoot> loot = new KList<>();
    @Desc("Whether the given loot tables override any and all other loot tables available in the dimension, region or biome.")
    private boolean overrideGlobalLoot = false;
    @Desc("This object / these objects override the following trees when they grow...")
    @ArrayType(min = 1, type = IrisTree.class)
    private KList<IrisTree> trees = new KList<>();
    private transient AtomicCache<TableCache> cache = new AtomicCache<>();

    public IrisObjectPlacement toPlacement(String... place) {
        IrisObjectPlacement p = new IrisObjectPlacement();
        p.setPlace(new KList<>(place));
        p.setTranslateCenter(translateCenter);
        p.setMode(mode);
        p.setEdit(edit);
        p.setTranslate(translate);
        p.setWarp(warp);
        p.setBore(bore);
        p.setMeld(meld);
        p.setWaterloggable(waterloggable);
        p.setOnwater(onwater);
        p.setSmartBore(smartBore);
        p.setCarvingSupport(carvingSupport);
        p.setUnderwater(underwater);
        p.setBoreExtendMaxY(boreExtendMaxY);
        p.setBoreExtendMinY(boreExtendMinY);
        p.setStiltSettings(stiltSettings);
        p.setDensity(density);
        p.setChance(chance);
        p.setSnow(snow);
        p.setClamp(clamp);
        p.setRotation(rotation);
        p.setLoot(loot);
        return p;
    }

    public CNG getSurfaceWarp(RNG rng, IrisData data) {
        return surfaceWarp.aquire(() ->
                getWarp().create(rng, data));
    }

    public double warp(RNG rng, double x, double y, double z, IrisData data) {
        return getSurfaceWarp(rng, data).fitDouble(-(getWarp().getMultiplier() / 2D), (getWarp().getMultiplier() / 2D), x, y, z);
    }

    public IrisObject getObject(DataProvider g, RNG random) {
        if (place.isEmpty()) {
            return null;
        }

        return g.getData().getObjectLoader().load(place.get(random.nextInt(place.size())));
    }

    public boolean matches(IrisTreeSize size, TreeType type) {
        for (IrisTree i : getTrees()) {
            if (i.matches(size, type)) {
                return true;
            }
        }

        return false;
    }

    public int getDensity() {
        if (densityStyle == null) {
            return density;
        }
        return densityStyle.getMid();
    }

    public int getDensity(RNG rng, double x, double z, IrisData data) {
        if (densityStyle == null) {
            return density;
        }

        return (int) Math.round(densityStyle.get(rng, x, z, data));
    }

    private TableCache getCache(IrisData manager) {
        return cache.aquire(() -> {
            TableCache tc = new TableCache();

            for (IrisObjectLoot loot : getLoot()) {
                if (loot == null)
                    continue;
                IrisLootTable table = manager.getLootLoader().load(loot.getName());
                if (table == null) {
                    Iris.warn("Couldn't find loot table " + loot.getName());
                    continue;
                }

                if (loot.getFilter().isEmpty()) //Table applies to all containers
                {
                    tc.global.put(table, loot.getWeight());
                } else if (!loot.isExact()) //Table is meant to be by type
                {
                    for (BlockData filterData : loot.getFilter(manager)) {
                        if (!tc.basic.containsKey(filterData.getMaterial())) {
                            tc.basic.put(filterData.getMaterial(), new WeightedRandom<>());
                        }

                        tc.basic.get(filterData.getMaterial()).put(table, loot.getWeight());
                    }
                } else //Filter is exact
                {
                    for (BlockData filterData : loot.getFilter(manager)) {
                        if (!tc.exact.containsKey(filterData.getMaterial())) {
                            tc.exact.put(filterData.getMaterial(), new KMap<>());
                        }

                        if (!tc.exact.get(filterData.getMaterial()).containsKey(filterData)) {
                            tc.exact.get(filterData.getMaterial()).put(filterData, new WeightedRandom<>());
                        }

                        tc.exact.get(filterData.getMaterial()).get(filterData).put(table, loot.getWeight());
                    }
                }
            }
            return tc;
        });
    }

    /**
     * Gets the loot table that should be used for the block
     *
     * @param data        The block data of the block
     * @param dataManager Iris Data Manager
     * @return The loot table it should use.
     */
    public IrisLootTable getTable(BlockData data, IrisData dataManager) {
        TableCache cache = getCache(dataManager);

        if (B.isStorageChest(data)) {
            IrisLootTable picked = null;
            if (cache.exact.containsKey(data.getMaterial()) && cache.exact.containsKey(data)) {
                picked = cache.exact.get(data.getMaterial()).get(data).pullRandom();
            } else if (cache.basic.containsKey(data.getMaterial())) {
                picked = cache.basic.get(data.getMaterial()).pullRandom();
            } else if (cache.global.getSize() > 0) {
                picked = cache.global.pullRandom();
            }

            return picked;
        }

        return null;
    }

    private static class TableCache {
        final transient WeightedRandom<IrisLootTable> global = new WeightedRandom<>();
        final transient KMap<Material, WeightedRandom<IrisLootTable>> basic = new KMap<>();
        final transient KMap<Material, KMap<BlockData, WeightedRandom<IrisLootTable>>> exact = new KMap<>();
    }
}
