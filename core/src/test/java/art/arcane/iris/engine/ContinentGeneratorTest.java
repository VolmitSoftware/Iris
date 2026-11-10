package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.NoiseStyle;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.mock;

public class ContinentGeneratorTest {
    private static final long WORLD_SEED = 7L;
    private static final double CONTINENT_ZOOM = 4D;
    private static final double FRACTURE_MULTIPLIER = 3D;
    private static final double CHILD_SCALE = 3D;
    private static final int CHILD_SIGNATURE = 5;
    private static final int CHILDREN_SEED_SALT = 2137;
    private static final double[][] SAMPLE_BLOCKS = {{0D, 0D}, {137D, -52D}, {-480D, 911D}, {2048D, 2048D}};

    private final IrisData packData = mock(IrisData.class);

    @Test
    public void continentMapIsTheUnscaledNoiseSampledAtZoomedOutBlocks() {
        IrisDimension dimension = dimensionWithContinentStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        CNG unscaled = dimension.getContinentalStyle()
                .createNoCache(ContinentGenerator.deriveContinentSeed(new RNG(WORLD_SEED)), packData);

        ProceduralStream<Double> continent = ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData);

        assertArrayEquals(sample(unscaled, 1D / CONTINENT_ZOOM), sample(continent, 1D), 0D);
    }

    @Test
    public void fracturesOfTheContinentMapScaleWithTheContinentZoom() {
        IrisGeneratorStyle fractured = new IrisGeneratorStyle(NoiseStyle.SIMPLEX)
                .setFracture(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(FRACTURE_MULTIPLIER));
        IrisDimension zoomed = dimensionWithContinentStyle(fractured);
        IrisDimension unzoomed = dimensionWithContinentStyle(fractured).setContinentZoom(1D);

        ProceduralStream<Double> zoomedContinent = ContinentGenerator.create(zoomed, new RNG(WORLD_SEED), packData);
        ProceduralStream<Double> unzoomedContinent = ContinentGenerator.create(unzoomed, new RNG(WORLD_SEED), packData);

        assertArrayEquals(sample(unzoomedContinent, 1D / CONTINENT_ZOOM), sample(zoomedContinent, 1D), 0D);
    }

    @Test
    public void rebuildingTheContinentMapZoomsItOnlyOnce() {
        IrisDimension dimension = dimensionWithContinentStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        double[] first = sample(ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData), 1D);

        double[] rebuilt = sample(ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData), 1D);

        assertArrayEquals(first, rebuilt, 0D);
    }

    @Test
    public void buildingTheContinentMapLeavesTheSharedStyleGeneratorUnscaled() {
        IrisDimension dimension = dimensionWithContinentStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        RNG continentSeed = ContinentGenerator.deriveContinentSeed(new RNG(WORLD_SEED));
        double[] before = sample(dimension.getContinentalStyle().create(continentSeed, packData), 1D);

        ContinentGenerator.create(dimension, new RNG(WORLD_SEED), packData);

        assertArrayEquals(before, sample(dimension.getContinentalStyle().create(continentSeed, packData), 1D), 0D);
    }

    @Test
    public void childGeneratorsOfTwoBiomesSharingAStyleAreScaledOnlyOnce() {
        IrisGeneratorStyle sharedStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        IrisBiome first = biomeWithChildStyle(sharedStyle);
        IrisBiome second = biomeWithChildStyle(sharedStyle);

        CNG firstChildren = first.getChildrenGenerator(new RNG(WORLD_SEED), CHILD_SIGNATURE, CHILD_SCALE);
        CNG secondChildren = second.getChildrenGenerator(new RNG(WORLD_SEED), CHILD_SIGNATURE, CHILD_SCALE);

        assertNotSame(firstChildren, secondChildren);
        assertArrayEquals(sample(firstChildren, 1D), sample(secondChildren, 1D), 0D);
    }

    @Test
    public void childGeneratorLeavesTheSharedStyleGeneratorUnscaled() {
        IrisGeneratorStyle sharedStyle = new IrisGeneratorStyle(NoiseStyle.SIMPLEX);
        IrisBiome biome = biomeWithChildStyle(sharedStyle);
        RNG childSeed = new RNG(WORLD_SEED).nextParallelRNG(CHILD_SIGNATURE * CHILDREN_SEED_SALT);
        double[] before = sample(sharedStyle.create(childSeed, packData), 1D);

        biome.getChildrenGenerator(new RNG(WORLD_SEED), CHILD_SIGNATURE, CHILD_SCALE);

        assertArrayEquals(before, sample(sharedStyle.create(childSeed, packData), 1D), 0D);
    }

    private IrisBiome biomeWithChildStyle(IrisGeneratorStyle childStyle) {
        IrisBiome biome = new IrisBiome().setChildStyle(childStyle);
        biome.setLoader(packData);
        return biome;
    }

    private static IrisDimension dimensionWithContinentStyle(IrisGeneratorStyle continentalStyle) {
        return new IrisDimension()
                .setContinentalStyle(continentalStyle)
                .setContinentZoom(CONTINENT_ZOOM);
    }

    private static double[] sample(CNG generator, double blockScale) {
        double[] samples = new double[SAMPLE_BLOCKS.length];
        for (int i = 0; i < SAMPLE_BLOCKS.length; i++) {
            samples[i] = generator.noise(SAMPLE_BLOCKS[i][0] * blockScale, SAMPLE_BLOCKS[i][1] * blockScale);
        }
        return samples;
    }

    private static double[] sample(ProceduralStream<Double> stream, double blockScale) {
        double[] samples = new double[SAMPLE_BLOCKS.length];
        for (int i = 0; i < SAMPLE_BLOCKS.length; i++) {
            samples[i] = stream.get(SAMPLE_BLOCKS[i][0] * blockScale, SAMPLE_BLOCKS[i][1] * blockScale);
        }
        return samples;
    }
}
