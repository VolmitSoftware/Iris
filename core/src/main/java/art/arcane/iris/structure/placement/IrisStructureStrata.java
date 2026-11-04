package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.decoration.IrisOreBands;
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
    private final RNG rng;
    private final int offsetY;
    private final int bedrockFloor;
    private final boolean hideOres;

    private IrisStructureStrata(Engine engine) {
        complex = Objects.requireNonNull(engine.getComplex(), "Structure strata require a terrain complex");
        dimension = engine.getDimension();
        data = engine.getData();
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
                surface + 1 - bedrockFloor, surface, data, FLAT_GROUND);
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
        private final IrisOreBands biomeSurface;
        private final IrisOreBands regionSurface;
        private final IrisOreBands dimensionSurface;
        private final IrisOreBands biomeUnderground;
        private final IrisOreBands regionUnderground;
        private final IrisOreBands dimensionUnderground;

        private StrataColumn(int x, int z, int surface, boolean submerged, KList<NativeBlockState> layers,
                             NativeBlockState mapped, NativeBlockState rock, IrisBiome biome, IrisRegion region) {
            this.x = x;
            this.z = z;
            this.surface = surface;
            this.submerged = submerged;
            this.layers = layers;
            this.mapped = mapped;
            this.rock = rock;
            biomeSurface = hideOres ? IrisOreBands.EMPTY : biome.getSurfaceOreBands();
            regionSurface = hideOres ? IrisOreBands.EMPTY : region.getSurfaceOreBands();
            dimensionSurface = hideOres ? IrisOreBands.EMPTY : dimension.getSurfaceOreBands();
            biomeUnderground = hideOres ? IrisOreBands.EMPTY : biome.getUndergroundOreBands();
            regionUnderground = hideOres ? IrisOreBands.EMPTY : region.getUndergroundOreBands();
            dimensionUnderground = hideOres ? IrisOreBands.EMPTY : dimension.getUndergroundOreBands();
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

        private NativeBlockState ore(IrisOreBands first, IrisOreBands second, IrisOreBands third, int internalY) {
            NativeBlockState ore = null;
            if (first.contains(internalY)) {
                ore = first.generate(x, internalY, z, rng, data, data == null ? null : data.getEngine());
            }
            if (ore == null && second.contains(internalY)) {
                ore = second.generate(x, internalY, z, rng, data, data == null ? null : data.getEngine());
            }
            if (ore == null && third.contains(internalY)) {
                ore = third.generate(x, internalY, z, rng, data, data == null ? null : data.getEngine());
            }
            return ore;
        }
    }
}
