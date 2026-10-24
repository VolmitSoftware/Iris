package com.volmit.support;

import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.generation.GenerationContext;
import com.volmit.generation.MantleGenerationContext;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.object.ObjectTransformer;
import com.volmit.placement.PlacementContext;
import com.volmit.world.TerrainSampler;
import com.volmit.world.BlockView;
import com.volmit.world.PackWorld;

/** Tests needing no platform facilities fail loudly if code starts using one. */
public final class TestContexts {
    public static final long SEED = 42;

    private TestContexts() {}

    /** The chunk at the origin; a null {@code blocks} fails when used. */
    public static GenerationContext generation(Hunk<BlockState> blocks) {
        return generation(blocks, null, null);
    }

    /** The chunk at the origin; a null {@code blocks}, {@code settings} or {@code mantle} fails when used. */
    public static GenerationContext generation(Hunk<BlockState> blocks, FeatureSettings settings, Mantle mantle) {
        return generation(blocks, settings, mantle, null);
    }

    /** The chunk at the origin in {@code world}; a null {@code blocks}, {@code settings}, {@code mantle} or {@code world}
     * fails when used.
     */
    public static GenerationContext generation(Hunk<BlockState> blocks, FeatureSettings settings, Mantle mantle, PackWorld world) {
        return new GenerationContext() {
            public int originX() { return 0; }
            public int originZ() { return 0; }
            public PackWorld world() { return used(world, "world"); }
            public TerrainSampler terrain() { throw new AssertionError("Unused terrain"); }
            public FeatureSettings settings() { return used(settings, "settings"); }
            public Mantle mantle() { return used(mantle, "mantle"); }
            public Hunk<BlockState> blocks() { return used(blocks, "blocks"); }
        };
    }

    /** The mantle invocation of the chunk whose first block is ({@code originX}, {@code originZ}). */
    public static MantleGenerationContext mantle(int originX, int originZ, FeatureSettings settings, Mantle mantle) {
        return new MantleGenerationContext() {
            public int originX() { return originX; }
            public int originZ() { return originZ; }
            public PackWorld world() { throw new AssertionError("Unused world"); }
            public TerrainSampler terrain() { throw new AssertionError("Unused terrain"); }
            public FeatureSettings settings() { return settings; }
            public Mantle mantle() { return mantle; }
        };
    }

    public static PlacementContext placement() {
        return placement(null);
    }

    public static PlacementContext placement(BlockView blocks) {
        return new PlacementContext() {
            public PackWorld world() { throw new AssertionError("Unused world"); }
            public TerrainSampler terrain() { throw new AssertionError("Unused terrain"); }
            public FeatureSettings settings() { throw new AssertionError("Unused settings"); }
            public ObjectTransformer objects() { throw new AssertionError("Unused transforms"); }
            public BlockView blocks() {
                if (blocks == null) throw new AssertionError("Unused blocks");
                return blocks;
            }
        };
    }

    private static <T> T used(T value, String name) {
        if (value == null) throw new AssertionError("Unused " + name);
        return value;
    }
}
