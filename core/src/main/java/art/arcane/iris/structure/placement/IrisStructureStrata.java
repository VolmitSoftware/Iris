package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisOreBands;
import art.arcane.iris.generation.decoration.OreBandsRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stage.IrisTerrainNormalActuator;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStrata;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.stream.ProceduralStream;

import java.util.Objects;

public final class IrisStructureStrata implements StructureStrata {
    private static final ProceduralStream<Double> FLAT_GROUND = ProceduralStream.of((x, z) -> 0D, Interpolated.DOUBLE);

    private final IrisComplex complex;
    private final IrisDimension dimension;
    private final IrisData data;
    private final PackRuntime runtime;
    private final RNG rng;
    private final int offsetY;
    private final int bedrockFloor;
    private final boolean hideOres;

    private IrisStructureStrata(Engine engine) {
        complex = Objects.requireNonNull(engine.getComplex(), "Structure strata require a terrain complex");
        dimension = engine.getDimension();
        data = engine.getData();
        runtime = PackRuntime.of(engine, data);
        rng = new RNG(engine.getSeedManager().getTerrain());
        offsetY = engine.getMinHeight();
        bedrockFloor = dimension.isBedrock() ? 1 : 0;
        hideOres = dimension.isHideOresForHiddenOre();
    }

    public static StructureStrata world(Engine engine) {
        return new IrisStructureStrata(engine);
    }

    @Override
    public Column column(int x, int z, int surfaceY, boolean submerged) {
        long revision = ProvisionalSampling.revision();
        IrisBiome biome = complex.getTrueBiomeStream().get(x, z);
        IrisRegion region = complex.getRegionStream().get(x, z);
        NativeBlockState rock = complex.getRockStream().get(x, z);
        NativeBlockState mapped = complex.getImageMapRuntime().sampleSurfaceBlock(x, z);
        if (ProvisionalSampling.revision() != revision && !complex.isPlatformMainThread()) {
            throw new ProvisionalAnswerException("Structure strata at " + x + "," + z
                    + " read a provisional answer; structures are only fitted over final terrain.");
        }
        int surface = surfaceY - offsetY;
        KList<NativeBlockState> layers = biome.generateLayersWithSlope(dimension, x, z, rng,
                surface + 1 - bedrockFloor, surface, runtime, FLAT_GROUND);
        return new StrataColumn(x, z, surface, submerged, layers, mapped, rock, biome, region);
    }

    private final class StrataColumn implements Column {
        private final int x;
        private final int z;
        private final int surface;
        private final boolean submerged;
        private final KList<NativeBlockState> layers;
        private final NativeBlockState mapped;
        private final NativeBlockState rock;
        private final OreBandsRuntime biomeSurface;
        private final OreBandsRuntime regionSurface;
        private final OreBandsRuntime dimensionSurface;
        private final OreBandsRuntime biomeUnderground;
        private final OreBandsRuntime regionUnderground;
        private final OreBandsRuntime dimensionUnderground;

        private StrataColumn(int x, int z, int surface, boolean submerged, KList<NativeBlockState> layers,
                             NativeBlockState mapped, NativeBlockState rock, IrisBiome biome, IrisRegion region) {
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.submerged = submerged;
            this.layers = layers;
            this.mapped = mapped;
            this.rock = rock;
            biomeSurface = ores(biome.getSurfaceOreBands());
            regionSurface = ores(region.getSurfaceOreBands());
            dimensionSurface = ores(dimension.getSurfaceOreBands());
            biomeUnderground = ores(biome.getUndergroundOreBands());
            regionUnderground = ores(region.getUndergroundOreBands());
            dimensionUnderground = ores(dimension.getUndergroundOreBands());
        }

        @Override
        public NativeBlockState block(int y) {
            int internalY = y - offsetY;
            NativeBlockState ore = ore(biomeSurface, regionSurface, dimensionSurface, internalY);
            if (ore != null) {
                return ore;
            }
            int depth = surface - internalY;
            if (depth == 0 && mapped != null) {
                return submerged ? IrisTerrainNormalActuator.submergedSoil(mapped) : mapped;
            }
            if (layers.hasIndex(depth)) {
                NativeBlockState layer = layers.get(depth);
                return submerged ? IrisTerrainNormalActuator.submergedSoil(layer) : layer;
            }
            ore = ore(biomeUnderground, regionUnderground, dimensionUnderground, internalY);
            return ore != null ? ore : rock;
        }

        @Override
        public int soilDepth() {
            return layers.size();
        }

        private OreBandsRuntime ores(IrisOreBands bands) {
            return runtime.oreBands(hideOres ? IrisOreBands.EMPTY : bands, rng.getSeed());
        }

        private NativeBlockState ore(OreBandsRuntime first, OreBandsRuntime second, OreBandsRuntime third, int internalY) {
            NativeBlockState ore = null;
            if (first.contains(internalY)) {
                ore = first.generate(x, internalY, z, rng);
            }
            if (ore == null && second.contains(internalY)) {
                ore = second.generate(x, internalY, z, rng);
            }
            if (ore == null && third.contains(internalY)) {
                ore = third.generate(x, internalY, z, rng);
            }
            return ore;
        }
    }
}
