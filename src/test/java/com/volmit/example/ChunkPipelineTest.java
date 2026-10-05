package com.volmit.example;

import com.volmit.block.BlockState;
import com.volmit.example.chunk.GrassModule;
import com.volmit.example.chunk.GrassSettings;
import com.volmit.example.chunk.TerrainModule;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ChunkRunner;
import com.volmit.host.ConfiguredSettings;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.StageGraphBuilder;
import com.volmit.hunk.Hunk;
import com.volmit.stage.Pipeline;
import com.volmit.support.TestContexts;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** The chunk pipeline runs on the same graph machinery as placement. */
class ChunkPipelineTest {
    private static final int SIZE = 4;
    private static final int HEIGHT = 8;
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static final BlockState GRASS = new BlockState("minecraft:grass_block");

    private static ChunkRunner runner() {
        var builder = new StageGraphBuilder();
        builder.install(DiscoveredModule.builtIn(new GrassModule()));
        builder.install(DiscoveredModule.builtIn(new TerrainModule()));
        var graph = builder.resolve(Pipeline.CHUNK);
        assertEquals(List.of("iris:terrain/surface", "example:grass/top"),
                graph.stages().stream().map(stage -> stage.stage().id().toString()).toList());
        assertTrue(graph.blockPasses().isEmpty());
        return new ChunkRunner(graph);
    }

    private static Hunk<BlockState> generate(ConfiguredSettings settings) {
        var blocks = new ArrayHunk<>(BlockState.class, SIZE, HEIGHT, SIZE, AIR);
        runner().generate(TestContexts.generation(Map.of(BlockState.class, blocks)), TestContexts.SEED, settings);
        return blocks;
    }

    @Test
    void decorationReadsTerrainOutputWhenConfigured() {
        var blocks = generate(ConfiguredSettings.of(new GrassSettings()));

        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int height = TerrainModule.surfaceHeight(x, z);
                assertEquals(TerrainModule.STONE, blocks.get(x, height - 2, z));
                assertEquals(GRASS, blocks.get(x, height - 1, z));
                assertEquals(AIR, blocks.get(x, height, z));
            }
        }
    }

    @Test
    void decorationIsInactiveWithoutItsSettings() {
        var blocks = generate(ConfiguredSettings.none());

        int height = TerrainModule.surfaceHeight(0, 0);
        assertEquals(TerrainModule.STONE, blocks.get(0, height - 1, 0));
    }
}
