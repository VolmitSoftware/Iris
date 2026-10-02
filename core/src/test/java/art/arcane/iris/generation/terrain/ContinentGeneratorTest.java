package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.RunningEnginePackData;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.Arrays;

import static art.arcane.iris.testsupport.GeneratorSampling.SAMPLE_BLOCKS;
import static art.arcane.iris.testsupport.GeneratorSampling.sampleAtBlocks;
import static org.junit.Assert.assertArrayEquals;

public class ContinentGeneratorTest {
    private static final long WORLD_SEED = 7L;
    private static final double POWER_OF_TWO_CONTINENT_ZOOM = 4D;
    private static final double SQUARED_CONTINENT_ZOOM = POWER_OF_TWO_CONTINENT_ZOOM * POWER_OF_TWO_CONTINENT_ZOOM;
    private static final double OVERWORLD_STYLE_ZOOM = 6D;
    private static final double[][] BLOCKS_SHRUNK_BY_CONTINENT_ZOOM = shrinkByContinentZoom(SAMPLE_BLOCKS);
    private static final double[] CONTINENT_MAP_OF_EXISTING_WORLDS =
            {0.21889892332042843D, 0.6984934456740721D, 0.2708759584352327D};

    private final IrisData packData = RunningEnginePackData.create();
    private final IrisDimension baseZoomDimension = createDimensionWithContinentZoom(POWER_OF_TWO_CONTINENT_ZOOM);
    private final IrisDimension squaredZoomDimension = createDimensionWithContinentZoom(SQUARED_CONTINENT_ZOOM);
    private final IrisDimension overworldStyleDimension = createDimensionWithOverworldContinentalStyle();

    @Test
    public void doesTheContinentMapAtWorldStartMatchExistingWorlds() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAtWorldStart(baseZoomDimension), 0D);
    }

    @Test
    public void doesSquaredContinentZoomStretchTheContinentMapByTheBaseZoom() {
        assertArrayEquals(sampleBaseContinentMapStretchedByBaseZoom(),
                sampleContinentMapAtWorldStart(squaredZoomDimension), 0D);
    }

    @Test
    public void doesRebuildingTheSameWorldZoomItsContinentMapOnlyOnce() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAfterRebuild(baseZoomDimension), 0D);
    }

    @Test
    public void doesRebuildingTheSameWorldTwiceZoomItsContinentMapOnlyOnce() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAfterSecondRebuild(baseZoomDimension), 0D);
    }

    @Test
    public void doesRebuildingAWorldWithTheOverworldContinentalStyleZoomItsContinentMapOnlyOnce() {
        assertArrayEquals(sampleOverworldContinentMapAtWorldStart(), sampleContinentMapAfterRebuild(overworldStyleDimension), 0D);
    }

    @Test
    public void doesBuildingTheContinentMapLeaveTheSharedStyleGeneratorUnscaled() {
        assertArrayEquals(sampleUntouchedSharedStyleGenerator(), sampleSharedStyleGeneratorAfterContinentMapBuilt(baseZoomDimension), 0D);
    }

    private double[] sampleContinentMapAtWorldStart(IrisDimension dimension) {
        return sampleContinentMap(dimension, SAMPLE_BLOCKS);
    }

    private double[] sampleBaseContinentMapStretchedByBaseZoom() {
        return sampleContinentMap(baseZoomDimension, BLOCKS_SHRUNK_BY_CONTINENT_ZOOM);
    }

    private double[] sampleContinentMapAfterRebuild(IrisDimension dimension) {
        sampleContinentMapAtWorldStart(dimension);
        return sampleContinentMapAtWorldStart(dimension);
    }

    private double[] sampleContinentMapAfterSecondRebuild(IrisDimension dimension) {
        sampleContinentMapAtWorldStart(dimension);
        return sampleContinentMapAfterRebuild(dimension);
    }

    private double[] sampleOverworldContinentMapAtWorldStart() {
        return sampleContinentMapAtWorldStart(createDimensionWithOverworldContinentalStyle());
    }

    private double[] sampleSharedStyleGeneratorAfterContinentMapBuilt(IrisDimension dimension) {
        sampleContinentMapAtWorldStart(dimension);
        return sampleSharedStyleGenerator(dimension.getContinentalStyle());
    }

    private double[] sampleUntouchedSharedStyleGenerator() {
        return sampleSharedStyleGenerator(createSimplexStyle());
    }

    private double[] sampleSharedStyleGenerator(IrisGeneratorStyle style) {
        return sampleAtBlocks(style.create(ContinentGenerator.deriveContinentSeed(new RNG(WORLD_SEED)), packData), SAMPLE_BLOCKS);
    }

    private double[] sampleContinentMap(IrisDimension dimension, double[][] blocks) {
        return sampleAtBlocks(ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData), blocks);
    }

    private static double[][] shrinkByContinentZoom(double[][] blocks) {
        return Arrays.stream(blocks).map(block -> shrinkByContinentZoom(block)).toArray(double[][]::new);
    }

    private static double[] shrinkByContinentZoom(double[] block) {
        return new double[]{block[0] / POWER_OF_TWO_CONTINENT_ZOOM, block[1] / POWER_OF_TWO_CONTINENT_ZOOM};
    }

    private static IrisDimension createDimensionWithContinentZoom(double zoom) {
        return new IrisDimension().setContinentalStyle(createSimplexStyle()).setContinentZoom(zoom);
    }

    private static IrisGeneratorStyle createSimplexStyle() {
        return new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
    }

    private static IrisDimension createDimensionWithOverworldContinentalStyle() {
        return createDimensionWithContinentZoom(POWER_OF_TWO_CONTINENT_ZOOM).setContinentalStyle(createOverworldContinentalStyle());
    }

    private static IrisGeneratorStyle createOverworldContinentalStyle() {
        return new IrisGeneratorStyle(NoiseStyle.NOWHERE_CELLULAR).setZoom(OVERWORLD_STYLE_ZOOM).setFracture(createSmokeFracture());
    }

    private static IrisGeneratorStyle createSmokeFracture() {
        return new IrisGeneratorStyle(NoiseStyle.FRACTAL_SMOKE).setFracture(new IrisGeneratorStyle(NoiseStyle.STATIC));
    }
}
