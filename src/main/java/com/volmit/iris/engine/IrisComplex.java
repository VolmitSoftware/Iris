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

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.modifier.IrisCaveModifier;
import com.volmit.iris.engine.object.biome.InferredType;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.common.CaveResult;
import com.volmit.iris.engine.object.decoration.IrisDecorationPart;
import com.volmit.iris.engine.object.decoration.IrisDecorator;
import com.volmit.iris.engine.object.dimensional.IrisTerrainMode;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.engine.object.noise.IrisInterpolator;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
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

import java.util.List;
import java.util.UUID;

@Data
public class IrisComplex implements DataProvider {
    private RNG rng;
    private double fluidHeight;
    private IrisData data;
    private KList<IrisGenerator> generators;
    private static final BlockData AIR = Material.AIR.createBlockData();
    private ProceduralStream<IrisRegion> regionStream;
    private ProceduralStream<Double> regionStyleStream;
    private ProceduralStream<Double> regionIdentityStream;
    private ProceduralStream<UUID> regionIDStream;
    private ProceduralStream<Boolean> islandStream;
    private ProceduralStream<Double> islandHeightStream;
    private ProceduralStream<Double> islandDepthStream;
    private ProceduralStream<InferredType> bridgeStream;
    private ProceduralStream<IrisBiome> landBiomeStream;
    private ProceduralStream<IrisBiome> caveBiomeStream;
    private ProceduralStream<IrisBiome> seaBiomeStream;
    private ProceduralStream<IrisBiome> shoreBiomeStream;
    private ProceduralStream<IrisBiome> baseBiomeStream;
    private ProceduralStream<UUID> baseBiomeIDStream;
    private ProceduralStream<IrisBiome> trueBiomeStream;
    private ProceduralStream<IrisBiome> trueBiomeStreamNoFeatures;
    private ProceduralStream<Biome> trueBiomeDerivativeStream;
    private ProceduralStream<Double> heightStream;
    private ProceduralStream<Double> heightStreamNoFeatures;
    private ProceduralStream<Double> objectChanceStream;
    private ProceduralStream<Double> maxHeightStream;
    private ProceduralStream<Double> overlayStream;
    private ProceduralStream<Double> heightFluidStream;
    private ProceduralStream<Integer> trueHeightStream;
    private ProceduralStream<Double> slopeStream;
    private ProceduralStream<Integer> islandTopStream;
    private ProceduralStream<Integer> islandBottomStream;
    private ProceduralStream<RNG> rngStream;
    private ProceduralStream<RNG> chunkRngStream;
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
            case LAKE:
            case RIVER:
            default:
                break;
        }

        return null;
    }

    public IrisComplex(Engine engine) {
        this(engine, false);
    }

    public IrisComplex(Engine engine, boolean simple) {
        int cacheSize = 131072;
        IrisBiome emptyBiome = new IrisBiome();
        UUID focusUUID = UUID.nameUUIDFromBytes("focus".getBytes());
        this.rng = new RNG(engine.getWorld().seed());
        this.data = engine.getData();
        double height = engine.getHeight();
        fluidHeight = engine.getDimension().getFluidHeight();
        generators = new KList<>();
        focus = engine.getFocus();
        KMap<InferredType, ProceduralStream<IrisBiome>> inferredStreams = new KMap<>();

        if (focus != null) {
            focus.setInferredType(InferredType.LAND);
        }

        IrisRegion focusRegion = focus != null ? findRegion(focus, engine) : null;
        RNG rng = new RNG(engine.getWorld().seed());
        //@builder
        engine.getDimension().getRegions().forEach((i) -> data.getRegionLoader().load(i)
                .getAllBiomes(this).forEach((b) -> b
                        .getGenerators()
                        .forEach((c) -> registerGenerator(c.getCachedGenerator(this)))));
        overlayStream = ProceduralStream.ofDouble((x, z) -> 0D);
        engine.getDimension().getOverlayNoise().forEach((i) -> overlayStream.add((x, z) -> i.get(rng, getData(), x, z)));
        rngStream = ProceduralStream.of((x, z) -> new RNG(((x.longValue()) << 32) | (z.longValue() & 0xffffffffL))
                .nextParallelRNG(engine.getWorld().seed()), Interpolated.RNG);
        chunkRngStream = rngStream.blockToChunkCoords();
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
                .selectRarity(engine.getDimension().getRegions(), (i) -> data.getRegionLoader().load(i))
                .convertCached((s) -> data.getRegionLoader().load(s)).cache2D(cacheSize);
        islandStream = regionStyleStream
                .seededChance(rng.nextParallelRNG(29349), 23968888888L,
                        1D / engine.getDimension().getIslandMode().getIslandChance());
        islandHeightStream = regionIdentityStream.style(rng.nextParallelRNG(330466), engine.getDimension().getIslandMode().getHeight(), data);
        islandDepthStream = engine.getDimension().getIslandMode().getIslandDepth().stream(rng.nextParallelRNG(-39578888), data);
        regionIDStream = regionIdentityStream.convertCached((i) -> new UUID(Double.doubleToLongBits(i), String.valueOf(i * 38445).hashCode() * 3245556666L));
        caveBiomeStream = regionStream.convert((r)
                -> engine.getDimension().getCaveBiomeStyle().create(rng.nextParallelRNG(InferredType.CAVE.ordinal()), getData()).stream()
                .zoom(r.getCaveBiomeZoom())
                .selectRarity(r.getCaveBiomes(), (i) -> data.getBiomeLoader().load(i))
                .onNull("")
                .convertCached((s) -> {
                    if (s.isEmpty()) {
                        return emptyBiome;
                    }

                    return data.getBiomeLoader().load(s)
                            .setInferredType(InferredType.CAVE);
                })
        ).convertAware2D(ProceduralStream::get).cache2D(cacheSize);
        inferredStreams.put(InferredType.CAVE, caveBiomeStream);
        landBiomeStream = regionStream.convert((r)
                        -> engine.getDimension().getLandBiomeStyle().create(rng.nextParallelRNG(InferredType.LAND.ordinal()), getData()).stream()
                        .zoom(r.getLandBiomeZoom())
                        .selectRarity(r.getLandBiomes(), (i) -> data.getBiomeLoader().load(i))
                        .convertCached((s) -> data.getBiomeLoader().load(s)
                                .setInferredType(InferredType.LAND))
                ).convertAware2D(ProceduralStream::get)
                .cache2D(cacheSize);
        inferredStreams.put(InferredType.LAND, landBiomeStream);
        seaBiomeStream = regionStream.convert((r)
                        -> engine.getDimension().getSeaBiomeStyle().create(rng.nextParallelRNG(InferredType.SEA.ordinal()), getData()).stream()
                        .zoom(r.getSeaBiomeZoom())
                        .selectRarity(r.getSeaBiomes(), (i) -> data.getBiomeLoader().load(i))
                        .convertCached((s) -> data.getBiomeLoader().load(s)
                                .setInferredType(InferredType.SEA))
                ).convertAware2D(ProceduralStream::get)
                .cache2D(cacheSize);
        inferredStreams.put(InferredType.SEA, seaBiomeStream);
        shoreBiomeStream = regionStream.convert((r)
                -> engine.getDimension().getShoreBiomeStyle().create(rng.nextParallelRNG(InferredType.SHORE.ordinal()), getData()).stream()
                .zoom(r.getShoreBiomeZoom())
                .selectRarity(r.getShoreBiomes(), (i) -> data.getBiomeLoader().load(i))
                .convertCached((s) -> data.getBiomeLoader().load(s)
                        .setInferredType(InferredType.SHORE))
        ).convertAware2D(ProceduralStream::get).cache2D(cacheSize);
        inferredStreams.put(InferredType.SHORE, shoreBiomeStream);
        bridgeStream = focus != null ? ProceduralStream.of((x, z) -> focus.getInferredType(),
                Interpolated.of(a -> 0D, a -> focus.getInferredType())) :
                engine.getDimension().getContinentalStyle().create(rng.nextParallelRNG(234234565), getData())
                        .bake().scale(1D / engine.getDimension().getContinentZoom()).bake().stream()
                        .convert((v) -> v >= engine.getDimension().getLandChance() ? InferredType.SEA : InferredType.LAND).cache2D(cacheSize);
        baseBiomeStream = focus != null ? ProceduralStream.of((x, z) -> focus,
                Interpolated.of(a -> 0D, a -> focus)) :
                bridgeStream.convertAware2D((t, x, z) -> inferredStreams.get(t).get(x, z))
                        .convertAware2D(this::implode).cache2D(cacheSize);
        heightStream = ProceduralStream.of((x, z) -> {
            IrisBiome b = focus != null ? focus : baseBiomeStream.get(x, z);
            return getHeight(engine, b, x, z, engine.getWorld().seed(), true);
        }, Interpolated.DOUBLE).clamp(0, engine.getHeight()).cache2D(cacheSize);
        heightStreamNoFeatures = ProceduralStream.of((x, z) -> {
            IrisBiome b = focus != null ? focus : baseBiomeStream.get(x, z);
            return getHeight(engine, b, x, z, engine.getWorld().seed(), false);
        }, Interpolated.DOUBLE).clamp(0, engine.getHeight()).cache2D(cacheSize);
        slopeStream = heightStream.slope(3).cache2D(cacheSize);
        objectChanceStream = ProceduralStream.ofDouble((x, z) -> {
            if (engine.getDimension().hasFeatures(engine)) {
                AtomicDouble str = new AtomicDouble(1D);
                for (IrisFeaturePositional i : engine.getFramework().getEngineParallax().forEachFeature(x, z)) {
                    str.set(Math.min(str.get(), i.getObjectChanceModifier(x, z, rng, getData())));
                }

                return str.get();
            }

            return 1D;
        });
        trueBiomeStream = focus != null ? ProceduralStream.of((x, y) -> focus, Interpolated.of(a -> 0D,
                        b -> focus)).convertAware2D((b, x, z) -> {
                    for (IrisFeaturePositional i : engine.getFramework().getEngineParallax().forEachFeature(x, z)) {
                        IrisBiome bx = i.filter(x, z, b, rng);

                        if (bx != null) {
                            bx.setInferredType(b.getInferredType());
                            return bx;
                        }
                    }

                    return b;
                })
                .cache2D(cacheSize) : heightStream
                .convertAware2D((h, x, z) ->
                        fixBiomeType(h, baseBiomeStream.get(x, z),
                                regionStream.get(x, z), x, z, fluidHeight))
                .convertAware2D((b, x, z) -> {
                    for (IrisFeaturePositional i : engine.getFramework().getEngineParallax().forEachFeature(x, z)) {
                        IrisBiome bx = i.filter(x, z, b, rng);

                        if (bx != null) {
                            bx.setInferredType(b.getInferredType());
                            return bx;
                        }
                    }

                    return b;
                })
                .cache2D(cacheSize);
        trueBiomeStream = focus != null ? ProceduralStream.of((x, y) -> focus, Interpolated.of(a -> 0D,
                        b -> focus)).convertAware2D((b, x, z) -> {
                    for (IrisFeaturePositional i : engine.getFramework().getEngineParallax().forEachFeature(x, z)) {
                        IrisBiome bx = i.filter(x, z, b, rng);

                        if (bx != null) {
                            bx.setInferredType(b.getInferredType());
                            return bx;
                        }
                    }

                    return b;
                })
                .cache2D(cacheSize) : heightStream
                .convertAware2D((h, x, z) ->
                        fixBiomeType(h, baseBiomeStream.get(x, z),
                                regionStream.get(x, z), x, z, fluidHeight))
                .convertAware2D((b, x, z) -> {
                    for (IrisFeaturePositional i : engine.getFramework().getEngineParallax().forEachFeature(x, z)) {
                        IrisBiome bx = i.filter(x, z, b, rng);

                        if (bx != null) {
                            bx.setInferredType(b.getInferredType());
                            return bx;
                        }
                    }

                    return b;
                })
                .cache2D(cacheSize);
        trueBiomeStreamNoFeatures = focus != null ? ProceduralStream.of((x, y) -> focus, Interpolated.of(a -> 0D,
                b -> focus))
                : heightStreamNoFeatures
                .convertAware2D((h, x, z) ->
                        fixBiomeType(h, baseBiomeStream.get(x, z),
                                regionStream.get(x, z), x, z, fluidHeight))
                .cache2D(cacheSize);
        trueBiomeDerivativeStream = trueBiomeStream.convert(IrisBiome::getDerivative).cache2D(cacheSize);
        heightFluidStream = heightStream.max(fluidHeight).cache2D(cacheSize);
        maxHeightStream = ProceduralStream.ofDouble((x, z) -> height);
        terrainSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D(cacheSize);
        terrainCeilingDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D(cacheSize);
        terrainCaveSurfaceDecoration = caveBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.NONE)).cache2D(cacheSize);
        terrainCaveCeilingDecoration = caveBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.CEILING)).cache2D(cacheSize);
        shoreSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SHORE_LINE)).cache2D(cacheSize);
        seaSurfaceDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_SURFACE)).cache2D(cacheSize);
        seaFloorDecoration = trueBiomeStream
                .convertAware2D((b, xx, zz) -> decorateFor(b, xx, zz, IrisDecorationPart.SEA_FLOOR)).cache2D(cacheSize);
        trueHeightStream = ProceduralStream.of((x, z) -> {
            int rx = (int) Math.round(engine.modifyX(x));
            int rz = (int) Math.round(engine.modifyZ(z));
            int heightf = (int) Math.round(getHeightStream().get(rx, rz));
            int m = heightf;

            if (engine.getDimension().isCarving() && engine.getDimension().getTerrainMode().equals(IrisTerrainMode.NORMAL)) {
                if (engine.getDimension().isCarved(getData(), rx, m, rz, ((IrisTerrainNormalActuator) engine.getFramework().getTerrainActuator()).getRng(), heightf)) {
                    m--;

                    while (engine.getDimension().isCarved(getData(), rx, m, rz, ((IrisTerrainNormalActuator) engine.getFramework().getTerrainActuator()).getRng(), heightf)) {
                        m--;
                    }
                }
            }

            if (engine.getDimension().isCaves()) {
                KList<CaveResult> caves = ((IrisCaveModifier) engine.getFramework().getCaveModifier()).genCaves(rx, rz, 0, 0, null);
                boolean again = true;

                while (again) {
                    again = false;
                    for (CaveResult i : caves) {
                        if (i.getCeiling() > m && i.getFloor() < m) {
                            m = i.getFloor();
                            again = true;
                        }
                    }
                }
            }

            return m;
        }, Interpolated.INT).cache2D(cacheSize);
        baseBiomeIDStream = trueBiomeStream.convertAware2D((b, x, z) -> {
                    UUID d = regionIDStream.get(x, z);
                    return new UUID(b.getLoadKey().hashCode() * 818223L,
                            d.hashCode());
                })
                .cache2D(cacheSize);
        islandTopStream = islandStream.convertAware2D((i, x, z) ->
                i ? heightStream.round()
                        .subtract(fluidHeight)
                        .add((xx, zz) -> getIslandHeight(xx.intValue(), zz.intValue(), engine.getDimension()
                                .getIslandMode().getIslandEdgeInterpolator()))
                        .get(x, z) : 0);
        islandBottomStream = islandStream.convertAware2D((i, x, z) ->
                i ? islandHeightStream.subtract(islandDepthStream).round().get(x, z) : 0);
        //@done
    }

    private double getIslandHeight(int x, int z, IrisInterpolator interp) {
        return interp.interpolate(x, z, (xx, zz) -> {
            if (getIslandStream().get(xx, zz)) {
                return getIslandHeightStream().get(xx, zz);
            }

            return 0;
        });
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
        RNG rngc = chunkRngStream.get(x, z);

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

    private double getHeight(Engine engine, IrisBiome b, double x, double z, long seed, boolean features) {
        double h = 0;

        for (IrisGenerator gen : generators) {
            h += gen.getInterpolator().interpolate(x, z, (xx, zz) ->
            {
                try {
                    IrisBiome bx = baseBiomeStream.get(xx, zz);

                    return M.lerp(bx.getGenLinkMin(gen.getLoadKey()),
                            bx.getGenLinkMax(gen.getLoadKey()),
                            gen.getHeight(x, z, seed + 239945));
                } catch (Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                    Iris.warn("Failed to sample hi biome at " + xx + " " + zz + " using the generator " + gen.getLoadKey());
                }

                return 0;
            });
        }

        AtomicDouble noise = new AtomicDouble(h + fluidHeight + overlayStream.get(x, z));

        if (features) {
            List<IrisFeaturePositional> p = engine.getFramework().getEngineParallax().forEachFeature(x, z);

            for (IrisFeaturePositional i : p) {
                noise.set(i.filter(x, z, noise.get(), rng, getData()));
            }
        }

        return Math.min(engine.getHeight(), Math.max(noise.get(), 0));
    }

    private void registerGenerator(IrisGenerator cachedGenerator) {
        for (IrisGenerator i : generators) {
            if (i.getLoadKey().equals(cachedGenerator.getLoadKey())) {
                return;
            }
        }

        generators.add(cachedGenerator);
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
}
