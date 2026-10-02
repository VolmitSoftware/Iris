package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixed;
import art.arcane.iris.testsupport.ExpectedToFailUntilFixedRule;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContinentGeneratorTest {
    private static final String SHARED_STYLE_GENERATOR_BUG =
            "ContinentGenerator scales the shared style generator instead of a private one. "
                    + "Ref.: https://github.com/VolmitSoftware/Iris/issues/1251";
    private static final long WORLD_SEED = 7L;
    private static final double POWER_OF_TWO_CONTINENT_ZOOM = 4D;
    private static final double SQUARED_CONTINENT_ZOOM = POWER_OF_TWO_CONTINENT_ZOOM * POWER_OF_TWO_CONTINENT_ZOOM;
    private static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};
    private static final double[][] BLOCKS_SHRUNK_BY_CONTINENT_ZOOM = shrinkByContinentZoom(SAMPLE_BLOCKS);
    private static final double[] CONTINENT_MAP_OF_EXISTING_WORLDS =
            {0.21889892332042843D, 0.6984934456740721D, 0.2708759584352327D};

    private final IrisData packData = createPackDataThatBelongsToOneRunningEngine();
    private final IrisDimension baseZoomDimension = createDimensionWithContinentZoom(POWER_OF_TWO_CONTINENT_ZOOM);
    private final IrisDimension squaredZoomDimension = createDimensionWithContinentZoom(SQUARED_CONTINENT_ZOOM);

    @Rule
    public final ExpectedToFailUntilFixedRule expectedFailures = new ExpectedToFailUntilFixedRule();

    @Test
    public void doesTheContinentMapAtWorldStartMatchExistingWorlds() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAtWorldStart(baseZoomDimension), 0D);
    }

    @Test
    public void doesSquaredContinentZoomStretchTheContinentMapByTheBaseZoom() {
        assertArrayEquals(sampleBaseContinentMapStretchedByBaseZoom(),
                sampleContinentMapAtWorldStart(squaredZoomDimension), 0D);
    }

    @ExpectedToFailUntilFixed(SHARED_STYLE_GENERATOR_BUG)
    @Test
    public void doesRebuildingTheSameWorldZoomItsContinentMapOnlyOnce() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAfterRebuild(baseZoomDimension), 0D);
    }

    @ExpectedToFailUntilFixed(SHARED_STYLE_GENERATOR_BUG)
    @Test
    public void doesRebuildingTheSameWorldTwiceZoomItsContinentMapOnlyOnce() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAfterSecondRebuild(baseZoomDimension), 0D);
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

    private double[] sampleContinentMap(IrisDimension dimension, double[][] blocks) {
        return sampleAtBlocks(ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData), blocks);
    }

    private static double[] sampleAtBlocks(CNG continentGenerator, double[][] blocks) {
        return Arrays.stream(blocks).mapToDouble(block -> sampleAt(continentGenerator, block)).toArray();
    }

    private static double sampleAt(CNG continentGenerator, double[] block) {
        return continentGenerator.noise(block[0], block[1]);
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

    private static IrisData createPackDataThatBelongsToOneRunningEngine() {
        IrisData newPackData = mock(IrisData.class);
        bindToNewRunningEngine(newPackData);
        return newPackData;
    }

    private static void bindToNewRunningEngine(IrisData packData) {
        when(packData.getEngine()).thenReturn(mock(Engine.class));
    }
}
