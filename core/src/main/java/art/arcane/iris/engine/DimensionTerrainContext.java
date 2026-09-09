package art.arcane.iris.engine;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.InferredType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.engine.object.IrisInterpolator;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisShapedGeneratorStyle;
import art.arcane.iris.engine.terrain.Terrain3DColumn;
import art.arcane.iris.engine.terrain.Terrain3DRuntime;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.iris.util.project.interpolation.NoiseBounds;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public final class DimensionTerrainContext implements DataProvider {
    private static final NoiseBounds ZERO_NOISE_BOUNDS = new NoiseBounds(0D, 0D);

    private final Engine engine;
    private final IrisDimension dimension;
    private final IrisData data;
    private final int localHeight;
    private final ProceduralStream<Double> heightStream;
    private final ProceduralStream<Double> slopeStream;
    private final ProceduralStream<Double> fluidHeightStream;
    private final ProceduralStream<IrisBiome> biomeStream;
    private final ProceduralStream<IrisRegion> regionStream;
    private final ProceduralStream<PlatformBlockState> rockStream;
    private final FluidBlockSampler fluidBlockSampler;
    private final IrisImageMapRuntime imageMapRuntime;
    private final boolean selfReferencing;
    private final SelfFallback selfFallback;
    private final Terrain3DRuntime terrain3D;
    private final boolean naturalSelf;

    private DimensionTerrainContext(ContextState state) {
        engine = state.engine();
        dimension = state.dimension();
        data = state.data();
        localHeight = state.localHeight();
        heightStream = state.heightStream();
        slopeStream = state.slopeStream();
        fluidHeightStream = state.fluidHeightStream();
        biomeStream = state.biomeStream();
        regionStream = state.regionStream();
        rockStream = state.rockStream();
        fluidBlockSampler = state.fluidBlockSampler();
        imageMapRuntime = state.imageMapRuntime();
        selfReferencing = state.selfReferencing();
        selfFallback = state.selfFallback();
        terrain3D = state.terrain3D();
        naturalSelf = state.naturalSelf();
    }

    public static DimensionTerrainContext forStack(Engine engine, IrisDimension dimension) {
        boolean selfReferencing = dimension.getLoadKey().equals(engine.getDimension().getLoadKey());
        if (selfReferencing) {
            return createSelfReferencing(engine, false);
        }

        int localHeight = dimension.getMaxHeight() - dimension.getMinHeight();
        return createCrossReferencing(
                engine,
                dimension,
                localHeight,
                dimension.getMinHeight(),
                "stack-" + dimension.getLoadKey(),
                true
        );
    }

    static DimensionTerrainContext forUpper(Engine engine, IrisDimension dimension) {
        boolean selfReferencing = dimension.getLoadKey().equals(engine.getDimension().getLoadKey());
        if (selfReferencing) {
            return createSelfReferencing(engine, true);
        }

        return createCrossReferencing(
                engine,
                dimension,
                engine.getHeight(),
                engine.getMinHeight(),
                "upper",
                false
        );
    }

    private static DimensionTerrainContext createSelfReferencing(Engine engine, boolean natural) {
        IrisComplex complex = engine.getComplex();
        double fluidHeight = engine.getDimension().getFluidHeight();
        ProceduralStream<Double> fluidHeightStream = natural
                ? ProceduralStream.ofDouble((x, z) -> fluidHeight)
                : complex.getRiverWaterSurfaceStream();
        FluidBlockSampler fluidBlockSampler = natural
                ? (x, z) -> complex.getFluidStream().get(x, z)
                : complex::resolveSurfaceFluid;
        SelfFallback selfFallback = natural
                ? null
                : new SelfFallback(
                        complex.getNaturalHeightStream(),
                        complex.getNaturalTrueBiomeStream(),
                        fluidHeight,
                        (x, z) -> complex.getFluidStream().get(x, z)
                );
        return new DimensionTerrainContext(new ContextState(
                engine,
                engine.getDimension(),
                engine.getData(),
                engine.getHeight(),
                natural ? complex.getUnblendedNaturalHeightStream() : complex.getHeightStream(),
                natural ? complex.getNaturalSlopeStream() : complex.getSlopeStream(),
                fluidHeightStream,
                natural ? complex.getUnblendedNaturalTrueBiomeStream() : complex.getTrueBiomeStream(),
                complex.getRegionStream(),
                complex.getRockStream(),
                fluidBlockSampler,
                complex.getImageMapRuntime(),
                true,
                selfFallback,
                null,
                natural
        ));
    }

    private static DimensionTerrainContext createCrossReferencing(
            Engine engine,
            IrisDimension dimension,
            int localHeight,
            int minimumWorldHeight,
            String cachePrefix,
            boolean includeFluid
    ) {
        IrisData resolvedData = dimension.getLoader();
        if (resolvedData == null) {
            resolvedData = engine.getData();
        }
        IrisData dimensionData = resolvedData;
        IrisImageMapRuntime imageMapRuntime = IrisImageMapRuntime.compile(
                dimensionData,
                dimension,
                minimumWorldHeight
        );
        long seedOffset = dimension.getLoadKey().hashCode();
        RNG rng = new RNG(engine.getSeedManager().getComplex() ^ seedOffset);
        double fluidHeight = dimension.getFluidHeight();
        int cacheSize = IrisSettings.get().getPerformance().getNoiseCacheSize();
        DataProvider dataProvider = () -> dimensionData;
        IrisBiome configuredFocusBiome = loadFocusBiome(dimension, dimensionData);
        IrisBiome focusBiome = configuredFocusBiome == null
                ? null
                : configuredFocusBiome.withInferredType(InferredType.LAND);
        IrisRegion focusRegion = focusBiome == null
                ? loadFocusRegion(dimension, dimensionData)
                : dimension.resolveFocusRegion(focusBiome, dataProvider);

        Map<IrisInterpolator, Set<IrisGenerator>> generators = new HashMap<>();
        Set<IrisBiome> allBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<IrisBiome, IrisComplex.ChildSelectionPlan> childSelectionPlans =
                Collections.synchronizedMap(new IdentityHashMap<>());
        KList<IrisRegion> preparedRegions = new KList<>();
        if (focusRegion != null) {
            preparedRegions.add(focusRegion);
            focusRegion.getNaturalBiomes(dataProvider).forEach(biome -> registerBiomeGenerators(
                    biome, dataProvider, allBiomes, generators));
        } else {
            for (IrisRegion region : terrainRegions(dimension.getAllRegions(dataProvider), imageMapRuntime)) {
                preparedRegions.add(region);
                region.getNaturalBiomes(dataProvider).forEach(biome -> registerBiomeGenerators(
                        biome, dataProvider, allBiomes, generators));
            }
            for (IrisBiome mappedBiome : imageMapRuntime.getMappedBiomes()) {
                if (!mappedBiome.isCompatExcluded()) {
                    registerBiomeGenerators(mappedBiome, dataProvider, allBiomes, generators);
                }
            }
        }
        if (focusBiome != null) {
            allBiomes.add(focusBiome);
        }

        IrisComplex.GeneratorGroup[] generatorGroups = IrisComplex.freezeGeneratorGroups(generators);
        Map<IrisInterpolator, IdentityHashMap<IrisBiome, NoiseBounds>> generatorBounds = new HashMap<>();
        for (IrisComplex.GeneratorGroup group : generatorGroups) {
            IdentityHashMap<IrisBiome, NoiseBounds> interpolatorBounds =
                    new IdentityHashMap<>(Math.max(allBiomes.size(), 16));
            for (IrisBiome biome : allBiomes) {
                double minimum = 0D;
                double maximum = 0D;
                for (IrisGenerator generator : group.generators()) {
                    String key = generator.getLoadKey();
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    maximum += biome.getGenLinkMax(key, engine);
                    minimum += biome.getGenLinkMin(key, engine);
                }
                interpolatorBounds.put(biome, new NoiseBounds(minimum, maximum));
            }
            generatorBounds.put(group.interpolator(), interpolatorBounds);
        }

        ProceduralStream<Double> regionStyleStream = dimension.getRegionStyle()
                .create(rng.nextParallelRNG(883), dimensionData).stream()
                .zoom(dimension.getRegionZoom());
        KList<IrisRegion> regionPool = new KList<>();
        for (IrisRegion region : dimensionData.getRegionLoader().loadAll(dimension.getRegions())) {
            if (!region.isCompatExcluded()) {
                regionPool.add(region);
            }
        }
        ProceduralStream<IrisRegion> proceduralRegionStream = focusRegion == null
                ? regionStyleStream.selectRarity(regionPool)
                : ProceduralStream.of(
                        (x, z) -> focusRegion,
                        Interpolated.of(value -> 0D, value -> focusRegion)
                );
        ProceduralStream<IrisRegion> regionStream = focusRegion == null
                ? proceduralRegionStream
                        .convertAware2D((region, x, z) -> mappedRegion(imageMapRuntime, region, x, z))
                        .cache2D(cachePrefix + "ImageMappedRegionStream", engine, cacheSize)
                : proceduralRegionStream;
        Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> preparedBiomeStreams =
                compileInferredBiomeStreams(dimension, dimensionData, rng, preparedRegions);

        ProceduralStream<IrisBiome> landBiomeStream = regionStream
                .convert(region -> IrisComplex.preparedInferredBiomeStream(
                        preparedBiomeStreams, region, InferredType.LAND))
                .convertAware2D(ProceduralStream::get);
        ProceduralStream<IrisBiome> seaBiomeStream = regionStream
                .convert(region -> IrisComplex.preparedInferredBiomeStream(
                        preparedBiomeStreams, region, InferredType.SEA))
                .convertAware2D(ProceduralStream::get);
        ProceduralStream<IrisBiome> shoreBiomeStream = regionStream
                .convert(region -> IrisComplex.preparedInferredBiomeStream(
                        preparedBiomeStreams, region, InferredType.SHORE))
                .convertAware2D(ProceduralStream::get);

        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new HashMap<>();
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);

        ProceduralStream<InferredType> bridgeStream = focusBiome == null
                ? dimension.getContinentalStyle()
                        .create(rng.nextParallelRNG(234234565), dimensionData)
                        .bake().scale(1D / dimension.getContinentZoom()).bake().stream()
                        .convert(value -> value >= dimension.getLandChance()
                                ? InferredType.SEA
                                : InferredType.LAND)
                : ProceduralStream.of(
                        (x, z) -> focusBiome.getInferredType(),
                        Interpolated.of(value -> 0D, value -> focusBiome.getInferredType())
                );

        ProceduralStream<IrisBiome> proceduralBaseBiomeStream = focusBiome == null
                ? bridgeStream
                        .convertAware2D((type, x, z) -> {
                            ProceduralStream<IrisBiome> stream = inferredStreams.get(type);
                            return stream != null
                                    ? stream.get(x, z)
                                    : inferredStreams.get(InferredType.LAND).get(x, z);
                        })
                        .convertAware2D((biome, x, z) -> implode(
                                biome, x, z, rng, dataProvider, childSelectionPlans, 3))
                : ProceduralStream.of(
                        (x, z) -> focusBiome,
                        Interpolated.of(value -> 0D, value -> focusBiome)
                );
        ProceduralStream<IrisBiome> baseBiomeStream = focusBiome == null
                ? proceduralBaseBiomeStream
                        .convertAware2D((biome, x, z) -> mappedBiome(imageMapRuntime, biome, x, z))
                        .cache2D(cachePrefix + "ImageMappedBaseBiomeStream", engine, cacheSize)
                : proceduralBaseBiomeStream;

        KList<IrisShapedGeneratorStyle> overlayNoise = dimension.getOverlayNoise();
        ProceduralStream<Double> overlayStream = overlayNoise.isEmpty()
                ? ProceduralStream.ofDouble((x, z) -> 0D)
                : ProceduralStream.ofDouble((x, z) -> {
            double value = 0D;
            for (IrisShapedGeneratorStyle style : overlayNoise) {
                value += style.get(rng, dimensionData, x, z);
            }
            return value;
        });

        long heightSeed = engine.getSeedManager().getHeight() ^ seedOffset;
        ProceduralStream<Double> heightStream = ProceduralStream.of((x, z) -> {
            IrisBiome biome = baseBiomeStream.get(x, z);
            if (biome == null) {
                return mappedTerrainHeight(imageMapRuntime, fluidHeight, x, z);
            }
            double interpolatedHeight = 0D;
            for (IrisComplex.GeneratorGroup group : generatorGroups) {
                IrisInterpolator interpolator = group.interpolator();
                IrisGenerator[] groupGenerators = group.generators();
                if (groupGenerators.length == 0) {
                    continue;
                }
                IdentityHashMap<IrisBiome, NoiseBounds> cachedBounds = generatorBounds.get(interpolator);
                NoiseBounds sampledBounds = interpolator.interpolateBounds(x, z, (sampleX, sampleZ) -> {
                    try {
                        IrisBiome sampledBiome = baseBiomeStream.get(sampleX, sampleZ);
                        if (sampledBiome == null) {
                            return ZERO_NOISE_BOUNDS;
                        }
                        NoiseBounds bounds = cachedBounds == null ? null : cachedBounds.get(sampledBiome);
                        if (bounds != null) {
                            return bounds;
                        }
                        double minimum = 0D;
                        double maximum = 0D;
                        for (IrisGenerator generator : groupGenerators) {
                            String key = generator.getLoadKey();
                            if (key == null || key.isBlank()) {
                                continue;
                            }
                            maximum += sampledBiome.getGenLinkMax(key, engine);
                            minimum += sampledBiome.getGenLinkMin(key, engine);
                        }
                        return new NoiseBounds(minimum, maximum);
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                        return ZERO_NOISE_BOUNDS;
                    }
                });
                interpolatedHeight += IrisComplex.averageGeneratorHeights(
                        groupGenerators,
                        sampledBounds.min(),
                        sampledBounds.max(),
                        x,
                        z,
                        heightSeed + 239945
                );
            }
            double proceduralHeight = Math.max(
                    Math.min(interpolatedHeight + fluidHeight + overlayStream.get(x, z), localHeight),
                    0D
            );
            return mappedTerrainHeight(imageMapRuntime, proceduralHeight, x, z);
        }, Interpolated.DOUBLE).cache2DDouble(
                cachePrefix + "ImageMappedHeightStream", engine, cacheSize);
        ProceduralStream<IrisBiome> finalBiomeStream = focusBiome == null
                ? heightStream.convertAware2D((height, x, z) -> {
                    IrisBiome mappedBiome = imageMapRuntime.sampleBiome(x, z);
                    if (mappedBiome != null) {
                        return mappedBiome;
                    }
                    IrisBiome baseBiome = baseBiomeStream.get(x, z);
                    IrisBiome resolvedBiome = IrisComplex.resolveSurfaceBiome(
                            height,
                            baseBiome,
                            regionStream.get(x, z),
                            x,
                            z,
                            fluidHeight,
                            landBiomeStream,
                            seaBiomeStream,
                            shoreBiomeStream
                    );
                    return resolvedBiome == baseBiome
                            ? baseBiome
                            : implode(resolvedBiome, x, z, rng, dataProvider, childSelectionPlans, 3);
                }).cache2D(cachePrefix + "ImageMappedFinalBiomeStream", engine, cacheSize)
                : ProceduralStream.of(
                        (x, z) -> focusBiome,
                        Interpolated.of(value -> 0D, value -> focusBiome)
                ).cache2D(cachePrefix + "FinalBiomeStreamFocus", engine, cacheSize);

        boolean terrain3DEnabled = false;
        for (IrisBiome biome : allBiomes) {
            if (biome.getTerrain3D() != null) {
                biome.getTerrain3D().validate();
                terrain3DEnabled |= biome.getTerrain3D().isEnabled();
            }
        }
        Terrain3DRuntime terrain3D = !terrain3DEnabled ? null : new Terrain3DRuntime(
                new Terrain3DRuntime.Sources(heightStream::getDouble, finalBiomeStream::get),
                new Terrain3DRuntime.Options(engine.getSeedManager().getTerrain() ^ seedOffset,
                        localHeight, fluidHeight, dimensionData, true, Math.max(4096, cacheSize))
        );
        ProceduralStream<Double> shapedHeightStream = terrain3D == null ? heightStream
                : ProceduralStream.ofDouble((x, z) -> shapedHeight(terrain3D, x, z))
                .cache2DDouble(cachePrefix + "Terrain3DHeightStream", engine, cacheSize);
        ProceduralStream<Double> slopeStream = shapedHeightStream.slope(3)
                .cache2DDouble(cachePrefix + "SlopeStream", engine, cacheSize);

        ProceduralStream<PlatformBlockState> rockStream = dimension.getRockPalette()
                .getLayerGenerator(rng.nextParallelRNG(45), dimensionData).stream()
                .select(dimension.getRockPalette().getBlockData(dimensionData));
        FluidBlockSampler fluidBlockSampler;
        if (includeFluid) {
            ProceduralStream<PlatformBlockState> fluidStream = dimension.getFluidPalette()
                    .getLayerGenerator(rng.nextParallelRNG(78), dimensionData).stream()
                    .select(dimension.getFluidPalette().getBlockData(dimensionData));
            fluidBlockSampler = fluidStream::get;
        } else {
            fluidBlockSampler = (x, z) -> null;
        }

        return new DimensionTerrainContext(new ContextState(
                engine,
                dimension,
                dimensionData,
                localHeight,
                shapedHeightStream,
                slopeStream,
                ProceduralStream.ofDouble((x, z) -> fluidHeight),
                finalBiomeStream,
                regionStream,
                rockStream,
                fluidBlockSampler,
                imageMapRuntime,
                false,
                null,
                terrain3D,
                false
        ));
    }

    private static void registerBiomeGenerators(
            IrisBiome biome,
            DataProvider dataProvider,
            Set<IrisBiome> allBiomes,
            Map<IrisInterpolator, Set<IrisGenerator>> generators
    ) {
        allBiomes.add(biome);
        biome.getGenerators().forEach(link -> {
            IrisGenerator generator = link.getCachedGenerator(dataProvider);
            if (generator != null) {
                generators.computeIfAbsent(generator.getInterpolator(), key -> new HashSet<>()).add(generator);
            }
        });
    }

    static KList<IrisRegion> terrainRegions(Iterable<IrisRegion> declared, IrisImageMapRuntime imageMaps) {
        KList<IrisRegion> regions = new KList<>();
        Set<IrisRegion> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IrisRegion region : declared) {
            if (!region.isCompatExcluded() && seen.add(region)) {
                regions.add(region);
            }
        }
        for (IrisRegion region : imageMaps.getMappedRegions()) {
            if (!region.isCompatExcluded() && seen.add(region)) {
                regions.add(region);
            }
        }
        return regions;
    }

    static IrisRegion mappedRegion(
            IrisImageMapRuntime imageMapRuntime,
            IrisRegion proceduralRegion,
            double worldX,
            double worldZ
    ) {
        IrisRegion mappedRegion = imageMapRuntime.sampleRegion(worldX, worldZ);
        return mappedRegion == null ? proceduralRegion : mappedRegion;
    }

    static double mappedTerrainHeight(
            IrisImageMapRuntime imageMapRuntime,
            double proceduralHeight,
            double worldX,
            double worldZ
    ) {
        return imageMapRuntime.sampleTerrainHeight(worldX, worldZ, proceduralHeight);
    }

    static IrisBiome mappedBiome(
            IrisImageMapRuntime imageMapRuntime,
            IrisBiome proceduralBiome,
            double worldX,
            double worldZ
    ) {
        IrisBiome mappedBiome = imageMapRuntime.sampleBiome(worldX, worldZ);
        return mappedBiome == null ? proceduralBiome : mappedBiome;
    }

    static PlatformBlockState mappedSurfaceBlock(
            IrisImageMapRuntime imageMapRuntime,
            PlatformBlockState proceduralBlock,
            double worldX,
            double worldZ
    ) {
        PlatformBlockState mappedBlock = imageMapRuntime.sampleSurfaceBlock(worldX, worldZ);
        return mappedBlock == null ? proceduralBlock : mappedBlock;
    }

    private static Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> compileInferredBiomeStreams(
            IrisDimension dimension,
            IrisData data,
            RNG rng,
            Iterable<IrisRegion> regions
    ) {
        IdentityHashMap<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> compiled =
                new IdentityHashMap<>();
        for (IrisRegion region : regions) {
            if (compiled.containsKey(region)) {
                continue;
            }
            EnumMap<InferredType, ProceduralStream<IrisBiome>> streams = new EnumMap<>(InferredType.class);
            streams.put(InferredType.LAND, createInferredBiomeStream(
                    dimension, data, rng, region, InferredType.LAND));
            streams.put(InferredType.SEA, createInferredBiomeStream(
                    dimension, data, rng, region, InferredType.SEA));
            streams.put(InferredType.SHORE, createInferredBiomeStream(
                    dimension, data, rng, region, InferredType.SHORE));
            compiled.put(region, Collections.unmodifiableMap(streams));
        }
        return Collections.unmodifiableMap(compiled);
    }

    private static ProceduralStream<IrisBiome> createInferredBiomeStream(
            IrisDimension dimension,
            IrisData data,
            RNG rng,
            IrisRegion region,
            InferredType type
    ) {
        return switch (type) {
            case LAND -> dimension.getLandBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.LAND.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(dimension.getLandZoom())
                    .zoom(region.getLandBiomeZoom())
                    .selectRarity(loadInferredBiomes(data, region.getLandBiomes(), InferredType.LAND));
            case SEA -> dimension.getSeaBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SEA.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(dimension.getSeaZoom())
                    .zoom(region.getSeaBiomeZoom())
                    .selectRarity(loadInferredBiomes(data, region.getSeaBiomes(), InferredType.SEA));
            case SHORE -> dimension.getShoreBiomeStyle()
                    .create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), data).stream()
                    .zoom(dimension.getBiomeZoom())
                    .zoom(region.getShoreBiomeZoom())
                    .selectRarity(loadInferredBiomes(data, region.getShoreBiomes(), InferredType.SHORE));
            default -> throw new IllegalArgumentException("Unsupported stacked terrain biome type: " + type);
        };
    }

    private static KList<IrisBiome> loadInferredBiomes(
            IrisData data,
            KList<String> keys,
            InferredType type
    ) {
        KList<IrisBiome> inferredBiomes = new KList<>();
        for (IrisBiome biome : data.getBiomeLoader().loadAll(keys)) {
            if (biome.isCompatExcluded()) {
                continue;
            }
            inferredBiomes.add(biome.withInferredType(type));
        }
        return inferredBiomes;
    }

    private static IrisBiome loadFocusBiome(IrisDimension dimension, IrisData data) {
        String key = dimension.getFocus();
        if (key == null || key.isBlank()) {
            return null;
        }
        IrisBiome biome = data.getBiomeLoader().load(key);
        return biome == null || biome.isCompatExcluded() ? null : biome;
    }

    private static IrisRegion loadFocusRegion(IrisDimension dimension, IrisData data) {
        String key = dimension.getFocusRegion();
        if (key == null || key.isBlank()) {
            return null;
        }
        IrisRegion region = data.getRegionLoader().load(key);
        return region == null || region.isCompatExcluded() ? null : region;
    }

    private static IrisBiome implode(
            IrisBiome biome,
            double x,
            double z,
            RNG rng,
            DataProvider dataProvider,
            Map<IrisBiome, IrisComplex.ChildSelectionPlan> childSelectionPlans,
            int remainingDepth
    ) {
        if (biome == null || remainingDepth < 0 || biome.getChildren().isEmpty()) {
            return biome;
        }

        IrisComplex.ChildSelectionPlan selectionPlan = childSelectionPlans.get(biome);
        if (selectionPlan == null) {
            synchronized (childSelectionPlans) {
                selectionPlan = childSelectionPlans.get(biome);
                if (selectionPlan == null) {
                    KList<IrisBiome> options = new KList<>();
                    for (IrisBiome child : biome.getRealChildren(dataProvider)) {
                        if (child != null) {
                            options.add(child);
                        }
                    }
                    options.add(biome);
                    selectionPlan = IrisComplex.ChildSelectionPlan.create(options);
                    childSelectionPlans.put(biome, selectionPlan);
                }
            }
        }

        IrisBiome selected = selectionPlan.select(
                biome.getChildrenGenerator(rng, 123, biome.getChildShrinkFactor()), x, z);
        if (selected == null) {
            return biome;
        }
        return implode(
                selected.withInferredType(biome.getInferredType()),
                x,
                z,
                rng,
                dataProvider,
                childSelectionPlans,
                remainingDepth - 1
        );
    }

    public double getNormalTerrainHeight(double x, double z) {
        return getNormalTerrainHeight(x, z, usesNaturalFallback(x, z));
    }

    public boolean hasTerrain3D() {
        return selfReferencing ? engine.getComplex().hasTerrain3D() : terrain3D != null;
    }

    public Terrain3DColumn terrainColumn(int x, int z) {
        return terrainColumn(x, z, usesNaturalFallback(x, z));
    }

    Terrain3DColumn terrainColumn(int x, int z, boolean naturalFallback) {
        if (selfReferencing) {
            return naturalSelf || naturalFallback
                    ? engine.getComplex().naturalTerrainColumn(x, z)
                    : engine.getComplex().terrainColumn(x, z);
        }
        if (terrain3D == null) {
            return null;
        }
        Terrain3DColumn column = terrain3D.column(x, z);
        return column.shaped() ? column : null;
    }

    private static double shapedHeight(Terrain3DRuntime runtime, double x, double z) {
        Terrain3DColumn column = runtime.column((int) Math.floor(x), (int) Math.floor(z));
        return column.shaped() ? column.topY() : column.baseHeight();
    }

    double getNormalTerrainHeight(double x, double z, boolean naturalFallback) {
        if (naturalFallback && selfFallback != null) {
            return selfFallback.heightStream().getDouble(x, z);
        }
        return heightStream.getDouble(x, z);
    }

    public double getFluidHeight(double x, double z) {
        return getFluidHeight(x, z, usesNaturalFallback(x, z));
    }

    double getFluidHeight(double x, double z, boolean naturalFallback) {
        if (naturalFallback && selfFallback != null) {
            return selfFallback.fluidHeight();
        }
        return fluidHeightStream.getDouble(x, z);
    }

    public ProceduralStream<Double> getSlopeStream() {
        return slopeStream;
    }

    public ProceduralStream<Double> getSurfaceSlopeStream(int sourceY) {
        return !hasTerrain3D() ? slopeStream : ProceduralStream.ofDouble((x, z) ->
                surfaceSlope((int) Math.floor(x), sourceY, (int) Math.floor(z)));
    }

    double surfaceSlope(int x, int sourceY, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        if (column == null || sourceY >= column.topY() || column.surfaceY(sourceY) != sourceY) {
            return slopeStream.getDouble(x, z);
        }
        return IrisComplex.calculateNaturalSlope(sourceY,
                nearbySurfaceHeight(x + 3, sourceY, z), nearbySurfaceHeight(x, sourceY, z + 3));
    }

    private double nearbySurfaceHeight(int x, int sourceY, int z) {
        Terrain3DColumn column = terrainColumn(x, z);
        return column == null ? getNormalTerrainHeight(x, z) : column.nearestSurfaceY(sourceY);
    }

    public IrisBiome getBiome(double x, double z) {
        return getBiome(x, z, usesNaturalFallback(x, z));
    }

    private IrisBiome getBiome(double x, double z, boolean naturalFallback) {
        if (naturalFallback && selfFallback != null) {
            return selfFallback.biomeStream().get(x, z);
        }
        return biomeStream.get(x, z);
    }

    public IrisRegion getRegion(double x, double z) {
        return regionStream == null ? null : regionStream.get(x, z);
    }

    public PlatformBlockState getRockBlock(double x, double z) {
        return rockStream.get(x, z);
    }

    public PlatformBlockState getFluidBlock(double x, double z) {
        return getFluidBlock(x, z, usesNaturalFallback(x, z));
    }

    private PlatformBlockState getFluidBlock(double x, double z, boolean naturalFallback) {
        if (naturalFallback && selfFallback != null) {
            return selfFallback.fluidBlockSampler().sample(x, z);
        }
        return fluidBlockSampler.sample(x, z);
    }

    public PlatformBlockState getSurfaceBlock(double x, double z) {
        return imageMapRuntime.sampleSurfaceBlock(x, z);
    }

    public IrisDimension getDimension() {
        return dimension;
    }

    @Override
    public IrisData getData() {
        return data;
    }

    public int getLocalHeight() {
        return localHeight;
    }

    public boolean isSelfReferencing() {
        return selfReferencing;
    }

    ColumnSample sampleColumn(double x, double z, boolean naturalFallback) {
        return new ColumnSample(
                getNormalTerrainHeight(x, z, naturalFallback),
                getFluidHeight(x, z, naturalFallback),
                getBiome(x, z, naturalFallback),
                getRegion(x, z),
                getRockBlock(x, z),
                getFluidBlock(x, z, naturalFallback),
                getSurfaceBlock(x, z),
                terrainColumn((int) Math.floor(x), (int) Math.floor(z), naturalFallback)
        );
    }

    private boolean usesNaturalFallback(double x, double z) {
        return selfFallback != null && engine.answersFromNaturalTerrain(
                (int) Math.floor(x),
                (int) Math.floor(z)
        );
    }

    private interface FluidBlockSampler {
        PlatformBlockState sample(double x, double z);
    }

    private record ContextState(
            Engine engine,
            IrisDimension dimension,
            IrisData data,
            int localHeight,
            ProceduralStream<Double> heightStream,
            ProceduralStream<Double> slopeStream,
            ProceduralStream<Double> fluidHeightStream,
            ProceduralStream<IrisBiome> biomeStream,
            ProceduralStream<IrisRegion> regionStream,
            ProceduralStream<PlatformBlockState> rockStream,
            FluidBlockSampler fluidBlockSampler,
            IrisImageMapRuntime imageMapRuntime,
            boolean selfReferencing,
            SelfFallback selfFallback,
            Terrain3DRuntime terrain3D,
            boolean naturalSelf
    ) {
    }

    private record SelfFallback(
            ProceduralStream<Double> heightStream,
            ProceduralStream<IrisBiome> biomeStream,
            double fluidHeight,
            FluidBlockSampler fluidBlockSampler
    ) {
    }

    record ColumnSample(
            double terrainHeight,
            double fluidHeight,
            IrisBiome biome,
            IrisRegion region,
            PlatformBlockState rockBlock,
            PlatformBlockState fluidBlock,
            PlatformBlockState surfaceBlock,
            Terrain3DColumn terrainColumn
    ) {
    }
}
