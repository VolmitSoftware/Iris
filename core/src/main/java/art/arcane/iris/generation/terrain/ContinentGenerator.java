package art.arcane.iris.generation.terrain;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

public final class ContinentGenerator {
    private static final int CONTINENT_SEED_SALT = 234234565;

    private ContinentGenerator() {
    }

    public static CNG create(IrisDimension dimension, RNG parentSeedSource, IrisData packData) {
        return createScaledContinentalStyleGenerator(dimension, deriveContinentSeed(parentSeedSource), packData);
    }

    private static CNG createScaledContinentalStyleGenerator(IrisDimension dimension, RNG continentSeed, IrisData packData) {
        return dimension.getContinentalStyle().createScaledGenerator(continentSeed, packData, inverseContinentZoom(dimension));
    }

    static RNG deriveContinentSeed(RNG parentSeedSource) {
        return parentSeedSource.nextParallelRNG(CONTINENT_SEED_SALT);
    }

    private static double inverseContinentZoom(IrisDimension dimension) {
        return 1D / dimension.getContinentZoom();
    }
}
