/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.data.DataProvider;
import com.volmit.iris.engine.noise.CNG;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.VanillaBiomeMap;
import com.volmit.iris.util.inventorygui.RandomColor;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.*;
import java.util.Random;


@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an iris region")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegistrant implements IRare {
    @MinNumber(2)
    @Required
    @Desc("The name of the region")
    private String name = "A Region";

    @ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
    @Desc("Jigsaw structures")
    private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();

    @Desc("Add random chances for terrain features")
    @ArrayType(min = 1, type = IrisFeaturePotential.class)
    private KList<IrisFeaturePotential> features = new KList<>();

    @ArrayType(min = 1, type = IrisEffect.class)
    @Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
    private KList<IrisEffect> effects = new KList<>();

    @Desc("Spawn Entities in this region over time. Iris will continually replenish these mobs just like vanilla does.")
    @ArrayType(min = 1, type = String.class)
    @RegistryListSpawner
    private KList<String> entitySpawners = new KList<>();

    @MinNumber(1)
    @MaxNumber(128)
    @Desc("The rarity of the region")
    private int rarity = 1;

    @ArrayType(min = 1, type = IrisBlockDrops.class)
    @Desc("Define custom block drops for this region")
    private KList<IrisBlockDrops> blockDrops = new KList<>();

    @MinNumber(0.0001)
    @MaxNumber(1)
    @Desc("The shore ration (How much percent of land should be a shore)")
    private double shoreRatio = 0.13;

    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Desc("Objects define what schematics (iob files) iris will place in this region")
    private KList<IrisObjectPlacement> objects = new KList<>();

    @MinNumber(0)
    @Desc("The min shore height")
    private double shoreHeightMin = 1.2;

    @Desc("Reference loot tables in this area")
    private IrisLootReference loot = new IrisLootReference();

    @MinNumber(0)
    @Desc("The the max shore height")
    private double shoreHeightMax = 3.2;

    @MinNumber(0.0001)
    @Desc("The varience of the shore height")
    private double shoreHeightZoom = 3.14;

    @MinNumber(0.0001)
    @Desc("How large land biomes are in this region")
    private double landBiomeZoom = 1;

    @MinNumber(0.0001)
    @Desc("How large shore biomes are in this region")
    private double shoreBiomeZoom = 1;

    @MinNumber(0.0001)
    @Desc("How large lake biomes are in this region")
    private double lakeBiomeZoom = 1;

    @MinNumber(0.0001)
    @Desc("How large river biomes are in this region")
    private double riverBiomeZoom = 1;

    @MinNumber(0.0001)
    @Desc("How large sea biomes are in this region")
    private double seaBiomeZoom = 1;

    @MinNumber(0.0001)
    @Desc("How large cave biomes are in this region")
    private double caveBiomeZoom = 1;

    @MinNumber(0.0001)
    @MaxNumber(1)
    @Desc("The biome implosion ratio, how much to implode biomes into children (chance)")
    private double biomeImplosionRatio = 0.4;

    @RegistryListBiome
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> landBiomes = new KList<>();

    @RegistryListBiome
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> seaBiomes = new KList<>();

    @RegistryListBiome
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> shoreBiomes = new KList<>();

    @RegistryListBiome
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> riverBiomes = new KList<>();

    @RegistryListBiome
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> lakeBiomes = new KList<>();

    @RegistryListBiome
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> caveBiomes = new KList<>();

    @ArrayType(min = 1, type = IrisRegionRidge.class)
    @Desc("Ridge biomes create a vein-like network like rivers through this region")
    private KList<IrisRegionRidge> ridgeBiomes = new KList<>();

    @ArrayType(min = 1, type = IrisRegionSpot.class)
    @Desc("Spot biomes splotch themselves across this region like lakes")
    private KList<IrisRegionSpot> spotBiomes = new KList<>();

    @ArrayType(min = 1, type = IrisDepositGenerator.class)
    @Desc("Define regional deposit generators that add onto the global deposit generators")
    private KList<IrisDepositGenerator> deposits = new KList<>();

    @Desc("The style of rivers")
    private IrisGeneratorStyle riverStyle = NoiseStyle.VASCULAR_THIN.style().zoomed(7.77);

    @Desc("The style of lakes")
    private IrisGeneratorStyle lakeStyle = NoiseStyle.CELLULAR_IRIS_THICK.style();

    @Desc("The style of river chances")
    private IrisGeneratorStyle riverChanceStyle = NoiseStyle.SIMPLEX.style().zoomed(4);

    @Desc("Generate lakes in this region")
    private boolean lakes = true;

    @Desc("Generate rivers in this region")
    private boolean rivers = true;

    @MinNumber(1)
    @Desc("Generate lakes in this region")
    private int lakeRarity = 22;

    @MinNumber(1)
    @Desc("Generate rivers in this region")
    private int riverRarity = 3;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Generate rivers in this region")
    private double riverThickness = 0.1;

    @Desc("A color for visualizing this region with a color. I.e. #F13AF5. This will show up on the map.")
    private String color = null;

    private final transient AtomicCache<KList<IrisObjectPlacement>> surfaceObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisObjectPlacement>> carveObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> cacheRidge = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> cacheSpot = new AtomicCache<>();
    private final transient AtomicCache<CNG> shoreHeightGenerator = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realLandBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realLakeBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realRiverBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realSeaBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realShoreBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realCaveBiomes = new AtomicCache<>();
    private final transient AtomicCache<CNG> lakeGen = new AtomicCache<>();
    private final transient AtomicCache<CNG> riverGen = new AtomicCache<>();
    private final transient AtomicCache<CNG> riverChanceGen = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColor = new AtomicCache<>();

    public String getName() {
        return name;
    }

    public KList<IrisObjectPlacement> getSurfaceObjects() {
        return getSurfaceObjectsCache().aquire(() ->
        {
            KList<IrisObjectPlacement> o = getObjects().copy();

            for (IrisObjectPlacement i : o.copy()) {
                if (!i.getCarvingSupport().supportsSurface()) {
                    o.remove(i);
                }
            }

            return o;
        });
    }

    public KList<IrisObjectPlacement> getCarvingObjects() {
        return getCarveObjectsCache().aquire(() ->
        {
            KList<IrisObjectPlacement> o = getObjects().copy();

            for (IrisObjectPlacement i : o.copy()) {
                if (!i.getCarvingSupport().supportsCarving()) {
                    o.remove(i);
                }
            }

            return o;
        });
    }

    public boolean isRiver(RNG rng, double x, double z) {
        if (!isRivers()) {
            return false;
        }

        if (getRiverBiomes().isEmpty()) {
            return false;
        }

        if (getRiverChanceGen().aquire(() -> getRiverChanceStyle().create(rng)).fit(1, getRiverRarity(), x, z) != 1) {
            return false;
        }

        return getRiverGen().aquire(() -> getRiverStyle().create(rng)).fitDouble(0, 1, x, z) < getRiverThickness();
    }

    public boolean isLake(RNG rng, double x, double z) {
        if (!isLakes()) {
            return false;
        }

        if (getLakeBiomes().isEmpty()) {
            return false;
        }

        return getLakeGen().aquire(() -> getLakeStyle().create(rng)).fit(1, getLakeRarity(), x, z) == 1;
    }

    public double getBiomeZoom(InferredType t) {
        switch (t) {
            case CAVE:
                return caveBiomeZoom;
            case LAKE:
                return lakeBiomeZoom;
            case RIVER:
                return riverBiomeZoom;
            case LAND:
                return landBiomeZoom;
            case SEA:
                return seaBiomeZoom;
            case SHORE:
                return shoreBiomeZoom;
            default:
                break;
        }

        return 1;
    }

    public KList<String> getRidgeBiomeKeys() {
        return cacheRidge.aquire(() ->
        {
            KList<String> cacheRidge = new KList<>();
            ridgeBiomes.forEach((i) -> cacheRidge.add(i.getBiome()));

            return cacheRidge;
        });
    }

    public KList<String> getSpotBiomeKeys() {
        return cacheSpot.aquire(() ->
        {
            KList<String> cacheSpot = new KList<>();
            spotBiomes.forEach((i) -> cacheSpot.add(i.getBiome()));
            return cacheSpot;
        });
    }

    public CNG getShoreHeightGenerator() {
        return shoreHeightGenerator.aquire(() ->
                CNG.signature(new RNG((long) (getName().length() + getLandBiomeZoom() + getLandBiomes().size() + 3458612))));
    }

    public double getShoreHeight(double x, double z) {
        return getShoreHeightGenerator().fitDouble(shoreHeightMin, shoreHeightMax, x / shoreHeightZoom, z / shoreHeightZoom);
    }

    public KSet<String> getAllBiomeIds() {
        KSet<String> names = new KSet<>();
        names.addAll(landBiomes);
        names.addAll(caveBiomes);
        names.addAll(seaBiomes);
        names.addAll(shoreBiomes);
        names.addAll(riverBiomes);
        names.addAll(lakeBiomes);
        spotBiomes.forEach((i) -> names.add(i.getBiome()));
        ridgeBiomes.forEach((i) -> names.add(i.getBiome()));

        return names;
    }

    public KList<IrisBiome> getAllBiomes(DataProvider g) {
        KMap<String, IrisBiome> b = new KMap<>();
        KSet<String> names = getAllBiomeIds();

        while (!names.isEmpty()) {
            for (String i : new KList<>(names)) {
                if (b.containsKey(i)) {
                    names.remove(i);
                    continue;
                }

                IrisBiome biome = g.getData().getBiomeLoader().load(i);

                names.remove(i);
                if (biome == null) {
                    continue;
                }

                names.add(biome.getCarvingBiome());
                b.put(biome.getLoadKey(), biome);
                names.addAll(biome.getChildren());
            }
        }

        return b.v();
    }

    public KList<IrisBiome> getBiomes(DataProvider g, InferredType type) {
        if (type.equals(InferredType.LAND)) {
            return getRealLandBiomes(g);
        } else if (type.equals(InferredType.SEA)) {
            return getRealSeaBiomes(g);
        } else if (type.equals(InferredType.SHORE)) {
            return getRealShoreBiomes(g);
        } else if (type.equals(InferredType.CAVE)) {
            return getRealCaveBiomes(g);
        } else if (type.equals(InferredType.LAKE)) {
            return getRealLakeBiomes(g);
        } else if (type.equals(InferredType.RIVER)) {
            return getRealRiverBiomes(g);
        }

        return new KList<>();
    }

    public KList<IrisBiome> getRealCaveBiomes(DataProvider g) {
        return realCaveBiomes.aquire(() ->
        {
            KList<IrisBiome> realCaveBiomes = new KList<>();

            for (String i : getCaveBiomes()) {
                realCaveBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realCaveBiomes;
        });
    }

    public KList<IrisBiome> getRealLakeBiomes(DataProvider g) {
        return realLakeBiomes.aquire(() ->
        {
            KList<IrisBiome> realLakeBiomes = new KList<>();

            for (String i : getLakeBiomes()) {
                realLakeBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realLakeBiomes;
        });
    }

    public KList<IrisBiome> getRealRiverBiomes(DataProvider g) {
        return realRiverBiomes.aquire(() ->
        {
            KList<IrisBiome> realRiverBiomes = new KList<>();

            for (String i : getRiverBiomes()) {
                realRiverBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realRiverBiomes;
        });
    }

    public KList<IrisBiome> getRealShoreBiomes(DataProvider g) {
        return realShoreBiomes.aquire(() ->
        {
            KList<IrisBiome> realShoreBiomes = new KList<>();

            for (String i : getShoreBiomes()) {
                realShoreBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realShoreBiomes;
        });
    }

    public KList<IrisBiome> getRealSeaBiomes(DataProvider g) {
        return realSeaBiomes.aquire(() ->
        {
            KList<IrisBiome> realSeaBiomes = new KList<>();

            for (String i : getSeaBiomes()) {
                realSeaBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realSeaBiomes;
        });
    }

    public KList<IrisBiome> getRealLandBiomes(DataProvider g) {
        return realLandBiomes.aquire(() ->
        {
            KList<IrisBiome> realLandBiomes = new KList<>();

            for (String i : getLandBiomes()) {
                realLandBiomes.add(g.getData().getBiomeLoader().load(i));
            }

            return realLandBiomes;
        });
    }

    public KList<IrisBiome> getAllAnyBiomes() {
        KMap<String, IrisBiome> b = new KMap<>();
        KSet<String> names = new KSet<>();
        names.addAll(landBiomes);
        names.addAll(caveBiomes);
        names.addAll(seaBiomes);
        names.addAll(shoreBiomes);
        names.addAll(riverBiomes);
        names.addAll(lakeBiomes);
        spotBiomes.forEach((i) -> names.add(i.getBiome()));
        ridgeBiomes.forEach((i) -> names.add(i.getBiome()));

        while (!names.isEmpty()) {
            for (String i : new KList<>(names)) {
                if (b.containsKey(i)) {
                    names.remove(i);
                    continue;
                }

                IrisBiome biome = IrisDataManager.loadAnyBiome(i);

                names.remove(i);
                if (biome == null) {
                    continue;
                }

                names.add(biome.getCarvingBiome());
                b.put(biome.getLoadKey(), biome);
                names.addAll(biome.getChildren());
            }
        }

        return b.v();
    }

    public Color getColor(DataProvider dataProvider, RenderType type) {
        return this.cacheColor.aquire(() -> {
            if (this.color == null) {
                Random rand = new Random(getName().hashCode() + getAllBiomeIds().hashCode());
                RandomColor randomColor = new RandomColor(rand);

                KList<IrisBiome> biomes = getRealLandBiomes(dataProvider);

                while (biomes.size() > 0) {
                    int index = rand.nextInt(biomes.size());
                    IrisBiome biome = biomes.get(index);

                    if (biome.getVanillaDerivative() != null) {
                        RandomColor.Color col = VanillaBiomeMap.getColorType(biome.getVanillaDerivative());
                        RandomColor.Luminosity lum = VanillaBiomeMap.getColorLuminosity(biome.getVanillaDerivative());
                        RandomColor.SaturationType sat = VanillaBiomeMap.getColorSaturatiom(biome.getVanillaDerivative());
                        int newColorI = randomColor.randomColor(col, col == RandomColor.Color.MONOCHROME ? RandomColor.SaturationType.MONOCHROME : sat, lum);
                        return new Color(newColorI);
                    }

                    biomes.remove(index);
                }

                Iris.warn("Couldn't find a suitable color for region " + getName());
                return new Color(new RandomColor(rand).randomColor());
            }

            try {
                return Color.decode(this.color);
            } catch (NumberFormatException e) {
                Iris.warn("Could not parse color \"" + this.color + "\" for region " + getName());
                return Color.WHITE;
            }
        });
    }

    public void pickRandomColor(DataProvider data) {

    }
}
