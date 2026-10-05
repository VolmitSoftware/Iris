package com.volmit.support;

import com.volmit.config.FeatureSettings;
import com.volmit.generation.GenerationContext;
import com.volmit.mantle.Mantle;
import com.volmit.object.ObjectTransformer;
import com.volmit.placement.PlacementContext;
import com.volmit.resource.PackResources;
import com.volmit.world.TerrainSampler;
import com.volmit.world.BlockView;
import java.util.Random;
import java.util.random.RandomGenerator;

/** Tests needing no platform facilities fail loudly if code starts using one. */
public final class TestContexts {
    private TestContexts() {}

    public static GenerationContext generation() {
        return new GenerationContext() {
            private final RandomGenerator random = new Random(42);
            public int originX() { return 0; }
            public int originY() { return -64; }
            public int originZ() { return 0; }
            public long worldSeed() { return 42; }
            public RandomGenerator random() { return random; }
            public PackResources resources() { throw new AssertionError("Unused resources"); }
            public TerrainSampler terrain() { throw new AssertionError("Unused terrain"); }
            public FeatureSettings settings() { throw new AssertionError("Unused settings"); }
            public Mantle mantle() { throw new AssertionError("Unused mantle"); }
        };
    }

    public static PlacementContext placement() {
        return new PlacementContext() {
            private final RandomGenerator random = new Random(42);
            public long worldSeed() { return 42; }
            public RandomGenerator random() { return random; }
            public PackResources resources() { throw new AssertionError("Unused resources"); }
            public TerrainSampler terrain() { throw new AssertionError("Unused terrain"); }
            public FeatureSettings settings() { throw new AssertionError("Unused settings"); }
            public ObjectTransformer objects() { throw new AssertionError("Unused transforms"); }
            public BlockView blocks() { throw new AssertionError("Unused blocks"); }
        };
    }
}
