/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDecorationPart;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.engine.object.IrisGenerator;
import com.volmit.iris.engine.object.IrisInterpolator;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.DataProvider;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.interpolation.Interpolated;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.UUID;

@Data
public class IrisComplex implements DataProvider {
    private static final BlockData AIR = Material.AIR.createBlockData();
    private RNG rng;
    private double fluidHeight;
    private IrisData data;
    private KMap<IrisInterpolator, KSet<IrisGenerator>> generators;
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
    private IrisBiome focus;

    public IrisComplex(Engine engine) {
        this(engine, false);
    }

    public IrisComplex(Engine engine, boolean simple) {
        int cacheSize = IrisSettings.get().getPerformance().getCacheSize();
        IrisBiome emptyBiome = new IrisBiome();
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getSeedManager().getComplex());
        this.data = engine.getData();
        double height = engine.getHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new KMap<>();
        focus = engine.getFocus();
        KMap<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new KMap<>();

        if (focus != null) {
            focus.setInferredType(InferredType.LAND);
        }

        IrisRegion focusRegion = focus != null ? findRegion(focus, engine) : null;
        //@builder
        engine.getDimension().getRegions().forEach((i) -> data.getRegionLoader().load(i)
                .getAllBiomes(this).forEach((b) -> b
                        .getGenerators()
                        .forEach((c) -> registerGenerator(c.getCachedGenerator(this)))));
        overlayStream = ProceduralStream.ofDouble((x, z) -> 0D);
        engine.getDimension().getOverlayNoise().forEach((i) -> overlayStream.add((x, z) -> i.get(rng, getData(), x, z)));
        rockStream = engine.getDimension().getRockPalette().getLayerGenerator(rng.nextParallelRNG(45), data).stream()
                .select(engine.getDimension().getRockPalette().getBlockData(data));
        fluidStream = engine.getDimension().getFluidPalette().getLayerGenerator(rng.nextParallelRNG(78), data).stream()
                .select(engine.getDimension().getFluidPalette().getBlockData(data));
        regionStyleStream = engine.getDimension().getRegionStyle().create(rng.nextParallelRNG(883), getData()).stream()
                .zoom(engine.getDimension().getRegionZoom());
        regionIdentityStream = regionStyleStream.fit(Integer.MIN_VALUE, Integer.MAX_VALUE);
        regionStream = focusRegion != null ?
                ProceduralStream.of((x, z) -> focusRegion,
                        Interpolated.of(a -> 0D, a -> focusRegion))
                : regionStyleStream
                .selectRarity(data.getRegionLoader().loadAll(engine.getDimension().getRegions()))
                .cache2D("regionStream", engine, cacheSize);
        regionIDStream = regionIdentityStream.convertCached((i) -> new UUID(Double.doubleToLongBits(i), String.valueOf(i * 38445).hashCode() * 3245556666L));
        caveBiomeStream = regionStream.convert((r)
                -> engine.getDimension().getCaveBiomeStyle().create(rng.nextParallelRNG(InferredType.CAVE.ordinal()), getData()).stream()
                .zoom(r.getCaveBiomeZoom())
                .selectRarity(data.getBiomeLoader().loadAll(r.getCaveBiomes()))
                .onNull(emptyBiome)
        ).convertAware2D(ProceduralStream::get).cache2D("caveBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.CAVE, caveBiomeStream);
        landBiomeStream = regionStream.convert((r)
                        -> engine.getDimension().getLandBiomeStyle().create(rng.nextParallelRNG(InferredType.LAND.ordinal()), getData()).stream()
                        .zoom(r.getLandBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getLandBiomes(), (t) -> t.setInferredType(InferredType.LAND)))
                ).convertAware2D(ProceduralStream::get)
                .cache2D("landBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        seaBiomeStream = regionStream.convert((r)
                        -> engine.getDimension().getSeaBiomeStyle().create(rng.nextParallelRNG(InferredType.SEA.ordinal()), getData()).stream()
                        .zoom(r.getSeaBiomeZoom())
                        .selectRarity(data.getBiomeLoader().loadAll(r.getSeaBiomes(), (t) -> t.setInferredType(InferredType.SEA)))
                ).convertAware2D(ProceduralStream::get)
                .cache2D("seaBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        shoreBiomeStream = regionStream.convert((r)
                -> engine.getDimension().getShoreBiomeStyle().create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), getData()).stream()
                .zoom(r.getShoreBiomeZoom())
                .selectRarity(data.getBiomeLoader().loadAll(r.getShoreBiomes(), (t) -> t.setInferredType(InferredType.SHORE)))
        ).convertAware2D(ProceduralStream::get).cache2D("shoreBiomeStream", engine, cacheSize);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);
        bridgeStream = focus != null ? ProceduralStream.of((x, z) -> focus.getInferredType(),
                Interpolated.of(a -> 0D, a -> focus.getInferredType())) :
                engine.getDimension().getContinentalStyle().create(rng.nextParallelRNG(234234565), getData())
                        .bake().scale(1D / engine.getDimension().getContinentZoom()).bake().stream()
                        .convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND)
                        .cache2D("bridgeStream", engine, cacheSize);
        baseBiomeStream = focus != null ? ProceduralStream.of((x, z) -> focus,
                Interpolated.of(a -> 0D, a -> focus)) :
                bridgeStream.convertAware2D((t, x, z) -> inferredStreams.get(t).get(x, z))
                        .convertAware2D(this::implode)
                    .cache2D("baseBiomeStream", engine, cacheSize);
        heightStream = ProceduralStream.of((x, z) -> {
            IrisBiome b = focus != null ? focus : baseBiomeStream.get(x, z);
            return getHeight(engine, b, x, z, engine.getSeedManager().getHeight());
        }, Interpolated.DOUBLE).clamp(0, engine.getHeight()).cache2D("heightStream", engine, cacheSize);
        roundedHeighteightStream = heightStream.round();
        slopeStream = heightStream.slope(3).cache2D("slopeStream", engine, cacheSize);
        trueBiomeStream = focus != null ? ProceduralStream.of((x, y) -> focus, Interpolated.of(a -> 0D,
                        b -> focus))
                .cache2D("trueBiomeStream-focus", engine, cacheSize) : heightStream
                .convertAware2D((h, x, z) ->
                        fixBiomeType(h, baseBiomeStream.get(x, z),
                                regionStream.get(x, z), x, z, fluidHeight))
                .cache2D("trueBiomeStream", engine, cacheSize);
        trueBiomeDerivativeStream = trueBiomeStream.convert(IrisBiome::getDerivative).cache2D("trueBiomeDerivativeStream", engine, cacheSize);
        heightFluidStream = heightStream.max(fluidHeight).cache2D("heightFluidStream", engine, cacheSize);
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
        terrainSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainSurfaceDecoration", engine, cacheSize);
        terrainCeilingDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCeilingDecoration", engine, cacheSize);
        terrainCaveSurfaceDecoration = caveBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D("terrainCaveSurfaceDecoration", engine, cacheSize);
        terrainCaveCeilingDecoration = caveBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D("terrainCaveCeilingDecoration", engine, cacheSize);
        shoreSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SHORE_LINE)).cache2D("shoreSurfaceDecoration", engine, cacheSize);
        seaSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_SURFACE)).cache2D("seaSurfaceDecoration", engine, cacheSize);
        seaFloorDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_FLOOR)).cache2D("seaFloorDecoration", engine, cacheSize);
        baseBiomeIDStream = trueBiomeStream.convertAware2D((b, x, z) -> {
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
            case DEFER:
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

        return null;
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

    private double interpolateGenerators(Engine engine, IrisInterpolator interpolator, KSet<IrisGenerator> generators, double x, double z, long seed) {
        if (generators.isEmpty()) {
            return 0;
        }

        double hi = interpolator.interpolate(x, z, (xx, zz) -> {
            try {
                IrisBiome bx = baseBiomeStream.get(xx, zz);
                double b = 0;

                for (IrisGenerator gen : generators) {
                    b += bx.getGenLinkMax(gen.getLoadKey());
                }

                return b;
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
                Iris.error("Failed to sample hi biome at " + xx + " " + zz + "...");
            }

            return 0;
        });

        double lo = interpolator.interpolate(x, z, (xx, zz) -> {
            try {
                IrisBiome bx = baseBiomeStream.get(xx, zz);
                double b = 0;

                for (IrisGenerator gen : generators) {
                    b += bx.getGenLinkMin(gen.getLoadKey());
                }

                return b;
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

        for (IrisInterpolator i : generators.keySet()) {
            h += interpolateGenerators(engine, i, generators.get(i), x, z, seed);
        }

        return h;
    }

    private double getHeight(Engine engine, IrisBiome b, double x, double z, long seed) {
        return Math.min(engine.getHeight(),
                Math.max(getInterpolatedHeight(engine, x, z, seed) + fluidHeight + overlayStream.get(x, z), 0));
    }

    private void registerGenerator(IrisGenerator cachedGenerator) {
        generators.computeIfAbsent(cachedGenerator.getInterpolator(), (k) -> new KSet<>()).add(cachedGenerator);
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

    public void close() {

    }
}
