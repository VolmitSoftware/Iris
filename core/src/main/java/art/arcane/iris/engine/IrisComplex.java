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

package art.arcane.iris.engine;


import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.history.GenerationBlend;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyNaturalSample;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.engine.hydrology.runtime.IrisHydrologyRuntimeContext;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.mantle.components.MantleHydrologyCaveVoxelView;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisRaritySelection;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.engine.object.IrisInterpolator;
import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisDeepFluidConfig;
import art.arcane.iris.engine.object.IrisSurfacePoolConfig;
import art.arcane.iris.engine.object.IrisHydrology;
import art.arcane.iris.engine.object.IrisRiverHydrology;
import art.arcane.iris.engine.object.IrisRiverProfile;
import art.arcane.iris.engine.object.IrisShapedGeneratorStyle;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.interpolation.NoiseBounds;
import art.arcane.iris.util.project.interpolation.NoiseBoundsProvider;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Path;
import java.util.function.BiFunction;

@Data
@EqualsAndHashCode(exclude = {"data", "gridBoundsCache", "sharedCornerBounds", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "hydrologyRuntime", "imageMapRuntime"})
@ToString(exclude = {"data", "gridBoundsCache", "sharedCornerBounds", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "hydrologyRuntime", "imageMapRuntime"})
public class IrisComplex implements DataProvider {
    private static final NoiseBounds ZERO_NOISE_BOUNDS = new NoiseBounds(0D, 0D);
    private static final AtomicLong lastBoundsFailureLog = new AtomicLong(0L);
    private static final int GRID_BOUNDS_CACHE_SIZE = 8192;
    private static final int STUDIO_NOISE_CACHE_SIZE = 32_768;
    /** One million corners: about 16 MB, roughly a 4000 by 4000 block area at the 4-block grid. */
    private static final int SHARED_CORNER_BOUNDS_CAPACITY = 1 << 20;
    private static final int HEIGHT_BOUNDS_GRID = 4;
    private static final Comparator<IrisInterpolator> INTERPOLATOR_ORDER = Comparator
            .comparing((IrisInterpolator interpolator) -> interpolator.getFunction().name())
            .thenComparingDouble(IrisInterpolator::getHorizontalScale);
    private static final Comparator<IrisGenerator> GENERATOR_ORDER = Comparator.comparing(
            IrisGenerator::getLoadKey,
            Comparator.nullsFirst(Comparator.naturalOrder())
    );
    private static final InferredType[] INFERRED_BIOME_PREPARATION_ORDER = {
            InferredType.LAND,
            InferredType.CAVE,
            InferredType.SEA,
            InferredType.SHORE
    };
    @Getter(AccessLevel.NONE)
    private final transient ThreadLocal<GridBoundsCache> gridBoundsCache = ThreadLocal.withInitial(GridBoundsCache::new);
    private transient volatile SharedCornerBounds sharedCornerBounds = new SharedCornerBounds(SHARED_CORNER_BOUNDS_CAPACITY);
    @Getter(AccessLevel.NONE)
    private final transient IrisInterpolator[] frozenInterpolators;
    @Getter(AccessLevel.NONE)
    private final transient IrisGenerator[][] frozenGenerators;
    @Getter(AccessLevel.NONE)
    private final transient Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> inferredBiomeStreams;
    private final TransitionGenerationPlan transitionGenerationPlan;
    private RNG rng;
    private double fluidHeight;
    private IrisData data;
    private Map<IrisInterpolator, Set<IrisGenerator>> generators;
    private ProceduralStream<IrisRegion> regionStream;
    private ProceduralStream<Double> regionStyleStream;
    private ProceduralStream<Double> regionIdentityStream;
    private ProceduralStream<UUID> regionIDStream;
    private ProceduralStream<InferredType> bridgeStream;
    private ProceduralStream<IrisBiome> landBiomeStream;
    private ProceduralStream<IrisBiome> caveBiomeStream;
    private ProceduralStream<IrisBiome> seaBiomeStream;
    private ProceduralStream<IrisBiome> shoreBiomeStream;
    private ProceduralStream<IrisBiome> baseBiomeStream;
    @Getter(AccessLevel.NONE)
    private final transient boolean reuseNaturalBaseBiome;
    private ProceduralStream<UUID> baseBiomeIDStream;
    private ProceduralStream<IrisBiome> naturalTrueBiomeStream;
    private ProceduralStream<IrisBiome> unblendedNaturalTrueBiomeStream;
    private ProceduralStream<IrisBiome> trueBiomeStream;
    private ProceduralStream<PlatformBiome> trueBiomeDerivativeStream;
    private ProceduralStream<Double> naturalHeightStream;
    private ProceduralStream<Double> unblendedNaturalHeightStream;
    private final ResolvedTerrainProvider resolvedTerrain;
    private ProceduralStream<Double> placementHeightStream;
    private ProceduralStream<Double> heightStream;
    private ProceduralStream<Integer> roundedHeighteightStream;
    private ProceduralStream<Double> maxHeightStream;
    private ProceduralStream<Double> overlayStream;
    private ProceduralStream<Double> heightFluidStream;
    private ProceduralStream<Double> slopeStream;
    private ProceduralStream<Double> naturalSlopeStream;
    private ProceduralStream<Double> riverDistanceStream;
    private ProceduralStream<Double> riverFlowStream;
    private ProceduralStream<Double> riverCarveWeightStream;
    private ProceduralStream<Double> riverWaterSurfaceStream;
    private ProceduralStream<Integer> topSurfaceStream;
    private ProceduralStream<IrisDecorator> terrainSurfaceDecoration;
    private ProceduralStream<IrisDecorator> terrainCeilingDecoration;
    private ProceduralStream<IrisDecorator> terrainCaveSurfaceDecoration;
    private ProceduralStream<IrisDecorator> terrainCaveCeilingDecoration;
    private ProceduralStream<IrisDecorator> seaSurfaceDecoration;
    private ProceduralStream<IrisDecorator> seaFloorDecoration;
    private ProceduralStream<IrisDecorator> shoreSurfaceDecoration;
    private ProceduralStream<PlatformBlockState> rockStream;
    private ProceduralStream<PlatformBlockState> fluidStream;
    private Map<String, ProceduralStream<PlatformBlockState>> hydrologyFluidStreams;
    private IrisBiome focusBiome;
    private IrisRegion focusRegion;
    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> generatorBounds;
    private Set<IrisBiome> generatorBiomes;
    private IrisHydrologyRuntime hydrologyRuntime;
    private transient IrisImageMapRuntime imageMapRuntime;
    // Copy-on-write: reads happen per column on every burst thread; the synchronizedMap
    // monitor was taken on every HIT. Writes are once per biome and bounded, so a fresh map
    // per insert is cheap. Identity keying is load-bearing (IrisBiome is mutable/value-hashed).
    private volatile IdentityHashMap<IrisBiome, ChildSelectionPlan> childSelectionPlans = new IdentityHashMap<>();
    private final Object childSelectionPlanLock = new Object();

    public IrisComplex(Engine engine) {
        this(engine, false, null);
    }

    public IrisComplex(Engine engine, boolean simple) {
        this(engine, simple, null);
    }

    public IrisComplex(Engine engine, TransitionGenerationPlan transitionGenerationPlan) {
        this(engine, false, transitionGenerationPlan);
    }

    IrisComplex(Engine engine, boolean simple, TransitionGenerationPlan transitionGenerationPlan) {
        this.transitionGenerationPlan = transitionGenerationPlan;
        this.resolvedTerrain = new ResolvedTerrainProvider(engine);
        int cacheSize = noiseCacheSize(engine, IrisSettings.get().getPerformance().getNoiseCacheSize());
        IrisBiome emptyBiome = new IrisBiome().setInferredType(InferredType.CAVE);
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getSeedManager().getComplex());
        this.data = engine.getData();
        imageMapRuntime = IrisImageMapRuntime.compile(engine);
        double height = engine.getMaxHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new HashMap<>();
        generatorBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        // A registrant the version-content gate excluded never enters a pool, focus included.
        focusBiome = compatUsable(engine.getFocus());
        focusRegion = compatUsable(engine.getFocusRegion());
        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new HashMap<>();
        KList<IrisRegion> preparedRegions = new KList<>();

        if (focusBiome != null) {
            focusBiome = focusBiome.withInferredType(InferredType.LAND);
            focusRegion = findRegion(focusBiome, engine);
        }

        //@builder
        if (focusRegion != null) {
            prepareInferredBiomes(focusRegion, preparedRegions);
            focusRegion.getNaturalBiomes(this).forEach(this::registerGenerators);
        } else {
            engine.getDimension().getRegions().forEach(regionKey -> {
                IrisRegion region = data.getRegionLoader().load(regionKey);
                if (region == null || region.isCompatExcluded()) {
                    return;
                }
                prepareInferredBiomes(region, preparedRegions);
                region.getNaturalBiomes(this).forEach(this::registerGenerators);
            });
            for (IrisRegion region : imageMapRuntime.getMappedRegions()) {
                prepareInferredBiomes(region, preparedRegions);
                region.getNaturalBiomes(this).forEach(this::registerGenerators);
            }
            imageMapRuntime.getMappedBiomes().forEach(this::registerGenerators);
        }
        reuseNaturalBaseBiome = hasFixedNaturalBiomeNoise(engine.getDimension(), generatorBiomes);
        inferredBiomeStreams = compileInferredBiomeStreams(
                preparedRegions,
                (region, inferredType) -> compileInferredBiomeStream(engine, region, inferredType, emptyBiome)
        );
        GeneratorGroup[] generatorGroups = freezeGeneratorGroups(generators);
        int interpolatorCount = generatorGroups.length;
        frozenInterpolators = new IrisInterpolator[interpolatorCount];
        frozenGenerators = new IrisGenerator[interpolatorCount][];
        for (int frozenIndex = 0; frozenIndex < generatorGroups.length; frozenIndex++) {
            frozenInterpolators[frozenIndex] = generatorGroups[frozenIndex].interpolator();
            frozenGenerators[frozenIndex] = generatorGroups[frozenIndex].generators();
        }
        generatorBounds = buildGeneratorBounds(engine);
        KList<IrisShapedGeneratorStyle> overlayNoise = engine.getDimension().getOverlayNoise();
        overlayStream = overlayNoise.isEmpty()
                ? ProceduralStream.ofDouble((x, z) -> 0.0D)
                : ProceduralStream.ofDouble((x, z) -> {
            double value = 0D;

            for (IrisShapedGeneratorStyle style : overlayNoise) {
                value += style.get(rng, getData(), x, z);
            }

            return value;
        });
        rockStream = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextParallelRNG(45), data).stream()
                .select(engine.getDimension().getRockPalette().getBlockData(data));
        fluidStream = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextParallelRNG(78), data).stream()
                .select(engine.getDimension().getFluidPalette().getBlockData(data));
        hydrologyFluidStreams = createHydrologyFluidStreams(engine.getDimension().getHydrology());
        regionStyleStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883), getData()).stream()
                .zoom(engine.getDimension().getRegionZoom());
        regionIdentityStream = regionStyleStream.fit(Integer.MIN_VALUE, Integer.MAX_VALUE);
        ProceduralStream<IrisRegion> proceduralRegionStream = focusRegion != null ?
                ProceduralStream.of((x, z) -> focusRegion,
                        Interpolated.of(a -> 0D, a -> focusRegion))
                : regionStyleStream
                .selectRarity(compatRegionPool(engine))
                .cache2D("regionStream", engine, cacheSize);
        regionStream = focusRegion != null ? proceduralRegionStream : proceduralRegionStream
                .convertAware2D((region, x, z) -> {
                    IrisRegion mapped = imageMapRuntime.sampleRegion(x, z);
                    return mapped == null ? region : mapped;
                })
                .cache2D("imageMappedRegionStream", engine, cacheSize);
        regionIDStream = regionIdentityStream.convertCached((i) -> new UUID(Double.doubleToLongBits(i),
                String.valueOf(i * 38445).hashCode() * 3245556666L));
        caveBiomeStream = regionStream.contextInjecting(engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.CAVE))
                .convertAware2D(ProceduralStream::get).cache2D("caveBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.CAVE, caveBiomeStream);
        landBiomeStream = regionStream.contextInjecting(engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.LAND))
                .convertAware2D(ProceduralStream::get)
                .cache2D("landBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        seaBiomeStream = regionStream.contextInjecting(engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.SEA))
                .convertAware2D(ProceduralStream::get)
                .cache2D("seaBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        shoreBiomeStream = regionStream.contextInjecting(engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.SHORE))
                .convertAware2D(ProceduralStream::get).cache2D("shoreBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);
        bridgeStream = focusBiome != null ? ProceduralStream.of((x, z) -> focusBiome.getInferredType(),
                Interpolated.of(a -> 0D, a -> focusBiome.getInferredType())) :
                engine.getDimension().getContinentalStyle().create(rng.nextParallelRNG(234234565), getData())
                        .bake().scale(1D / engine.getDimension().getContinentZoom()).bake().stream()
                        .convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND)
                        .cache2D("bridgeStream", engine, cacheSize);
        ProceduralStream<IrisBiome> proceduralBaseBiomeStream = focusBiome != null ? ProceduralStream.of((x, z) -> focusBiome,
                Interpolated.of(a -> 0D, a -> focusBiome)) :
                bridgeStream.convertAware2D((t, x, z) -> inferredStreams.get(t).get(x, z))
                        .convertAware2D(this::implode)
                        .cache2D("baseBiomeStream", engine, cacheSize);
        baseBiomeStream = focusBiome != null ? proceduralBaseBiomeStream : proceduralBaseBiomeStream
                .convertAware2D((biome, x, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null ? biome : mapped;
                })
                .cache2D("imageMappedBaseBiomeStream", engine, cacheSize);
        unblendedNaturalHeightStream = ProceduralStream.of(
                (x, z) -> sampleUnblendedNaturalTerrainHeight(engine, x, z),
                Interpolated.DOUBLE
        ).cache2DDouble("unblendedNaturalHeightStream", engine, cacheSize);
        naturalHeightStream = unblendedNaturalHeightStream;
        naturalTrueBiomeStream = focusBiome != null ? ProceduralStream.of((x, y) -> focusBiome, Interpolated.of(a -> 0D,
                        b -> focusBiome))
                .cache2D("naturalTrueBiomeStream-focus", engine, cacheSize) : naturalHeightStream
                .convertAware2D((h, x, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null
                            ? fixBiomeType(h, baseBiomeStream.get(x, z), regionStream.get(x, z), x, z, fluidHeight)
                            : mapped;
                })
                .cache2D("naturalTrueBiomeStream", engine, cacheSize);
        unblendedNaturalTrueBiomeStream = transitionGenerationPlan == null || focusBiome != null
                ? naturalTrueBiomeStream
                : unblendedNaturalHeightStream.convertAware2D((h, x, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null
                            ? fixBiomeType(h, baseBiomeStream.get(x, z), regionStream.get(x, z), x, z, fluidHeight)
                            : mapped;
                }).cache2D("unblendedNaturalTrueBiomeStream", engine, cacheSize);
        IrisHydrology configuredHydrology = engine.getDimension().getHydrology();
        if (hydrologyActive(configuredHydrology)) {
            hydrologyRuntime = new IrisHydrologyRuntime(new IrisHydrologyRuntimeContext(
                    engine.getSeedManager().getBodies(),
                    engine.getHeight(),
                    engine.getDimension(),
                    data,
                    (x, z, naturalHeight) -> sampleHydrologyNatural(engine, x, z, naturalHeight),
                    (x, z) -> naturalHeightStream.getDouble(x, z),
                    (x, z) -> describeNaturalHeight(engine, x, z),
                    this::sampleNaturalOcean,
                    footprint -> new MantleHydrologyCaveVoxelView(engine, this, footprint),
                    () -> engine.getPlatformHooks().isMainThread()
            ));
            hydrologyRuntime.setNeighbourPrefetchEnabled(!engine.isStudio());
        }
        heightStream = ProceduralStream.ofDouble((x, z) -> resolveHydrologyTerrainHeight(x, z))
                .cache2DDouble("heightStream", engine, cacheSize);
        placementHeightStream = ProceduralStream.ofDouble(this::samplePlacementHeight);
        roundedHeighteightStream = placementHeightStream.contextInjecting(engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .round();
        slopeStream = placementHeightStream.contextInjecting(engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .slope(3);
        naturalSlopeStream = naturalHeightStream.slope(3).cache2DDouble("naturalSlopeStream", engine, cacheSize);
        trueBiomeStream = focusBiome != null ? ProceduralStream.of((x, y) -> focusBiome, Interpolated.of(a -> 0D,
                        b -> focusBiome))
                .cache2D("trueBiomeStream-focus", engine, cacheSize) : heightStream
                .convertAware2D((terrainHeight, x, z) -> resolveHydrologySurfaceBiome(terrainHeight, x, z))
                .cache2D("trueBiomeStream", engine, cacheSize);
        trueBiomeDerivativeStream = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convert((b) -> IrisPlatforms.get().registries().biome(b.getDerivativeKey())).cache2D("trueBiomeDerivativeStream", engine, cacheSize);
        riverDistanceStream = ProceduralStream.ofDouble(this::resolveHydrologyDistance)
                .cache2DDouble("riverDistanceStream", engine, cacheSize);
        riverFlowStream = ProceduralStream.ofDouble(this::resolveHydrologyFlow)
                .cache2DDouble("riverFlowStream", engine, cacheSize);
        riverCarveWeightStream = ProceduralStream.ofDouble(this::resolveHydrologyCarveWeight)
                .cache2DDouble("riverCarveWeightStream", engine, cacheSize);
        riverWaterSurfaceStream = ProceduralStream.ofDouble(this::resolveHydrologyFluidSurface)
                .cache2DDouble("riverWaterSurfaceStream", engine, cacheSize);
        heightFluidStream = ProceduralStream.ofDouble((x, z) -> Math.max(
                        heightStream.get(x, z),
                        riverWaterSurfaceStream.get(x, z)
                ))
                .cache2DDouble("heightFluidStream", engine, cacheSize);
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
        terrainSurfaceDecoration = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainSurfaceDecoration", engine, cacheSize);
        terrainCeilingDecoration = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCeilingDecoration", engine, cacheSize);
        terrainCaveSurfaceDecoration = caveBiomeStream.contextInjecting(engine, (c, x, z) -> c.getCave().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainCaveSurfaceDecoration", engine, cacheSize);
        terrainCaveCeilingDecoration = caveBiomeStream.contextInjecting(engine, (c, x, z) -> c.getCave().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCaveCeilingDecoration", engine, cacheSize);
        shoreSurfaceDecoration = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SHORE_LINE)).cache2D("shoreSurfaceDecoration", engine, cacheSize);
        seaSurfaceDecoration = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_SURFACE)).cache2D("seaSurfaceDecoration", engine, cacheSize);
        seaFloorDecoration = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_FLOOR)).cache2D("seaFloorDecoration", engine, cacheSize);
        baseBiomeIDStream = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, x, z) -> {
                    UUID d = regionIDStream.get(x, z);
                    return new UUID(b.getLoadKey().hashCode() * 818223L,
                            d.hashCode());
                })
                .cache2D("", engine, cacheSize);
        //@done
    }

    static int noiseCacheSize(Engine engine, int configuredSize) {
        return engine.isStudio() ? Math.max(configuredSize, STUDIO_NOISE_CACHE_SIZE) : configuredSize;
    }

    void enableStudioHydrologyCache(String runtimeIdentity, Path persistentRoot) {
        if (hydrologyRuntime != null) {
            hydrologyRuntime.enableSharedCache(runtimeIdentity, persistentRoot);
        }
    }

    static InferredType resolveNaturalInferredType(
            ProceduralStream<InferredType> bridgeStream,
            IrisBiome focusBiome,
            double x,
            double z
    ) {
        if (focusBiome != null) {
            return focusBiome.getInferredType();
        }
        return bridgeStream.get(x, z);
    }

    private IrisHydrologyNaturalSample sampleHydrologyNatural(
            Engine engine,
            int x,
            int z,
            double naturalHeight
    ) {
        InferredType inferredType = resolveNaturalInferredType(bridgeStream, focusBiome, x, z);
        IrisRegion region = regionStream.get(x, z);
        IrisBiome biome = focusBiome == null
                ? sampleNaturalBiome(inferredType, region, naturalHeight, x, z)
                : focusBiome;
        return new IrisHydrologyNaturalSample(
                naturalHeight,
                inferredType == InferredType.SEA,
                biome,
                region
        );
    }

    private boolean sampleNaturalOcean(int x, int z) {
        return resolveNaturalInferredType(bridgeStream, focusBiome, x, z) == InferredType.SEA;
    }

    /**
     * Every input to the natural height at one column, recomputed outside the caches: the region
     * and base biome, the fluid height and overlay, each interpolator's bounds and generator
     * heights, then the cached and the fresh natural height. Diagnostic only.
     */
    String describeNaturalHeight(Engine engine, int x, int z) {
        long seed = engine.getSeedManager().getHeight();
        IrisRegion region = regionStream.get(x, z);
        IrisBiome biome = baseBiomeStream.get(x, z);
        StringBuilder out = new StringBuilder(256);
        out.append("region=").append(region == null ? "null" : region.getLoadKey());
        out.append(" biome=").append(biome == null ? "null" : biome.getLoadKey());
        out.append(" fluidHeight=").append(fluidHeight);
        out.append(" overlay=").append(overlayStream.getDouble(x, z));
        for (int interpolatorIndex = 0; interpolatorIndex < frozenInterpolators.length; interpolatorIndex++) {
            IrisGenerator[] generators = frozenGenerators[interpolatorIndex];
            NoiseBounds bounds = gridSampleBounds(engine, frozenInterpolators[interpolatorIndex], interpolatorIndex, generators, x, z);
            out.append(" interpolator[").append(interpolatorIndex).append("] bounds=")
                    .append(bounds.min()).append("..").append(bounds.max());
            for (IrisGenerator generator : generators) {
                out.append(' ').append(generator.getLoadKey()).append('=')
                        .append(generator.getHeight(x, z, seed + 239945));
            }
        }
        out.append(" cachedNatural=").append(naturalHeightStream.getDouble(x, z));
        out.append(" freshNatural=").append(sampleNaturalTerrainHeight(engine, x, z));
        return out.toString();
    }

    private double sampleNaturalTerrainHeight(Engine engine, double x, double z) {
        return sampleUnblendedNaturalTerrainHeight(engine, x, z);
    }

    private double sampleUnblendedNaturalTerrainHeight(Engine engine, double x, double z) {
        double proceduralHeight = getHeight(engine, x, z, engine.getSeedManager().getHeight());
        return imageMapRuntime.sampleTerrainHeight(x, z, proceduralHeight);
    }

    static double calculateNaturalSlope(double naturalHeight, double easternHeight, double southernHeight) {
        double deltaX = easternHeight - naturalHeight;
        double deltaZ = southernHeight - naturalHeight;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private IrisBiome sampleNaturalBiome(
            InferredType inferredType,
            IrisRegion region,
            double naturalHeight,
            int x,
            int z
    ) {
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        if (mapped != null) {
            return mapped;
        }
        IrisContext context = IrisContext.get();
        IrisBiome baseBiome = reuseNaturalBaseBiome && (context == null || context.getChunkContext() == null)
                ? baseBiomeStream.get(x, z)
                : implode(sampleInferredBiome(region, inferredType, x, z), (double) x, (double) z);
        IrisBiome resolved = resolveNaturalSurfaceBiome(naturalHeight, baseBiome, region, x, z);
        return resolved == baseBiome ? baseBiome : implode(resolved, (double) x, (double) z);
    }

    private IrisBiome resolveNaturalSurfaceBiome(
            double height,
            IrisBiome biome,
            IrisRegion region,
            double x,
            double z
    ) {
        if (biome == null || region == null) {
            return biome;
        }
        double shoreHeight = region.getShoreHeight(x, z);
        if (height >= fluidHeight - 1 && height <= fluidHeight + shoreHeight && !biome.isShore()) {
            return sampleInferredBiome(region, InferredType.SHORE, x, z);
        }
        if (height > fluidHeight + shoreHeight && !biome.isLand()) {
            return sampleInferredBiome(region, InferredType.LAND, x, z);
        }
        if (height < fluidHeight && !biome.isAquatic()) {
            return sampleInferredBiome(region, InferredType.SEA, x, z);
        }
        if (height == fluidHeight && !biome.isShore()) {
            return sampleInferredBiome(region, InferredType.SHORE, x, z);
        }
        return biome;
    }

    private IrisBiome sampleInferredBiome(
            IrisRegion region,
            InferredType inferredType,
            double x,
            double z
    ) {
        return createInferredBiomeStream(region, inferredType).get(x, z);
    }

    private ProceduralStream<IrisBiome> createInferredBiomeStream(
            IrisRegion region,
            InferredType inferredType
    ) {
        return preparedInferredBiomeStream(inferredBiomeStreams, region, inferredType);
    }

    private ProceduralStream<IrisBiome> compileInferredBiomeStream(
            Engine engine,
            IrisRegion region,
            InferredType inferredType,
            IrisBiome emptyBiome
    ) {
        return switch (inferredType) {
            case CAVE -> engine.getDimension().getCaveBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.CAVE.ordinal()), getData()).stream()
                    .zoom(engine.getDimension().getBiomeZoom())
                    .zoom(region.getCaveBiomeZoom())
                    .selectRarity(loadInferredBiomes(region.getCaveBiomes(), InferredType.CAVE))
                    .onNull(emptyBiome);
            case LAND -> engine.getDimension().getLandBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.LAND.ordinal()), getData()).stream()
                    .zoom(engine.getDimension().getBiomeZoom())
                    .zoom(engine.getDimension().getLandZoom())
                    .zoom(region.getLandBiomeZoom())
                    .selectRarity(loadInferredBiomes(region.getLandBiomes(), InferredType.LAND));
            case SEA -> engine.getDimension().getSeaBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SEA.ordinal()), getData()).stream()
                    .zoom(engine.getDimension().getBiomeZoom())
                    .zoom(engine.getDimension().getSeaZoom())
                    .zoom(region.getSeaBiomeZoom())
                    .selectRarity(loadInferredBiomes(region.getSeaBiomes(), InferredType.SEA));
            case SHORE -> engine.getDimension().getShoreBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), getData()).stream()
                    .zoom(engine.getDimension().getBiomeZoom())
                    .zoom(region.getShoreBiomeZoom())
                    .selectRarity(loadInferredBiomes(region.getShoreBiomes(), InferredType.SHORE));
        };
    }

    private static boolean hydrologyActive(IrisHydrology hydrology) {
        if (hydrology == null) {
            return false;
        }
        IrisRiverHydrology rivers = hydrology.getRivers();
        if (rivers != null && rivers.isEnabled()) {
            return true;
        }
        for (IrisDeepFluidConfig deepFluid : hydrology.getDeepFluids()) {
            if (deepFluid != null
                    && deepFluid.getDensity() > 0D
                    && (deepFluid.isContainedPools() || deepFluid.isShortChannels())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, ProceduralStream<PlatformBlockState>> createHydrologyFluidStreams(IrisHydrology hydrology) {
        if (hydrology == null) {
            return Map.of();
        }
        LinkedHashMap<String, ProceduralStream<PlatformBlockState>> streams = new LinkedHashMap<>();
        IrisRiverHydrology rivers = Objects.requireNonNull(hydrology.getRivers(), "hydrology.rivers");
        int streamIndex = 0;
        for (IrisRiverProfile profile : rivers.getProfiles()) {
            if (profile == null) {
                throw new IllegalArgumentException("hydrology.rivers.profiles cannot contain null entries");
            }
            String profileKey = requireHydrologyKey(profile.getId(), "hydrology.rivers.profiles[].id");
            ProceduralStream<PlatformBlockState> stream = configuredFluidStream(
                    profile.getFluidPalette(),
                    rng.nextParallelRNG(7900 + streamIndex++),
                    "hydrology river profile " + profileKey
            );
            if (streams.putIfAbsent(profileKey, stream) != null) {
                throw new IllegalArgumentException("Duplicate hydrology fluid profile: " + profileKey);
            }
        }
        for (IrisDeepFluidConfig deepFluid : hydrology.getDeepFluids()) {
            if (deepFluid == null) {
                throw new IllegalArgumentException("hydrology.deepFluids cannot contain null entries");
            }
            String profileKey = requireHydrologyKey(deepFluid.getId(), "hydrology.deepFluids[].id");
            ProceduralStream<PlatformBlockState> stream = configuredFluidStream(
                    deepFluid.getFluidPalette(),
                    rng.nextParallelRNG(8900 + streamIndex++),
                    "hydrology deep-fluid profile " + profileKey
            );
            if (streams.putIfAbsent(profileKey, stream) != null) {
                throw new IllegalArgumentException("Duplicate hydrology fluid profile: " + profileKey);
            }
        }
        for (IrisSurfacePoolConfig pool : hydrology.getSurfacePools()) {
            if (pool == null) {
                throw new IllegalArgumentException("hydrology.surfacePools cannot contain null entries");
            }
            String profileKey = requireHydrologyKey(pool.getId(), "hydrology.surfacePools[].id");
            ProceduralStream<PlatformBlockState> stream = configuredFluidStream(
                    pool.getFluidPalette(),
                    rng.nextParallelRNG(9900 + streamIndex++),
                    "hydrology surface pool " + profileKey
            );
            if (streams.putIfAbsent(profileKey, stream) != null) {
                throw new IllegalArgumentException("Duplicate hydrology fluid profile: " + profileKey);
            }
        }
        return Collections.unmodifiableMap(streams);
    }

    private static String requireHydrologyKey(String key, String path) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
        return key.trim();
    }

    private ProceduralStream<PlatformBlockState> configuredFluidStream(
            IrisMaterialPalette palette,
            RNG fluidRng,
            String configurationName
    ) {
        Objects.requireNonNull(palette, configurationName + " fluidPalette must be configured");
        KList<PlatformBlockState> blocks = palette.getBlockData(data);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    configurationName + " fluidPalette must resolve at least one fluid block");
        }
        for (PlatformBlockState block : blocks) {
            if (block == null || !block.isFluid()) {
                throw new IllegalArgumentException(
                        configurationName + " fluidPalette may contain only fluid blocks");
            }
        }
        return palette.getLayerGenerator(fluidRng, data).stream().select(blocks);
    }

    public PlatformBlockState resolveHydrologyFluid(String profileKey, double x, double z) {
        String key = Objects.requireNonNull(profileKey, "profileKey").trim();
        ProceduralStream<PlatformBlockState> stream = hydrologyFluidStreams.get(key);
        if (stream == null) {
            throw new IllegalArgumentException("Unknown hydrology fluid profile: " + key);
        }
        return stream.get(x, z);
    }

    public PlatformBlockState resolveSurfaceFluid(double x, double z) {
        HydrologyColumnLayer layer = surfaceFluidLayer(x, z);
        if (layer != null) {
            return resolveHydrologyFluid(layer.profileKey(), x, z);
        }
        return fluidStream.get(x, z);
    }

    private double resolveHydrologyTerrainHeight(double x, double z) {
        HydrologyColumnSample sample = hydrologySample(x, z);
        return sample == null ? naturalHeightStream.get(x, z) : sample.terrainHeight();
    }

    private double resolveHydrologyDistance(double x, double z) {
        HydrologyColumnLayer layer = surfaceLayer(x, z);
        if (layer == null) {
            return Double.MAX_VALUE;
        }
        if (layer.channel()) {
            return 0D;
        }
        return layer.shore() ? 1D : 2D;
    }

    private double resolveHydrologyFlow(double x, double z) {
        HydrologyColumnLayer layer = surfaceFluidLayer(x, z);
        if (layer == null) {
            return 0D;
        }
        return layer.feature().flowDeltaX() == 0 && layer.feature().flowDeltaZ() == 0
                ? 0D
                : transitionHydrologyWeight(x, z);
    }

    private double resolveHydrologyCarveWeight(double x, double z) {
        HydrologyColumnLayer layer = surfaceLayer(x, z);
        if (layer == null) {
            return 0D;
        }
        double hydrologyWeight = transitionHydrologyWeight(x, z);
        if (layer.channel()) {
            return hydrologyWeight;
        }
        return hydrologyWeight * (layer.shore() ? 0.75D : 0.5D);
    }

    private double resolveHydrologyFluidSurface(double x, double z) {
        HydrologyColumnLayer layer = surfaceFluidLayer(x, z);
        return layer == null ? fluidHeight : layer.fluidHeadY();
    }

    public HydrologyColumnSample sampleHydrologyColumn(double x, double z) {
        if (hydrologyRuntime == null) {
            return null;
        }
        if (transitionGenerationPlan == null) {
            return hydrologyRuntime.sample(x, z).orElse(null);
        }
        double hydrologyWeight = transitionHydrologyWeight(x, z);
        if (hydrologyWeight == 0D) {
            return null;
        }
        HydrologyColumnSample sample = hydrologyRuntime.sample(x, z).orElse(null);
        if (sample == null || hydrologyWeight == 1D) {
            return sample;
        }
        return taperHydrologySample(sample, hydrologyWeight);
    }

    static HydrologyColumnSample taperHydrologySample(
            HydrologyColumnSample sample,
            double hydrologyWeight
    ) {
        Objects.requireNonNull(sample, "hydrology sample");
        if (hydrologyWeight == 1D) {
            return sample;
        }
        ArrayList<HydrologyColumnLayer> taperedLayers = new ArrayList<>(sample.layers().size());
        for (HydrologyColumnLayer layer : sample.layers()) {
            taperedLayers.add(taperHydrologyLayer(layer, sample.naturalHeight(), hydrologyWeight));
        }
        return new HydrologyColumnSample(
                sample.x(),
                sample.z(),
                sample.naturalHeight(),
                sample.seaLevel(),
                sample.ocean(),
                sample.parentBiomeKey(),
                taperedLayers);
    }

    private static HydrologyColumnLayer taperHydrologyLayer(
            HydrologyColumnLayer layer,
            int naturalHeight,
            double hydrologyWeight
    ) {
        if (layer.feature().type().isUnderground() || layer.feature().type().isDeepFluid()) {
            return layer;
        }
        return new HydrologyColumnLayer(
                layer.feature(),
                GenerationBlend.interpolateHeight(naturalHeight, layer.bedY(), hydrologyWeight),
                GenerationBlend.interpolateHeight(naturalHeight, layer.fluidHeadY(), hydrologyWeight),
                GenerationBlend.interpolateHeight(naturalHeight, layer.ceilingY(), hydrologyWeight),
                layer.channel(),
                layer.shore(),
                layer.grading(),
                layer.connectedFluid(),
                layer.fallingFluid(),
                layer.receivingPool(),
                layer.terrainOwned(),
                layer.fluidOwned(),
                layer.oceanApron(),
                layer.profileKey(),
                layer.surfaceBiomeKey(),
                layer.mouthBiomeKey(),
                layer.shoreBiomeKey(),
                layer.bankBiomeKey(),
                layer.floodedCaveBiomeKey());
    }

    private HydrologyColumnSample hydrologySample(double x, double z) {
        return sampleHydrologyColumn(x, z);
    }

    private double transitionHydrologyWeight(double x, double z) {
        return transitionGenerationPlan == null
                ? 1D
                : transitionGenerationPlan.hydrologyWeightAt(blockCoordinate(x), blockCoordinate(z));
    }

    /** Whether the column's hydrology can be sampled without waiting for a plan (see Engine.answersFromNaturalTerrain). */
    public boolean isHydrologyPlanned(int x, int z) {
        return hydrologyRuntime == null
                || transitionHydrologyWeight(x, z) == 0D
                || hydrologyRuntime.isPlanned(x, z);
    }

    public ProceduralStream<Double> getRawHeightStream() {
        return heightStream;
    }

    public ProceduralStream<Double> getHeightStream() {
        return placementHeightStream;
    }

    public Optional<TerrainBoundarySignature> resolvedTerrainColumn(int blockX, int blockZ) {
        if (transitionGenerationPlan == null || isNaturalTerrainContext()
                || !transitionGenerationPlan.hasTransitionAtChunk(blockX >> 4, blockZ >> 4)) {
            return Optional.empty();
        }
        return Optional.of(resolvedTerrain.column(blockX, blockZ));
    }

    public OptionalInt resolvedTerrainHeight(int blockX, int blockZ, boolean ignoreFluid) {
        if (transitionGenerationPlan == null || isNaturalTerrainContext()
                || !transitionGenerationPlan.hasTransitionAtChunk(blockX >> 4, blockZ >> 4)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(resolvedTerrain.height(blockX, blockZ, ignoreFluid));
    }

    boolean isNaturalTerrainContext() {
        IrisContext context = IrisContext.get();
        return context != null && context.getChunkContext() != null
                && context.getChunkContext().getComplex() == this
                && context.getChunkContext().isNaturalTerrain();
    }

    private double samplePlacementHeight(double x, double z) {
        OptionalInt resolved = resolvedTerrainHeight(blockCoordinate(x), blockCoordinate(z), true);
        return resolved.isPresent() ? resolved.getAsInt() : heightStream.getDouble(x, z);
    }

    public boolean isHistoricalChunk(int chunkX, int chunkZ) {
        return transitionGenerationPlan != null
                && transitionGenerationPlan.boundary().isHistoricalChunk(chunkX, chunkZ);
    }

    public boolean allowsMantleWrite(int blockX, int blockZ) {
        return transitionGenerationPlan == null
                || !transitionGenerationPlan.isHistoricalBlock(blockX, blockZ);
    }

    public boolean allowsMantleChunkWrite(int chunkX, int chunkZ) {
        return transitionGenerationPlan == null
                || !transitionGenerationPlan.boundary().isHistoricalChunk(chunkX, chunkZ);
    }

    public boolean allowsNewDiscreteContentAt(int blockX, int blockZ) {
        return transitionGenerationPlan == null
                || transitionGenerationPlan.allowsNewDiscreteContentAt(blockX, blockZ);
    }

    public boolean allowsNewGenerationChunk(int chunkX, int chunkZ) {
        if (transitionGenerationPlan == null) {
            return true;
        }
        int minimumX = Math.multiplyExact(chunkX, 16);
        int minimumZ = Math.multiplyExact(chunkZ, 16);
        return transitionGenerationPlan.allowsNewFootprint(
                minimumX,
                minimumZ,
                Math.addExact(minimumX, 15),
                Math.addExact(minimumZ, 15));
    }

    public boolean allowsNewGenerationFootprint(
            int minimumX,
            int minimumZ,
            int maximumX,
            int maximumZ
    ) {
        return transitionGenerationPlan == null
                || transitionGenerationPlan.allowsNewFootprint(minimumX, minimumZ, maximumX, maximumZ);
    }

    public Optional<String> historicalPhysicalBiomeKeyAt(int blockX, int blockY, int blockZ) {
        return transitionGenerationPlan == null
                ? Optional.empty()
                : transitionGenerationPlan.historicalPhysicalBiomeKeyAt(blockX, blockY, blockZ);
    }

    private static int blockCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate)) {
            throw new IllegalArgumentException("Generation coordinate must be finite");
        }
        return (int) Math.floor(coordinate);
    }

    /** The natural terrain height of a column, from the natural height stream only: never touches hydrology or its caches. */
    public int naturalTrueHeight(int x, int z) {
        return (int) Math.round(naturalHeightStream.getDouble(x, z));
    }

    /** The terrain slope of a column from the natural height stream only: never touches hydrology or its caches. */
    public double naturalSlope(int x, int z) {
        return naturalSlopeStream.getDouble(x, z);
    }

    /** The surface biome a column would have without any river content, from natural streams only. */
    public IrisBiome naturalSurfaceBiome(int x, int z) {
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        if (mapped != null) {
            return mapped;
        }
        double terrainHeight = naturalHeightStream.getDouble(x, z);
        return fixBiomeType(terrainHeight, baseBiomeStream.get(x, z), regionStream.get(x, z), (double) x, (double) z, fluidHeight);
    }

    private HydrologyColumnLayer surfaceLayer(double x, double z) {
        HydrologyColumnSample sample = hydrologySample(x, z);
        return sample == null ? null : sample.primarySurfaceLayer().orElse(null);
    }

    private HydrologyColumnLayer surfaceFluidLayer(double x, double z) {
        HydrologyColumnSample sample = hydrologySample(x, z);
        return sample == null ? null : sample.primarySurfaceFluidLayer().orElse(null);
    }

    public boolean hasHydrologySurfaceFluid(int x, int z) {
        return surfaceFluidLayer(x, z) != null;
    }

    public boolean hasHydrologyChannelOrShore(int x, int z) {
        HydrologyColumnLayer layer = surfaceLayer(x, z);
        return layer != null && (layer.channel() || layer.shore());
    }

    public ProceduralStream<IrisBiome> getBiomeStream(InferredType type) {
        switch (type) {
            case CAVE:
                return caveBiomeStream;
            case LAND:
                return landBiomeStream;
            case SEA:
                return seaBiomeStream;
            case SHORE:
                return shoreBiomeStream;
            default:
                break;
        }

        return null;
    }

    public double sampleProceduralTerrainHeight(Engine engine, double worldX, double worldZ) {
        if (engine == null) {
            throw new IllegalArgumentException("Engine is required to sample procedural terrain height");
        }
        return getHeight(engine, worldX, worldZ, engine.getSeedManager().getHeight());
    }

    private IrisRegion findRegion(IrisBiome focus, Engine engine) {
        for (IrisRegion i : engine.getDimension().getAllRegions(engine)) {
            if (i.getAllBiomeIds().contains(focus.getLoadKey())) {
                return i;
            }
        }

        String key = UUID.randomUUID().toString();
        IrisRegion region = new IrisRegion();
        region.getLandBiomes().add(focus.getLoadKey());
        region.getSeaBiomes().add(focus.getLoadKey());
        region.getShoreBiomes().add(focus.getLoadKey());
        region.setLoadKey(key);
        region.setLoader(data);
        region.setLoadFile(new File(data.getDataFolder(), data.getRegionLoader().getFolderName() + "/" + key + ".json"));
        return region;
    }

    private IrisDecorator decorateFor(IrisBiome b, double x, double z, IrisDecorationPart part) {
        RNG rngc = new RNG(Cache.key(((int) x), ((int) z)));

        for (IrisDecorator i : b.getDecorators()) {
            if (!i.getPartOf().equals(part)) {
                continue;
            }

            PlatformBlockState block = i.getBlockData(b, rngc, x, z, data);

            if (block != null) {
                return i;
            }
        }

        return null;
    }

    private IrisBiome resolveHydrologySurfaceBiome(double terrainHeight, double x, double z) {
        HydrologyColumnSample sample = hydrologySample(x, z);
        HydrologyColumnLayer layer = sample == null ? null : sample.primarySurfaceLayer().orElse(null);
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        if (mapped != null && layer == null) {
            return mapped;
        }
        if (layer != null) {
            String biomeKey = hydrologySurfaceBiomeKey(sample);
            IrisBiome biome = data.getBiomeLoader().load(biomeKey);
            if (biome == null) {
                throw new IllegalStateException("Hydrology biome does not exist: " + biomeKey);
            }
            if (layer.biomeKey() == null) {
                return implode(biome.withInferredType(InferredType.LAND), x, z);
            }
            InferredType inferredType = layer.shore()
                    ? InferredType.SHORE
                    : layer.connectedFluid() ? InferredType.SEA : InferredType.LAND;
            return implode(biome.withInferredType(inferredType), x, z);
        }
        return fixBiomeType(
                terrainHeight,
                baseBiomeStream.get(x, z),
                regionStream.get(x, z),
                x,
                z,
                fluidHeight
        );
    }

    static String hydrologySurfaceBiomeKey(HydrologyColumnSample sample) {
        Objects.requireNonNull(sample, "sample");
        HydrologyColumnLayer layer = sample.primarySurfaceLayer().orElse(null);
        if (layer == null) {
            return sample.parentBiomeKey();
        }
        String biomeKey = layer.biomeKey();
        return biomeKey == null ? sample.parentBiomeKey() : biomeKey;
    }

    private IrisBiome fixBiomeType(Double height, IrisBiome biome, IrisRegion region, Double x, Double z, double fluidHeight) {
        IrisBiome resolved = resolveSurfaceBiome(
                height,
                biome,
                region,
                x,
                z,
                fluidHeight,
                landBiomeStream,
                seaBiomeStream,
                shoreBiomeStream);
        return resolved == biome ? biome : implode(resolved, x, z);
    }

    static IrisBiome resolveSurfaceBiome(
            double height,
            IrisBiome biome,
            IrisRegion region,
            double x,
            double z,
            double fluidHeight,
            ProceduralStream<IrisBiome> landBiomes,
            ProceduralStream<IrisBiome> seaBiomes,
            ProceduralStream<IrisBiome> shoreBiomes
    ) {
        if (biome == null || region == null) {
            return biome;
        }
        double sh = region.getShoreHeight(x, z);

        if (height >= fluidHeight - 1 && height <= fluidHeight + sh && !biome.isShore()) {
            return shoreBiomes.get(x, z);
        }

        if (height > fluidHeight + sh && !biome.isLand()) {
            return landBiomes.get(x, z);
        }

        if (height < fluidHeight && !biome.isAquatic()) {
            return seaBiomes.get(x, z);
        }

        if (height == fluidHeight && !biome.isShore()) {
            return shoreBiomes.get(x, z);
        }

        return biome;
    }

    private double interpolateGenerators(Engine engine, IrisInterpolator interpolator, int interpolatorIndex, IrisGenerator[] generators, double x, double z, long seed) {
        if (generators.length == 0) {
            return 0;
        }

        NoiseBounds sampledBounds = gridSampleBounds(engine, interpolator, interpolatorIndex, generators, x, z);
        double hi = sampledBounds.max();
        double lo = sampledBounds.min();

        return averageGeneratorHeights(generators, lo, hi, x, z, seed + 239945);
    }

    private NoiseBounds gridSampleBounds(Engine engine, IrisInterpolator interpolator, int interpolatorIndex, IrisGenerator[] generators, double x, double z) {
        int grid = HEIGHT_BOUNDS_GRID;
        GridBoundsCache cache = gridBoundsCache.get();
        if (grid <= 1) {
            return sampleBoundsRaw(cache, engine, interpolator, generators, x, z);
        }

        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        int mask = grid - 1;
        int gx = xi & ~mask;
        int gz = zi & ~mask;
        double fx = (x - gx) / grid;
        double fz = (z - gz) / grid;

        long b00 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx, gz);
        if (fx == 0D && fz == 0D) {
            return new NoiseBounds(boundsLow(b00), boundsHigh(b00));
        }

        if (fz == 0D) {
            long b10 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx + grid, gz);
            return new NoiseBounds(
                    biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b00), boundsLow(b10), fx, fz),
                    biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b00), boundsHigh(b10), fx, fz)
            );
        }

        if (fx == 0D) {
            long b01 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx, gz + grid);
            return new NoiseBounds(
                    biLerp(boundsLow(b00), boundsLow(b00), boundsLow(b01), boundsLow(b01), fx, fz),
                    biLerp(boundsHigh(b00), boundsHigh(b00), boundsHigh(b01), boundsHigh(b01), fx, fz)
            );
        }

        long b10 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx + grid, gz);
        long b01 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx, gz + grid);
        long b11 = cornerBounds(cache, engine, interpolator, interpolatorIndex, generators, gx + grid, gz + grid);

        double lo = biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b01), boundsLow(b11), fx, fz);
        double hi = biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b01), boundsHigh(b11), fx, fz);
        return new NoiseBounds(lo, hi);
    }

    private long cornerBounds(GridBoundsCache cache, Engine engine, IrisInterpolator interpolator, int interpolatorIndex, IrisGenerator[] generators, int gx, int gz) {
        int slot = cache.slot(gx, gz, interpolatorIndex);
        if (cache.valid[slot] && cache.gx[slot] == gx && cache.gz[slot] == gz && cache.idx[slot] == interpolatorIndex) {
            return cache.packed[slot];
        }

        // The per-thread table above catches the walk through one chunk; the shared table
        // catches the same corner reached by another thread or by hydrology planning earlier.
        SharedCornerBounds shared = sharedCornerBounds();
        long sharedKey = SharedCornerBounds.key(gx, gz, interpolatorIndex);
        long packed = shared.get(sharedKey);
        if (packed == Long.MIN_VALUE) {
            packed = computePackedBounds(cache, engine, interpolator, generators, gx, gz);
            if (!Double.isFinite(boundsLow(packed)) || !Double.isFinite(boundsHigh(packed))) {
                // A non-finite corner is a transient sampling fault, not a fact about the terrain:
                // hand it back uncached so the next sample recomputes it instead of pinning it.
                return packed;
            }
            shared.put(sharedKey, packed);
        }
        cache.gx[slot] = gx;
        cache.gz[slot] = gz;
        cache.idx[slot] = interpolatorIndex;
        cache.packed[slot] = packed;
        cache.valid[slot] = true;
        return packed;
    }

    /** The field initializer covers every real complex; an instance built without it gets one here. */
    private SharedCornerBounds sharedCornerBounds() {
        SharedCornerBounds shared = sharedCornerBounds;
        if (shared == null) {
            shared = new SharedCornerBounds(SHARED_CORNER_BOUNDS_CAPACITY);
            sharedCornerBounds = shared;
        }
        return shared;
    }

    private long computePackedBounds(GridBoundsCache cache, Engine engine, IrisInterpolator interpolator, IrisGenerator[] generators, int gx, int gz) {
        NoiseBounds bounds = sampleBoundsRaw(cache, engine, interpolator, generators, gx, gz);
        return (((long) Float.floatToRawIntBits((float) bounds.min())) << 32) | (Float.floatToRawIntBits((float) bounds.max()) & 0xFFFFFFFFL);
    }

    private static double boundsLow(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    private static double boundsHigh(long packed) {
        return Float.intBitsToFloat((int) (packed & 0xFFFFFFFFL));
    }

    private static double biLerp(double v00, double v10, double v01, double v11, double fx, double fz) {
        double a = v00 + ((v10 - v00) * fx);
        double b = v01 + ((v11 - v01) * fx);
        return a + ((b - a) * fz);
    }

    private NoiseBounds sampleBoundsRaw(GridBoundsCache cache, Engine engine, IrisInterpolator interpolator, IrisGenerator[] generators, double x, double z) {
        IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds = generatorBounds.get(interpolator);
        BoundsSampler sampler = cache.sampler.isInUse() ? new BoundsSampler() : cache.sampler;
        sampler.bind(this, engine, generators, cachedBounds);

        try {
            return interpolator.interpolateBounds(x, z, sampler);
        } finally {
            sampler.release();
        }
    }

    private double getInterpolatedHeight(Engine engine, double x, double z, long seed) {
        double h = 0;

        for (int interpolatorIndex = 0; interpolatorIndex < frozenInterpolators.length; interpolatorIndex++) {
            h += interpolateGenerators(engine, frozenInterpolators[interpolatorIndex], interpolatorIndex, frozenGenerators[interpolatorIndex], x, z, seed);
        }

        return h;
    }

    private NoiseBounds naturalHeightBounds(
            Engine engine,
            KList<IrisShapedGeneratorStyle> overlayNoise,
            double x,
            double z
    ) {
        double minimum = fluidHeight;
        double maximum = fluidHeight;
        for (int interpolatorIndex = 0; interpolatorIndex < frozenInterpolators.length; interpolatorIndex++) {
            NoiseBounds bounds = gridSampleBounds(
                    engine,
                    frozenInterpolators[interpolatorIndex],
                    interpolatorIndex,
                    frozenGenerators[interpolatorIndex],
                    x,
                    z
            );
            minimum += Math.min(bounds.min(), bounds.max());
            maximum += Math.max(bounds.min(), bounds.max());
        }
        for (IrisShapedGeneratorStyle style : overlayNoise) {
            minimum += Math.min(style.getMin(), style.getMax());
            maximum += Math.max(style.getMin(), style.getMax());
        }
        minimum = imageMapRuntime.sampleTerrainHeight(x, z, minimum);
        maximum = imageMapRuntime.sampleTerrainHeight(x, z, maximum);
        return new NoiseBounds(
                Math.max(0D, Math.min(engine.getHeight(), Math.min(minimum, maximum))),
                Math.max(0D, Math.min(engine.getHeight(), Math.max(minimum, maximum)))
        );
    }

    private double getHeight(Engine engine, double x, double z, long seed) {
        return Math.max(Math.min(getInterpolatedHeight(engine, x, z, seed) + fluidHeight + overlayStream.get(x, z), engine.getHeight()), 0);
    }

    private void prepareInferredBiomes(IrisRegion region, KList<IrisRegion> preparedRegions) {
        loadInferredBiomes(region.getLandBiomes(), InferredType.LAND);
        loadInferredBiomes(region.getCaveBiomes(), InferredType.CAVE);
        loadInferredBiomes(region.getSeaBiomes(), InferredType.SEA);
        loadInferredBiomes(region.getShoreBiomes(), InferredType.SHORE);
        preparedRegions.add(region);
    }

    static Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> compileInferredBiomeStreams(
            Iterable<IrisRegion> regions,
            BiFunction<IrisRegion, InferredType, ProceduralStream<IrisBiome>> compiler
    ) {
        IdentityHashMap<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> compiled = new IdentityHashMap<>();
        for (IrisRegion region : regions) {
            if (compiled.containsKey(region)) {
                continue;
            }
            EnumMap<InferredType, ProceduralStream<IrisBiome>> regionStreams = new EnumMap<>(InferredType.class);
            for (InferredType inferredType : INFERRED_BIOME_PREPARATION_ORDER) {
                regionStreams.put(inferredType, Objects.requireNonNull(compiler.apply(region, inferredType)));
            }
            compiled.put(region, Collections.unmodifiableMap(regionStreams));
        }
        return Collections.unmodifiableMap(compiled);
    }

    static ProceduralStream<IrisBiome> preparedInferredBiomeStream(
            Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> streams,
            IrisRegion region,
            InferredType inferredType
    ) {
        Map<InferredType, ProceduralStream<IrisBiome>> regionStreams = streams.get(region);
        if (regionStreams == null) {
            String regionKey = region == null || region.getLoadKey() == null || region.getLoadKey().isBlank()
                    ? "<unkeyed>"
                    : region.getLoadKey();
            throw new IllegalStateException("Inferred-biome streams were not prepared for region '"
                    + regionKey + "'.");
        }
        ProceduralStream<IrisBiome> stream = regionStreams.get(inferredType);
        if (stream == null) {
            throw new IllegalStateException("Inferred-biome stream was not prepared for type " + inferredType + ".");
        }
        return stream;
    }

    private KList<IrisBiome> loadInferredBiomes(KList<String> keys, InferredType type) {
        KList<IrisBiome> inferred = new KList<>();
        for (IrisBiome biome : data.getBiomeLoader().loadAll(keys)) {
            // Excluded biomes cannot generate on this Minecraft version, so they leave the selection pool.
            if (biome.isCompatExcluded()) {
                continue;
            }
            inferred.add(biome.withInferredType(type));
        }
        return inferred;
    }

    /** The dimension's region pool with regions the version-content gate excluded removed. */
    private KList<IrisRegion> compatRegionPool(Engine engine) {
        KList<IrisRegion> pool = new KList<>();
        for (IrisRegion region : data.getRegionLoader().loadAll(engine.getDimension().getRegions())) {
            if (!region.isCompatExcluded()) {
                pool.add(region);
            }
        }
        return pool;
    }

    private static <T extends IrisRegistrant> T compatUsable(T registrant) {
        return registrant == null || registrant.isCompatExcluded() ? null : registrant;
    }

    private void registerGenerators(IrisBiome biome) {
        generatorBiomes.add(biome);
        biome.getGenerators().forEach(c -> registerGenerator(c.getCachedGenerator(this)));
    }

    private void registerGenerator(IrisGenerator cachedGenerator) {
        generators.computeIfAbsent(cachedGenerator.getInterpolator(), (k) -> new HashSet<>()).add(cachedGenerator);
    }

    static GeneratorGroup[] freezeGeneratorGroups(Map<IrisInterpolator, Set<IrisGenerator>> generators) {
        GeneratorGroup[] groups = new GeneratorGroup[generators.size()];
        int groupIndex = 0;
        for (Map.Entry<IrisInterpolator, Set<IrisGenerator>> entry : generators.entrySet()) {
            IrisGenerator[] groupGenerators = entry.getValue().toArray(new IrisGenerator[0]);
            Arrays.sort(groupGenerators, GENERATOR_ORDER);
            groups[groupIndex] = new GeneratorGroup(entry.getKey(), groupGenerators);
            groupIndex++;
        }
        Arrays.sort(groups, Comparator.comparing(GeneratorGroup::interpolator, INTERPOLATOR_ORDER));
        return groups;
    }

    static double averageGeneratorHeights(
            IrisGenerator[] generators,
            double low,
            double high,
            double x,
            double z,
            long seed
    ) {
        if (generators.length == 0) {
            return 0D;
        }
        if (!Double.isFinite(low) || !Double.isFinite(high)) {
            throw new IllegalStateException("Terrain generator bounds were not finite at " + x + "," + z
                    + " (generator=" + generators[0].getLoadKey() + ", bounds=" + low + ".." + high + ").");
        }
        double height = 0D;
        if (low == high) {
            for (int i = 0; i < generators.length; i++) {
                height += low;
            }
            return height / generators.length;
        }
        for (IrisGenerator generator : generators) {
            double noise = generator.getHeight(x, z, seed);
            if (!Double.isFinite(noise)) {
                throw new IllegalStateException("Terrain generator noise was not finite at " + x + "," + z
                        + " (generator=" + generator.getLoadKey() + ", noise=" + noise
                        + ", bounds=" + low + ".." + high + ").");
            }
            height += M.lerp(low, high, noise);
        }
        return height / generators.length;
    }

    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> buildGeneratorBounds(Engine engine) {
        Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> bounds = new HashMap<>();
        KList<IrisBiome> allBiomes = new KList<>(generatorBiomes);

        if (focusBiome != null && !allBiomes.contains(focusBiome)) {
            allBiomes.add(focusBiome);
        }

        for (int i = 0; i < frozenInterpolators.length; i++) {
            IdentityHashMap<IrisBiome, GeneratorBounds> interpolatorBounds = new IdentityHashMap<>(Math.max(allBiomes.size(), 16));
            for (IrisBiome biome : allBiomes) {
                interpolatorBounds.put(biome, computeGeneratorBounds(engine, frozenGenerators[i], biome));
            }
            bounds.put(frozenInterpolators[i], interpolatorBounds);
        }

        return bounds;
    }

    private GeneratorBounds computeGeneratorBounds(Engine engine, IrisGenerator[] generators, IrisBiome biome) {
        double min = 0D;
        double max = 0D;

        for (IrisGenerator gen : generators) {
            String key = gen.getLoadKey();
            if (key == null || key.isBlank()) {
                continue;
            }

            max += biome.getGenLinkMax(key, engine);
            min += biome.getGenLinkMin(key, engine);
        }

        return new GeneratorBounds(min, max);
    }

    private GeneratorBounds resolveGeneratorBounds(
            Engine engine,
            IrisGenerator[] generators,
            IrisBiome biome,
            IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds,
            IdentityHashMap<IrisBiome, GeneratorBounds> localBounds
    ) {
        GeneratorBounds bounds = cachedBounds == null ? null : cachedBounds.get(biome);
        if (bounds != null) {
            return bounds;
        }

        GeneratorBounds local = localBounds.get(biome);
        if (local != null) {
            return local;
        }

        GeneratorBounds computed = computeGeneratorBounds(engine, generators, biome);
        localBounds.put(biome, computed);
        return computed;
    }

    private IrisBiome implode(IrisBiome b, Double x, Double z) {
        if (b.getChildren().isEmpty()) {
            return b;
        }

        return implode(b, x, z, 3);
    }

    private IrisBiome implode(IrisBiome b, Double x, Double z, int max) {
        if (max < 0) {
            return b;
        }

        if (b.getChildren().isEmpty()) {
            return b;
        }

        CNG childCell = b.getChildrenGenerator(rng, 123, b.getChildShrinkFactor());
        ChildSelectionPlan childSelectionPlan = resolveChildSelectionPlan(b);
        IrisBiome biome = childSelectionPlan.select(childCell, x, z).withInferredType(b.getInferredType());
        if (biome == b && hasFixedNoise(b.getChildStyle())) {
            return biome;
        }
        return implode(biome, x, z, max - 1);
    }

    static boolean hasFixedNaturalBiomeNoise(IrisDimension dimension, Iterable<IrisBiome> biomes) {
        if (!hasFixedNoise(dimension.getRegionStyle()) || !hasFixedNoise(dimension.getContinentalStyle())
                || !hasFixedNoise(dimension.getLandBiomeStyle()) || !hasFixedNoise(dimension.getSeaBiomeStyle())) {
            return false;
        }
        for (IrisBiome biome : biomes) {
            if (!biome.getChildren().isEmpty() && !hasFixedNoise(biome.getChildStyle())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasFixedNoise(IrisGeneratorStyle style) {
        for (IrisGeneratorStyle current = style; current != null; current = current.getFracture()) {
            if (current.getExpression() != null) {
                return false;
            }
        }
        return true;
    }

    private ChildSelectionPlan resolveChildSelectionPlan(IrisBiome biome) {
        ChildSelectionPlan cachedPlan = childSelectionPlans.get(biome);
        if (cachedPlan != null) {
            return cachedPlan;
        }

        synchronized (childSelectionPlanLock) {
            ChildSelectionPlan synchronizedPlan = childSelectionPlans.get(biome);
            if (synchronizedPlan != null) {
                return synchronizedPlan;
            }

            KList<IrisBiome> children = biome.getRealChildren(this);
            KList<IrisBiome> options = new KList<>();
            for (IrisBiome child : children) {
                if (child != null) {
                    options.add(child);
                }
            }
            options.add(biome);

            ChildSelectionPlan createdPlan = ChildSelectionPlan.create(options);
            IdentityHashMap<IrisBiome, ChildSelectionPlan> next = new IdentityHashMap<>(childSelectionPlans);
            next.put(biome, createdPlan);
            childSelectionPlans = next;
            return createdPlan;
        }
    }

    private static class GeneratorBounds {
        private final double min;
        private final double max;
        private final NoiseBounds noiseBounds;

        private GeneratorBounds(double min, double max) {
            this.min = min;
            this.max = max;
            this.noiseBounds = new NoiseBounds(min, max);
        }
    }

    private static final class GridBoundsCache {
        private final int[] gx = new int[GRID_BOUNDS_CACHE_SIZE];
        private final int[] gz = new int[GRID_BOUNDS_CACHE_SIZE];
        private final int[] idx = new int[GRID_BOUNDS_CACHE_SIZE];
        private final long[] packed = new long[GRID_BOUNDS_CACHE_SIZE];
        private final boolean[] valid = new boolean[GRID_BOUNDS_CACHE_SIZE];
        private final BoundsSampler sampler = new BoundsSampler();

        private int slot(int cornerX, int cornerZ, int interpolatorIndex) {
            long h = (cornerX * 0x9E3779B97F4A7C15L) ^ (cornerZ * 0xC2B2AE3D27D4EB4FL) ^ (interpolatorIndex * 0x165667B19E3779F9L);
            h ^= (h >>> 32);
            return (int) (h & (GRID_BOUNDS_CACHE_SIZE - 1));
        }
    }

    /**
     * Reusable per-thread bounds provider. Holds the biome memo and the lazily computed generator
     * bounds for one sampleBoundsRaw pass so the pass allocates nothing; {@link #bind}
     * resets generator bounds while retaining bounded coordinate samples for the same biome stream.
     * <p>
     * Single threaded and non reentrant by contract, matching the thread local sample caches in
     * IrisInterpolation that this provider is invoked through. If a nested pass ever does appear,
     * {@link #isInUse()} makes the caller fall back to a freshly allocated sampler.
     */
    private static final class BoundsSampler implements NoiseBoundsProvider {
        private final CoordinateBiomeCache sampleCache = new CoordinateBiomeCache(4096);
        private WeakReference<ProceduralStream<IrisBiome>> sampleSource = new WeakReference<>(null);
        private final IdentityHashMap<IrisBiome, GeneratorBounds> localBounds = new IdentityHashMap<>(8);
        private IrisComplex complex;
        private Engine engine;
        private IrisGenerator[] generators;
        private IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds;
        private boolean inUse;

        private boolean isInUse() {
            return inUse;
        }

        private void bind(IrisComplex complex, Engine engine, IrisGenerator[] generators, IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds) {
            this.complex = complex;
            this.engine = engine;
            this.generators = generators;
            this.cachedBounds = cachedBounds;
            this.inUse = true;
            if (sampleSource.get() != complex.baseBiomeStream) {
                sampleCache.clear();
                sampleSource = new WeakReference<>(complex.baseBiomeStream);
            }

            if (!localBounds.isEmpty()) {
                localBounds.clear();
            }
        }

        private void release() {
            complex = null;
            engine = null;
            generators = null;
            cachedBounds = null;
            inUse = false;
        }

        @Override
        public NoiseBounds noise(double xx, double zz) {
            try {
                IrisBiome bx = sampleCache.get(xx, zz);
                if (bx == null) {
                    bx = complex.baseBiomeStream.get(xx, zz);
                    sampleCache.put(xx, zz, bx);
                }

                GeneratorBounds bounds = complex.resolveGeneratorBounds(engine, generators, bx, cachedBounds, localBounds);
                return bounds.noiseBounds;
            } catch (Throwable e) {
                long now = System.currentTimeMillis();
                long last = lastBoundsFailureLog.get();
                // The five second gate keeps this off the hot path; the once key keeps it out of a log
                // scan for the rest of the generation, where the same cause repeats for every column.
                if (now - last >= 5000L && lastBoundsFailureLog.compareAndSet(last, now)
                        && IrisLogging.warnOnce("biome-bounds:" + e.getClass().getName() + ":" + e.getMessage(),
                        "Failed to sample interpolated biome bounds at " + xx + " " + zz + ", flattening height to zero: " + e.getClass().getSimpleName() + ": " + e.getMessage())) {
                    IrisLogging.reportError(e);
                }
            }

            return ZERO_NOISE_BOUNDS;
        }
    }

    /**
     * Bounded biome memo keyed on the packed coordinate bits, replacing a linear scan that was
     * quadratic in the number of columns a wide starcast touches. Single threaded by contract.
     */
    private static final class CoordinateBiomeCache {
        private final long[] xBits;
        private final long[] zBits;
        private final IrisBiome[] values;
        private final int mask;

        private CoordinateBiomeCache(int capacity) {
            xBits = new long[capacity];
            zBits = new long[capacity];
            values = new IrisBiome[capacity];
            mask = capacity - 1;
        }

        private void clear() {
            Arrays.fill(values, null);
        }

        private IrisBiome get(double x, double z) {
            long xb = Double.doubleToLongBits(x);
            long zb = Double.doubleToLongBits(z);
            int slot = mix(xb, zb) & mask;
            return xBits[slot] == xb && zBits[slot] == zb ? values[slot] : null;
        }

        private void put(double x, double z, IrisBiome biome) {
            long xb = Double.doubleToLongBits(x);
            long zb = Double.doubleToLongBits(z);
            int slot = mix(xb, zb) & mask;
            xBits[slot] = xb;
            zBits[slot] = zb;
            values[slot] = biome;
        }

        private int mix(long xb, long zb) {
            long hash = xb * 0x9E3779B97F4A7C15L;
            hash ^= Long.rotateLeft(zb * 0xC2B2AE3D27D4EB4FL, 32);
            hash ^= (hash >>> 33);
            hash *= 0xff51afd7ed558ccdL;
            hash ^= (hash >>> 33);
            return (int) hash;
        }
    }

    static final class ChildSelectionPlan {
        private final IrisRaritySelection<IrisBiome> selection;

        private ChildSelectionPlan(IrisRaritySelection<IrisBiome> selection) {
            this.selection = selection;
        }

        static ChildSelectionPlan create(KList<IrisBiome> options) {
            return new ChildSelectionPlan(IrisRaritySelection.create(options));
        }

        IrisBiome select(CNG childCell, double x, double z) {
            long maximumIndex = selection.size() - 1L;
            if (maximumIndex <= 0L) {
                return selection.get(0L);
            }
            if (maximumIndex <= Integer.MAX_VALUE) {
                return selection.get(childCell.fit2D(0, (int) maximumIndex, x, z));
            }
            return selection.select(childCell.noiseFast2D(x, z));
        }
    }

    record GeneratorGroup(IrisInterpolator interpolator, IrisGenerator[] generators) {
    }

    public void close() {
        resolvedTerrain.clear();
        if (hydrologyRuntime != null) {
            hydrologyRuntime.close();
        }
    }
}
