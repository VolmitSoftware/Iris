package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContinentGeneratorTest {
    private static final long WORLD_SEED = 7L;
    private static final double POWER_OF_TWO_CONTINENT_ZOOM = 4D;
    private static final double[][] SAMPLE_BLOCKS = {{1000D, -2000D}, {12.5D, -7.25D}, {-517D, 333D}};
    private static final double[] CONTINENT_MAP_OF_EXISTING_WORLDS =
            {0.21889892332042843D, 0.6984934456740721D, 0.2708759584352327D};

    private final IrisData packData = createPackDataThatBelongsToOneRunningEngine();
    private final IrisDimension baseZoomDimension = createDimensionWithContinentZoom(POWER_OF_TWO_CONTINENT_ZOOM);

    @Test
    public void doesTheContinentMapAtWorldStartMatchExistingWorlds() {
        assertArrayEquals(CONTINENT_MAP_OF_EXISTING_WORLDS, sampleContinentMapAtWorldStart(baseZoomDimension), 0D);
    }

    private double[] sampleContinentMapAtWorldStart(IrisDimension dimension) {
        return sampleContinentMap(dimension, SAMPLE_BLOCKS);
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
