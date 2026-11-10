package art.arcane.iris.engine;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.engine.object.noise.IrisInterpolator;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.interpolation.NoiseBounds;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class UpperDimensionContext implements DataProvider {
    private static final NoiseBounds ZERO_NOISE_BOUNDS = new NoiseBounds(0D, 0D);
    private final IrisDimension dimension;
    private final IrisData data;
    private final int chunkHeight;
    private final ProceduralStream<Double> heightStream;
    private final ProceduralStream<IrisBiome> biomeStream;
    private final ProceduralStream<IrisRegion> regionStream;
    private final IrisImageMapRuntime imageMapRuntime;
    private final boolean selfReferencing;

    private UpperDimensionContext(ContextState state) {
        this.dimension = state.dimension();
        this.data = state.data();
        this.chunkHeight = state.chunkHeight();
        this.heightStream = state.heightStream();
        this.biomeStream = state.biomeStream();
        this.regionStream = state.regionStream();
        this.imageMapRuntime = state.imageMapRuntime();
        this.selfReferencing = state.selfReferencing();
    }

    public static UpperDimensionContext create(Engine engine, IrisDimension upperDim) {
        boolean selfRef = upperDim.getLoadKey().equals(engine.getDimension().getLoadKey());
        int chunkHeight = engine.getHeight();
        if (selfRef) {
            return createSelfReferencing(engine, chunkHeight);
        }
        return createCrossReferencing(engine, upperDim, chunkHeight);
    }

    private static UpperDimensionContext createSelfReferencing(Engine engine, int chunkHeight) {
        IrisComplex complex = engine.getComplex();
        return new UpperDimensionContext(new ContextState(
                engine.getDimension(),
                engine.getData(),
                chunkHeight,
                complex.getHeightStream(),
                complex.getTrueBiomeStream(),
                complex.getRegionStream(),
                complex.getImageMapRuntime(),
                true
        ));
    }

    private static UpperDimensionContext createCrossReferencing(Engine engine, IrisDimension upperDim, int chunkHeight) {
        IrisData resolvedData = upperDim.getLoader();
        if (resolvedData == null) {
            resolvedData = engine.getData();
        }
        IrisData upperData = resolvedData;
        IrisImageMapRuntime imageMapRuntime = IrisImageMapRuntime.compile(
                upperData,
                upperDim,
                engine.getMinHeight()
        );
        long seedOffset = upperDim.getLoadKey().hashCode();
        RNG rng = new RNG(engine.getSeedManager().getComplex() ^ seedOffset);
        double fluidHeight = upperDim.getFluidHeight();
        int cacheSize = IrisSettings.get().getPerformance().getNoiseCacheSize();
        DataProvider dataProvider = () -> upperData;

        Map<IrisInterpolator, Set<IrisGenerator>> generators = new HashMap<>();
        Set<IrisBiome> allBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<IrisBiome, IrisComplex.ChildSelectionPlan> childSelectionPlans =
                Collections.synchronizedMap(new IdentityHashMap<>());
        upperDim.getRegions().forEach(regionKey -> {
            IrisRegion region = upperData.getRegionLoader().load(regionKey);
            if (region != null) {
                region.getAllBiomes(dataProvider).forEach(biome -> registerBiomeGenerators(
                        biome, dataProvider, allBiomes, generators));
            }
        });
        for (IrisRegion mappedRegion : imageMapRuntime.getMappedRegions()) {
            mappedRegion.getAllBiomes(dataProvider).forEach(biome -> registerBiomeGenerators(
                    biome, dataProvider, allBiomes, generators));
        }
        for (IrisBiome mappedBiome : imageMapRuntime.getMappedBiomes()) {
            registerBiomeGenerators(mappedBiome, dataProvider, allBiomes, generators);
        }

        IrisComplex.GeneratorGroup[] generatorGroups = IrisComplex.freezeGeneratorGroups(generators);
        Map<IrisInterpolator, IdentityHashMap<IrisBiome, NoiseBounds>> generatorBounds = new HashMap<>();
        for (IrisComplex.GeneratorGroup group : generatorGroups) {
            IdentityHashMap<IrisBiome, NoiseBounds> interpolatorBounds = new IdentityHashMap<>(Math.max(allBiomes.size(), 16));
            for (IrisBiome biome : allBiomes) {
                double min = 0D;
                double max = 0D;
                for (IrisGenerator gen : group.generators()) {
                    String key = gen.getLoadKey();
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    max += biome.getGenLinkMax(key, engine);
                    min += biome.getGenLinkMin(key, engine);
                }
                interpolatorBounds.put(biome, new NoiseBounds(min, max));
            }
            generatorBounds.put(group.interpolator(), interpolatorBounds);
        }

        ProceduralStream<Double> regionStyleStream = upperDim.getRegionStyle()
                .create(rng.nextParallelRNG(883), upperData).stream()
                .zoom(upperDim.getRegionZoom());
        ProceduralStream<IrisRegion> proceduralRegionStream = regionStyleStream
                .selectRarity(upperData.getRegionLoader().loadAll(upperDim.getRegions()));
        ProceduralStream<IrisRegion> regionStream = proceduralRegionStream
                .convertAware2D((region, x, z) -> mappedRegion(imageMapRuntime, region, x, z))
                .cache2D("upperImageMappedRegionStream", engine, cacheSize);

        ProceduralStream<IrisBiome> landBiomeStream = regionStream
                .convert(r -> upperDim.getLandBiomeStyle()
                        .create(rng.nextParallelRNG(InferredType.LAND.ordinal()), upperData).stream()
                        .zoom(upperDim.getBiomeZoom())
                        .zoom(upperDim.getLandZoom())
                        .zoom(r.getLandBiomeZoom())
                        .selectRarity(loadInferredBiomes(upperData, r.getLandBiomes(), InferredType.LAND)))
                .convertAware2D(ProceduralStream::get);
        ProceduralStream<IrisBiome> seaBiomeStream = regionStream
                .convert(r -> upperDim.getSeaBiomeStyle()
                        .create(rng.nextParallelRNG(InferredType.SEA.ordinal()), upperData).stream()
                        .zoom(upperDim.getBiomeZoom())
                        .zoom(upperDim.getSeaZoom())
                        .zoom(r.getSeaBiomeZoom())
                        .selectRarity(loadInferredBiomes(upperData, r.getSeaBiomes(), InferredType.SEA)))
                .convertAware2D(ProceduralStream::get);
        ProceduralStream<IrisBiome> shoreBiomeStream = regionStream
                .convert(r -> upperDim.getShoreBiomeStyle()
                        .create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), upperData).stream()
                        .zoom(upperDim.getBiomeZoom())
                        .zoom(r.getShoreBiomeZoom())
                        .selectRarity(loadInferredBiomes(upperData, r.getShoreBiomes(), InferredType.SHORE)))
                .convertAware2D(ProceduralStream::get);

        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new HashMap<>();
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);

        ProceduralStream<InferredType> bridgeStream = ContinentGenerator.create(upperDim, rng, upperData).stream()
                .convert(v -> v >= upperDim.getLandChance() ? InferredType.SEA : InferredType.LAND);

        ProceduralStream<IrisBiome> proceduralBaseBiomeStream = bridgeStream
                .convertAware2D((t, x, z) -> {
                    ProceduralStream<IrisBiome> stream = inferredStreams.get(t);
                    return stream != null ? stream.get(x, z) : inferredStreams.get(InferredType.LAND).get(x, z);
                })
                .convertAware2D((biome, x, z) -> implode(
                        biome, x, z, rng, dataProvider, childSelectionPlans, 3));
        ProceduralStream<IrisBiome> baseBiomeStream = proceduralBaseBiomeStream
                .convertAware2D((biome, x, z) -> mappedBiome(imageMapRuntime, biome, x, z))
                .cache2D("upperImageMappedBaseBiomeStream", engine, cacheSize);

        KList<IrisShapedGeneratorStyle> overlayNoise = upperDim.getOverlayNoise();
        ProceduralStream<Double> overlayStream = overlayNoise.isEmpty()
                ? ProceduralStream.ofDouble((x, z) -> 0.0D)
                : ProceduralStream.ofDouble((x, z) -> {
            double value = 0D;
            for (IrisShapedGeneratorStyle style : overlayNoise) {
                value += style.get(rng, upperData, x, z);
            }
            return value;
        });

        long heightSeed = engine.getSeedManager().getHeight() ^ seedOffset;

        ProceduralStream<Double> heightStream = ProceduralStream.of((x, z) -> {
            IrisBiome b = baseBiomeStream.get(x, z);
            if (b == null) {
                return mappedTerrainHeight(imageMapRuntime, fluidHeight, x, z);
            }
            double interpolatedHeight = 0;
            for (IrisComplex.GeneratorGroup group : generatorGroups) {
                IrisInterpolator interpolator = group.interpolator();
                IrisGenerator[] groupGenerators = group.generators();
                if (groupGenerators.length == 0) {
                    continue;
                }
                IdentityHashMap<IrisBiome, NoiseBounds> cachedBounds = generatorBounds.get(interpolator);
                NoiseBounds sampledBounds = interpolator.interpolateBounds(x, z, (xx, zz) -> {
                    try {
                        IrisBiome bx = baseBiomeStream.get(xx, zz);
                        if (bx == null) {
                            return ZERO_NOISE_BOUNDS;
                        }
                        NoiseBounds bounds = cachedBounds != null ? cachedBounds.get(bx) : null;
                        if (bounds != null) {
                            return bounds;
                        }
                        double bMin = 0D;
                        double bMax = 0D;
                        for (IrisGenerator gen : groupGenerators) {
                            String key = gen.getLoadKey();
                            if (key == null || key.isBlank()) {
                                continue;
                            }
                            bMax += bx.getGenLinkMax(key, engine);
                            bMin += bx.getGenLinkMin(key, engine);
                        }
                        return new NoiseBounds(bMin, bMax);
                    } catch (Throwable e) {
                        IrisLogging.reportError(e);
                        return ZERO_NOISE_BOUNDS;
                    }
                });
                double hi = sampledBounds.max();
                double lo = sampledBounds.min();
                interpolatedHeight += IrisComplex.averageGeneratorHeights(
                        groupGenerators,
                        lo,
                        hi,
                        x,
                        z,
                        heightSeed + 239945
                );
            }
            double proceduralHeight = Math.max(
                    Math.min(interpolatedHeight + fluidHeight + overlayStream.get(x, z), chunkHeight),
                    0D
            );
            return mappedTerrainHeight(imageMapRuntime, proceduralHeight, x, z);
        }, Interpolated.DOUBLE).cache2DDouble("upperImageMappedHeightStream", engine, cacheSize);

        ProceduralStream<IrisBiome> finalBiomeStream = heightStream.convertAware2D((height, x, z) -> {
            IrisBiome mappedBiome = imageMapRuntime.sampleBiome(x, z);
            if (mappedBiome != null) {
                return mappedBiome;
            }
            IrisBiome baseBiome = baseBiomeStream.get(x, z);
            IrisBiome resolved = IrisComplex.resolveSurfaceBiome(
                    height,
                    baseBiome,
                    regionStream.get(x, z),
                    x,
                    z,
                    fluidHeight,
                    landBiomeStream,
                    seaBiomeStream,
                    shoreBiomeStream);
            return resolved == baseBiome
                    ? baseBiome
                    : implode(resolved, x, z, rng, dataProvider, childSelectionPlans, 3);
        }).cache2D("upperImageMappedFinalBiomeStream", engine, cacheSize);

        return new UpperDimensionContext(new ContextState(
                upperDim,
                upperData,
                chunkHeight,
                heightStream,
                finalBiomeStream,
                regionStream,
                imageMapRuntime,
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

    private static KList<IrisBiome> loadInferredBiomes(IrisData data, KList<String> keys, InferredType type) {
        KList<IrisBiome> inferred = new KList<>();
        for (IrisBiome biome : data.getBiomeLoader().loadAll(keys)) {
            inferred.add(biome.withInferredType(type));
        }
        return inferred;
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
                remainingDepth - 1);
    }

    public int getUpperSurfaceY(int x, int z) {
        double rawHeight = heightStream.get((double) x, (double) z);
        return chunkHeight - 1 - (int) Math.round(rawHeight);
    }

    public IrisBiome getUpperBiome(int x, int z) {
        return biomeStream.get((double) x, (double) z);
    }

    public IrisRegion getUpperRegion(int x, int z) {
        return regionStream == null ? null : regionStream.get((double) x, (double) z);
    }

    public PlatformBlockState getSurfaceBlock(int x, int z) {
        return imageMapRuntime.sampleSurfaceBlock(x, z);
    }

    public IrisDimension getDimension() {
        return dimension;
    }

    @Override
    public IrisData getData() {
        return data;
    }

    public boolean isSelfReferencing() {
        return selfReferencing;
    }

    private record ContextState(
            IrisDimension dimension,
            IrisData data,
            int chunkHeight,
            ProceduralStream<Double> heightStream,
            ProceduralStream<IrisBiome> biomeStream,
            ProceduralStream<IrisRegion> regionStream,
            IrisImageMapRuntime imageMapRuntime,
            boolean selfReferencing
    ) {
    }
}
