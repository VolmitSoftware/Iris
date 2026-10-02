package art.arcane.iris.generation.terrain;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

public final class ContinentGenerator {
    private static final int CONTINENT_SEED_SALT = 234234565;

    private ContinentGenerator() {
    }

    public static CNG create(IrisDimension dimension, RNG parentSeedSource, IrisData packData) {
        return scaleByContinentZoom(createContinentalStyleGenerator(dimension, parentSeedSource, packData), dimension);
    }

    private static CNG createContinentalStyleGenerator(IrisDimension dimension, RNG parentSeedSource, IrisData packData) {
        return dimension.getContinentalStyle().createNoCache(deriveContinentSeed(parentSeedSource), packData);
    }

    static RNG deriveContinentSeed(RNG parentSeedSource) {
        return parentSeedSource.nextParallelRNG(CONTINENT_SEED_SALT);
    }

    private static CNG scaleByContinentZoom(CNG generator, IrisDimension dimension) {
        return generator.bake().scale(1D / dimension.getContinentZoom()).bake();
    }
}
