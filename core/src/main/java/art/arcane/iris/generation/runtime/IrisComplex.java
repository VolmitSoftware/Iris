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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.ChunkPlanes;
import art.arcane.iris.generation.bake.GeneratorEvaluations;
import art.arcane.iris.generation.bake.PlaneSource;
import art.arcane.iris.generation.bake.TerrainProgram;
import art.arcane.iris.generation.noise.TerrainBatchScratch;
import art.arcane.iris.generation.stream.BakedDoubleStream;
import art.arcane.iris.generation.stream.BakedHeightStream;
import art.arcane.iris.generation.stream.BakedStream;
import art.arcane.iris.generation.stream.GenerationStreams;
import art.arcane.iris.generation.stream.RarityStream;
import art.arcane.iris.generation.stream.CachedStream2D;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;


import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.studio.generation.BiomeBuffetLayout;
import art.arcane.iris.world.history.GenerationBlend;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.TransitionDisplacementField;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyColumnSnapshot;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyNaturalSample;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyNaturalHeightProvider;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntimeContext;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.mantle.MantleHydrologyCaveVoxelView;
import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.pack.value.IrisRaritySelection;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.ContinentGenerator;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.terrain.transform.TerrainTransformRegistry;
import art.arcane.iris.generation.terrain.transform.TerrainTransformRuntime;
import art.arcane.iris.generation.terrain.transform.TerrainTransformer;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.terrain.RegionSelection;
import art.arcane.iris.generation.hydrology.IrisDeepFluidConfig;
import art.arcane.iris.generation.hydrology.IrisSurfacePoolConfig;
import art.arcane.iris.generation.hydrology.IrisHydrology;
import art.arcane.iris.generation.hydrology.IrisRiverHydrology;
import art.arcane.iris.generation.hydrology.IrisRiverBank3DConfig;
import art.arcane.iris.generation.hydrology.IrisRiverProfile;
import art.arcane.iris.generation.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.generation.terrain.Terrain3DRuntime;
import art.arcane.iris.generation.terrain.ProceduralTerrainHeightSampler;
import art.arcane.iris.generation.terrain.HydrologyBankTerrainRuntime;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.block.DataProvider;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.interpolation.IrisInterpolation;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import art.arcane.volmlib.util.interpolation.NoiseBoundsProvider;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Path;
import java.util.function.BiFunction;

@Data
@EqualsAndHashCode(exclude = {"data", "gridBoundsCache", "sharedCornerBounds", "sharedBoundsBiomes", "terrainProgram", "bakeCache", "regionSource", "baseBiomeSource", "caveSource", "rockSource", "fluidSource", "childStyleSeed", "inferredByType", "naturalHeightSource", "naturalTrueBiomeSource", "trueBiomeSource", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "hydrologyRuntime", "imageMapRuntime", "terrainEngine", "terrain3D", "hydrologyBanks3D"})
@ToString(exclude = {"data", "gridBoundsCache", "sharedCornerBounds", "sharedBoundsBiomes", "terrainProgram", "bakeCache", "regionSource", "baseBiomeSource", "caveSource", "rockSource", "fluidSource", "childStyleSeed", "inferredByType", "naturalHeightSource", "naturalTrueBiomeSource", "trueBiomeSource", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "hydrologyRuntime", "imageMapRuntime", "terrainEngine", "terrain3D", "hydrologyBanks3D"})
public class IrisComplex implements DataProvider {
    private static final NoiseBounds ZERO_NOISE_BOUNDS = new NoiseBounds(0D, 0D);
    private static final AtomicLong lastBoundsFailureLog = new AtomicLong(0L);
    private static final int GRID_BOUNDS_CACHE_SIZE = 8192;
    private static final int STUDIO_NOISE_CACHE_SIZE = 32_768;
    private static final int TERRAIN_COLUMN_CACHE_SIZE = 524_288;
    /** One million corners: about 20 MB, roughly a 4000 by 4000 block area at the 4-block grid. */
    private static final int SHARED_CORNER_BOUNDS_CAPACITY = 1 << 20;
    /** The slope streams measure the rise across a run of this many blocks, so gradient is slope over run. */
    private static final double SLOPE_RUN = 3D;
    /** A sheer coast would let shoreMinimumWidth buy unbounded height, so the shore band stops climbing here. */
    private static final double MAX_SHORE_BAND_GRADIENT = 3D;
    static final double SHORE_NOISE_LOWEST = -0.5D;
    static final double SHORE_NOISE_HIGHEST = 1.5D;
    private static final Comparator<IrisInterpolator> INTERPOLATOR_ORDER = Comparator
            .comparing((IrisInterpolator interpolator) -> interpolator.getFunction().name())
            .thenComparingDouble(IrisInterpolator::getHorizontalScale);
    private static final Comparator<IrisGenerator> GENERATOR_ORDER = Comparator.comparing(
            IrisGenerator::getLoadKey,
            Comparator.nullsFirst(Comparator.naturalOrder())
    );
    private static final ThreadLocal<TerrainBatchScratch> BATCH_SCRATCH = ThreadLocal.withInitial(TerrainBatchScratch::new);
    private static final InferredType[] INFERRED_BIOME_PREPARATION_ORDER = {
            InferredType.LAND,
            InferredType.CAVE,
            InferredType.SEA,
            InferredType.SHORE
    };
    @Getter(AccessLevel.NONE)
    private final transient ThreadLocal<GridBoundsCache> gridBoundsCache = ThreadLocal.withInitial(GridBoundsCache::new);
    @Getter(AccessLevel.NONE)
    private final int biomeBoundsSamplingStep;
    private transient volatile SharedCornerBounds sharedCornerBounds;
    private transient volatile SharedBoundsBiomes sharedBoundsBiomes;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient volatile TerrainProgram terrainProgram;
    @Getter(AccessLevel.NONE)
    private final transient ChunkBakeCache bakeCache;
    @Getter(AccessLevel.NONE)
    private final transient long childStyleSeed;
    @Getter(AccessLevel.NONE)
    private final transient Map<InferredType, ProceduralStream<IrisBiome>> inferredByType;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<IrisRegion> regionSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<IrisBiome> baseBiomeSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<IrisBiome> caveSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<NativeBlockState> rockSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<NativeBlockState> fluidSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<Double> naturalHeightSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<IrisBiome> naturalTrueBiomeSource;
    @Getter(AccessLevel.NONE)
    private final transient ProceduralStream<IrisBiome> trueBiomeSource;
    @Getter(AccessLevel.NONE)
    private final transient IrisInterpolator[] frozenInterpolators;
    @Getter(AccessLevel.NONE)
    private final transient IrisGenerator[][] frozenGenerators;
    @Getter(AccessLevel.NONE)
    private final transient Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> inferredBiomeStreams;
    private final TransitionGenerationPlan transitionGenerationPlan;
    @Getter(AccessLevel.NONE)
    private final transient boolean detached;
    private RNG rng;
    private double fluidHeight;
    private IrisData data;
    private Map<IrisInterpolator, Set<IrisGenerator>> generators;
    private ProceduralStream<IrisRegion> regionStream;
    private ProceduralStream<Double> regionStyleStream;
    private ProceduralStream<Double> regionIdentityStream;
    private ProceduralStream<InferredType> bridgeStream;
    private ProceduralStream<IrisBiome> landBiomeStream;
    private ProceduralStream<IrisBiome> caveBiomeStream;
    private ProceduralStream<IrisBiome> seaBiomeStream;
    private ProceduralStream<IrisBiome> shoreBiomeStream;
    private ProceduralStream<IrisBiome> baseBiomeStream;
    @Getter(AccessLevel.NONE)
    private final transient boolean reuseNaturalBaseBiome;
    private ProceduralStream<IrisBiome> naturalTrueBiomeStream;
    private ProceduralStream<IrisBiome> trueBiomeStream;
    private ProceduralStream<Double> naturalHeightStream;
    private ProceduralStream<Double> baseTerrainHeightStream;
    @Getter(AccessLevel.NONE)
    private final ProceduralTerrainHeightSampler proceduralTerrainHeight;
    @Getter(AccessLevel.NONE)
    private final transient Engine terrainEngine;
    @Setter(AccessLevel.NONE)
    private final transient TerrainTransformRuntime terrainTransform;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient ProceduralStream<Double> transformedHeightStream;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient ProceduralStream<Integer> transformedRoundedHeightStream;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient ProceduralStream<Double> transformedTopStream;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final transient ProceduralStream<Double> transformedSlopeStream;
    @Getter(AccessLevel.NONE)
    private transient Terrain3DRuntime terrain3D;
    private transient HydrologyBankTerrainRuntime hydrologyBanks3D;
    private ProceduralStream<Double> unblendedNaturalHeightStream;
    private final TransitionDisplacementField transitionDisplacement;
    private ProceduralStream<Double> heightStream;
    private ProceduralStream<Integer> roundedHeighteightStream;
    private ProceduralStream<Double> maxHeightStream;
    private ProceduralStream<Double> overlayStream;
    private ProceduralStream<Double> heightFluidStream;
    private ProceduralStream<Double> slopeStream;
    private ProceduralStream<Double> naturalSlopeStream;
    private ProceduralStream<Double> shoreSlopeStream;
    private ProceduralStream<Double> riverDistanceStream;
    private ProceduralStream<Double> riverFlowStream;
    private ProceduralStream<Double> riverCarveWeightStream;
    private ProceduralStream<Double> riverWaterSurfaceStream;
    private ProceduralStream<Integer> topSurfaceStream;
    private ProceduralStream<NativeBlockState> rockStream;
    private ProceduralStream<NativeBlockState> fluidStream;
    private Map<String, ProceduralStream<NativeBlockState>> hydrologyFluidStreams;
    private final BiomeBuffetLayout biomeBuffet;
    @Getter(AccessLevel.NONE)
    private final Map<IrisBiome, GeneratorGroup[]> biomeBuffetGenerators;
    private IrisBiome focusBiome;
    private IrisRegion focusRegion;
    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> generatorBounds;
    private Set<IrisBiome> generatorBiomes;
    private IrisHydrologyRuntime hydrologyRuntime;
    @Setter(AccessLevel.NONE)
    private final transient SubterrainPlanner subterrainPlanner;
    private transient IrisImageMapRuntime imageMapRuntime;
    // Copy-on-write: reads happen per column on every burst thread; the synchronizedMap
    // monitor was taken on every HIT. Writes are once per biome and bounded, so a fresh map
    // per insert is cheap. Identity keying is load-bearing (IrisBiome is mutable/value-hashed).
    private volatile IdentityHashMap<IrisBiome, ChildSelectionPlan> childSelectionPlans = new IdentityHashMap<>();
    private final Object childSelectionPlanLock = new Object();

    public IrisComplex(Engine engine) {
        this(engine, false, null, false);
    }

    public IrisComplex(Engine engine, boolean simple) {
        this(engine, simple, null, false);
    }

    public IrisComplex(Engine engine, TransitionGenerationPlan transitionGenerationPlan, boolean detached) {
        this(engine, false, transitionGenerationPlan, detached);
    }

    IrisComplex(Engine engine, boolean simple, TransitionGenerationPlan transitionGenerationPlan, boolean detached) {
        terrainEngine = engine;
        if (engine.getDimension().allowsSubterrainFeatures()) {
            SubterrainRasterizer.validateMaterials(engine.getDimension().getSubterrainFeatures());
        }
        subterrainPlanner = new SubterrainPlanner(new SubterrainPlanner.Options(
                engine.getDimension().allowsSubterrainFeatures() ? engine.getDimension().getSubterrainFeatures() : List.of(),
                engine.getSeedManager().getBodies(),
                engine.getDimension().getMinHeight(), engine.getDimension().getMaxHeight(),
                (x, z) -> regionStream.get(x, z).getLoadKey()),
                bounds -> transitionGenerationPlan == null || transitionGenerationPlan.allowsNewFootprint(
                        bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ()));
        biomeBoundsSamplingStep = engine.getDimension().getBiomeBoundsSamplingStep();
        this.detached = detached;
        this.transitionGenerationPlan = transitionGenerationPlan;
        int cacheSize = noiseCacheSize(engine, IrisSettings.get().getPerformance().getNoiseCacheSize(), detached);
        IrisBiome emptyBiome = new IrisBiome().setInferredType(InferredType.CAVE);
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getSeedManager().getComplex());
        childStyleSeed = IrisBiome.childStyleSeed(rng, 123);
        this.data = engine.getData();
        imageMapRuntime = IrisImageMapRuntime.compile(engine);
        double height = engine.getMaxHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new HashMap<>();
        generatorBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        // A registrant the version-content gate excluded never enters a pool, focus included.
        biomeBuffet = engine.isStudio() && "bukkit".equals(IrisPlatforms.get().platformName())
                && engine.getDimension().getStudioMode().biomeSizeChunks() > 0
                ? new BiomeBuffetLayout(engine.getDimension(), engine) : null;
        boolean buffetActive = biomeBuffet != null && !biomeBuffet.cells().isEmpty();
        focusBiome = buffetActive ? null : compatUsable(engine.getFocus());
        focusRegion = buffetActive ? null : compatUsable(engine.getFocusRegion());
        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new EnumMap<>(InferredType.class);
        inferredByType = inferredStreams;
        KList<IrisRegion> preparedRegions = new KList<>();

        if (focusBiome != null) {
            focusBiome = focusBiome.withInferredType(InferredType.LAND);
            focusRegion = engine.getDimension().resolveFocusRegion(focusBiome, engine);
        }

        ProceduralStream<IrisBiome> focusedBiomes = buffetActive
                ? ProceduralStream.of((x, z) -> biomeBuffet.terrain(x, z).biome(),
                        Interpolated.of(value -> 0D, value -> biomeBuffet.cells().getFirst().biome()))
                : focusBiome == null ? null : ProceduralStream.of((x, z) -> focusBiome,
                        Interpolated.of(value -> 0D, value -> focusBiome));
        ProceduralStream<IrisRegion> focusedRegions = buffetActive
                ? ProceduralStream.of((x, z) -> biomeBuffet.terrain(x, z).region(),
                        Interpolated.of(value -> 0D, value -> biomeBuffet.cells().getFirst().region()))
                : focusRegion == null ? null : ProceduralStream.of((x, z) -> focusRegion,
                        Interpolated.of(value -> 0D, value -> focusRegion));

        //@builder
        if (buffetActive) {
            for (BiomeBuffetLayout.Cell cell : biomeBuffet.cells()) {
                prepareInferredBiomes(cell.region(), preparedRegions);
                cell.region().getNaturalBiomes(this).forEach(this::registerGenerators);
                registerGenerators(cell.biome());
            }
        } else if (focusRegion != null) {
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
        GeneratorGroup[] generatorGroups = focusBiome == null && !buffetActive
                ? contributingGroups(freezeGeneratorGroups(generators), baseCapableBiomes(preparedRegions))
                : freezeGeneratorGroups(generators);
        int interpolatorCount = generatorGroups.length;
        frozenInterpolators = new IrisInterpolator[interpolatorCount];
        frozenGenerators = new IrisGenerator[interpolatorCount][];
        for (int frozenIndex = 0; frozenIndex < generatorGroups.length; frozenIndex++) {
            frozenInterpolators[frozenIndex] = generatorGroups[frozenIndex].interpolator();
            frozenGenerators[frozenIndex] = generatorGroups[frozenIndex].generators();
        }
        generatorBounds = buildGeneratorBounds(engine);
        biomeBuffetGenerators = compileBuffetGeneratorGroups();
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
        bakeCache = new ChunkBakeCache(new BakeSource(), detached ? ChunkBakeCache.MINIMUM_CAPACITY
                : ChunkBakeCache.capacityFor(Runtime.getRuntime().maxMemory()));
        rockSource = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextParallelRNG(45), data).stream()
                .select(engine.getDimension().getRockPalette().getBlockData(data));
        rockStream = new BakedStream<>(bakeCache, BakePlane.ROCK, rockSource);
        fluidSource = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextParallelRNG(78), data).stream()
                .select(engine.getDimension().getFluidPalette().getBlockData(data));
        fluidStream = new BakedStream<>(bakeCache, BakePlane.FLUID, fluidSource);
        hydrologyFluidStreams = createHydrologyFluidStreams(engine.getDimension().getHydrology());
        regionStyleStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883), getData()).stream()
                .zoom(engine.getDimension().getRegionZoom());
        regionIdentityStream = regionStyleStream.fit(Integer.MIN_VALUE, Integer.MAX_VALUE);
        regionSource = focusedRegions != null ? focusedRegions
                : RegionSelection.select(new RegionSelection.Options(
                        engine.getDimension(), data, regionStyleStream, imageMapRuntime, null));
        regionStream = focusedRegions != null ? focusedRegions : new BakedStream<>(bakeCache, BakePlane.REGION, regionSource);
        caveSource = GenerationStreams.contextInjecting(regionStream, engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.CAVE))
                .convertAware2D(ProceduralStream::get);
        caveBiomeStream = new BakedStream<>(bakeCache, BakePlane.CAVE_BIOME, caveSource);
        inferredStreams.put(InferredType.CAVE, caveBiomeStream);
        landBiomeStream = GenerationStreams.contextInjecting(regionStream, engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.LAND))
                .convertAware2D(ProceduralStream::get);
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        seaBiomeStream = GenerationStreams.contextInjecting(regionStream, engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.SEA))
                .convertAware2D(ProceduralStream::get);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        shoreBiomeStream = GenerationStreams.contextInjecting(regionStream, engine, (c, x, z) -> c.getRegion().get(x, z))
                .convert((r) -> createInferredBiomeStream(r, InferredType.SHORE))
                .convertAware2D(ProceduralStream::get);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);
        bridgeStream = focusedBiomes != null ? focusedBiomes.convert(IrisBiome::getInferredType) :
                ContinentGenerator.create(engine.getDimension(), rng, getData()).stream()
                        .convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND);
        ProceduralStream<IrisBiome> proceduralBaseBiomeStream = focusedBiomes != null ? focusedBiomes
                : ProceduralStream.of(this::proceduralBaseBiome,
                        (x, y, z) -> implode(inferredByType.get(bridgeStream.get(x, y, z)).get(x, z), x, z),
                        Interpolated.of(value -> 0D, value -> null));
        baseBiomeSource = focusedBiomes != null || !imageMapRuntime.has(IrisImageMapApplication.BIOME)
                ? proceduralBaseBiomeStream : ProceduralStream.of((x, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null ? proceduralBaseBiomeStream.get(x, z) : mapped;
                }, (x, y, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null ? proceduralBaseBiomeStream.get(x, y, z) : mapped;
                }, proceduralBaseBiomeStream);
        baseBiomeStream = focusedBiomes != null ? focusedBiomes : new BakedStream<>(bakeCache, BakePlane.BASE_BIOME, baseBiomeSource);
        proceduralTerrainHeight = new ProceduralTerrainHeightSampler(
                (x, z) -> getHeight(engine, x, z, engine.getSeedManager().getHeight()),
                engine.getDimension().getTerrainSamplingStep());
        baseTerrainHeightStream = new BakedDoubleStream(bakeCache, BakePlane.BASE_HEIGHT,
                ProceduralStream.ofDouble(this::sampleUnblendedNaturalTerrainHeight));
        boolean terrain3DEnabled = false;
        for (IrisBiome biome : generatorBiomes) {
            if (biome.getTerrain3D() != null) {
                biome.getTerrain3D().validate();
                terrain3DEnabled |= biome.getTerrain3D().isEnabled();
            }
        }
        terrain3D = !terrain3DEnabled ? null : new Terrain3DRuntime(
                new Terrain3DRuntime.Sources(
                        (x, z) -> baseTerrainHeightStream.getDouble(x, z),
                        this::sampleTerrain3DBiome),
                new Terrain3DRuntime.Options(engine.getSeedManager().getTerrain(), engine.getHeight(),
                        fluidHeight, data, terrain3DEnabled, TERRAIN_COLUMN_CACHE_SIZE));
        unblendedNaturalHeightStream = terrain3DEnabled
                ? ProceduralStream.ofDouble(this::sampleTerrain3DHeight)
                : baseTerrainHeightStream;
        transitionDisplacement = transitionGenerationPlan == null ? null : new TransitionDisplacementField(
                transitionGenerationPlan, new TransitionDisplacementField.Sources(
                (x, z) -> unblendedNaturalHeightStream.getDouble(x, z), engine.getHeight()));
        naturalHeightSource = transitionDisplacement == null
                ? unblendedNaturalHeightStream
                : ProceduralStream.ofDouble((x, z) -> transitionDisplacement.height(
                blockCoordinate(x), blockCoordinate(z), unblendedNaturalHeightStream.getDouble(x, z)));
        naturalHeightStream = new BakedDoubleStream(bakeCache, BakePlane.NATURAL_HEIGHT, naturalHeightSource);
        naturalTrueBiomeSource = focusedBiomes != null ? focusedBiomes
                : ProceduralStream.of(this::sampleNaturalTrueBiome, this::sampleNaturalTrueBiome,
                Interpolated.of(value -> 0D, value -> null));
        naturalTrueBiomeStream = new BakedStream<>(bakeCache, BakePlane.NATURAL_TRUE_BIOME, naturalTrueBiomeSource);
        IrisHydrology configuredHydrology = engine.getDimension().getHydrology();
        if (hydrologyActive(configuredHydrology)) {
            hydrologyRuntime = new IrisHydrologyRuntime(new IrisHydrologyRuntimeContext(
                    engine.getSeedManager().getBodies(),
                    engine.getHeight(),
                    engine.getDimension(),
                    data,
                    (x, z, naturalHeight) -> sampleHydrologyNatural(engine, x, z, naturalHeight),
                    new HydrologyHeightProvider(),
                    (x, z) -> describeNaturalHeight(engine, x, z),
                    this::sampleNaturalOcean,
                    footprint -> new MantleHydrologyCaveVoxelView(engine, this, footprint),
                    () -> engine.getPlatformHooks().isMainThread()
            ));
            hydrologyRuntime.setNeighbourPrefetchEnabled(!engine.isStudio());
            IrisRiverBank3DConfig banks3D = configuredHydrology.getRivers().getGeometry().getBanks3D();
            banks3D.validate();
            if (configuredHydrology.getRivers().isEnabled()
                    && configuredHydrology.getRivers().getSurface().isEnabled()
                    && banks3D.isEnabled() && banks3D.getAmplitude() > 0D) {
                hydrologyBanks3D = new HydrologyBankTerrainRuntime(
                        new HydrologyBankTerrainRuntime.Sources(
                                (x, z) -> naturalBankTerrainColumn(x, z, engine.getHeight()), this::sampleHydrologySnapshot),
                        new HydrologyBankTerrainRuntime.Options(engine.getSeedManager().getBodies(), engine.getHeight(),
                                Math.max(4096, cacheSize), data, banks3D));
            }
        }
        heightStream = new BakedHeightStream(bakeCache, () -> terrainEngine.getPlatformHooks().isMainThread(),
                this::nonblockingTerrainHeight, this::resolveHydrologyTerrainHeight);
        roundedHeighteightStream = GenerationStreams.contextInjecting(heightStream, engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .round();
        slopeStream = GenerationStreams.contextInjecting(heightStream, engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .slope(3);
        naturalSlopeStream = GenerationStreams.cache2DDouble(naturalHeightStream.slope(3), "naturalSlopeStream", engine, cacheSize);
        // Terrain3D column heights resolve the surface biome, so the shore band reads the pre-terrain3D slope or it recurses.
        shoreSlopeStream = terrain3DEnabled ? baseTerrainHeightStream.slope(3) : naturalSlopeStream;
        trueBiomeSource = focusedBiomes != null ? focusedBiomes
                : heightStream.convertAware2D((terrainHeight, x, z) -> resolveHydrologySurfaceBiome(terrainHeight, x, z));
        trueBiomeStream = new BakedStream<>(bakeCache, BakePlane.TRUE_BIOME, trueBiomeSource);
        riverDistanceStream = GenerationStreams.cache2DDouble(ProceduralStream.ofDouble(this::resolveHydrologyDistance), "riverDistanceStream", engine, cacheSize);
        riverFlowStream = GenerationStreams.cache2DDouble(ProceduralStream.ofDouble(this::resolveHydrologyFlow), "riverFlowStream", engine, cacheSize);
        riverCarveWeightStream = GenerationStreams.cache2DDouble(ProceduralStream.ofDouble(this::resolveHydrologyCarveWeight), "riverCarveWeightStream", engine, cacheSize);
        riverWaterSurfaceStream = GenerationStreams.cache2DDouble(ProceduralStream.ofDouble(this::resolveHydrologyFluidSurface), "riverWaterSurfaceStream", engine, cacheSize);
        heightFluidStream = GenerationStreams.cache2DDouble(ProceduralStream.ofDouble((x, z) -> Math.max(
                        heightStream.get(x, z),
                        riverWaterSurfaceStream.get(x, z)
                )), "heightFluidStream", engine, cacheSize);
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
        //@done
        TerrainTransformer transformer = engine.getDimension().getTerrainTransform() == null ? null
                : TerrainTransformRegistry.discover().resolve(engine.getDimension().getTerrainTransform());
        terrainTransform = transformer == null ? null
                : new TerrainTransformRuntime(new TerrainTransformRuntime.Options(engine, this), transformer);
        transformedHeightStream = transformer == null ? null : ProceduralStream.ofDouble((x, z) -> {
            int transformed = transformedHeight((int) Math.floor(x), (int) Math.floor(z), true);
            return transformed == Integer.MIN_VALUE ? heightStream.getDouble(x, z) : transformed;
        });
        transformedRoundedHeightStream = transformer == null ? null : ProceduralStream.ofDouble((x, z) -> {
            int transformed = transformedHeight((int) Math.floor(x), (int) Math.floor(z), true);
            return transformed == Integer.MIN_VALUE ? roundedHeighteightStream.get(x, z) : transformed;
        }).round();
        transformedTopStream = transformer == null ? null : ProceduralStream.ofDouble((x, z) -> {
            int transformed = transformedHeight((int) Math.floor(x), (int) Math.floor(z), false);
            return transformed == Integer.MIN_VALUE ? heightFluidStream.getDouble(x, z) : transformed;
        });
        ProceduralStream<Double> effectiveSlope = transformer == null ? null : transformedHeightStream.slope(3);
        transformedSlopeStream = transformer == null ? null : ProceduralStream.ofDouble((x, z) ->
                isNaturalTerrainContext() ? slopeStream.getDouble(x, z) : effectiveSlope.getDouble(x, z));
    }

    /**
     * The Studio-wide cache exists for the runtime a player edits against. Detached runtimes serve
     * historical activations (transition bands, saved-biome queries) and keep the configured size, or
     * every hotload would add another full set of Studio caches to the resident set.
     */
    static int noiseCacheSize(Engine engine, int configuredSize, boolean detached) {
        return engine.isStudio() && !detached ? Math.max(configuredSize, STUDIO_NOISE_CACHE_SIZE) : configuredSize;
    }

    void enablePreparedHydrologyCache(String runtimeIdentity, Path persistentRoot) {
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
        IrisBiome focused = focusedBiomeAt(x, z);
        InferredType inferredType = resolveNaturalInferredType(bridgeStream, focused, x, z);
        IrisRegion region = regionStream.get(x, z);
        IrisBiome biome = focused == null
                ? sampleNaturalBiome(inferredType, region, naturalHeight, x, z)
                : focused;
        return new IrisHydrologyNaturalSample(
                naturalHeight,
                inferredType == InferredType.SEA,
                biome,
                region
        );
    }

    private boolean sampleNaturalOcean(int x, int z) {
        return resolveNaturalInferredType(bridgeStream, focusedBiomeAt(x, z), x, z) == InferredType.SEA;
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
        return naturalHeightStream.getDouble(x, z);
    }

    private double sampleTerrain3DHeight(double x, double z) {
        return terrain3D.height(blockCoordinate(x), blockCoordinate(z));
    }

    private IrisBiome focusedBiomeAt(double x, double z) {
        BiomeBuffetLayout.Cell cell = biomeBuffet == null ? null : biomeBuffet.terrain(x, z);
        return cell == null ? focusBiome : cell.biome();
    }

    private IrisBiome sampleTerrain3DBiome(int x, int z) {
        IrisBiome focused = focusedBiomeAt(x, z);
        if (focused != null) {
            return focused;
        }
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        return mapped == null
                ? fixBiomeType(baseTerrainHeightStream.getDouble(x, z), baseBiomeStream.get(x, z),
                        regionStream.get(x, z), (double) x, (double) z, fluidHeight)
                : mapped;
    }

    public Terrain3DColumn naturalTerrainColumn(int x, int z) {
        if (terrain3D == null || !terrain3D.active()) {
            return null;
        }
        Terrain3DColumn column = terrain3D.column(x, z);
        if (transitionDisplacement != null && column.shaped()) {
            int offset = (int) Math.round(naturalHeightStream.getDouble(x, z)) - column.topY();
            column = column.displaced(offset, terrainEngine.getHeight());
        }
        return column.shaped() ? column : null;
    }

    public boolean hasTerrain3D() {
        return terrain3D != null && terrain3D.active() || hydrologyBanks3D != null;
    }

    public Terrain3DColumn terrainColumn(int x, int z, HydrologyColumnSample hydrology) {
        if (hydrology != null) {
            HydrologyColumnLayer layer = hydrology.primarySurfaceLayer().orElse(null);
            if (layer != null && layer.terrainOwned()) {
                return hydrologyBanks3D != null && !layer.channel() && layer.feature().type().isSurface()
                        ? hydrologyBanks3D.column(x, z) : null;
            }
        }
        return naturalTerrainColumn(x, z);
    }

    public Terrain3DColumn terrainColumn(int x, int z) {
        Terrain3DColumn transformed = transformedColumn(x, z);
        if (transformed != null) {
            return transformed;
        }
        return originalTerrainColumn(x, z);
    }

    Terrain3DColumn originalTerrainColumn(int x, int z) {
        if (!hasTerrain3D()) {
            return null;
        }
        if (terrainEngine != null && terrainEngine.answersFromNaturalTerrain(x, z)) {
            return naturalTerrainColumn(x, z);
        }
        return terrainColumn(x, z, sampleHydrologyColumn(x, z));
    }

    private Terrain3DColumn naturalBankTerrainColumn(int x, int z, int height) {
        Terrain3DColumn natural = naturalTerrainColumn(x, z);
        return natural == null ? Terrain3DColumn.unshaped(naturalHeightStream.getDouble(x, z), height) : natural;
    }

    public boolean isTerrain3DOpening(int x, int y, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        return column != null && y >= column.minY() && y < column.topY() && !column.isSolid(y)
                && (terrainEngine == null || !terrainEngine.isAdditionalTerrainOwned(x, y, z));
    }

    public boolean isNaturalTerrainSolid(int x, int y, int z) {
        Terrain3DColumn column = naturalTerrainColumn(x, z);
        return column == null ? y <= naturalTrueHeight(x, z) : column.isSolid(y);
    }

    public boolean isTerrain3DSurface(int x, int y, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        if (column == null || y < column.minY()) {
            return false;
        }
        if (!column.isSolid(y)) {
            return true;
        }
        int surface = column.surfaceY(y);
        return surface >= y && surface - y < 2;
    }

    public double terrainSurfaceSlope(int x, int surfaceY, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        if (column == null || surfaceY >= column.topY() || column.surfaceY(surfaceY) != surfaceY) {
            return getSlopeStream().getDouble(x, z);
        }
        return calculateNaturalSlope(surfaceY,
                nearbyTerrainSurfaceHeight(x + 3, surfaceY, z),
                nearbyTerrainSurfaceHeight(x, surfaceY, z + 3));
    }

    private double nearbyTerrainSurfaceHeight(int x, int surfaceY, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        return column == null ? getHeightStream().getDouble(x, z) : column.nearestSurfaceY(surfaceY);
    }

    private boolean supportsHeightBatch() {
        if (terrainEngine.getDimension().getTerrainSamplingStep() != 1 || !biomeBuffetGenerators.isEmpty()
                || !reuseNaturalBaseBiome) {
            return false;
        }
        for (IrisGenerator[] group : frozenGenerators) {
            for (IrisGenerator generator : group) {
                if (!generator.supportsBatch()) {
                    return false;
                }
            }
        }
        for (IrisShapedGeneratorStyle overlay : terrainEngine.getDimension().getOverlayNoise()) {
            if (overlay.getClass() != IrisShapedGeneratorStyle.class) {
                return false;
            }
            for (IrisGeneratorStyle style = overlay.getGenerator(); style != null; style = style.getFracture()) {
                if (style.getClass() != IrisGeneratorStyle.class || style.getExpression() != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void resolveBaseHeightBatch(long[] missing, int size, double[] values) {
        TerrainBatchScratch scratch = BATCH_SCRATCH.get();
        int mark = scratch.mark();
        int indexMark = scratch.indexMark();
        try {
            double[] xs = scratch.buffer(size);
            double[] zs = scratch.buffer(size);
            double[] lows = scratch.buffer(size);
            double[] highs = scratch.buffer(size);
            Arrays.fill(values, 0, size, 0D);
            for (int index = 0; index < size; index++) {
                xs[index] = RiverFootprint.unpackX(missing[index]);
                zs[index] = RiverFootprint.unpackZ(missing[index]);
            }
            TerrainProgram program = terrainProgram();
            long evaluations = 0L;
            for (int group = 0; group < frozenInterpolators.length; group++) {
                if (frozenGenerators[group].length == 0) {
                    continue;
                }
                for (int index = 0; index < size; index++) {
                    NoiseBounds bounds = gridSampleBounds(terrainEngine, frozenInterpolators[group], group,
                            frozenGenerators[group], xs[index], zs[index]);
                    lows[index] = bounds.min();
                    highs[index] = bounds.max();
                }
                evaluations += program.accumulate(group, scratch, xs, zs, size, lows, highs, values);
            }
            GeneratorEvaluations.batched(evaluations);
            for (int index = 0; index < size; index++) {
                values[index] = finishBaseHeight(xs[index], zs[index], values[index]);
            }
        } finally {
            scratch.reset(mark);
            scratch.resetIndices(indexMark);
        }
    }

    private void bakeBaseHeights(int chunkX, int chunkZ, long[] missing, double[] values) {
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        if (!supportsChunkHeightBatch()) {
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    values[index] = sampleUnblendedNaturalTerrainHeight(originX + (index & 15), originZ + (index >> 4));
                }
            }
            return;
        }
        TerrainBatchScratch scratch = BATCH_SCRATCH.get();
        int mark = scratch.mark();
        int indexMark = scratch.indexMark();
        try {
            double[] xs = scratch.buffer(256);
            double[] zs = scratch.buffer(256);
            double[] lows = scratch.buffer(256);
            double[] highs = scratch.buffer(256);
            double[] heights = scratch.buffer(256);
            int[] columns = scratch.indices(256);
            int count = 0;
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    xs[count] = originX + (index & 15);
                    zs[count] = originZ + (index >> 4);
                    heights[count] = 0D;
                    columns[count++] = index;
                }
            }
            if (count == 0) {
                return;
            }
            TerrainProgram program = terrainProgram();
            GridBoundsCache cache = gridBoundsCache.get();
            int grid = biomeBoundsSamplingStep;
            boolean corners = ChunkCorners.supports(grid);
            long evaluations = 0L;
            for (int group = 0; group < frozenInterpolators.length; group++) {
                if (frozenGenerators[group].length == 0) {
                    continue;
                }
                if (corners) {
                    if (!chunkCornerBounds(cache, group, grid, originX, originZ, xs, zs, count, lows, highs)) {
                        continue;
                    }
                } else {
                    for (int index = 0; index < count; index++) {
                        NoiseBounds bounds = gridSampleBounds(terrainEngine, frozenInterpolators[group], group,
                                frozenGenerators[group], xs[index], zs[index]);
                        lows[index] = bounds.min();
                        highs[index] = bounds.max();
                    }
                }
                evaluations += program.accumulate(group, scratch, xs, zs, count, lows, highs, heights);
            }
            for (int index = 0; index < count; index++) {
                values[columns[index]] = finishBaseHeight(xs[index], zs[index], heights[index]);
            }
            GeneratorEvaluations.bakedChunk(evaluations);
        } finally {
            scratch.reset(mark);
            scratch.resetIndices(indexMark);
        }
    }

    private boolean chunkCornerBounds(GridBoundsCache cache, int group, int grid, int originX, int originZ,
                                      double[] xs, double[] zs, int count, double[] lows, double[] highs) {
        int cells = 16 / grid;
        int stride = cells + 1;
        long[] packed = new long[stride * stride];
        boolean silent = true;
        for (int row = 0; row < stride; row++) {
            for (int column = 0; column < stride; column++) {
                long corner = cornerBounds(cache, terrainEngine, frozenInterpolators[group], group,
                        frozenGenerators[group], originX + column * grid, originZ + row * grid);
                packed[row * stride + column] = corner;
                silent &= silentBounds(corner);
            }
        }
        if (silent) {
            return false;
        }
        for (int index = 0; index < count; index++) {
            double x = xs[index];
            double z = zs[index];
            int gx = ((int) Math.floor(x)) & ~(grid - 1);
            int gz = ((int) Math.floor(z)) & ~(grid - 1);
            double fx = (x - gx) / grid;
            double fz = (z - gz) / grid;
            int cornerIndex = ((gz - originZ) / grid) * stride + (gx - originX) / grid;
            long b00 = packed[cornerIndex];
            if (fx == 0D && fz == 0D) {
                lows[index] = boundsLow(b00);
                highs[index] = boundsHigh(b00);
            } else if (fz == 0D) {
                long b10 = packed[cornerIndex + 1];
                lows[index] = biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b00), boundsLow(b10), fx, fz);
                highs[index] = biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b00), boundsHigh(b10), fx, fz);
            } else if (fx == 0D) {
                long b01 = packed[cornerIndex + stride];
                lows[index] = biLerp(boundsLow(b00), boundsLow(b00), boundsLow(b01), boundsLow(b01), fx, fz);
                highs[index] = biLerp(boundsHigh(b00), boundsHigh(b00), boundsHigh(b01), boundsHigh(b01), fx, fz);
            } else {
                long b10 = packed[cornerIndex + 1];
                long b01 = packed[cornerIndex + stride];
                long b11 = packed[cornerIndex + stride + 1];
                lows[index] = biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b01), boundsLow(b11), fx, fz);
                highs[index] = biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b01), boundsHigh(b11), fx, fz);
            }
        }
        return true;
    }

    private void prefetchBaseHeights(int chunkX, int chunkZ, long[] naturalMissing) {
        ChunkPlanes.Plane base = bakeCache.plane(BakePlane.BASE_HEIGHT, chunkX, chunkZ);
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        long[] compute = new long[4];
        boolean any = false;
        for (int index = 0; index < 256; index++) {
            if ((naturalMissing[index >>> 6] & (1L << (index & 63))) == 0L || base.ready(index)) {
                continue;
            }
            int x = originX + (index & 15);
            int z = originZ + (index >> 4);
            if (terrain3D != null && !terrain3D.requiresBaseHeight(x, z)) {
                continue;
            }
            compute[index >>> 6] |= 1L << (index & 63);
            any = true;
        }
        if (!any) {
            return;
        }
        double[] values = new double[256];
        long revision = ProvisionalSampling.revision();
        bakeBaseHeights(chunkX, chunkZ, compute, values);
        if (revision != ProvisionalSampling.revision()) {
            return;
        }
        for (int index = 0; index < 256; index++) {
            if ((compute[index >>> 6] & (1L << (index & 63))) != 0L) {
                base.storeDouble(index, values[index]);
            }
        }
    }

    private boolean supportsChunkHeightBatch() {
        return terrainEngine.getDimension().getTerrainSamplingStep() == 1 && biomeBuffetGenerators.isEmpty()
                && (biomeBuffet == null || biomeBuffet.cells().isEmpty());
    }

    private double finishBaseHeight(double x, double z, double interpolated) {
        double height = Math.max(Math.min(interpolated + fluidHeight + overlayStream.get(x, z), terrainEngine.getHeight()), 0);
        return imageMapRuntime.sampleTerrainHeight(x, z, height);
    }

    private TerrainProgram terrainProgram() {
        TerrainProgram program = terrainProgram;
        if (program == null) {
            synchronized (this) {
                program = terrainProgram;
                if (program == null) {
                    program = TerrainProgram.compile(frozenGenerators, terrainEngine.getSeedManager().getHeight() + 239945);
                    terrainProgram = program;
                }
            }
        }
        return program;
    }

    private void sampleNaturalBatch(long[] coordinates, int count, double[] heights) {
        long[] required = new long[count];
        int requiredCount = 0;
        for (int index = 0; index < count; index++) {
            int x = RiverFootprint.unpackX(coordinates[index]);
            int z = RiverFootprint.unpackZ(coordinates[index]);
            if (bakedColumn(BakePlane.NATURAL_HEIGHT, x, z)
                    || terrain3D != null && !terrain3D.requiresBaseHeight(x, z)
                    || bakedColumn(BakePlane.BASE_HEIGHT, x, z)) {
                continue;
            }
            required[requiredCount++] = coordinates[index];
        }
        if (requiredCount > 0) {
            double[] baseHeights = new double[requiredCount];
            long revision = ProvisionalSampling.revision();
            resolveBaseHeightBatch(required, requiredCount, baseHeights);
            if (bakeCache.memoAllowed()) {
                for (int index = 0; index < requiredCount; index++) {
                    bakeCache.storeDouble(BakePlane.BASE_HEIGHT, RiverFootprint.unpackX(required[index]),
                            RiverFootprint.unpackZ(required[index]), baseHeights[index], revision);
                }
            }
        }
        for (int index = 0; index < count; index++) {
            heights[index] = naturalHeightStream.getDouble(RiverFootprint.unpackX(coordinates[index]), RiverFootprint.unpackZ(coordinates[index]));
        }
    }

    private IrisBiome proceduralBaseBiome(double x, double z) {
        return implode(inferredByType.get(bridgeStream.get(x, z)).get(x, z), x, z);
    }

    private boolean bakedColumn(BakePlane plane, int blockX, int blockZ) {
        ChunkPlanes.Plane values = bakeCache.planeIfPresent(plane, blockX >> 4, blockZ >> 4);
        return values != null && values.ready(((blockZ & 15) << 4) | (blockX & 15));
    }

    private final class HydrologyHeightProvider implements IrisHydrologyNaturalHeightProvider {
        private final boolean batch = supportsHeightBatch();

        @Override
        public double sample(int blockX, int blockZ) {
            return naturalHeightStream.getDouble(blockX, blockZ);
        }

        @Override
        public boolean supportsBatch() {
            return batch;
        }

        @Override
        public void sampleBatch(long[] coordinates, int count, double[] heights) {
            if (!batch) {
                IrisHydrologyNaturalHeightProvider.super.sampleBatch(coordinates, count, heights);
                return;
            }
            sampleNaturalBatch(coordinates, count, heights);
        }
    }

    private final class BakeSource implements PlaneSource {
        @Override
        public void fill(BakePlane plane, int chunkX, int chunkZ, long[] missing, Object values) {
            switch (plane) {
                case REGION -> objectPlane(regionSource, chunkX, chunkZ, missing, (Object[]) values);
                case BASE_BIOME -> objectPlane(baseBiomeSource, chunkX, chunkZ, missing, (Object[]) values);
                case BASE_HEIGHT -> bakeBaseHeights(chunkX, chunkZ, missing, (double[]) values);
                case NATURAL_HEIGHT -> naturalPlane(chunkX, chunkZ, missing, (double[]) values);
                case NATURAL_TRUE_BIOME -> objectPlane(naturalTrueBiomeSource, chunkX, chunkZ, missing, (Object[]) values);
                case HEIGHT -> heightPlane(chunkX, chunkZ, missing, (double[]) values);
                case TRUE_BIOME -> objectPlane(trueBiomeSource, chunkX, chunkZ, missing, (Object[]) values);
                case ROCK -> objectPlane(rockSource, chunkX, chunkZ, missing, (Object[]) values);
                case FLUID -> objectPlane(fluidSource, chunkX, chunkZ, missing, (Object[]) values);
                case CAVE_BIOME -> objectPlane(caveSource, chunkX, chunkZ, missing, (Object[]) values);
            }
        }

        @Override
        public boolean mainThread() {
            return terrainEngine.getPlatformHooks().isMainThread();
        }

        private void objectPlane(ProceduralStream<?> source, int chunkX, int chunkZ, long[] missing, Object[] values) {
            int originX = chunkX << 4;
            int originZ = chunkZ << 4;
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    values[index] = source.get(originX + (index & 15), originZ + (index >> 4));
                }
            }
        }

        private void naturalPlane(int chunkX, int chunkZ, long[] missing, double[] values) {
            prefetchBaseHeights(chunkX, chunkZ, missing);
            int originX = chunkX << 4;
            int originZ = chunkZ << 4;
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    values[index] = naturalHeightSource.getDouble(originX + (index & 15), originZ + (index >> 4));
                }
            }
        }

        private void heightPlane(int chunkX, int chunkZ, long[] missing, double[] values) {
            int originX = chunkX << 4;
            int originZ = chunkZ << 4;
            boolean main = terrainEngine.getPlatformHooks().isMainThread();
            if (!main && !ChunkBakeCache.inSparseScope()) {
                bakeCache.complete(BakePlane.NATURAL_HEIGHT, chunkX, chunkZ);
            }
            for (int index = 0; index < 256; index++) {
                if ((missing[index >>> 6] & (1L << (index & 63))) != 0L) {
                    int x = originX + (index & 15);
                    int z = originZ + (index >> 4);
                    values[index] = main ? nonblockingTerrainHeight(x, z) : resolveHydrologyTerrainHeight(x, z);
                }
            }
        }
    }

    public long[] bakeCensus() {
        return bakeCache.census();
    }

    private double sampleUnblendedNaturalTerrainHeight(double x, double z) {
        double proceduralHeight = proceduralTerrainHeight.sample(x, z);
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
        double localFluidHeight = transitionFluidHeight(x, z, fluidHeight);
        double shoreTop = shoreBandTop(height, region, x, z, localFluidHeight, shoreSlopeStream);
        if (height >= localFluidHeight - 1 && height <= shoreTop && !biome.isShore()) {
            return sampleInferredBiome(region, InferredType.SHORE, x, z);
        }
        if (height > shoreTop && !biome.isLand()) {
            return sampleInferredBiome(region, InferredType.LAND, x, z);
        }
        if (height < localFluidHeight && !biome.isAquatic()) {
            return sampleInferredBiome(region, InferredType.SEA, x, z);
        }
        if (height == localFluidHeight && !biome.isShore()) {
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
        ProceduralStream<IrisBiome> picked = new RarityStream<>(biomePickNoiseStream(engine.getDimension(), region, inferredType),
                loadInferredBiomes(biomeKeys(region, inferredType), inferredType));
        return inferredType == InferredType.CAVE ? picked.onNull(emptyBiome) : picked;
    }

    private ProceduralStream<Double> biomePickNoiseStream(IrisDimension dimension, IrisRegion region, InferredType inferredType) {
        return switch (inferredType) {
            case CAVE -> dimension.getCaveBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.CAVE.ordinal()), getData()).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(region.getCaveBiomeZoom());
            case LAND -> dimension.getLandBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.LAND.ordinal()), getData()).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(dimension.getLandZoom())
                    .zoom(region.getLandBiomeZoom());
            case SEA -> dimension.getSeaBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SEA.ordinal()), getData()).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(dimension.getSeaZoom())
                    .zoom(region.getSeaBiomeZoom());
            case SHORE -> dimension.getShoreBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), getData()).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(region.getShoreBiomeZoom());
        };
    }

    private static KList<String> biomeKeys(IrisRegion region, InferredType inferredType) {
        return switch (inferredType) {
            case CAVE -> region.getCaveBiomes();
            case LAND -> region.getLandBiomes();
            case SEA -> region.getSeaBiomes();
            case SHORE -> region.getShoreBiomes();
        };
    }

    /**
     * The noise that picks one of the region's biomes of this type, before rarity splits it. Image maps and the
     * studio biome buffet bypass it.
     */
    public ProceduralStream<Double> biomePickNoiseStream(IrisRegion region, InferredType inferredType) {
        return biomePickNoiseStream(terrainEngine.getDimension(), region, inferredType);
    }

    public double biomePickNoise(IrisRegion region, InferredType inferredType, double x, double z) {
        return biomePickNoiseStream(region, inferredType).getDouble(x, z);
    }

    /**
     * The region's biomes of this type in the order their rarities split the picking noise.
     */
    public KList<IrisBiome> noisePickedBiomes(IrisRegion region, InferredType inferredType) {
        return loadInferredBiomes(biomeKeys(region, inferredType), inferredType);
    }

    /**
     * The biome the noise picks from the region's biomes of this type, before child biomes or the coast replace it.
     */
    public IrisBiome biomePickedByNoise(IrisRegion region, InferredType inferredType, double x, double z) {
        return createInferredBiomeStream(region, inferredType).get(x, z);
    }

    /**
     * The dimension's regions in the order their rarities split the region noise.
     */
    public KList<IrisRegion> noisePickedRegions() {
        KList<IrisRegion> regions = new KList<>();
        for (IrisRegion region : data.getRegionLoader().loadAll(terrainEngine.getDimension().getRegions())) {
            if (!region.isCompatExcluded()) {
                regions.add(region);
            }
        }
        return regions;
    }

    /**
     * The base biome a spot gets in the given region, before the terrain height can move it to another biome list,
     * and the list it was picked from.
     */
    public BiomePick basePickIn(IrisRegion region, double x, double z) {
        if (focusBiome != null) {
            return new BiomePick(focusBiome, focusBiome.getInferredType());
        }
        InferredType type = bridgeStream.get(x, z);
        return new BiomePick(implode(createInferredBiomeStream(region, type).get(x, z), x, z), type);
    }

    /**
     * The surface biome a spot would get if it belonged to the given region, and the biome list it came from, for
     * previewing one region across the map.
     */
    public BiomePick surfacePickAsIfIn(IrisRegion region, double x, double z) {
        BiomePick base = basePickIn(region, x, z);
        if (focusBiome != null) {
            return base;
        }
        IrisBiome surface = resolveSurfaceBiome(heightStream.getDouble(x, z), base.biome(), region, x, z, fluidHeight,
                createInferredBiomeStream(region, InferredType.LAND),
                createInferredBiomeStream(region, InferredType.SEA),
                createInferredBiomeStream(region, InferredType.SHORE),
                shoreSlopeStream);
        return surface == base.biome() ? base : new BiomePick(implode(surface, x, z), surface.getInferredType());
    }

    public IrisBiome surfaceBiomeAsIfIn(IrisRegion region, double x, double z) {
        return surfacePickAsIfIn(region, x, z).biome();
    }

    public record BiomePick(IrisBiome biome, InferredType type) {
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

    private Map<String, ProceduralStream<NativeBlockState>> createHydrologyFluidStreams(IrisHydrology hydrology) {
        if (hydrology == null) {
            return Map.of();
        }
        LinkedHashMap<String, ProceduralStream<NativeBlockState>> streams = new LinkedHashMap<>();
        IrisRiverHydrology rivers = Objects.requireNonNull(hydrology.getRivers(), "hydrology.rivers");
        int streamIndex = 0;
        for (IrisRiverProfile profile : rivers.getProfiles()) {
            if (profile == null) {
                throw new IllegalArgumentException("hydrology.rivers.profiles cannot contain null entries");
            }
            String profileKey = requireHydrologyKey(profile.getId(), "hydrology.rivers.profiles[].id");
            ProceduralStream<NativeBlockState> stream = configuredFluidStream(
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
            ProceduralStream<NativeBlockState> stream = configuredFluidStream(
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
            ProceduralStream<NativeBlockState> stream = configuredFluidStream(
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

    private ProceduralStream<NativeBlockState> configuredFluidStream(
            IrisMaterialPalette palette,
            RNG fluidRng,
            String configurationName
    ) {
        Objects.requireNonNull(palette, configurationName + " fluidPalette must be configured");
        KList<NativeBlockState> blocks = palette.getBlockData(data);
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    configurationName + " fluidPalette must resolve at least one fluid block");
        }
        for (NativeBlockState block : blocks) {
            if (block == null || !block.isFluid()) {
                throw new IllegalArgumentException(
                        configurationName + " fluidPalette may contain only fluid blocks");
            }
        }
        return palette.getLayerGenerator(fluidRng, data).stream().select(blocks);
    }

    public NativeBlockState resolveHydrologyFluid(String profileKey, double x, double z) {
        String key = Objects.requireNonNull(profileKey, "profileKey").trim();
        ProceduralStream<NativeBlockState> stream = hydrologyFluidStreams.get(key);
        if (stream == null) {
            throw new IllegalArgumentException("Unknown hydrology fluid profile: " + key);
        }
        return stream.get(x, z);
    }

    public NativeBlockState resolveSurfaceFluid(double x, double z) {
        if (transitionDisplacement != null) {
            String historical = transitionDisplacement.fluidStateKey(blockCoordinate(x), blockCoordinate(z));
            if (historical != null && !historical.isEmpty()) {
                NativeBlockState fluid = IrisPlatforms.get().registries().blockOrNull(historical);
                if (fluid == null || !fluid.isFluid()) {
                    throw new IllegalStateException("Historical surface fluid is unavailable: " + historical);
                }
                return fluid;
            }
        }
        HydrologyColumnLayer layer = surfaceFluidLayer(x, z);
        if (layer != null) {
            return resolveHydrologyFluid(layer.profileKey(), x, z);
        }
        return fluidStream.get(x, z);
    }

    private double resolveHydrologyTerrainHeight(double x, double z) {
        HydrologyColumnSample sample = hydrologySample(x, z);
        if (sample == null) {
            return naturalHeightStream.get(x, z);
        }
        HydrologyColumnLayer layer = sample.primarySurfaceLayerOrNull();
        if (hydrologyBanks3D != null && layer != null && layer.terrainOwned() && !layer.channel()) {
            return hydrologyBanks3D.column(blockCoordinate(x), blockCoordinate(z)).topY();
        }
        return sample.terrainHeight();
    }

    private IrisBiome sampleNaturalTrueBiome(double x, double z) {
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        return mapped == null
                ? fixBiomeType(naturalHeightStream.getDouble(x, z), baseBiomeStream.get(x, z), regionStream.get(x, z), x, z,
                transitionFluidHeight(x, z, fluidHeight))
                : mapped;
    }

    private IrisBiome sampleNaturalTrueBiome(double x, double y, double z) {
        IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
        return mapped == null
                ? fixBiomeType(naturalHeightStream.getDouble(x, y, z), baseBiomeStream.get(x, z), regionStream.get(x, z), x, z,
                transitionFluidHeight(x, z, fluidHeight))
                : mapped;
    }

    private double nonblockingTerrainHeight(double x, double z) {
        HydrologyColumnSnapshot snapshot = sampleHydrologySnapshot(blockCoordinate(x), blockCoordinate(z));
        if (!snapshot.available() || snapshot.column() == null) {
            return naturalHeightStream.getDouble(x, z);
        }
        HydrologyColumnSample sample = snapshot.column();
        HydrologyColumnLayer layer = sample.primarySurfaceLayerOrNull();
        if (hydrologyBanks3D != null && layer != null && layer.terrainOwned() && !layer.channel()) {
            Optional<Terrain3DColumn> column = hydrologyBanks3D.columnIfReady(blockCoordinate(x), blockCoordinate(z));
            if (column.isPresent()) {
                return column.get().topY();
            }
            ProvisionalSampling.mark();
            return naturalHeightStream.getDouble(x, z);
        }
        return sample.terrainHeight();
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
        if (layer != null) {
            return layer.fluidHeadY();
        }
        return transitionFluidHeight(x, z, fluidHeight);
    }

    private double transitionFluidHeight(double x, double z, double nativeHead) {
        return transitionDisplacement == null ? nativeHead
                : transitionDisplacement.fluidHeight(blockCoordinate(x), blockCoordinate(z), nativeHead);
    }

    public HydrologyColumnSample sampleHydrologyColumn(double x, double z) {
        if (hydrologyRuntime == null) {
            return null;
        }
        if (transitionGenerationPlan == null) {
            return hydrologyRuntime.sample(x, z).orElse(null);
        }
        double hydrologyWeight = transitionHydrologyWeight(x, z);
        HydrologyColumnSample sample = hydrologyRuntime.sample(x, z).orElse(null);
        if (sample == null) {
            return sample;
        }
        return transitionHydrologySample(sample, hydrologyWeight, transitionDisplacement);
    }

    private HydrologyColumnSnapshot sampleHydrologySnapshot(int x, int z) {
        if (hydrologyRuntime == null) {
            return HydrologyColumnSnapshot.ready(null);
        }
        HydrologyColumnSnapshot snapshot = hydrologyRuntime.sampleSnapshot(x, z);
        return !snapshot.available() || snapshot.column() == null ? snapshot
                : HydrologyColumnSnapshot.ready(transitionHydrologySample(
                        snapshot.column(), transitionHydrologyWeight(x, z), transitionDisplacement));
    }

    static HydrologyColumnSample transitionHydrologySample(
            HydrologyColumnSample sample, double weight, TransitionDisplacementField transitionDisplacement
    ) {
        HydrologyColumnSample tapered = taperHydrologySample(sample, weight);
        if (transitionDisplacement == null
                || transitionDisplacement.sample(sample.x(), sample.z()).newTerrainWeight() == 1D) {
            return tapered;
        }
        ArrayList<HydrologyColumnLayer> layers = new ArrayList<>(tapered.layers().size());
        int seaLevel = (int) Math.round(transitionDisplacement.fluidHeight(sample.x(), sample.z(), tapered.seaLevel()));
        for (int index = 0; index < tapered.layers().size(); index++) {
            HydrologyColumnLayer layer = tapered.layers().get(index);
            if (layer.feature().type().isUnderground() || layer.feature().type().isDeepFluid()) {
                layers.add(layer);
                continue;
            }
            HydrologyColumnLayer original = sample.layers().get(index);
            int displacedHead = (int) Math.round(
                    transitionDisplacement.fluidHeight(sample.x(), sample.z(), original.fluidHeadY()));
            boolean channel = original.channel() && layer.bedY() <= displacedHead;
            int head = channel ? displacedHead : layer.bedY();
            if ((tapered.naturalHeight() < seaLevel || tapered.naturalHeight() == seaLevel && head > seaLevel)
                    && (original.terrainOwned() || original.fluidOwned() || original.grading() || original.shore())) {
                continue;
            }
            layers.add(new HydrologyColumnLayer(layer.feature(), layer.bedY(),
                    head, head, channel, layer.shore(), layer.grading(), original.connectedFluid() && channel,
                    original.fallingFluid() && channel, original.receivingPool() && channel,
                    layer.terrainOwned(), original.fluidOwned() && channel, layer.oceanApron(),
                    layer.profileKey(), layer.surfaceBiomeKey(), layer.mouthBiomeKey(), layer.shoreBiomeKey(),
                    layer.bankBiomeKey(), layer.floodedCaveBiomeKey()));
        }
        return new HydrologyColumnSample(tapered.x(), tapered.z(), tapered.naturalHeight(), Math.max(tapered.seaLevel(), seaLevel),
                tapered.ocean(), tapered.parentBiomeKey(), layers);
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
            if (hydrologyWeight == 0D && !layer.feature().type().isUnderground() && !layer.feature().type().isDeepFluid()) {
                continue;
            }
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
        int bed = GenerationBlend.interpolateHeight(naturalHeight, layer.bedY(), hydrologyWeight);
        boolean channel = layer.channel() && bed <= layer.fluidHeadY();
        int head = channel ? layer.fluidHeadY() : bed;
        return new HydrologyColumnLayer(
                layer.feature(),
                bed,
                head,
                head,
                channel,
                layer.shore(),
                layer.grading(),
                layer.connectedFluid() && channel,
                layer.fallingFluid() && channel,
                layer.receivingPool() && channel,
                layer.terrainOwned(),
                layer.fluidOwned() && channel,
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
        return transitionDisplacement == null
                ? 1D
                : transitionDisplacement.seamWeight(blockCoordinate(x), blockCoordinate(z));
    }

    /** Whether the column's hydrology can be sampled without waiting for a plan (see Engine.answersFromNaturalTerrain). */
    public boolean isHydrologyPlanned(int x, int z) {
        if (hydrologyRuntime == null) {
            return true;
        }
        if (!hydrologyRuntime.isPlanned(x, z)) {
            return false;
        }
        if (hydrologyBanks3D == null || !terrainEngine.getPlatformHooks().isMainThread()) {
            return true;
        }
        HydrologyColumnSnapshot snapshot = sampleHydrologySnapshot(x, z);
        if (!snapshot.available()) {
            return false;
        }
        HydrologyColumnLayer layer = snapshot.column() == null ? null : snapshot.column().primarySurfaceLayerOrNull();
        if (layer == null || !layer.terrainOwned() || layer.channel() || hydrologyBanks3D.columnIfReady(x, z).isPresent()) {
            return true;
        }
        ProvisionalSampling.mark();
        return false;
    }

    public boolean isPlatformMainThread() {
        return terrainEngine.getPlatformHooks().isMainThread();
    }

    public ProceduralStream<Double> getRawHeightStream() {
        return heightStream;
    }

    public ProceduralStream<Double> getHeightStream() {
        return terrainTransform == null ? heightStream : transformedHeightStream;
    }

    ProceduralStream<Double> getRawSlopeStream() {
        return terrainEngine.getDimension().getTerrainTransform() == null ? slopeStream : heightStream.slope(3);
    }

    public ProceduralStream<Integer> getRoundedHeighteightStream() {
        return terrainTransform == null ? roundedHeighteightStream : transformedRoundedHeightStream;
    }

    public ProceduralStream<Double> getHeightFluidStream() {
        return terrainTransform == null ? heightFluidStream : transformedTopStream;
    }

    public ProceduralStream<Double> getSlopeStream() {
        return terrainTransform == null ? slopeStream : transformedSlopeStream;
    }

    public int transformedHeight(int x, int z, boolean ignoreFluid) {
        if (terrainTransform == null || isNaturalTerrainContext()) {
            return Integer.MIN_VALUE;
        }
        return readsReadyTerrainOnly()
                ? terrainTransform.readyHeight(x, z, ignoreFluid)
                : terrainTransform.height(x, z, ignoreFluid);
    }

    public Terrain3DColumn transformedColumn(int x, int z) {
        if (terrainTransform == null || isNaturalTerrainContext()) {
            return null;
        }
        return readsReadyTerrainOnly()
                ? terrainTransform.readyColumn(x, z) : terrainTransform.column(x, z);
    }

    public int transformedFluidHeight(int x, int z) {
        if (terrainTransform == null || isNaturalTerrainContext()) {
            return Integer.MIN_VALUE;
        }
        return readsReadyTerrainOnly()
                ? terrainTransform.readyFluidHeight(x, z) : terrainTransform.fluidHeight(x, z);
    }

    private boolean readsReadyTerrainOnly() {
        if (!terrainEngine.getPlatformHooks().isMainThread()) {
            return false;
        }
        IrisContext context = IrisContext.get();
        if (context == null || context.getEngine() != terrainEngine) {
            return true;
        }
        ChunkContext chunk = context.getChunkContext();
        if (chunk != null) {
            return chunk.getComplex() != this || chunk.isNaturalTerrain();
        }
        if (context.getGenerationSessionId() == 0L || terrainEngine.getComplex() != this) {
            return true;
        }
        if (terrainEngine instanceof IrisEngine irisEngine && irisEngine.threadState.assembly() != null
                && irisEngine.threadState.assembly().mode == null) {
            return true;
        }
        return terrainEngine.getMode() == null;
    }

    boolean isNaturalTerrainContext() {
        IrisContext context = IrisContext.get();
        return context != null && context.getChunkContext() != null
                && context.getChunkContext().getComplex() == this
                && context.getChunkContext().isNaturalTerrain();
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

    /**
     * Whether the mantle chunk can hold hydrology cave cells. The hydrology component publishes them
     * only from footprint columns within one block of the chunk, so a chunk with none around it holds
     * none. A transition world or a studio keeps cells an earlier plan wrote, so there every chunk may.
     */
    public boolean mayHoldHydrologyCells(int chunkX, int chunkZ) {
        return hydrologyRuntime == null || transitionGenerationPlan != null
                || terrainEngine == null || terrainEngine.isStudio()
                || hydrologyRuntime.hasColumnsAround(chunkX, chunkZ);
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
        if (transitionGenerationPlan == null) {
            return true;
        }
        if (!transitionGenerationPlan.allowsNewFootprint(minimumX, minimumZ, maximumX, maximumZ)) {
            return false;
        }
        GenerationHistoryRuntimeRouter router = terrainEngine instanceof IrisEngine irisEngine
                ? irisEngine.getGenerationHistoryRuntimeRouter().orElse(null) : null;
        return router == null || router.allowsObjectFootprint(transitionGenerationPlan,
                new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ));
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
        return fixBiomeType(terrainHeight, baseBiomeStream.get(x, z), regionStream.get(x, z), (double) x, (double) z,
                transitionFluidHeight(x, z, fluidHeight));
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

    public double sampleProceduralTerrainHeight(double worldX, double worldZ) {
        return proceduralTerrainHeight.sample(worldX, worldZ);
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
                transitionFluidHeight(x, z, fluidHeight)
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

    private IrisBiome fixBiomeType(double height, IrisBiome biome, IrisRegion region, double x, double z, double fluidHeight) {
        IrisBiome resolved = resolveSurfaceBiome(
                height,
                biome,
                region,
                x,
                z,
                fluidHeight,
                landBiomeStream,
                seaBiomeStream,
                shoreBiomeStream,
                shoreSlopeStream);
        return resolved == biome ? biome : implode(resolved, x, z);
    }

    /**
     * The highest column the shore band still covers. Shore height alone is a vertical window, so a steep
     * coast gets a beach only a fraction of a block wide; shoreMinimumWidth trades the local gradient back into
     * height so the beach keeps that width across the ground. Zero width never reads the slope, and neither
     * does a column that is already inside the vertical window or too high for any widening to reach.
     */
    static double shoreBandTop(
            double height,
            IrisRegion region,
            double x,
            double z,
            double fluidHeight,
            ProceduralStream<Double> shoreSlopes
    ) {
        double bandTop = fluidHeight + region.getShoreHeight(x, z);
        double shoreMinimumWidth = region.getShoreMinimumWidth();

        if (shoreMinimumWidth <= 0
                || height <= bandTop
                || height < fluidHeight - 1
                || height > bandTop + shoreMinimumWidth * MAX_SHORE_BAND_GRADIENT) {
            return bandTop;
        }

        return bandTop + shoreMinimumWidth * Math.min(shoreSlopes.getDouble(x, z) / SLOPE_RUN, MAX_SHORE_BAND_GRADIENT);
    }

    static boolean aboveShoreBand(
            double height,
            IrisRegion region,
            double x,
            double z,
            double fluidHeight,
            ProceduralStream<Double> shoreSlopes
    ) {
        if (region.getShoreMinimumWidth() <= 0) {
            double minimum = region.getShoreHeightMin();
            double maximum = region.getShoreHeightMax();
            double lowestTop = fluidHeight + shoreHeightAt(minimum, maximum, SHORE_NOISE_LOWEST);
            double highestTop = fluidHeight + shoreHeightAt(minimum, maximum, SHORE_NOISE_HIGHEST);
            if (height > Math.max(lowestTop, highestTop)) {
                return true;
            }
            if (height <= Math.min(lowestTop, highestTop)) {
                return false;
            }
        }
        return height > shoreBandTop(height, region, x, z, fluidHeight, shoreSlopes);
    }

    private static double shoreHeightAt(double minimum, double maximum, double noise) {
        return minimum == maximum ? minimum : IrisInterpolation.lerp(minimum, maximum, noise);
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
            ProceduralStream<IrisBiome> shoreBiomes,
            ProceduralStream<Double> shoreSlopes
    ) {
        if (biome == null || region == null) {
            return biome;
        }

        if (height >= fluidHeight - 1 && !biome.isShore()) {
            if (!aboveShoreBand(height, region, x, z, fluidHeight, shoreSlopes)) {
                return shoreBiomes.get(x, z);
            }
            if (!biome.isLand()) {
                return landBiomes.get(x, z);
            }
        } else if (!biome.isLand() && aboveShoreBand(height, region, x, z, fluidHeight, shoreSlopes)) {
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

        return groupHeight(interpolatorIndex, generators, lo, hi, x, z, seed + 239945);
    }

    private double groupHeight(int group, IrisGenerator[] generators, double low, double high, double x, double z, long heightSeed) {
        TerrainProgram program = terrainProgram;
        if (program == null && terrainEngine != null) {
            program = terrainProgram();
        }
        if (program == null || program.heightSeed() != heightSeed || group >= program.groups()
                || program.groupSize(group) != generators.length || generators != frozenGenerators[group]) {
            return averageGeneratorHeights(generators, low, high, x, z, heightSeed);
        }
        return program.average(group, BATCH_SCRATCH.get(), low, high, x, z);
    }

    private NoiseBounds gridSampleBounds(Engine engine, IrisInterpolator interpolator, int interpolatorIndex, IrisGenerator[] generators, double x, double z) {
        if (biomeBuffet != null && !biomeBuffet.cells().isEmpty()) {
            return generatorBounds.get(interpolator).get(focusedBiomeAt(x, z)).noiseBounds;
        }
        int grid = biomeBoundsSamplingStep;
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
        long sharedKey = SharedCornerBounds.key(gx, gz);
        long packed = shared.get(sharedKey, interpolatorIndex);
        if (packed == Long.MIN_VALUE) {
            if (GenerationCounter.ENABLED) {
                GenerationCounter.CORNER_STARCASTS.increment();
            }
            packed = computePackedBounds(cache, engine, interpolator, generators, gx, gz);
            if (!Double.isFinite(boundsLow(packed)) || !Double.isFinite(boundsHigh(packed))) {
                // A non-finite corner is a transient sampling fault, not a fact about the terrain:
                // hand it back uncached so the next sample recomputes it instead of pinning it.
                return packed;
            }
            shared.put(sharedKey, interpolatorIndex, packed);
        }
        cache.gx[slot] = gx;
        cache.gz[slot] = gz;
        cache.idx[slot] = interpolatorIndex;
        cache.packed[slot] = packed;
        cache.valid[slot] = true;
        return packed;
    }

    /** Coarse bounds allocate their shared table on the first corner sample. */
    private SharedCornerBounds sharedCornerBounds() {
        SharedCornerBounds shared = sharedCornerBounds;
        if (shared == null) {
            synchronized (this) {
                shared = sharedCornerBounds;
                if (shared == null) {
                    shared = new SharedCornerBounds(SHARED_CORNER_BOUNDS_CAPACITY);
                    sharedCornerBounds = shared;
                }
            }
        }
        return shared;
    }

    private SharedBoundsBiomes sharedBoundsBiomes(ProceduralStream<IrisBiome> source) {
        SharedBoundsBiomes shared = sharedBoundsBiomes;
        if (shared != null && shared.source() == source) {
            return shared;
        }
        synchronized (this) {
            shared = sharedBoundsBiomes;
            if (shared == null || shared.source() != source) {
                shared = new SharedBoundsBiomes(source, SharedBoundsBiomes.capacityFor(Runtime.getRuntime().maxMemory()));
                sharedBoundsBiomes = shared;
            }
            return shared;
        }
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
        if (!biomeBuffetGenerators.isEmpty()) {
            IrisBiome biome = focusedBiomeAt(x, z);
            for (GeneratorGroup group : biomeBuffetGenerators.get(biome)) {
                NoiseBounds bounds = generatorBounds.get(group.interpolator()).get(biome).noiseBounds;
                h += averageGeneratorHeights(group.generators(), bounds.min(), bounds.max(), x, z, seed + 239945);
            }
            return h;
        }

        int grid = biomeBoundsSamplingStep;
        GridBoundsCache cache = gridBoundsCache.get();
        if (!ChunkCorners.supports(grid) || cache.cornersInUse) {
            for (int interpolatorIndex = 0; interpolatorIndex < frozenInterpolators.length; interpolatorIndex++) {
                h += interpolateGenerators(engine, frozenInterpolators[interpolatorIndex], interpolatorIndex, frozenGenerators[interpolatorIndex], x, z, seed);
            }
            return h;
        }

        cache.cornersInUse = true;
        try {
            return cornerBlockHeight(cache, engine, grid, x, z, seed);
        } finally {
            cache.cornersInUse = false;
        }
    }

    private double cornerBlockHeight(GridBoundsCache cache, Engine engine, int grid, double x, double z, long seed) {
        double h = 0;
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        int gx = xi & ~(grid - 1);
        int gz = zi & ~(grid - 1);
        double fx = (x - gx) / grid;
        double fz = (z - gz) / grid;
        ChunkCorners corners = cache.chunkCorners(xi >> 4, zi >> 4, frozenInterpolators.length, grid);
        int cornerX = (gx - corners.originX) / grid;
        int cornerZ = (gz - corners.originZ) / grid;
        int cell = cornerZ * corners.cellsPerAxis + cornerX;
        long heightSeed = seed + 239945;
        for (int interpolatorIndex = 0; interpolatorIndex < frozenInterpolators.length; interpolatorIndex++) {
            IrisGenerator[] generators = frozenGenerators[interpolatorIndex];
            if (generators.length == 0) {
                continue;
            }
            int state = corners.cellState(interpolatorIndex, cell);
            if (state == ChunkCorners.SILENT) {
                continue;
            }
            long b00 = cornerAt(cache, engine, corners, interpolatorIndex, cornerX, cornerZ);
            long b10 = fx == 0D ? 0L : cornerAt(cache, engine, corners, interpolatorIndex, cornerX + 1, cornerZ);
            long b01 = fz == 0D ? 0L : cornerAt(cache, engine, corners, interpolatorIndex, cornerX, cornerZ + 1);
            long b11 = fx == 0D || fz == 0D ? 0L : cornerAt(cache, engine, corners, interpolatorIndex, cornerX + 1, cornerZ + 1);
            if (state == ChunkCorners.UNKNOWN) {
                corners.classify(interpolatorIndex, cornerX, cornerZ, cell);
                if (silentBounds(b00) && silentBounds(b10) && silentBounds(b01) && silentBounds(b11)) {
                    continue;
                }
            }
            double lo;
            double hi;
            if (fx == 0D && fz == 0D) {
                lo = boundsLow(b00);
                hi = boundsHigh(b00);
            } else if (fz == 0D) {
                lo = biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b00), boundsLow(b10), fx, fz);
                hi = biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b00), boundsHigh(b10), fx, fz);
            } else if (fx == 0D) {
                lo = biLerp(boundsLow(b00), boundsLow(b00), boundsLow(b01), boundsLow(b01), fx, fz);
                hi = biLerp(boundsHigh(b00), boundsHigh(b00), boundsHigh(b01), boundsHigh(b01), fx, fz);
            } else {
                lo = biLerp(boundsLow(b00), boundsLow(b10), boundsLow(b01), boundsLow(b11), fx, fz);
                hi = biLerp(boundsHigh(b00), boundsHigh(b10), boundsHigh(b01), boundsHigh(b11), fx, fz);
            }
            h += groupHeight(interpolatorIndex, generators, lo, hi, x, z, heightSeed);
        }

        return h;
    }

    private long cornerAt(GridBoundsCache cache, Engine engine, ChunkCorners corners, int interpolatorIndex, int column, int row) {
        if (corners.known(interpolatorIndex, column, row)) {
            return corners.corner(interpolatorIndex, column, row);
        }
        long packed = cornerBounds(cache, engine, frozenInterpolators[interpolatorIndex], interpolatorIndex,
                frozenGenerators[interpolatorIndex], corners.originX + column * corners.grid, corners.originZ + row * corners.grid);
        if (Double.isFinite(boundsLow(packed)) && Double.isFinite(boundsHigh(packed))) {
            corners.remember(interpolatorIndex, column, row, packed);
        }
        return packed;
    }

    private static boolean silentBounds(long packed) {
        return (packed & 0x7FFFFFFF7FFFFFFFL) == 0L;
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

    private static <T extends IrisRegistrant> T compatUsable(T registrant) {
        return registrant == null || registrant.isCompatExcluded() ? null : registrant;
    }

    private Map<IrisBiome, GeneratorGroup[]> compileBuffetGeneratorGroups() {
        if (biomeBuffet == null || biomeBuffet.cells().isEmpty()) {
            return Map.of();
        }
        Map<IrisBiome, GeneratorGroup[]> cells = new IdentityHashMap<>();
        for (BiomeBuffetLayout.Cell cell : biomeBuffet.cells()) {
            Map<IrisInterpolator, Set<IrisGenerator>> groups = new HashMap<>();
            KList<IrisBiome> biomes = new KList<>(cell.region().getNaturalBiomes(this));
            biomes.add(cell.biome());
            for (IrisBiome biome : biomes) {
                for (IrisBiomeGeneratorLink link : biome.getGenerators()) {
                    IrisGenerator generator = link.getCachedGenerator(this);
                    groups.computeIfAbsent(generator.getInterpolator(), ignored -> new HashSet<>()).add(generator);
                }
            }
            cells.put(cell.biome(), freezeGeneratorGroups(groups));
        }
        return cells;
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

    static GeneratorGroup[] contributingGroups(GeneratorGroup[] groups, Collection<IrisBiome> baseBiomes) {
        List<GeneratorGroup> contributing = new ArrayList<>(groups.length);
        for (GeneratorGroup group : groups) {
            if (contributesHeight(group.generators(), baseBiomes)) {
                contributing.add(group);
            }
        }
        return contributing.toArray(new GeneratorGroup[0]);
    }

    private static boolean contributesHeight(IrisGenerator[] generators, Collection<IrisBiome> baseBiomes) {
        for (IrisBiome biome : baseBiomes) {
            GeneratorBounds bounds = computeGeneratorBounds(generators, biome);
            if (bounds.min != 0D || bounds.max != 0D) {
                return true;
            }
        }
        return false;
    }

    private Collection<IrisBiome> baseCapableBiomes(KList<IrisRegion> regions) {
        Set<IrisBiome> reached = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<IrisBiome> pending = new ArrayDeque<>();
        for (IrisRegion region : regions) {
            pending.addAll(loadInferredBiomes(region.getLandBiomes(), InferredType.LAND));
            pending.addAll(loadInferredBiomes(region.getSeaBiomes(), InferredType.SEA));
        }
        pending.addAll(imageMapRuntime.getMappedBiomes());
        while (!pending.isEmpty()) {
            IrisBiome biome = pending.poll();
            if (!reached.add(biome)) {
                continue;
            }
            for (IrisBiome child : biome.getRealChildren(this)) {
                if (child != null) {
                    pending.add(child);
                }
            }
        }
        return reached;
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
        GeneratorEvaluations.scalar(generators.length);
        if (GenerationCounter.ENABLED) {
            GenerationCounter.GENERATOR_EVALUATIONS.add(generators.length);
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
                interpolatorBounds.put(biome, computeGeneratorBounds(frozenGenerators[i], biome));
            }
            bounds.put(frozenInterpolators[i], interpolatorBounds);
        }

        return bounds;
    }

    private static GeneratorBounds computeGeneratorBounds(IrisGenerator[] generators, IrisBiome biome) {
        double min = 0D;
        double max = 0D;

        for (IrisGenerator gen : generators) {
            String key = gen.getLoadKey();
            if (key == null || key.isBlank()) {
                continue;
            }

            max += biome.getGenLinkMax(key);
            min += biome.getGenLinkMin(key);
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

        GeneratorBounds computed = computeGeneratorBounds(generators, biome);
        localBounds.put(biome, computed);
        return computed;
    }

    private IrisBiome implode(IrisBiome b, double x, double z) {
        if (b.getChildren().isEmpty()) {
            return b;
        }

        return implode(b, x, z, 3);
    }

    private IrisBiome implode(IrisBiome b, double x, double z, int max) {
        if (max < 0) {
            return b;
        }

        if (b.getChildren().isEmpty()) {
            return b;
        }

        CNG childCell = b.getChildrenGenerator(childStyleSeed, b.getChildShrinkFactor());
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
        private ChunkCorners recentCorners;
        private ChunkCorners previousCorners;
        private boolean cornersInUse;

        private ChunkCorners chunkCorners(int chunkX, int chunkZ, int groups, int grid) {
            ChunkCorners recent = recentCorners;
            if (recent != null && recent.matches(chunkX, chunkZ, groups, grid)) {
                return recent;
            }
            ChunkCorners previous = previousCorners;
            if (previous != null && previous.matches(chunkX, chunkZ, groups, grid)) {
                previousCorners = recent;
                recentCorners = previous;
                return previous;
            }
            ChunkCorners reused = previous != null && previous.fits(groups, grid) ? previous : new ChunkCorners(groups, grid);
            reused.reset(chunkX, chunkZ);
            previousCorners = recent;
            recentCorners = reused;
            return reused;
        }

        private int slot(int cornerX, int cornerZ, int interpolatorIndex) {
            long h = (cornerX * 0x9E3779B97F4A7C15L) ^ (cornerZ * 0xC2B2AE3D27D4EB4FL) ^ (interpolatorIndex * 0x165667B19E3779F9L);
            h ^= (h >>> 32);
            return (int) (h & (GRID_BOUNDS_CACHE_SIZE - 1));
        }
    }

    private static final class ChunkCorners {
        private static final int UNKNOWN = 0;
        private static final int SILENT = 1;
        private static final int ACTIVE = 2;

        private final int groups;
        private final int grid;
        private final int cornersPerAxis;
        private final int cellsPerAxis;
        private final long[] packed;
        private final boolean[] known;
        private final byte[] cellStates;
        private int chunkX;
        private int chunkZ;
        private int originX;
        private int originZ;

        private ChunkCorners(int groups, int grid) {
            this.groups = groups;
            this.grid = grid;
            cellsPerAxis = 16 / grid;
            cornersPerAxis = cellsPerAxis + 1;
            packed = new long[groups * cornersPerAxis * cornersPerAxis];
            known = new boolean[packed.length];
            cellStates = new byte[groups * cellsPerAxis * cellsPerAxis];
        }

        private static boolean supports(int grid) {
            return grid >= 2 && grid <= 16 && Integer.bitCount(grid) == 1;
        }

        private boolean fits(int groups, int grid) {
            return this.groups == groups && this.grid == grid;
        }

        private boolean matches(int chunkX, int chunkZ, int groups, int grid) {
            return this.chunkX == chunkX && this.chunkZ == chunkZ && fits(groups, grid);
        }

        private void reset(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            originX = chunkX << 4;
            originZ = chunkZ << 4;
            Arrays.fill(known, false);
            Arrays.fill(cellStates, (byte) UNKNOWN);
        }

        private int cellState(int group, int cell) {
            return cellStates[group * cellsPerAxis * cellsPerAxis + cell];
        }

        private void setCellState(int group, int cell, int state) {
            cellStates[group * cellsPerAxis * cellsPerAxis + cell] = (byte) state;
        }

        private boolean known(int group, int column, int row) {
            return known[index(group, column, row)];
        }

        private long corner(int group, int column, int row) {
            return packed[index(group, column, row)];
        }

        private void remember(int group, int column, int row, long value) {
            int index = index(group, column, row);
            packed[index] = value;
            known[index] = true;
        }

        private void classify(int group, int column, int row, int cell) {
            int first = index(group, column, row);
            int second = first + cornersPerAxis;
            if (!known[first] || !known[first + 1] || !known[second] || !known[second + 1]) {
                return;
            }
            boolean silent = silentBounds(packed[first]) && silentBounds(packed[first + 1])
                    && silentBounds(packed[second]) && silentBounds(packed[second + 1]);
            setCellState(group, cell, silent ? SILENT : ACTIVE);
        }

        private int index(int group, int column, int row) {
            return (group * cornersPerAxis + row) * cornersPerAxis + column;
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
        private SharedBoundsBiomes sharedBiomes;
        private boolean integerCoordinates;
        private boolean inUse;

        private boolean isInUse() {
            return inUse;
        }

        private void bind(IrisComplex complex, Engine engine, IrisGenerator[] generators, IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds) {
            this.complex = complex;
            this.engine = engine;
            this.generators = generators;
            this.cachedBounds = cachedBounds;
            this.integerCoordinates = complex.baseBiomeStream instanceof BakedStream<?>
                    || complex.baseBiomeStream instanceof CachedStream2D<?>;
            this.sharedBiomes = integerCoordinates ? complex.sharedBoundsBiomes(complex.baseBiomeStream) : null;
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
            sharedBiomes = null;
            inUse = false;
        }

        @Override
        public NoiseBounds noise(double xx, double zz) {
            try {
                double sampleX = integerCoordinates ? (int) xx : xx;
                double sampleZ = integerCoordinates ? (int) zz : zz;
                IrisBiome bx = sampleCache.get(sampleX, sampleZ);
                if (bx == null) {
                    bx = sharedBiomes == null ? null : sharedBiomes.get((int) sampleX, (int) sampleZ);
                    if (bx == null) {
                        ChunkBakeCache.enterSparse();
                        try {
                            bx = complex.baseBiomeStream.get(xx, zz);
                        } finally {
                            ChunkBakeCache.exitSparse();
                        }
                        if (sharedBiomes != null) {
                            sharedBiomes.put((int) sampleX, (int) sampleZ, bx);
                        }
                    }
                    sampleCache.put(sampleX, sampleZ, bx);
                }

                GeneratorBounds bounds = complex.resolveGeneratorBounds(engine, generators, bx, cachedBounds, localBounds);
                return bounds.noiseBounds;
            } catch (Throwable e) {
                GenerationFailures.rethrowEngineFailure(e);
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
        if (terrainTransform != null) {
            terrainTransform.close();
        }
        if (hydrologyBanks3D != null) {
            hydrologyBanks3D.clear();
        }
        if (terrain3D != null) {
            terrain3D.clear();
        }
        if (hydrologyRuntime != null) {
            hydrologyRuntime.close();
        }
        bakeCache.clear();
        if (transitionDisplacement != null) {
            transitionDisplacement.clear();
        }
    }
}
