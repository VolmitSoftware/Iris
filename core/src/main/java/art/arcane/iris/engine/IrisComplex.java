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

import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.*;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.common.data.DataProvider;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.util.*;

@Data
@EqualsAndHashCode(exclude = "data")
@ToString(exclude = "data")
public class IrisComplex implements DataProvider {
    private static final BlockData AIR = Material.AIR.createBlockData();
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
    private ProceduralStream<Biome> trueBiomeDerivativeStream;
    private ProceduralStream<Double> heightStream;
    private ProceduralStream<Integer> roundedHeighteightStream;
    private ProceduralStream<Double> maxHeightStream;
    private ProceduralStream<Double> overlayStream;
    private ProceduralStream<Double> heightFluidStream;
    private ProceduralStream<Double> slopeStream;
    private ProceduralStream<Integer> topSurfaceStream;
    private ProceduralStream<IrisDecorator> terrainSurfaceDecoration;
    private ProceduralStream<IrisDecorator> terrainCeilingDecoration;
    private ProceduralStream<IrisDecorator> terrainCaveSurfaceDecoration;
    private ProceduralStream<IrisDecorator> terrainCaveCeilingDecoration;
    private ProceduralStream<IrisDecorator> seaSurfaceDecoration;
    private ProceduralStream<IrisDecorator> seaFloorDecoration;
    private ProceduralStream<IrisDecorator> shoreSurfaceDecoration;
    private ProceduralStream<BlockData> rockStream;
    private ProceduralStream<BlockData> fluidStream;
    private IrisBiome focusBiome;
    private IrisRegion focusRegion;
    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> generatorBounds;
    private Set<IrisBiome> generatorBiomes;

    public IrisComplex(Engine engine) {
        this(engine, false);
    }

    public IrisComplex(Engine engine, boolean simple) {
        int cacheSize = IrisSettings.get().getPerformance().getNoiseCacheSize();
        IrisBiome emptyBiome = new IrisBiome();
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getSeedManager().getComplex());
        this.data = engine.getData();
        double height = engine.getMaxHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new HashMap<>();
        generatorBiomes = Collections.newSetFromMap(new IdentityHashMap<>());
        focusBiome = engine.getFocus();
        focusRegion = engine.getFocusRegion();
        Map<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new HashMap<>();

        if (focusBiome != null) {
            focusBiome.setInferredType(InferredType.LAND);
            focusRegion = findRegion(focusBiome, engine);
        }

        //@builder
        if (focusRegion != null) {
            focusRegion.getAllBiomes(this).forEach(this::registerGenerators);
        } else {
            engine.getDimension()
                    .getRegions()
                    .forEach(i -> data.getRegionLoader().load(i)
                            .getAllBiomes(this)
                            .forEach(this::registerGenerators));
        }
        generatorBounds = buildGeneratorBounds(engine);
        boolean legacy = engine.getDimension().isLegacyRarity();
        KList<IrisShapedGeneratorStyle> overlayNoise = engine.getDimension().getOverlayNoise();
        overlayStream = overlayNoise.isEmpty()
                ? ProceduralStream.ofDouble((x, z) -> 0.0D).waste("Overlay Stream")
                : ProceduralStream.ofDouble((x, z) -> {
            double value = 0D;

            for (IrisShapedGeneratorStyle style : overlayNoise) {
                value += style.get(rng, getData(), x, z);
            }

            return value;
        }).waste("Overlay Stream");
        rockStream = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextParallelRNG(45), data).stream()
                .select(engine.getDimension().getRockPalette().getBlockData(data)).waste("Rock Stream");
        fluidStream = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextParallelRNG(78), data).stream()
                .select(engine.getDimension().getFluidPalette().getBlockData(data)).waste("Fluid Stream");
        regionStyleStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883), getData()).stream()
                .zoom(engine.getDimension().getRegionZoom()).waste("Region Style");
        regionIdentityStream = regionStyleStream.fit(Integer.MIN_VALUE, Integer.MAX_VALUE).waste("Region Identity Stream");
        regionStream = focusRegion != null ?
                ProceduralStream.of((x, z) -> focusRegion,
                        Interpolated.of(a -> 0D, a -> focusRegion))
                : regionStyleStream
                .selectRarity(data.getRegionLoader().loadAll(engine.getDimension().getRegions()), legacy)
                .cache2D("regionStream", engine, cacheSize).waste("Region Stream");
        regionIDStream = regionIdentityStream.convertCached((i) -> new UUID(Double.doubleToLongBits(i),
                String.valueOf(i * 38445).hashCode() * 3245556666L)).waste("Region ID Stream");
        caveBiomeStream = regionStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getRegion().get(x, z))
                .convert((r)
                        -> engine.getDimension().getCaveBiomeStyle().create(rng.nextParallelRNG(InferredType.CAVE.ordinal()), getData()).stream()
                        .zoom(engine.getDimension().getBiomeZoom())
                        .zoom(r.getCaveBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getCaveBiomes()), legacy)
                        .onNull(emptyBiome)
                ).convertAware2D(ProceduralStream::get).cache2D("caveBiomeStream", engine, cacheSize).waste("Cave Biome Stream");
        inferredStreams.put(InferredType.CAVE, caveBiomeStream);
        landBiomeStream = regionStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getRegion().get(x, z))
                .convert((r)
                        -> engine.getDimension().getLandBiomeStyle().create(rng.nextParallelRNG(InferredType.LAND.ordinal()), getData()).stream()
                        .zoom(engine.getDimension().getBiomeZoom())
                        .zoom(engine.getDimension().getLandZoom())
                        .zoom(r.getLandBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getLandBiomes(), (t) -> t.setInferredType(InferredType.LAND)), legacy)
                ).convertAware2D(ProceduralStream::get)
                .cache2D("landBiomeStream", engine, cacheSize).waste("Land Biome Stream");
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        seaBiomeStream = regionStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getRegion().get(x, z))
                .convert((r)
                        -> engine.getDimension().getSeaBiomeStyle().create(rng.nextParallelRNG(InferredType.SEA.ordinal()), getData()).stream()
                        .zoom(engine.getDimension().getBiomeZoom())
                        .zoom(engine.getDimension().getSeaZoom())
                        .zoom(r.getSeaBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getSeaBiomes(), (t) -> t.setInferredType(InferredType.SEA)), legacy)
                ).convertAware2D(ProceduralStream::get)
                .cache2D("seaBiomeStream", engine, cacheSize).waste("Sea Biome Stream");
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        shoreBiomeStream = regionStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getRegion().get(x, z))
                .convert((r)
                        -> engine.getDimension().getShoreBiomeStyle().create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), getData()).stream()
                        .zoom(engine.getDimension().getBiomeZoom())
                        .zoom(r.getShoreBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getShoreBiomes(), (t) -> t.setInferredType(InferredType.SHORE)), legacy)
                ).convertAware2D(ProceduralStream::get).cache2D("shoreBiomeStream", engine, cacheSize).waste("Shore Biome Stream");
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);
        bridgeStream = focusBiome != null ? ProceduralStream.of((x, z) -> focusBiome.getInferredType(),
                Interpolated.of(a -> 0D, a -> focusBiome.getInferredType())) :
                engine.getDimension().getContinentalStyle().create(rng.nextParallelRNG(234234565), getData())
                        .bake().scale(1D / engine.getDimension().getContinentZoom()).bake().stream()
                        .convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND)
                        .cache2D("bridgeStream", engine, cacheSize).waste("Bridge Stream");
        baseBiomeStream = focusBiome != null ? ProceduralStream.of((x, z) -> focusBiome,
                Interpolated.of(a -> 0D, a -> focusBiome)) :
                bridgeStream.convertAware2D((t, x, z) -> inferredStreams.get(t).get(x, z))
                        .convertAware2D(this::implode)
                        .cache2D("baseBiomeStream", engine, cacheSize).waste("Base Biome Stream");
        heightStream = ProceduralStream.of((x, z) -> {
            IrisBiome b = focusBiome != null ? focusBiome : baseBiomeStream.get(x, z);
            return getHeight(engine, b, x, z, engine.getSeedManager().getHeight());
        }, Interpolated.DOUBLE).cache2D("heightStream", engine, cacheSize).waste("Height Stream");
        roundedHeighteightStream = heightStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getHeight().get(x, z))
                .round().waste("Rounded Height Stream");
        slopeStream = heightStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getHeight().get(x, z))
                .slope(3).cache2D("slopeStream", engine, cacheSize).waste("Slope Stream");
        trueBiomeStream = focusBiome != null ? ProceduralStream.of((x, y) -> focusBiome, Interpolated.of(a -> 0D,
                        b -> focusBiome))
                .cache2D("trueBiomeStream-focus", engine, cacheSize) : heightStream
                .convertAware2D((h, x, z) ->
                        fixBiomeType(h, baseBiomeStream.get(x, z),
                                regionStream.contextInjecting((c, xx, zz) -> IrisContext.getOr(engine).getChunkContext().getRegion().get(xx, zz)).get(x, z), x, z, fluidHeight))
                .cache2D("trueBiomeStream", engine, cacheSize).waste("True Biome Stream");
        trueBiomeDerivativeStream = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convert(IrisBiome::getDerivative).cache2D("trueBiomeDerivativeStream", engine, cacheSize).waste("True Biome Derivative Stream");
        heightFluidStream = heightStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getHeight().get(x, z))
                .max(fluidHeight).cache2D("heightFluidStream", engine, cacheSize).waste("Height Fluid Stream");
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height).waste("Max Height Stream");
        terrainSurfaceDecoration = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainSurfaceDecoration", engine, cacheSize).waste("Surface Decoration Stream");
        terrainCeilingDecoration = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCeilingDecoration", engine, cacheSize).waste("Ceiling Decoration Stream");
        terrainCaveSurfaceDecoration = caveBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getCave().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainCaveSurfaceDecoration", engine, cacheSize).waste("Cave Surface Stream");
        terrainCaveCeilingDecoration = caveBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getCave().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCaveCeilingDecoration", engine, cacheSize).waste("Cave Ceiling Stream");
        shoreSurfaceDecoration = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SHORE_LINE)).cache2D("shoreSurfaceDecoration", engine, cacheSize).waste("Shore Surface Stream");
        seaSurfaceDecoration = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_SURFACE)).cache2D("seaSurfaceDecoration", engine, cacheSize).waste("Sea Surface Stream");
        seaFloorDecoration = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_FLOOR)).cache2D("seaFloorDecoration", engine, cacheSize).waste("Sea Floor Stream");
        baseBiomeIDStream = trueBiomeStream.contextInjecting((c, x, z) -> IrisContext.getOr(engine).getChunkContext().getBiome().get(x, z))
                .convertAware2D((b, x, z) -> {
                    UUID d = regionIDStream.get(x, z);
                    return new UUID(b.getLoadKey().hashCode() * 818223L,
                            d.hashCode());
                })
                .cache2D("", engine, cacheSize).waste("Biome ID Stream");
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

            BlockData block = i.getBlockData(b, rngc, x, z, data);

            if (block != null) {
                return i;
            }
        }

        return null;
    }

    private IrisBiome fixBiomeType(Double height, IrisBiome biome, IrisRegion region, Double x, Double z, double fluidHeight) {
        double sh = region.getShoreHeight(x, z);

        if (height >= fluidHeight - 1 && height <= fluidHeight + sh && !biome.isShore()) {
            return shoreBiomeStream.get(x, z);
        }

        if (height > fluidHeight + sh && !biome.isLand()) {
            return landBiomeStream.get(x, z);
        }

        if (height < fluidHeight && !biome.isAquatic()) {
            return seaBiomeStream.get(x, z);
        }

        if (height == fluidHeight && !biome.isShore()) {
            return shoreBiomeStream.get(x, z);
        }

        return biome;
    }

    private double interpolateGenerators(Engine engine, IrisInterpolator interpolator, Set<IrisGenerator> generators, double x, double z, long seed) {
        if (generators.isEmpty()) {
            return 0;
        }

        CoordinateBiomeCache sampleCache = new CoordinateBiomeCache(64);
        IdentityHashMap<IrisBiome, GeneratorBounds> cachedBounds = generatorBounds.get(interpolator);
        IdentityHashMap<IrisBiome, GeneratorBounds> localBounds = new IdentityHashMap<>(8);
        double hi = interpolator.interpolate(x, z, (xx, zz) -> {
            try {
                IrisBiome bx = sampleCache.get(xx, zz);
                if (bx == null) {
                    bx = baseBiomeStream.get(xx, zz);
                    sampleCache.put(xx, zz, bx);
                }

                GeneratorBounds bounds = cachedBounds == null ? null : cachedBounds.get(bx);
                if (bounds == null) {
                    bounds = localBounds.get(bx);
                    if (bounds == null) {
                        bounds = computeGeneratorBounds(engine, generators, bx);
                        localBounds.put(bx, bounds);
                    }
                }

                return bounds.max;
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
                Iris.error("Failed to sample hi biome at " + xx + " " + zz + "...");
            }

            return 0;
        });

        double lo = interpolator.interpolate(x, z, (xx, zz) -> {
            try {
                IrisBiome bx = sampleCache.get(xx, zz);
                if (bx == null) {
                    bx = baseBiomeStream.get(xx, zz);
                    sampleCache.put(xx, zz, bx);
                }

                GeneratorBounds bounds = cachedBounds == null ? null : cachedBounds.get(bx);
                if (bounds == null) {
                    bounds = localBounds.get(bx);
                    if (bounds == null) {
                        bounds = computeGeneratorBounds(engine, generators, bx);
                        localBounds.put(bx, bounds);
                    }
                }

                return bounds.min;
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
                Iris.error("Failed to sample lo biome at " + xx + " " + zz + "...");
            }

            return 0;
        });

        double d = 0;

        for (IrisGenerator i : generators) {
            d += M.lerp(lo, hi, i.getHeight(x, z, seed + 239945));
        }

        return d / generators.size();
    }

    private double getInterpolatedHeight(Engine engine, double x, double z, long seed) {
        double h = 0;

        for (Map.Entry<IrisInterpolator, Set<IrisGenerator>> entry : generators.entrySet()) {
            h += interpolateGenerators(engine, entry.getKey(), entry.getValue(), x, z, seed);
        }

        return h;
    }

    private double getHeight(Engine engine, IrisBiome b, double x, double z, long seed) {
        return Math.max(Math.min(getInterpolatedHeight(engine, x, z, seed) + fluidHeight + overlayStream.get(x, z), engine.getHeight()), 0);
    }

    private void registerGenerators(IrisBiome biome) {
        generatorBiomes.add(biome);
        biome.getGenerators().forEach(c -> registerGenerator(c.getCachedGenerator(this)));
    }

    private void registerGenerator(IrisGenerator cachedGenerator) {
        generators.computeIfAbsent(cachedGenerator.getInterpolator(), (k) -> new HashSet<>()).add(cachedGenerator);
    }

    private Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> buildGeneratorBounds(Engine engine) {
        Map<IrisInterpolator, IdentityHashMap<IrisBiome, GeneratorBounds>> bounds = new HashMap<>();
        KList<IrisBiome> allBiomes = new KList<>(generatorBiomes);

        if (focusBiome != null && !allBiomes.contains(focusBiome)) {
            allBiomes.add(focusBiome);
        }

        for (Map.Entry<IrisInterpolator, Set<IrisGenerator>> entry : generators.entrySet()) {
            IdentityHashMap<IrisBiome, GeneratorBounds> interpolatorBounds = new IdentityHashMap<>(Math.max(allBiomes.size(), 16));
            for (IrisBiome biome : allBiomes) {
                interpolatorBounds.put(biome, computeGeneratorBounds(engine, entry.getValue(), biome));
            }
            bounds.put(entry.getKey(), interpolatorBounds);
        }

        return bounds;
    }

    private GeneratorBounds computeGeneratorBounds(Engine engine, Set<IrisGenerator> generators, IrisBiome biome) {
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
        KList<IrisBiome> chx = b.getRealChildren(this).copy();
        chx.add(b);
        IrisBiome biome = childCell.fitRarity(chx, x, z);
        biome.setInferredType(b.getInferredType());
        return implode(biome, x, z, max - 1);
    }

    private static class GeneratorBounds {
        private final double min;
        private final double max;

        private GeneratorBounds(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }

    private static class CoordinateBiomeCache {
        private long[] xBits;
        private long[] zBits;
        private IrisBiome[] values;
        private int size;

        private CoordinateBiomeCache(int initialSize) {
            xBits = new long[initialSize];
            zBits = new long[initialSize];
            values = new IrisBiome[initialSize];
            size = 0;
        }

        private IrisBiome get(double x, double z) {
            long xb = Double.doubleToLongBits(x);
            long zb = Double.doubleToLongBits(z);
            for (int i = 0; i < size; i++) {
                if (xBits[i] == xb && zBits[i] == zb) {
                    return values[i];
                }
            }

            return null;
        }

        private void put(double x, double z, IrisBiome biome) {
            if (size >= xBits.length) {
                grow();
            }

            xBits[size] = Double.doubleToLongBits(x);
            zBits[size] = Double.doubleToLongBits(z);
            values[size] = biome;
            size++;
        }

        private void grow() {
            int nextSize = xBits.length << 1;
            long[] nx = new long[nextSize];
            long[] nz = new long[nextSize];
            IrisBiome[] nv = new IrisBiome[nextSize];
            System.arraycopy(xBits, 0, nx, 0, size);
            System.arraycopy(zBits, 0, nz, 0, size);
            System.arraycopy(values, 0, nv, 0, size);
            xBits = nx;
            zBits = nz;
            values = nv;
        }
    }

    public void close() {

    }
}
