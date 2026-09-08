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

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.data.cache.LazyBoundedCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.DependsOn;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListBiome;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.stream.ProceduralStream;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import art.arcane.iris.spi.PlatformBlockState;
import org.bukkit.block.Biome;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Objects;

/**
 * Represents a biome in a pack. This type is Gson deserialized straight out of user pack JSON, so
 * the field block and the transient {@link AtomicCache} block below are load bearing and must not
 * move. Behavior lives in the same-package companions:
 * {@link IrisBiomeLayerGenerator}, {@link IrisBiomeDerivatives}, {@link IrisBiomeOres},
 * {@link IrisBiomeColorRenderer} and {@link IrisBiomeGenLinks}.
 */
@Accessors(chain = true)
@NoArgsConstructor
@Desc("Represents a biome in iris. Biomes are placed inside of regions and hold objects.\nA biome consists of layers (block palletes), decorations, objects & generators.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisBiome extends IrisRegistrant implements IRare {
    private static final int BIOME_GENERATOR_CACHE_SIZE = 8;

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
    @Getter(AccessLevel.NONE)
    private final transient LazyBoundedCache<Long, CNG> biomeGenerators =
            new LazyBoundedCache<>(BIOME_GENERATOR_CACHE_SIZE);
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient volatile SeededBiomeGenerator recentBiomeGenerator;
    private final transient AtomicCache<Integer> maxHeight = new AtomicCache<>();
    private final transient AtomicCache<Integer> maxWithObjectHeight = new AtomicCache<>();
    private final transient AtomicCache<IrisBiome> realCarveBiome = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisBiome>> realChildren = new AtomicCache<>();
    private final transient AtomicCache<KList<CNG>> layerHeightGenerators = new AtomicCache<>();
    private final transient AtomicCache<KList<CNG>> layerCeilingHeightGenerators = new AtomicCache<>();
    private final transient AtomicCache<KList<CNG>> layerSeaHeightGenerators = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisOreGenerator>> surfaceOreCache = new AtomicCache<>();
    private final transient AtomicCache<KList<IrisOreGenerator>> undergroundOreCache = new AtomicCache<>();
    private final transient AtomicCache<IrisOreGeneratorBounds> surfaceOreBoundsCache = new AtomicCache<>();
    private final transient AtomicCache<IrisOreGeneratorBounds> undergroundOreBoundsCache = new AtomicCache<>();
    private final transient AtomicCache<KSet<String>> surfaceOreReplaceableBlockData = new AtomicCache<>();
    private final transient AtomicCache<EnumMap<IrisDecorationPart, IrisDecorator[]>> decoratorBuckets = new AtomicCache<>();
    private final transient AtomicCache<Biome> derivativeResolved = new AtomicCache<>();
    private final transient AtomicCache<Biome> vanillaDerivativeResolved = new AtomicCache<>();
    private final transient AtomicCache<KList<Biome>> biomeScatterResolved = new AtomicCache<>();
    private final transient AtomicCache<KList<Biome>> biomeSkyScatterResolved = new AtomicCache<>();
    private static final IrisDecorator[] EMPTY_BUCKET = new IrisDecorator[0];
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
    @Desc("Profile-driven 3D cave configuration")
    private IrisCaveProfile caveProfile = new IrisCaveProfile();
    @Desc("Biome-level river placement, routing, profile, biome, and geometry policy. Omit to inherit region and dimension settings.")
    private IrisRiverPolicy riverPolicy = null;
    @MinNumber(1)
    @MaxNumber(512)
    @Desc("The rarity of this biome (integer)")
    private int rarity = 1;
    @Desc("A color for visualizing this biome with a color. I.e. #F13AF5. This will show up on the map.")
    private String color = null;
    @Required
    @RegistryListBiome
    @Desc("The raw derivative of this biome. This is required or the terrain will not properly generate. Use any vanilla biome type. Look in examples/biome-list.txt")
    private String derivative = "minecraft:the_void";
    @Required
    @RegistryListBiome
    @Desc("Override the derivative used for vanilla structure selection. Iris still enforces the generated terrain role: land-only Minecraft derivatives on sea biomes expose no native structure biome, and land-only derivatives on shore biomes resolve as beach, while exact ocean, river, beach, and shore variants remain eligible. Non-Minecraft namespaces remain authoritative. Not defining this value selects derivative.")
    private String vanillaDerivative = null;
    @ArrayType(min = 1, type = String.class)
    @RegistryListBiome
    @Desc("You can instead specify multiple biome derivatives to randomly scatter colors in this biome")
    private KList<String> biomeScatter = new KList<>();
    @ArrayType(min = 1, type = String.class)
    @RegistryListBiome
    @Desc("Since 1.13 supports 3D biomes, you can add different derivative colors for anything above the terrain. (Think swampy tree leaves with a desert looking grass surface)")
    private KList<String> biomeSkyScatter = new KList<>();
    @DependsOn({"children"})
    @Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, how much smaller will it be (inside of this biome). Higher values means a smaller biome relative to this biome's size. Set higher than 1.0 and below 3.0 for best results.")
    private double childShrinkFactor = 1.5;
    @DependsOn({"children"})
    @Desc("If this biome has children biomes, and the gen layer chooses one of this biomes children, How will it be shaped?")
    private IrisGeneratorStyle childStyle = NoiseStyle.CELLULAR_IRIS_DOUBLE.style();
    @RegistryListResource(IrisBiome.class)
    @ArrayType(min = 1, type = String.class)
    @Desc("List any biome names (file names without.json) here as children. Portions of this biome can sometimes morph into their children. Iris supports cyclic relationships such as A > B > A > B. Iris will stop checking 4 biomes down the tree.")
    private KList<String> children = new KList<>();
    @RegistryListResource(IrisBiome.class)
    @Desc("Registers the referenced biome with this pack's reachable biome set. It is NOT applied as a substitute under carvings; cave biomes come from the region's caveBiomes list.")
    private String carvingBiome = "";
    @MinNumber(0)
    @MaxNumber(256)
    @Desc("Minimum depth below terrain surface required before this cave biome can be selected.")
    private int caveMinDepthBelowSurface = 0;
    @Desc("The default slab if iris decides to place a slab in this biome. Default is no slab.")
    private IrisBiomePaletteLayer slab = new IrisBiomePaletteLayer().zero();
    @Desc("The default wall if iris decides to place a wall higher than 2 blocks (steep hills or possibly cliffs)")
    private IrisBiomePaletteLayer wall = new IrisBiomePaletteLayer().zero();
    @Required
    @ArrayType(type = IrisBiomePaletteLayer.class)
    @Desc("This defines the layers of materials in this biome. Each layer has a palette and min/max height and some other properties. Usually a grassy/sandy layer then a dirt layer then a stone layer. Iris will fill in the remaining blocks below your layers with stone.")
    private KList<IrisBiomePaletteLayer> layers = new KList<IrisBiomePaletteLayer>().qadd(new IrisBiomePaletteLayer());
    @ArrayType(type = IrisBiomePaletteLayer.class)
    @Desc("Layers of materials placed on cave ceilings in this biome, indexed upward from the ceiling surface. Must not have more entries than layers, whose height generators it reuses. Omitting this leaves cave ceilings unchanged.")
    private KList<IrisBiomePaletteLayer> caveCeilingLayers = new KList<>();
    @ArrayType(type = IrisBiomePaletteLayer.class)
    @Desc("Layers of materials filling the water column of sea biomes, indexed downward from the water surface. Anything below the last layer is filled with the dimension fluid palette, not stone.")
    private KList<IrisBiomePaletteLayer> seaLayers = new KList<>();
    @ArrayType(min = 1, type = IrisDecorator.class)
    @Desc("Decorators are used for things like tall grass, bisected flowers, and even kelp or cactus (random heights)")
    private KList<IrisDecorator> decorators = new KList<>();
    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Desc("Objects define what schematics (iob files) iris will place in this biome")
    private KList<IrisObjectPlacement> objects = new KList<>();
    @Desc("Procedural objects (such as trees) iris generates from scratch and places in this biome, instead of loading them from iob files")
    private IrisProceduralObjects proceduralObjects = new IrisProceduralObjects();
    @ArrayType(min = 1, type = IrisStructurePlacement.class)
    @Desc("Structures define jigsaw or vanilla/datapack structures iris will place in this biome")
    private KList<IrisStructurePlacement> structures = new KList<>();
    @ArrayType(min = 1, type = IrisFloatingChildBiomes.class)
    @Desc("Floating child biomes that procedurally generate above this biome's terrain. Each entry references a target biome whose layers, decorators, and objects drive the floating island's visual design, while the config here drives size, shape, altitude, rarity, and water level. Multiple entries are supported and selected by rarity per column unless mergeFloatingChildBiomes is enabled.")
    private KList<IrisFloatingChildBiomes> floatingChildBiomes = new KList<>();
    @Desc("When true, every floating child entry is sampled independently and their solid masks are unioned, so multiple floating islands can stack, overlap, and collide instead of the picker choosing only one child per column.")
    private boolean mergeFloatingChildBiomes = false;
    @ArrayType(min = 1, type = IrisBiomeGeneratorLink.class)
    @Desc("Generators for this biome. Multiple generators with different interpolation sizes will mix with other biomes how you would expect. This defines your biome height relative to the fluid height. Use negative for oceans.")
    private KList<IrisBiomeGeneratorLink> generators = new KList<IrisBiomeGeneratorLink>().qadd(new IrisBiomeGeneratorLink());

    @Desc("Optional volumetric surface terrain profile. Shapes projecting ledges, undercuts, arches, and fissures around this biome's height generators.")
    private IrisTerrain3D terrain3D = null;
    @ArrayType(min = 1, type = IrisDepositGenerator.class)
    @Desc("Define biome deposit generators that add onto the existing regional and global deposit generators")
    private KList<IrisDepositGenerator> deposits = new KList<>();
    @ArrayType(type = String.class)
    @Desc("Optional exact host block ids that replace each ore deposit's surfaceReplaceableBlocks on exterior terrain inside this biome. Omit to inherit the deposit setting; an empty list forbids surface ore.")
    private KList<String> surfaceOreReplaceableBlocks = null;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Multiplier applied to the frequency of every ore deposit whose center is inside this biome. A value of 0.4 keeps 40% of ore veins while leaving non-ore deposits unchanged.")
    private double oreDepositFrequencyMultiplier = 1D;
    @MinNumber(0.01)
    @MaxNumber(16)
    @Desc("Multiplier applied to the block count of every ore deposit whose center is inside this biome. Non-ore deposits are unchanged.")
    private double oreDepositSizeMultiplier = 1D;
    @ArrayType(min = 1, type = IrisDepositVariant.class)
    @Desc("Deposit ore remap rules scoped to this biome. Each entry declares a vertical band and a source->replacement block id map. Applied before regional and dimension rules; first matching biome rule wins.")
    private KList<IrisDepositVariant> depositVariants = new KList<>();
    private transient volatile InferredType inferredType;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient EnumMap<InferredType, IrisBiome> inferredVariants = new EnumMap<>(InferredType.class);
    @Desc("Collection of ores to be generated")
    @ArrayType(type = IrisOreGenerator.class, min = 1)
    private KList<IrisOreGenerator> ores = new KList<>();

    public boolean hasSurfaceOreReplaceableBlocks() {
        return IrisBiomeOres.hasSurfaceOreReplaceableBlocks(this);
    }

    public boolean canReplaceSurfaceOre(PlatformBlockState state) {
        return IrisBiomeOres.canReplaceSurfaceOre(this, state);
    }

    public PlatformBlockState generateOres(int x, int y, int z, RNG rng, IrisData data, boolean surface) {
        return IrisBiomeOres.generateOres(this, x, y, z, rng, data, surface);
    }

    public PlatformBlockState generateSurfaceOres(int x, int y, int z, RNG rng, IrisData data) {
        return IrisBiomeOres.generateSurfaceOres(this, x, y, z, rng, data);
    }

    public PlatformBlockState generateUndergroundOres(int x, int y, int z, RNG rng, IrisData data) {
        return IrisBiomeOres.generateUndergroundOres(this, x, y, z, rng, data);
    }

    public boolean hasSurfaceOres() {
        return IrisBiomeOres.hasSurfaceOres(this);
    }

    public boolean hasUndergroundOres() {
        return IrisBiomeOres.hasUndergroundOres(this);
    }

    public synchronized IrisBiome setInferredType(InferredType inferredType) {
        this.inferredType = inferredType;
        return this;
    }

    public IrisBiome withInferredType(InferredType type) {
        Objects.requireNonNull(type, "type");
        // Lock-free fast path on the volatile field: this runs per column per implode level
        // against biome instances shared by every burst thread. The variant map below stays
        // monitor-guarded (plain EnumMap, not safe for concurrent reads during writes).
        if (inferredType == type) {
            return this;
        }
        return withInferredTypeSlow(type);
    }

    private synchronized IrisBiome withInferredTypeSlow(InferredType type) {
        if (inferredType == null) {
            inferredType = type;
            return this;
        }
        if (inferredType == type) {
            return this;
        }
        IrisBiome cached = inferredVariants.get(type);
        if (cached != null) {
            return cached;
        }
        IrisData data = getLoader();
        if (data == null) {
            throw new IllegalStateException("Cannot create an inferred biome variant without an Iris data loader.");
        }
        IrisBiome variant = data.getGson().fromJson(data.getGson().toJson(this), IrisBiome.class);
        variant.setLoader(data);
        variant.setLoadKey(getLoadKey());
        variant.setLoadFile(getLoadFile());
        variant.inferredType = type;
        inferredVariants.put(type, variant);
        return variant;
    }

    /**
     * Hand written override of the Lombok setter. It must stay here because it invalidates the ore
     * caches that {@link IrisBiomeOres} reads.
     */
    public void setOres(KList<IrisOreGenerator> ores) {
        this.ores = ores == null ? new KList<>() : ores;
        surfaceOreCache.reset();
        undergroundOreCache.reset();
        surfaceOreBoundsCache.reset();
        undergroundOreBoundsCache.reset();
    }

    public IrisBiome setSurfaceOreReplaceableBlocks(KList<String> surfaceOreReplaceableBlocks) {
        this.surfaceOreReplaceableBlocks = surfaceOreReplaceableBlocks;
        surfaceOreReplaceableBlockData.reset();
        return this;
    }

    public KList<IrisOreGenerator> getSurfaceOreGenerators() {
        return IrisBiomeOres.getSurfaceOreGenerators(this);
    }

    public KList<IrisOreGenerator> getUndergroundOreGenerators() {
        return IrisBiomeOres.getUndergroundOreGenerators(this);
    }

    public IrisOreGeneratorBounds getSurfaceOreGeneratorBounds() {
        return IrisBiomeOres.getSurfaceOreGeneratorBounds(this);
    }

    public IrisOreGeneratorBounds getUndergroundOreGeneratorBounds() {
        return IrisBiomeOres.getUndergroundOreGeneratorBounds(this);
    }

    public Biome getDerivative() {
        Biome cached = derivativeResolved.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return derivativeResolved.aquire(() -> IrisBiomeDerivatives.resolveBiomeKey(derivative));
    }

    public Biome getVanillaDerivative() {
        Biome resolved = vanillaDerivative == null
                ? null
                : vanillaDerivativeResolved.aquire(() -> IrisBiomeDerivatives.resolveBiomeKey(vanillaDerivative));
        return resolved == null ? getDerivative() : resolved;
    }

    public String getVanillaDerivativeKey() {
        return IrisBiomeDerivatives.getVanillaDerivativeKey(derivative, vanillaDerivative);
    }

    public String getStructureDerivativeKey() {
        return IrisBiomeDerivatives.getStructureDerivativeKey(this);
    }

    public String getDerivativeKey() {
        return IrisBiomeDerivatives.namespacedBiomeKey(derivative);
    }

    KList<Biome> getBiomeScatterResolved() {
        KList<Biome> cached = biomeScatterResolved.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return biomeScatterResolved.aquire(() -> IrisBiomeDerivatives.resolveBiomeKeys(biomeScatter));
    }

    KList<Biome> getBiomeSkyScatterResolved() {
        KList<Biome> cached = biomeSkyScatterResolved.getIfPresent();

        if (cached != null) {
            return cached;
        }

        return biomeSkyScatterResolved.aquire(() -> IrisBiomeDerivatives.resolveBiomeKeys(biomeSkyScatter));
    }

    public boolean isCustom() {
        return getCustomDerivitives() != null && getCustomDerivitives().isNotEmpty();
    }

    public double getGenLinkMax(String loadKey, Engine engine) {
        return IrisBiomeGenLinks.getGenLinkMax(this, loadKey, engine);
    }

    public double getGenLinkMin(String loadKey, Engine engine) {
        return IrisBiomeGenLinks.getGenLinkMin(this, loadKey, engine);
    }

    public IrisBiomeGeneratorLink getGenLink(String loadKey) {
        return IrisBiomeGenLinks.getGenLink(this, loadKey);
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
        IrisData loader = getLoader();
        Engine engine = resolveBiomeGeneratorEngine(loader);
        return getBiomeGenerator(random, engine);
    }

    public CNG getBiomeGenerator(RNG random, Engine engine) {
        IrisData loader = getLoader();
        long seed = engine == null ? random.getSeed() : engine.getSeedManager().getBiome();
        SeededBiomeGenerator recent = recentBiomeGenerator;
        if (recent != null && recent.seed == seed) {
            return recent.generator;
        }

        CNG generator = biomeGenerators.computeIfAbsent(seed, ignored -> createBiomeGenerator(seed, loader));
        recentBiomeGenerator = new SeededBiomeGenerator(seed, generator);
        return generator;
    }

    private Engine resolveBiomeGeneratorEngine(IrisData loader) {
        IrisContext context = IrisContext.get();
        if (context != null) {
            Engine contextEngine = context.getEngine();
            if (!contextEngine.isClosed() && (loader == null || contextEngine.getData() == loader)) {
                return contextEngine;
            }
        }

        return loader == null ? null : loader.getEngine();
    }

    private CNG createBiomeGenerator(long seed, IrisData loader) {
        int signature = 213949 + 228888 + getRarity() + getName().length();
        return biomeStyle.createNoCache(new RNG(seed).nextParallelRNG(signature), loader);
    }

    public CNG getChildrenGenerator(RNG random, int sig, double scale) {
        return childrenCell.aquire(() -> getChildStyle().create(random.nextParallelRNG(sig * 2137), getLoader()).bake().scale(scale).bake());
    }

    public KList<PlatformBlockState> generateLayers(IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        return IrisBiomeLayerGenerator.generateLayers(this, dim, wx, wz, random, maxDepth, height, rdata, complex);
    }

    public KList<PlatformBlockState> generateLayersWithSlope(IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, ProceduralStream<Double> slopeStream) {
        return IrisBiomeLayerGenerator.generateLayers(this, dim, wx, wz, random, maxDepth, height, rdata, slopeStream);
    }

    public KList<PlatformBlockState> generateCeilingLayers(IrisDimension dim, double wx, double wz, RNG random, int maxDepth, int height, IrisData rdata, IrisComplex complex) {
        return IrisBiomeLayerGenerator.generateCeilingLayers(this, dim, wx, wz, random, maxDepth, height, rdata, complex);
    }

    public KList<PlatformBlockState> generateLockedLayers(double wx, double wz, RNG random, int maxDepthf, int height, IrisData rdata, IrisComplex complex) {
        return IrisBiomeLayerGenerator.generateLockedLayers(this, wx, wz, random, maxDepthf, height, rdata, complex);
    }

    public KList<PlatformBlockState> generateSeaLayers(double wx, double wz, RNG random, int maxDepth, IrisData rdata) {
        return IrisBiomeLayerGenerator.generateSeaLayers(this, wx, wz, random, maxDepth, rdata);
    }

    /**
     * Note the arity split: {@code getMaxHeight()} is the Lombok getter for the height cache field,
     * {@code getMaxHeight(Engine)} is the resolved biome height.
     */
    public int getMaxHeight(Engine engine) {
        return IrisBiomeLayerGenerator.getMaxHeight(this, engine);
    }

    public int getMaxWithObjectHeight(IrisData data, Engine engine) {
        return IrisBiomeLayerGenerator.getMaxWithObjectHeight(this, data, engine);
    }

    public KList<CNG> getLayerHeightGenerators(RNG rng, IrisData rdata) {
        return IrisBiomeLayerGenerator.getLayerHeightGenerators(this, rng, rdata);
    }

    public KList<CNG> getLayerSeaHeightGenerators(RNG rng, IrisData data) {
        return IrisBiomeLayerGenerator.getLayerSeaHeightGenerators(this, rng, data);
    }

    public PlatformBlockState getSurfaceBlock(int x, int z, RNG rng, IrisData idm) {
        return IrisBiomeLayerGenerator.getSurfaceBlock(this, x, z, rng, idm);
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
        return IrisBiomeDerivatives.getSkyBiome(this, rng, resolveBiomeGeneratorEngine(getLoader()), x, y, z);
    }

    public Biome getSkyBiome(RNG rng, Engine engine, double x, double y, double z) {
        return IrisBiomeDerivatives.getSkyBiome(this, rng, engine, x, y, z);
    }

    public IrisBiomeCustom getCustomBiome(RNG rng, double x, double y, double z) {
        return IrisBiomeDerivatives.getCustomBiome(this, rng, resolveBiomeGeneratorEngine(getLoader()), x, y, z);
    }

    public IrisBiomeCustom getCustomBiome(RNG rng, Engine engine, double x, double y, double z) {
        return IrisBiomeDerivatives.getCustomBiome(this, rng, engine, x, y, z);
    }

    public KList<IrisBiome> getRealChildren(DataProvider g) {
        return realChildren.aquire(() ->
        {
            IrisData data = g == null ? null : g.getData();
            return CompatPools.load(data == null ? null : data.getBiomeLoader(), getChildren(), data,
                    "biome", getLoadKey(), "children", null);
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
        return IrisBiomeDerivatives.getGroundBiome(this, rng, resolveBiomeGeneratorEngine(getLoader()), x, y, z);
    }

    public Biome getGroundBiome(RNG rng, Engine engine, double x, double y, double z) {
        return IrisBiomeDerivatives.getGroundBiome(this, rng, engine, x, y, z);
    }

    public String getSkyBiomeKey(RNG rng, double x, double y, double z) {
        return IrisBiomeDerivatives.getSkyBiomeKey(this, rng, resolveBiomeGeneratorEngine(getLoader()), x, y, z);
    }

    public String getSkyBiomeKey(RNG rng, Engine engine, double x, double y, double z) {
        return IrisBiomeDerivatives.getSkyBiomeKey(this, rng, engine, x, y, z);
    }

    public String getGroundBiomeKey(RNG rng, double x, double y, double z) {
        return IrisBiomeDerivatives.getGroundBiomeKey(this, rng, resolveBiomeGeneratorEngine(getLoader()), x, y, z);
    }

    public String getGroundBiomeKey(RNG rng, Engine engine, double x, double y, double z) {
        return IrisBiomeDerivatives.getGroundBiomeKey(this, rng, engine, x, y, z);
    }

    public Color getColor(Engine engine, RenderType type) {
        return IrisBiomeColorRenderer.getColor(this, engine, type);
    }

    @Override
    public String getFolderName() {
        return "biomes";
    }

    public IrisDecorator[] getDecoratorBucket(IrisDecorationPart part) {
        return decoratorBuckets.aquire(this::buildDecoratorBuckets).getOrDefault(part, EMPTY_BUCKET);
    }

    private EnumMap<IrisDecorationPart, IrisDecorator[]> buildDecoratorBuckets() {
        EnumMap<IrisDecorationPart, KList<IrisDecorator>> staging = new EnumMap<>(IrisDecorationPart.class);
        for (IrisDecorator d : decorators) {
            staging.computeIfAbsent(d.getPartOf(), k -> new KList<>()).add(d);
        }
        EnumMap<IrisDecorationPart, IrisDecorator[]> result = new EnumMap<>(IrisDecorationPart.class);
        for (IrisDecorationPart part : IrisDecorationPart.values()) {
            KList<IrisDecorator> list = staging.get(part);
            result.put(part, list == null ? EMPTY_BUCKET : list.toArray(EMPTY_BUCKET));
        }
        return result;
    }

    public String getTypeName() {
        return "Biome";
    }

    private static final class SeededBiomeGenerator {
        private final long seed;
        private final CNG generator;

        private SeededBiomeGenerator(long seed, CNG generator) {
            this.seed = seed;
            this.generator = generator;
        }
    }
}
