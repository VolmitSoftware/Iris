package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.cache.LazyBoundedCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

final class IrisBiomeLayerHeightCache {
    private final LazyBoundedCache<GeneratorKey, KList<CNG>> generators = new LazyBoundedCache<>(32);
    private volatile CachedGenerators recent;

    KList<CNG> get(KList<IrisBiomePaletteLayer> layers, int seedOffset, RNG rng, IrisData data) {
        Engine engine = data == null ? null : data.getEngine();
        long seed = rng.getSeed();
        CachedGenerators cached = recent;
        if (cached != null && cached.key.matches(data, engine, seed)) {
            return cached.generators;
        }

        GeneratorKey key = new GeneratorKey(data, engine, seed);
        KList<CNG> result = generators.computeIfAbsent(key, ignored -> create(layers, seedOffset, rng, data));
        recent = new CachedGenerators(key, result);
        return result;
    }

    private static KList<CNG> create(KList<IrisBiomePaletteLayer> layers, int seedOffset, RNG rng, IrisData data) {
        KList<CNG> result = new KList<>();
        int offset = seedOffset;
        for (IrisBiomePaletteLayer layer : layers) {
            result.add(layer.getHeightGenerator(rng.nextParallelRNG((offset++) * offset * offset * offset), data));
        }
        return result;
    }

    private record CachedGenerators(GeneratorKey key, KList<CNG> generators) {
    }

    private static final class GeneratorKey {
        private final IrisData data;
        private final Engine engine;
        private final long seed;

        private GeneratorKey(IrisData data, Engine engine, long seed) {
            this.data = data;
            this.engine = engine;
            this.seed = seed;
        }

        private boolean matches(IrisData data, Engine engine, long seed) {
            return this.data == data && this.engine == engine && this.seed == seed;
        }

        @Override
        public boolean equals(Object object) {
            return this == object || object instanceof GeneratorKey other
                    && matches(other.data, other.engine, other.seed);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            return 31 * result + Long.hashCode(seed);
        }
    }
}
