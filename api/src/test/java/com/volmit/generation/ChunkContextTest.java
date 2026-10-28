package com.volmit.generation;

import com.volmit.config.FeatureSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChunkContextTest {
    private static final int BOTTOM = 0;
    private static final int TOP = 384;

    /** A sampler that only knows its bounds. */
    private static TerrainSampler terrainWithin(BlockBox bounds) {
        return (TerrainSampler) Proxy.newProxyInstance(TerrainSampler.class.getClassLoader(), new Class<?>[] {TerrainSampler.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("bounds")) {
                        return bounds;
                    }
                    throw new AssertionError("Unused " + method.getName());
                });
    }

    private static ChunkContext chunkAt(int originX, int originZ) {
        var terrain = terrainWithin(new BlockBox(originX - 8, BOTTOM, originZ - 8, originX + 24, TOP, originZ + 24));
        return new MantleGenerationContext() {
            public int originX() { return originX; }
            public int originZ() { return originZ; }
            public PackWorld world() { throw new AssertionError("Unused world"); }
            public TerrainSampler terrain() { return terrain; }
            public FeatureSettings settings() { throw new AssertionError("Unused settings"); }
            public Mantle mantle() { throw new AssertionError("Unused mantle"); }
            public PlacementTerrain placementTerrain() { throw new AssertionError("Unused placement terrain"); }
        };
    }

    @Test
    void namesTheChunkItsCentreColumnAndItsArea() {
        var chunk = chunkAt(32, -48);

        assertEquals(2, chunk.chunkX());
        assertEquals(-3, chunk.chunkZ());
        assertEquals(39, chunk.centreX());
        assertEquals(-41, chunk.centreZ());
        assertEquals(new BlockBox(32, BOTTOM, -48, 48, TOP, -32), chunk.area(), "the chunk's columns at the terrain's full height");
    }

    @Test
    void theChunkLeftOfTheOriginIsMinusOne() {
        var chunk = chunkAt(-16, -16);

        assertEquals(-1, chunk.chunkX());
        assertEquals(-1, chunk.chunkZ());
        assertEquals(-9, chunk.centreX());
    }
}
