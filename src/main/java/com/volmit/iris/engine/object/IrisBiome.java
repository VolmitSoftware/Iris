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
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.data.VanillaBiomeMap;
import com.volmit.iris.util.inventorygui.RandomColor;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.awt.*;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a biome in iris. Biomes are placed inside of regions and hold objects.\nA biome consists of layers (block palletes), decorations, objects & generators.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegistrant implements IRare {
    private static final BlockData BARRIER = Material.BARRIER.createBlockData();
    private final transient AtomicCache<KMap<String, IrisBiomeGeneratorLink>> genCache = new AtomicCache<>();
    private final transient AtomicCache<KMap<String, Integer>> genCacheMax = new AtomicCache<>();
    private final transient AtomicCache<KMap<String, Integer>> genCacheMin = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisObjectPlacement>> surfaceObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisObjectPlacement>> carveObjectsCache = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColor = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColorObjectDensity = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColorDecoratorLoad = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColorLayerLoad = new AtomicCache<>();
    private final transient AtomicCache<Color> cacheColorDepositLoad = new AtomicCache<>();
    private final transient AtomicCache<CNG> childrenCell = new AtomicCache<>();
    private final transient AtomicCache<CNG> biomeGenerator = new AtomicCache<>();
    private final transient AtomicCache<Integer> maxHeight = new AtomicCache<>();
    private final transient AtomicCache<Integer> maxWithObjectHeight = new AtomicCache<>();
    private final transient AtomicCache<IrisBiome> realCarveBiome = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realChildren = new AtomicCache<>();
    private final transient AtomicCache<KList<CNG>> layerHeightGenerators = new AtomicCache<>();
    private final transient AtomicCache<KList<CNG>> layerSeaHeightGenerators = new AtomicCache<>();
    @MinNumber(2)
    @Required
    @Desc("This is the human readable name for this biome. This can and should be different than the file name. This is not used for loading biomes in other objects.")
    private String name = "Subterranean Land";
    @ArrayType(min = 1, type = IrisBiomeCustom.class)
    @Desc("If the biome type custom is defined, specify this")
    private KList<IrisBiomeCustom> customDerivitives;
    @Desc("Spawn Entities in this area over time. Iris will continually replenish these mobs just like vanilla does.")
    @ArrayType(min = 1, type = String.class)
    @RegistryListResource(IrisSpawner.class)
    private KList<String> entitySpawners = new KList<>();
    @ArrayType(min = 1, type = IrisEffect.class)
    @Desc("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play around the player even if where the effect was played is no longer in the biome the player is in.")
    private KList<IrisEffect> effects = new KList<>();
    @DependsOn({"biomeStyle", "biomeZoom", "biomeScatter"})
    @Desc("This changes the dispersion of the biome colors if multiple derivatives are chosen.")
    private IrisGeneratorStyle biomeStyle = NoiseStyle.SIMPLEX.style();
    @ArrayType(min = 1, type = IrisBlockDrops.class)
    @Desc("Define custom block drops for this biome")
    private KList<IrisBlockDrops> blockDrops = new KList<>();
    @Desc("Reference loot tables in this area")
    private IrisLootReference loot = new IrisLootReference();
    @Desc("Layers no longer descend from the surface block, they descend from the max possible height the biome can produce (constant) creating mesa like layers.")
    private boolean lockLayers = false;
    @Desc("The max layers to iterate below the surface for locked layer biomes (mesa).")
    private int lockLayersMax = 7;
    @Desc("Carving configuration for the dimension")
    private IrisCarving carving = new IrisCarving();
    @Desc("Configuration of fluid bodies such as rivers & lakes")
    private IrisFluidBodies fluidBodies = new IrisFluidBodies();
    @MinNumber(1)
    @MaxNumber(512)
    @Desc("The rarity of this biome (integer)")
    private int rarity = 1;
    @Desc("A color for visualizing this biome with a color. I.e. #F13AF5. This will show up on the map.")
    private String color = null;
    @Required
    @Desc("The raw derivative of this biome. This is required or the terrain will not properly generate. Use any vanilla biome type. Look in examples/biome-list.txt")
    private Biome derivative = Biome.THE_VOID;
    @Required
    @Desc("Override the derivative when vanilla places structures to this derivative. This is useful for example if you have an ocean biome, but you have set the derivative to desert to get a brown-ish color. To prevent desert structures from spawning on top of your ocean, you can set your vanillaDerivative to ocean, to allow for vanilla structures. Not defining this value will simply select the derivative.")
    private Biome vanillaDerivative = null;
    @ArrayType(min = 1, type = Biome.class)
    @Desc("You can instead specify multiple biome derivatives to randomly scatter colors in this biome")
    private KList<Biome> biomeScatter = new KList<>();
    @ArrayType(min = 1, type = Biome.class)
    @Desc("Since 1.13 supports 3D biomes, you can add different derivative colors for anything above the terrain. (Think swampy tree leaves with a desert looking grass surface)")
    private KList<Biome> biomeSkyScatter = new KList<>();
    @DependsOn({"children"})
    @Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, how much smaller will it be (inside of this biome). Higher values means a smaller biome relative to this biome's size. Set higher than 1.0 and below 3.0 for best results.")
    private double childShrinkFactor = 1.5;
    @DependsOn({"children"})
    @Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, How will it be shaped?")
    private IrisGeneratorStyle childStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(min = 1, type = String.class)
    @Desc("List any biome names (file names without.json) here as children. Portions of this biome can sometimes morph into their children. Iris supports cyclic relationships such as A > B > A > B. Iris will stop checking 9 biomes down the tree.")
    private KList<String> children = new KList<>();
    @ArrayType(min = 1, type = IrisJigsawStructurePlacement.class)
    @Desc("Jigsaw structures")
    private KList<IrisJigsawStructurePlacement> jigsawStructures = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @Desc("The carving biome. If specified the biome will be used when under a carving instead of this current biome.")
    private String carvingBiome = "";
    @Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
    private IrisBiomePaletteLayer slab = new IrisBiomePaletteLayer().zero();
    @Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
    private IrisBiomePaletteLayer wall = new IrisBiomePaletteLayer().zero();
    @Required
    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());
    @Required
    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> caveCeilingLayers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());
    @ArrayType(min = 1, type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> seaLayers = new KList<>();
    @ArrayType(min = 1, type = IrisDecorator.class)
    @Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
    private KList<IrisDecorator> decorators = new KList<>();
    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Desc("Objects define what schematics (iob files) iris will place in this biome")
    private KList<IrisObjectPlacement> objects = new KList<>();
    @Required
    @ArrayType(min = 1, type = IrisBiomeGeneratorLink.class)
    @Desc("Generators for this biome. Multiple generators with different interpolation sizes will mix with other biomes how you would expect. This defines your biome height relative to the fluid height. Use negative for oceans.")
    private KList<IrisBiomeGeneratorLink> generators = new KList<IrisBiomeGeneratorLink>().qadd(new IrisBiomeGeneratorLink());
    @ArrayType(min = 1, type = IrisDepositGenerator.class)
    @Desc("Define biome deposit generators that add onto the existing regional and global deposit generators")
    private KList<IrisDepositGenerator> deposits = new KList<>();
    private transient InferredType inferredType;
    @Desc("Collection of ores to be generated")
    @ArrayType(type = IrisOreGenerator.class, min = 1)
    private KList<IrisOreGenerator> ores = new KList<>();

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

    public Biome getVanillaDerivative() {
        return vanillaDerivative == null ? derivative : vanillaDerivative;
    }

    public boolean isCustom() {
        return getCustomDerivitives() != null && getCustomDerivitives().isNotEmpty();
    }

    public double getGenLinkMax(String loadKey) {
        Integer v = genCacheMax.aquire(() ->
        {
            KMap<String, Integer> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : getGenerators()) {
                l.put(i.getGenerator(), i.getMax());
            }

            return l;
        }).get(loadKey);

        return v == null ? 0 : v;
    }

    public double getGenLinkMin(String loadKey) {
        Integer v = genCacheMin.aquire(() ->
        {
            KMap<String, Integer> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : getGenerators()) {
                l.put(i.getGenerator(), i.getMin());
            }

            return l;
        }).get(loadKey);

        return v == null ? 0 : v;
    }

    public IrisBiomeGeneratorLink getGenLink(String loadKey) {
        return genCache.aquire(() ->
        {
            KMap<String, IrisBiomeGeneratorLink> l = new KMap<>();

            for (IrisBiomeGeneratorLink i : getGenerators()) {
                l.put(i.getGenerator(), i);
            }

            return l;
        }).get(loadKey);
    }

    public IrisBiome getRealCarvingBiome(IrisData data) {
        return realCarveBiome.aquire(() ->
        {
            IrisBiome biome = data.getBiomeLoader().load(getCarvingBiome());

            if (biome == null) {
                biome = this;
            }

            return biome;
        });
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

    public double getHeight(Engine xg, double x, double z, long seed) {
        double height = 0;

        for (IrisBiomeGeneratorLink i : generators) {
            height += i.getHeight(xg, x, z, seed);
        }

        return Math.max(0, Math.min(height, xg.getHeight()));
    }

    public CNG getBiomeGenerator(RNG random) {
        return biomeGenerator.aquire(() ->
                biomeStyle.create(random.nextParallelRNG(213949 + 228888 + getRarity() + getName().length()), getLoader()));
    }

    public CNG getChildrenGenerator(RNG random, int sig, double scale) {
        return childrenCell.aquire(() -> getChildStyle().create(random.nextParallelRNG(sig * 2137), getLoader()).bake().scale(scale).bake());
    }

    public KList<BlockData> generateLayers(IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        if (isLockLayers()) {
            return generateLockedLayers(wx, wz, random, maxDepth, height, rdata, complex);
        }

        KList<BlockData> data = new KList<>();

        if (maxDepth <= 0) {
            return data;
        }

        for (int i = 0; i < layers.size(); i++) {
            CNG hgen = getLayerHeightGenerators(random, rdata).get(i);
            double d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getZoom(), wz / layers.get(i).getZoom());

            IrisSlopeClip sc = getLayers().get(i).getSlopeCondition();

            if (!sc.isDefault()) {
                if (!sc.isValid(complex.getSlopeStream().get(wx, wz))) {
                    d = 0;
                }
            }

            if (d <= 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getZoom(), j, (wz - j) / layers.get(i).getZoom(), rdata));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(BARRIER);

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    public KList<BlockData> generateCeilingLayers(IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        KList<BlockData> data = new KList<>();

        if (maxDepth <= 0) {
            return data;
        }

        for (int i = 0; i < caveCeilingLayers.size(); i++) {
            CNG hgen = getLayerHeightGenerators(random, rdata).get(i);
            double d = hgen.fit(caveCeilingLayers.get(i).getMinHeight(), caveCeilingLayers.get(i).getMaxHeight(), wx / caveCeilingLayers.get(i).getZoom(), wz / caveCeilingLayers.get(i).getZoom());

            if (d <= 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(getCaveCeilingLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / caveCeilingLayers.get(i).getZoom(), j, (wz - j) / caveCeilingLayers.get(i).getZoom(), rdata));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }

            if (dim.isExplodeBiomePalettes()) {
                for (int j = 0; j < dim.getExplodeBiomePaletteSize(); j++) {
                    data.add(BARRIER);

                    if (data.size() >= maxDepth) {
                        break;
                    }
                }
            }
        }

        return data;
    }

    public KList<BlockData> generateLockedLayers(double wx, double wz, RNG random, int maxDepthf, int height, IrisData rdata, IrisComplex complex) {
        KList<BlockData> data = new KList<>();
        KList<BlockData> real = new KList<>();
        int maxDepth = Math.min(maxDepthf, getLockLayersMax());
        if (maxDepth <= 0) {
            return data;
        }

        for (int i = 0; i < layers.size(); i++) {
            CNG hgen = getLayerHeightGenerators(random, rdata).get(i);
            double d = hgen.fit(layers.get(i).getMinHeight(), layers.get(i).getMaxHeight(), wx / layers.get(i).getZoom(), wz / layers.get(i).getZoom());

            IrisSlopeClip sc = getLayers().get(i).getSlopeCondition();

            if (!sc.isDefault()) {
                if (!sc.isValid(complex.getSlopeStream().get(wx, wz))) {
                    d = 0;
                }
            }

            if (d <= 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                try {
                    data.add(getLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / layers.get(i).getZoom(), j, (wz - j) / layers.get(i).getZoom(), rdata));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }
        }

        if (data.isEmpty()) {
            return real;
        }

        for (int i = 0; i < maxDepth; i++) {
            int offset = (512 - height) - i;
            int index = offset % data.size();
            real.add(data.get(Math.max(index, 0)));
        }

        return real;
    }

    public int getMaxHeight() {
        return maxHeight.aquire(() ->
        {
            int maxHeight = 0;

            for (IrisBiomeGeneratorLink i : getGenerators()) {
                maxHeight += i.getMax();
            }

            return maxHeight;
        });
    }

    public int getMaxWithObjectHeight(IrisData data) {
        return maxWithObjectHeight.aquire(() ->
        {
            int maxHeight = 0;

            for (IrisBiomeGeneratorLink i : getGenerators()) {
                maxHeight += i.getMax();
            }

            int gg = 0;

            for (IrisObjectPlacement i : getObjects()) {
                for (IrisObject j : data.getObjectLoader().loadAll(i.getPlace())) {
                    gg = Math.max(gg, j.getH());
                }
            }

            return maxHeight + gg + 3;
        });
    }

    public KList<BlockData> generateSeaLayers(double wx, double wz, RNG random, int maxDepth, IrisData rdata) {
        KList<BlockData> data = new KList<>();

        for (int i = 0; i < seaLayers.size(); i++) {
            CNG hgen = getLayerSeaHeightGenerators(random, rdata).get(i);
            int d = hgen.fit(seaLayers.get(i).getMinHeight(), seaLayers.get(i).getMaxHeight(), wx / seaLayers.get(i).getZoom(), wz / seaLayers.get(i).getZoom());

            if (d < 0) {
                continue;
            }

            for (int j = 0; j < d; j++) {
                if (data.size() >= maxDepth) {
                    break;
                }

                try {
                    data.add(getSeaLayers().get(i).get(random.nextParallelRNG(i + j), (wx + j) / seaLayers.get(i).getZoom(), j, (wz - j) / seaLayers.get(i).getZoom(), rdata));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }

            if (data.size() >= maxDepth) {
                break;
            }
        }

        return data;
    }

    public KList<CNG> getLayerHeightGenerators(RNG rng, IrisData rdata) {
        return layerHeightGenerators.aquire(() ->
        {
            KList<CNG> layerHeightGenerators = new KList<>();

            int m = 7235;

            for (IrisBiomePaletteLayer i : getLayers()) {
                layerHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), rdata));
            }

            return layerHeightGenerators;
        });
    }

    public KList<CNG> getLayerSeaHeightGenerators(RNG rng, IrisData data) {
        return layerSeaHeightGenerators.aquire(() ->
        {
            KList<CNG> layerSeaHeightGenerators = new KList<>();

            int m = 7735;

            for (IrisBiomePaletteLayer i : getSeaLayers()) {
                layerSeaHeightGenerators.add(i.getHeightGenerator(rng.nextParallelRNG((m++) * m * m * m), data));
            }

            return layerSeaHeightGenerators;
        });
    }

    public boolean isLand() {
        if (inferredType == null) {
            return true;
        }

        return inferredType.equals(InferredType.LAND);
    }

    public boolean isSea() {
        if (inferredType == null) {
            return false;
        }
        return inferredType.equals(InferredType.SEA);
    }

    public boolean isAquatic() {
        return isSea();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isShore() {
        if (inferredType == null) {
            return false;
        }
        return inferredType.equals(InferredType.SHORE);
    }

    public Biome getSkyBiome(RNG rng, double x, double y, double z) {
        if (biomeSkyScatter.size() == 1) {
            return biomeSkyScatter.get(0);
        }

        if (biomeSkyScatter.isEmpty()) {
            return getGroundBiome(rng, x, y, z);
        }

        return biomeSkyScatter.get(getBiomeGenerator(rng).fit(0, biomeSkyScatter.size() - 1, x, y, z));
    }

    public IrisBiomeCustom getCustomBiome(RNG rng, double x, double y, double z) {
        if (customDerivitives.size() == 1) {
            return customDerivitives.get(0);
        }

        return customDerivitives.get(getBiomeGenerator(rng).fit(0, customDerivitives.size() - 1, x, y, z));
    }

    public KList<IrisBiome> getRealChildren(DataProvider g) {
        return realChildren.aquire(() ->
        {
            KList<IrisBiome> realChildren = new KList<>();

            for (String i : getChildren()) {
                realChildren.add(g.getData().getBiomeLoader().load(i));
            }

            return realChildren;
        });
    }

    public KList<String> getAllChildren(DataProvider g, int limit) {
        KSet<String> m = new KSet<>();
        m.addAll(getChildren());
        limit--;

        if (limit > 0) {
            for (String i : getChildren()) {
                IrisBiome b = g.getData().getBiomeLoader().load(i);
                m.addAll(b.getAllChildren(g, limit));
            }
        }

        return new KList<>(m);
    }

    //TODO: Test
    public Biome getGroundBiome(RNG rng, double x, double y, double z) {
        if (biomeScatter.isEmpty()) {
            return getDerivative();
        }

        if (biomeScatter.size() == 1) {
            return biomeScatter.get(0);
        }

        return getBiomeGenerator(rng).fit(biomeScatter, x, y, z);
    }

    public BlockData getSurfaceBlock(int x, int z, RNG rng, IrisData idm) {
        if (getLayers().isEmpty()) {
            return B.get("AIR");
        }

        return getLayers().get(0).get(rng, x, 0, z, idm);
    }

    public Color getColor(Engine engine, RenderType type) {
        switch (type) {
            case BIOME, HEIGHT, CAVE_LAND, REGION, BIOME_SEA, BIOME_LAND -> {
                return this.cacheColor.aquire(() -> {
                    if (this.color == null) {
                        RandomColor randomColor = new RandomColor(getName().hashCode());
                        if (this.getVanillaDerivative() == null) {
                            Iris.warn("No vanilla biome found for " + getName());
                            return new Color(randomColor.randomColor());
                        }
                        RandomColor.Color col = VanillaBiomeMap.getColorType(this.getVanillaDerivative());
                        RandomColor.Luminosity lum = VanillaBiomeMap.getColorLuminosity(this.getVanillaDerivative());
                        RandomColor.SaturationType sat = VanillaBiomeMap.getColorSaturatiom(this.getVanillaDerivative());
                        int newColorI = randomColor.randomColor(col, col == RandomColor.Color.MONOCHROME ? RandomColor.SaturationType.MONOCHROME : sat, lum);

                        return new Color(newColorI);
                    }

                    try {
                        return Color.decode(this.color);
                    } catch (NumberFormatException e) {
                        Iris.warn("Could not parse color \"" + this.color + "\" for biome " + getName());
                        return new Color(new RandomColor(getName().hashCode()).randomColor());
                    }
                });
            }
            case OBJECT_LOAD -> {
                return cacheColorObjectDensity.aquire(() -> {
                    double density = 0;

                    for (IrisObjectPlacement i : getObjects()) {
                        density += i.getDensity() * i.getChance();
                    }

                    return Color.getHSBColor(0.225f, (float) (density / engine.getMaxBiomeObjectDensity()), 1f);
                });
            }
            case DECORATOR_LOAD -> {
                return cacheColorDecoratorLoad.aquire(() -> {
                    double density = 0;

                    for (IrisDecorator i : getDecorators()) {
                        density += i.getChance() * Math.min(1, i.getStackMax()) * 256;
                    }

                    return Color.getHSBColor(0.41f, (float) (density / engine.getMaxBiomeDecoratorDensity()), 1f);
                });
            }
            case LAYER_LOAD -> {
                return cacheColorLayerLoad.aquire(() -> Color.getHSBColor(0.625f, (float) (getLayers().size() / engine.getMaxBiomeLayerDensity()), 1f));
            }
        }

        return Color.black;
    }

    @Override
    public String getFolderName() {
        return "biomes";
    }

    @Override
    public String getTypeName() {
        return "Biome";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
