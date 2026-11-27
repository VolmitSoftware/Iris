package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

public final class ContinentGenerator {
    private static final int CONTINENT_SEED_SALT = 234234565;

    private ContinentGenerator() {
    }

    public static CNG create(IrisDimension dimension, RNG parentSeedSource, PackRuntime runtime) {
        return createScaledContinentalStyleGenerator(dimension, deriveContinentSeed(parentSeedSource), runtime);
    }

    private static CNG createScaledContinentalStyleGenerator(IrisDimension dimension, RNG continentSeed, PackRuntime runtime) {
        return dimension.getContinentalStyle().createScaledGenerator(continentSeed, runtime, inverseContinentZoom(dimension));
    }

    static RNG deriveContinentSeed(RNG parentSeedSource) {
        return parentSeedSource.nextParallelRNG(CONTINENT_SEED_SALT);
    }

    private static double inverseContinentZoom(IrisDimension dimension) {
        return 1D / dimension.getContinentZoom();
    }
}
