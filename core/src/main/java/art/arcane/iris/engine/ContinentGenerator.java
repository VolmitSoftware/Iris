package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;

public final class ContinentGenerator {
    private static final int CONTINENT_SEED_SALT = 234234565;

    private ContinentGenerator() {
    }

    public static CNG create(IrisDimension dimension, RNG parentSeedSource, IrisData packData) {
        return dimension.getContinentalStyle()
                .createScaledGenerator(deriveContinentSeed(parentSeedSource), packData, inverseContinentZoom(dimension));
    }

    static RNG deriveContinentSeed(RNG parentSeedSource) {
        return parentSeedSource.nextParallelRNG(CONTINENT_SEED_SALT);
    }

    private static double inverseContinentZoom(IrisDimension dimension) {
        return 1D / dimension.getContinentZoom();
    }
}
