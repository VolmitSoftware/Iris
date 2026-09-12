package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.Objects;

public record BiomeEnvironment(long activationId, IrisBiome biome, IrisRegion region,
                               IrisDimension dimension, IrisData data) {
    public BiomeEnvironment {
        if (activationId < 0) {
            throw new IllegalArgumentException("Biome activation cannot be negative.");
        }
        Objects.requireNonNull(biome, "biome");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(data, "data");
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
