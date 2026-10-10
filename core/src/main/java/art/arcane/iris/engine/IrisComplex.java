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
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.engine.object.noise.IrisInterpolator;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.noise.IrisShapedGeneratorStyle;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBiome;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

@Data
@EqualsAndHashCode(exclude = {"data", "gridBoundsCache", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "imageMapRuntime"})
@ToString(exclude = {"data", "gridBoundsCache", "frozenInterpolators", "frozenGenerators", "inferredBiomeStreams", "imageMapRuntime"})
public class IrisComplex implements DataProvider {
    private static final NoiseBounds ZERO_NOISE_BOUNDS = new NoiseBounds(0D, 0D);
    private static final AtomicLong lastBoundsFailureLog = new AtomicLong(0L);
    private static final int GRID_BOUNDS_CACHE_SIZE = 8192;
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
    @Getter(AccessLevel.NONE)
    private final transient IrisInterpolator[] frozenInterpolators;
    @Getter(AccessLevel.NONE)
    private final transient IrisGenerator[][] frozenGenerators;
    @Getter(AccessLevel.NONE)
    private final transient Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> inferredBiomeStreams;
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
    private ProceduralStream<UUID> baseBiomeIDStream;
    private ProceduralStream<IrisBiome> trueBiomeStream;
    private ProceduralStream<PlatformBiome> trueBiomeDerivativeStream;
    private ProceduralStream<Double> heightStream;
    private ProceduralStream<Integer> roundedHeighteightStream;
    private ProceduralStream<Double> maxHeightStream;
    private ProceduralStream<Double> overlayStream;
    private ProceduralStream<Double> heightFluidStream;
    private ProceduralStream<Double> slopeStream;
    private ProceduralStream<Integer> topSurfaceStream;
    private IrisBiome focusBiome;
    private IrisRegion focusRegion;
    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> generatorBounds;
    private Set<IrisBiome> generatorBiomes;
    private transient IrisImageMapRuntime imageMapRuntime;
    // Copy-on-write: reads happen per column on every burst thread; the synchronizedMap
    // monitor was taken on every HIT. Writes are once per biome and bounded, so a fresh map
    // per insert is cheap. Identity keying is load-bearing (IrisBiome is mutable/value-hashed).
    private volatile IdentityHashMap<IrisBiome, ChildSelectionPlan> childSelectionPlans = new IdentityHashMap<>();
    private final Object childSelectionPlanLock = new Object();

    public IrisComplex(Engine engine) {
        this(engine, false);
    }

    public IrisComplex(Engine engine, boolean simple) {
        int cacheSize = IrisSettings.get().getPerformance().getNoiseCacheSize();
        IrisBiome emptyBiome = new IrisBiome().setInferredType(InferredType.CAVE);
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getSeedManager().getComplex());
        this.data = engine.getData();
        imageMapRuntime = IrisImageMapRuntime.compile(engine);
        double height = engine.getMaxHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new HashMap<>();
        generatorBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        focusBiome = engine.getFocus();
        focusRegion = engine.getFocusRegion();
        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new HashMap<>();
        KList<IrisRegion> preparedRegions = new KList<>();

        if (focusBiome != null) {
            focusBiome = focusBiome.withInferredType(InferredType.LAND);
            focusRegion = findRegion(focusBiome, engine);
        }

        //@builder
        if (focusRegion != null) {
            prepareInferredBiomes(focusRegion, preparedRegions);
            focusRegion.getAllBiomes(this).forEach(this::registerGenerators);
        } else {
            engine.getDimension().getRegions().forEach(regionKey -> {
                IrisRegion region = data.getRegionLoader().load(regionKey);
                if (region == null) {
                    return;
                }
                prepareInferredBiomes(region, preparedRegions);
                region.getAllBiomes(this).forEach(this::registerGenerators);
            });
            for (IrisRegion region : imageMapRuntime.getMappedRegions()) {
                prepareInferredBiomes(region, preparedRegions);
                region.getAllBiomes(this).forEach(this::registerGenerators);
            }
            imageMapRuntime.getMappedBiomes().forEach(this::registerGenerators);
        }
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
        regionStyleStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883), getData()).stream()
                .zoom(engine.getDimension().getRegionZoom());
        regionIdentityStream = regionStyleStream.fit(Integer.MIN_VALUE, Integer.MAX_VALUE);
        ProceduralStream<IrisRegion> proceduralRegionStream = focusRegion != null ?
                ProceduralStream.of((x, z) -> focusRegion,
                        Interpolated.of(a -> 0D, a -> focusRegion))
                : regionStyleStream
                .selectRarity(data.getRegionLoader().loadAll(engine.getDimension().getRegions()))
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
        heightStream = ProceduralStream.of((x, z) -> {
            double proceduralHeight = getHeight(engine, x, z, engine.getSeedManager().getHeight());
            return imageMapRuntime.sampleTerrainHeight(x, z, proceduralHeight);
        }, Interpolated.DOUBLE).cache2DDouble("heightStream", engine, cacheSize);
        slopeStream = heightStream.slope(3)
                .cache2DDouble("slopeStream", engine, cacheSize);
        trueBiomeStream = focusBiome != null ? ProceduralStream.of((x, y) -> focusBiome, Interpolated.of(a -> 0D,
                        b -> focusBiome))
                .cache2D("trueBiomeStream-focus", engine, cacheSize) : heightStream
                .convertAware2D((h, x, z) -> {
                    IrisBiome mapped = imageMapRuntime.sampleBiome(x, z);
                    return mapped == null
                            ? fixBiomeType(h, baseBiomeStream.get(x, z), regionStream.get(x, z), x, z, fluidHeight)
                            : mapped;
                })
                .cache2D("trueBiomeStream", engine, cacheSize);
        roundedHeighteightStream = heightStream.contextInjecting(engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .round();
        trueBiomeDerivativeStream = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convert((b) -> IrisPlatforms.get().registries().biome(b.getDerivativeKey())).cache2D("trueBiomeDerivativeStream", engine, cacheSize);
        heightFluidStream = heightStream.contextInjecting(engine, (c, x, z) -> c.getHeight().getDouble(x, z))
                .max(fluidHeight).cache2DDouble("heightFluidStream", engine, cacheSize);
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
        baseBiomeIDStream = trueBiomeStream.contextInjecting(engine, (c, x, z) -> c.getBiome().get(x, z))
                .convertAware2D((b, x, z) -> {
                    UUID d = regionIDStream.get(x, z);
                    return new UUID(b.getLoadKey().hashCode() * 818223L,
                            d.hashCode());
                })
                .cache2D("", engine, cacheSize);
        //@done
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

        NoiseBounds bounds = sampleBoundsRaw(cache, engine, interpolator, generators, gx, gz);
        long packed = (((long) Float.floatToRawIntBits((float) bounds.min())) << 32) | (Float.floatToRawIntBits((float) bounds.max()) & 0xFFFFFFFFL);
        cache.gx[slot] = gx;
        cache.gz[slot] = gz;
        cache.idx[slot] = interpolatorIndex;
        cache.packed[slot] = packed;
        cache.valid[slot] = true;
        return packed;
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
            inferred.add(biome.withInferredType(type));
        }
        return inferred;
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
        double height = 0D;
        for (IrisGenerator generator : generators) {
            height += M.lerp(low, high, generator.getHeight(x, z, seed));
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
        return implode(biome, x, z, max - 1);
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
     * resets both, giving each pass the same empty-scratch semantics a fresh allocation had.
     * <p>
     * Single threaded and non reentrant by contract, matching the thread local sample caches in
     * IrisInterpolation that this provider is invoked through. If a nested pass ever does appear,
     * {@link #isInUse()} makes the caller fall back to a freshly allocated sampler.
     */
    private static final class BoundsSampler implements NoiseBoundsProvider {
        private final CoordinateBiomeCache sampleCache = new CoordinateBiomeCache(64);
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
            sampleCache.clear();

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
     * Open addressed biome memo keyed on the packed coordinate bits, replacing a linear scan that was
     * quadratic in the number of columns a wide starcast touches. Single threaded by contract.
     */
    private static final class CoordinateBiomeCache {
        private long[] xBits;
        private long[] zBits;
        private IrisBiome[] values;
        private byte[] states;
        private int mask;
        private int resizeThreshold;
        private int size;

        private CoordinateBiomeCache(int initialCapacity) {
            int minimumCapacity = Math.max(8, initialCapacity);
            int tableSize = tableSizeFor((minimumCapacity << 1) + minimumCapacity);
            xBits = new long[tableSize];
            zBits = new long[tableSize];
            values = new IrisBiome[tableSize];
            states = new byte[tableSize];
            mask = tableSize - 1;
            resizeThreshold = Math.max(1, (tableSize * 3) >> 2);
            size = 0;
        }

        private void clear() {
            if (size == 0) {
                return;
            }

            Arrays.fill(states, (byte) 0);
            size = 0;
        }

        private IrisBiome get(double x, double z) {
            int slot = findSlot(Double.doubleToLongBits(x), Double.doubleToLongBits(z));
            return states[slot] == 0 ? null : values[slot];
        }

        private void put(double x, double z, IrisBiome biome) {
            long xb = Double.doubleToLongBits(x);
            long zb = Double.doubleToLongBits(z);
            int slot = findSlot(xb, zb);
            boolean occupied = states[slot] != 0;
            xBits[slot] = xb;
            zBits[slot] = zb;
            values[slot] = biome;
            states[slot] = 1;

            if (occupied) {
                return;
            }

            size++;
            if (size >= resizeThreshold) {
                grow();
            }
        }

        private int findSlot(long xb, long zb) {
            int slot = mix(xb, zb) & mask;
            while (states[slot] != 0) {
                if (xBits[slot] == xb && zBits[slot] == zb) {
                    break;
                }
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        private int mix(long xb, long zb) {
            long hash = xb * 0x9E3779B97F4A7C15L;
            hash ^= Long.rotateLeft(zb * 0xC2B2AE3D27D4EB4FL, 32);
            hash ^= (hash >>> 33);
            hash *= 0xff51afd7ed558ccdL;
            hash ^= (hash >>> 33);
            return (int) hash;
        }

        private void grow() {
            long[] previousXBits = xBits;
            long[] previousZBits = zBits;
            IrisBiome[] previousValues = values;
            byte[] previousStates = states;

            int nextLength = previousXBits.length << 1;
            xBits = new long[nextLength];
            zBits = new long[nextLength];
            values = new IrisBiome[nextLength];
            states = new byte[nextLength];
            mask = nextLength - 1;
            resizeThreshold = Math.max(1, (nextLength * 3) >> 2);
            size = 0;

            for (int i = 0; i < previousStates.length; i++) {
                if (previousStates[i] == 0) {
                    continue;
                }

                int slot = findSlot(previousXBits[i], previousZBits[i]);
                xBits[slot] = previousXBits[i];
                zBits[slot] = previousZBits[i];
                values[slot] = previousValues[i];
                states[slot] = 1;
                size++;
            }
        }

        private int tableSizeFor(int value) {
            int n = value - 1;
            n |= n >>> 1;
            n |= n >>> 2;
            n |= n >>> 4;
            n |= n >>> 8;
            n |= n >>> 16;
            int tableSize = n + 1;
            if (tableSize < 8) {
                return 8;
            }
            return tableSize;
        }
    }

    static final class ChildSelectionPlan {
        private final IrisBiome[] mappedBiomes;
        private final int maxIndex;

        private ChildSelectionPlan(IrisBiome[] mappedBiomes) {
            this.mappedBiomes = mappedBiomes;
            this.maxIndex = mappedBiomes.length - 1;
        }

        static ChildSelectionPlan create(KList<IrisBiome> options) {
            if (options.isEmpty()) {
                return new ChildSelectionPlan(new IrisBiome[0]);
            }

            int maxRarity = 1;
            for (IrisBiome biome : options) {
                if (biome != null && biome.getRarity() > maxRarity) {
                    maxRarity = biome.getRarity();
                }
            }

            int rarityMax = maxRarity + 1;
            boolean flip = false;
            KList<IrisBiome> mapped = new KList<>();
            for (IrisBiome biome : options) {
                if (biome == null) {
                    continue;
                }

                int rarity = Math.max(1, biome.getRarity());
                int count = rarityMax - rarity;
                for (int index = 0; index < count; index++) {
                    flip = !flip;
                    if (flip) {
                        mapped.add(biome);
                    } else {
                        mapped.add(0, biome);
                    }
                }
            }

            if (mapped.isEmpty()) {
                IrisBiome[] fallback = new IrisBiome[]{options.get(0)};
                return new ChildSelectionPlan(fallback);
            }

            IrisBiome[] mappedBiomes = mapped.toArray(new IrisBiome[0]);
            return new ChildSelectionPlan(mappedBiomes);
        }

        IrisBiome select(CNG childCell, double x, double z) {
            if (mappedBiomes.length == 0) {
                return null;
            }

            if (mappedBiomes.length == 1) {
                return mappedBiomes[0];
            }

            int selectedIndex = childCell.fit2D(0, maxIndex, x, z);
            if (selectedIndex < 0) {
                return mappedBiomes[0];
            }

            if (selectedIndex > maxIndex) {
                return mappedBiomes[maxIndex];
            }

            return mappedBiomes[selectedIndex];
        }
    }

    record GeneratorGroup(IrisInterpolator interpolator, IrisGenerator[] generators) {
    }

    public void close() {
    }

}
