package art.arcane.iris.engine;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.math.RNG;

public final class ContinentGenerator {
    private static final int CONTINENT_SEED_SALT = 234234565;

    private ContinentGenerator() {
    }

    public static ProceduralStream<Double> create(IrisDimension dimension, RNG parentSeedSource, IrisData packData) {
        return dimension.getContinentalStyle()
                .createNoCache(deriveContinentSeed(parentSeedSource), packData)
                .stream()
                .zoom(dimension.getContinentZoom());
    }

    static RNG deriveContinentSeed(RNG parentSeedSource) {
        return parentSeedSource.nextParallelRNG(CONTINENT_SEED_SALT);
    }
}
