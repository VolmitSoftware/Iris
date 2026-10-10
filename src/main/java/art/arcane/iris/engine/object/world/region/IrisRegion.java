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

package art.arcane.iris.engine.object.world.region;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.procedural.IrisProceduralObjects;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.data.VanillaBiomeColors;
import art.arcane.volmlib.util.inventorygui.RandomColor;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.Color;
import java.util.Random;


@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents an iris region")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegistrant implements IRare {
    private final transient AtomicCache<KList<IrisObjectPlacement>> surfaceObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisObjectPlacement>> carveObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> cacheRidge = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> cacheSpot = new AtomicCache<>();
    private final transient AtomicCache<CNG> shoreHeightGenerator = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realLandBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realSeaBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realShoreBiomes = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realCaveBiomes = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColor = new AtomicCache<>();
    @MinNumber(2)
    @Required
    @Desc("The name of the region")
    private String name = "A Region";
    @MinNumber(1)
    @MaxNumber(128)
    @Desc("The rarity of the region")
    private int rarity = 1;
    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Desc("Objects define what schematics (iob files) iris will place in this region")
    private KList<IrisObjectPlacement> objects = new KList<>();
    @Desc("Procedural objects (trees, ruins, formations, coral, fungi, crystals) iris generates from scratch and places across this region")
    private IrisProceduralObjects proceduralObjects = new IrisProceduralObjects();
    @ArrayType(min = 1, type = IrisStructurePlacement.class)
    @Desc("Structures define jigsaw or vanilla/datapack structures iris will place in this region")
    private KList<IrisStructurePlacement> structures = new KList<>();
    @MinNumber(0)
    @Desc("The min shore height")
    private double shoreHeightMin = 1.2;
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
    @Desc("How large sea biomes are in this region")
    private double seaBiomeZoom = 1;
    @MinNumber(0.0001)
    @Desc("How large cave biomes are in this region")
    private double caveBiomeZoom = 1;
    @RegistryListResource(IrisBiome.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> landBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Desc("A list of root-level sea biomes in this region. Optional: leave empty for land-only dimensions (flat, island, or fully-land worlds where no ocean is generated). Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> seaBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Desc("A list of root-level shore biomes in this region. Optional: leave empty for land-only dimensions (flat, island, or fully-land worlds where no shoreline is generated). Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> shoreBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(min = 1, type = String.class)
    @Desc("A list of root-level cave biomes in this region, used for carved cave interiors. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> caveBiomes = new KList<>();
    @Desc("A color for visualizing this region with a color. I.e. #F13AF5. This will show up on the map.")
    private String color = null;

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

                names.addAll(biome.getModuleSettings().treeBiomes());
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

        while (!names.isEmpty()) {
            for (String i : new KList<>(names)) {
                if (b.containsKey(i)) {
                    names.remove(i);
                    continue;
                }

                IrisBiome biome = IrisData.loadAnyBiome(i, getLoader());

                names.remove(i);
                if (biome == null) {
                    continue;
                }

                names.addAll(biome.getModuleSettings().treeBiomes());
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

                    String vanillaKey = biome.getVanillaDerivativeKey();
                    RandomColor.Color col = vanillaKey == null ? null : VanillaBiomeColors.getColorType(vanillaKey);
                    if (col != null) {
                        RandomColor.Luminosity lum = VanillaBiomeColors.getColorLuminosity(vanillaKey);
                        RandomColor.SaturationType sat = VanillaBiomeColors.getColorSaturation(vanillaKey);
                        int newColorI = randomColor.randomColor(col, col == RandomColor.Color.MONOCHROME ? RandomColor.SaturationType.MONOCHROME : sat, lum);
                        return new Color(newColorI);
                    }

                    biomes.remove(index);
                }

                IrisLogging.warn("Couldn't find a suitable color for region " + getName());
                return new Color(new RandomColor(rand).randomColor());
            }

            try {
                return Color.decode(this.color);
            } catch (NumberFormatException e) {
                IrisLogging.warn("Could not parse color \"" + this.color + "\" for region " + getName());
                return Color.WHITE;
            }
        });
    }

    public void pickRandomColor(DataProvider data) {

    }

    @Override
    public String getFolderName() {
        return "regions";
    }

    @Override
    public String getTypeName() {
        return "Region";
    }
}
