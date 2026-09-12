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

package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositVariant;
import art.arcane.iris.generation.decoration.IrisOreGenerator;
import art.arcane.iris.generation.decoration.IrisOreGeneratorBounds;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.pack.validation.CompatPools;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.world.entity.IrisEffect;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.world.loot.IrisBlockDrops;
import art.arcane.iris.world.loot.IrisLootReference;

import art.arcane.volmlib.util.math.Rarity;

import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.pack.validation.CompatFinding;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.volmlib.util.data.VanillaBiomeColors;
import art.arcane.volmlib.util.inventorygui.RandomColor;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


@Accessors(chain = true)
@NoArgsConstructor
@Description("Represents an iris region")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisRegion extends IrisRegistrant implements Rarity {
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
    private final transient AtomicCache<KList<IrisOreGenerator>> surfaceOreCache = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisOreGenerator>> undergroundOreCache = new AtomicCache<>();
    private final transient AtomicCache<IrisOreGeneratorBounds> surfaceOreBoundsCache = new AtomicCache<>();
    private final transient AtomicCache<IrisOreGeneratorBounds> undergroundOreBoundsCache = new AtomicCache<>();
    @MinNumber(2)
    @Required
    @Description("The name of the region")
    private String name = "A Region";
    @ArrayType(min = 1, type = IrisEffect.class)
    @Description("Effects are ambient effects such as potion effects, random sounds, or even particles around each player. All of these effects are played via packets so two players won't see/hear each others effects.\nDue to performance reasons, effects will play arround the player even if where the effect was played is no longer in the biome the player is in.")
    private KList<IrisEffect> effects = new KList<>();
    @Description("Spawn Entities in this region over time. Iris will continually replenish these mobs just like vanilla does.")
    @ArrayType(min = 1, type = String.class)
    @RegistryListResource(IrisSpawner.class)
    private KList<String> entitySpawners = new KList<>();
    @MinNumber(1)
    @MaxNumber(128)
    @Description("The rarity of the region")
    private int rarity = 1;
    @ArrayType(min = 1, type = IrisBlockDrops.class)
    @Description("Define custom block drops for this region")
    private KList<IrisBlockDrops> blockDrops = new KList<>();
    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Description("Objects define what schematics (iob files) iris will place in this region")
    private KList<IrisObjectPlacement> objects = new KList<>();
    @Description("Procedural objects (trees, ruins, formations, coral, fungi, crystals) iris generates from scratch and places across this region")
    private IrisProceduralObjects proceduralObjects = new IrisProceduralObjects();
    @ArrayType(min = 1, type = IrisStructurePlacement.class)
    @Description("Structures define jigsaw or vanilla/datapack structures iris will place in this region")
    private KList<IrisStructurePlacement> structures = new KList<>();
    @MinNumber(0)
    @Description("The min shore height")
    private double shoreHeightMin = 1.2;
    @Description("Reference loot tables in this area")
    private IrisLootReference loot = new IrisLootReference();
    @MinNumber(0)
    @Description("The the max shore height")
    private double shoreHeightMax = 3.2;
    @MinNumber(0.0001)
    @Description("The varience of the shore height")
    private double shoreHeightZoom = 3.14;
    @MinNumber(0.0001)
    @Description("How large land biomes are in this region")
    private double landBiomeZoom = 1;
    @MinNumber(0.0001)
    @Description("How large shore biomes are in this region")
    private double shoreBiomeZoom = 1;
    @MinNumber(0.0001)
    @Description("How large sea biomes are in this region")
    private double seaBiomeZoom = 1;
    @MinNumber(0.0001)
    @Description("How large cave biomes are in this region")
    private double caveBiomeZoom = 1;
    @Description("Profile-driven 3D cave configuration")
    private IrisCaveProfile caveProfile = new IrisCaveProfile();
    @Description("Region-level river placement, routing, profile, biome, and geometry policy. Omit to inherit dimension settings.")
    private IrisRiverPolicy riverPolicy = null;
    @RegistryListResource(IrisBiome.class)
    @Required
    @ArrayType(min = 1, type = String.class)
    @Description("A list of root-level biomes in this region. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> landBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("A list of root-level sea biomes in this region. Optional: leave empty for land-only dimensions (flat, island, or fully-land worlds where no ocean is generated). Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> seaBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class)
    @Description("A list of root-level shore biomes in this region. Optional: leave empty for land-only dimensions (flat, island, or fully-land worlds where no shoreline is generated). Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> shoreBiomes = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(min = 1, type = String.class)
    @Description("A list of root-level cave biomes in this region, used for carved cave interiors. Don't specify child biomes of other biomes here. Just the root parents.")
    private KList<String> caveBiomes = new KList<>();
    @ArrayType(min = 1, type = IrisDepositGenerator.class)
    @Description("Define regional deposit generators that add onto the global deposit generators")
    private KList<IrisDepositGenerator> deposits = new KList<>();
    @ArrayType(min = 1, type = IrisDepositVariant.class)
    @Description("Deposit ore remap rules scoped to this region. Each entry declares a vertical band and a source->replacement block id map. Applied after biome rules but before dimension rules; first matching region rule wins.")
    private KList<IrisDepositVariant> depositVariants = new KList<>();
    @Description("A color for visualizing this region with a color. I.e. #F13AF5. This will show up on the map.")
    private String color = null;
    @Description("Collection of ores to be generated")
    @ArrayType(type = IrisOreGenerator.class, min = 1)
    private KList<IrisOreGenerator> ores = new KList<>();

    public PlatformBlockState generateOres(int x, int y, int z, RNG rng, IrisData data, boolean surface) {
        KList<IrisOreGenerator> localOres = surface ? getSurfaceOres() : getUndergroundOres();
        return generateOres(localOres, x, y, z, rng, data);
    }

    public PlatformBlockState generateSurfaceOres(int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getSurfaceOres(), x, y, z, rng, data);
    }

    public PlatformBlockState generateUndergroundOres(int x, int y, int z, RNG rng, IrisData data) {
        return generateOres(getUndergroundOres(), x, y, z, rng, data);
    }

    public boolean hasSurfaceOres() {
        return !getSurfaceOres().isEmpty();
    }

    public boolean hasUndergroundOres() {
        return !getUndergroundOres().isEmpty();
    }

    private PlatformBlockState generateOres(KList<IrisOreGenerator> localOres, int x, int y, int z, RNG rng, IrisData data) {
        if (localOres.isEmpty()) {
            return null;
        }

        int oreCount = localOres.size();
        for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
            IrisOreGenerator oreGenerator = localOres.get(oreIndex);
            PlatformBlockState ore = oreGenerator.generate(x, y, z, rng, data);
            if (ore != null) {
                return ore;
            }
        }
        return null;
    }

    public void setOres(KList<IrisOreGenerator> ores) {
        this.ores = ores == null ? new KList<>() : ores;
        surfaceOreCache.reset();
        undergroundOreCache.reset();
        surfaceOreBoundsCache.reset();
        undergroundOreBoundsCache.reset();
    }

    public KList<IrisOreGenerator> getSurfaceOreGenerators() {
        return getOres(true);
    }

    public KList<IrisOreGenerator> getUndergroundOreGenerators() {
        return getOres(false);
    }

    public IrisOreGeneratorBounds getSurfaceOreGeneratorBounds() {
        return surfaceOreBoundsCache.aquire(() -> IrisOreGeneratorBounds.of(getSurfaceOres()));
    }

    public IrisOreGeneratorBounds getUndergroundOreGeneratorBounds() {
        return undergroundOreBoundsCache.aquire(() -> IrisOreGeneratorBounds.of(getUndergroundOres()));
    }

    private KList<IrisOreGenerator> getSurfaceOres() {
        return getOres(true);
    }

    private KList<IrisOreGenerator> getUndergroundOres() {
        return getOres(false);
    }

    private KList<IrisOreGenerator> getOres(boolean surface) {
        AtomicCache<KList<IrisOreGenerator>> oreCache = surface ? surfaceOreCache : undergroundOreCache;
        return oreCache.aquire(() -> {
            KList<IrisOreGenerator> filtered = new KList<>();
            KList<IrisOreGenerator> localOres = ores;
            int oreCount = localOres.size();
            for (int oreIndex = 0; oreIndex < oreCount; oreIndex++) {
                IrisOreGenerator oreGenerator = localOres.get(oreIndex);
                if (oreGenerator.isGenerateSurface() == surface) {
                    filtered.add(oreGenerator);
                }
            }

            return filtered;
        });
    }

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
        KSet<String> names = getNaturalBiomeIds();
        if (riverPolicy != null) {
            names.addAll(riverPolicy.getAllBiomeIds());
        }
        return names;
    }

    public KSet<String> getNaturalBiomeIds() {
        KSet<String> names = new KSet<>();
        names.addAll(landBiomes);
        names.addAll(caveBiomes);
        names.addAll(seaBiomes);
        names.addAll(shoreBiomes);
        return names;
    }

    public KList<IrisBiome> getAllBiomes(DataProvider g) {
        return resolveBiomes(g, getAllBiomeIds(), true);
    }

    public KList<IrisBiome> getNaturalBiomes(DataProvider g) {
        return resolveBiomes(g, getNaturalBiomeIds(), false);
    }

    private KList<IrisBiome> resolveBiomes(DataProvider g, KSet<String> biomeIds, boolean includeRiverBiomes) {
        KMap<String, IrisBiome> b = new KMap<>();
        KSet<String> names = biomeIds.copy();

        while (!names.isEmpty()) {
            for (String i : new KList<>(names)) {
                if (i == null || i.isBlank() || b.containsKey(i)) {
                    names.remove(i);
                    continue;
                }

                IrisBiome biome = g.getData().getBiomeLoader().load(i);

                names.remove(i);
                if (biome == null) {
                    continue;
                }

                if (biome.isCompatExcluded()) {
                    CompatPools.drop(g.getData(), biome, "region", getLoadKey(), "biomes " + i, null);
                    continue;
                }

                names.add(biome.getCarvingBiome());
                b.put(biome.getLoadKey(), biome);
                names.addAll(biome.getChildren());
                if (includeRiverBiomes && biome.getRiverPolicy() != null) {
                    names.addAll(biome.getRiverPolicy().getAllBiomeIds());
                }
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
        return realCaveBiomes.aquire(() -> resolvePool(g, getCaveBiomes(), "caveBiomes", null));
    }

    public KList<IrisBiome> getRealShoreBiomes(DataProvider g) {
        return realShoreBiomes.aquire(() -> resolvePool(g, getShoreBiomes(), "shoreBiomes", null));
    }

    public KList<IrisBiome> getRealSeaBiomes(DataProvider g) {
        return realSeaBiomes.aquire(() -> resolvePool(g, getSeaBiomes(), "seaBiomes", null));
    }

    public KList<IrisBiome> getRealLandBiomes(DataProvider g) {
        return realLandBiomes.aquire(() -> resolvePool(g, getLandBiomes(), "landBiomes", null));
    }

    /** A biome reference pool with unloadable and gate-excluded biomes removed; drops are reported once. */
    private KList<IrisBiome> resolvePool(DataProvider g, KList<String> keys, String field, List<CompatFinding> sink) {
        IrisData data = g == null ? null : g.getData();
        return CompatPools.load(data == null ? null : data.getBiomeLoader(), keys, data,
                "region", getLoadKey(), field, sink);
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

    /**
     * Cascade: a region that lost every land biome to the gate cannot generate, so it leaves the dimension's region
     * pool too. Loading biomes here is safe - biomes never load regions.
     */
    @Override
    public CompatStatus evaluateCompat(ContentGate gate) {
        CompatStatus base = super.evaluateCompat(gate);

        if (base.excluded()) {
            return base;
        }

        IrisData data = getLoader();

        if (data == null) {
            return base;
        }

        for (int index = 0; index < structures.size(); index++) {
            structures.get(index).reportExcludedCaveBiomes(data, "region", getLoadKey(),
                    "structures[" + index + "].caveBiomes");
        }

        if (getLandBiomes().isEmpty()) {
            return base;
        }

        List<CompatFinding> drops = new ArrayList<>();

        if (!resolvePool(() -> data, getLandBiomes(), "landBiomes", drops).isEmpty()) {
            return base;
        }

        return CompatPools.cascade(data, base, drops, "region", getLoadKey(), "no land biomes remain");
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
