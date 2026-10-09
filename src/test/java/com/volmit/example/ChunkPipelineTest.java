package com.volmit.example;

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.example.chunk.GrassModule;
import com.volmit.example.chunk.GrassSettings;
import com.volmit.example.chunk.TerrainModule;
import com.volmit.example.chunk.VeinModule;
import com.volmit.example.chunk.VeinSettings;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ChunkRunner;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.LoadedModules;
import com.volmit.host.MantleRunner;
import com.volmit.host.StageGraphBuilder;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.support.ColumnSettings;
import com.volmit.support.DeclaredSettings;
import com.volmit.support.MemoryMantle;
import com.volmit.support.TestContexts;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The chunk pipeline runs on the same graph machinery as placement; the mantle pipeline feeds it. */
class ChunkPipelineTest {
    private static final int SIZE = 4;
    private static final int HEIGHT = 8;
    private static final int CHUNK = 16;
    private static final int BOTTOM = 0;
    private static final int TOP = 384;
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static final BlockState GRASS = new BlockState("minecraft:grass_block");
    private static final ResourceId DIMENSION = ResourceId.parse("pack:dimension/overworld");
    private static final ResourceId PLAINS = ResourceId.parse("pack:biome/plains");
    private static final ResourceId SWAMP = ResourceId.parse("pack:biome/swamp");

    @Feature(id = "test:levels", name = "Levels", targets = {Target.DIMENSION, Target.BIOME})
    static final class Marker implements IrisFeature {}

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

    private static Hunk<BlockState> generate(ColumnSettings settings) {
        var blocks = new ArrayHunk<>(BlockState.class, SIZE, HEIGHT, SIZE, AIR);
        runner().generate(TestContexts.generation(Map.of(BlockState.class, blocks), settings, null), TestContexts.SEED);
        return blocks;
    }

    private static Entry<GrassSettings> grass(ResourceId biome) {
        return new Entry<>(Target.BIOME, biome, new GrassSettings());
    }

    @Test
    void decorationReadsTerrainOutputWhenConfigured() {
        var blocks = generate(ColumnSettings.everywhere(grass(PLAINS)));

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
        var blocks = generate(ColumnSettings.NONE);

        int height = TerrainModule.surfaceHeight(0, 0);
        assertEquals(TerrainModule.STONE, blocks.get(0, height - 1, 0));
    }

    @Test
    void aStageRunsWhenAnyColumnHasItsSettingsAndReadsThemPerColumn() {
        var farCorner = new ColumnSettings((x, z) -> x == CHUNK - 1 && z == CHUNK - 1 ? List.of(grass(PLAINS)) : List.of());
        var blocks = generate(farCorner);
        assertEquals(TerrainModule.STONE, blocks.get(0, TerrainModule.surfaceHeight(0, 0) - 1, 0),
                "the stage ran because one column has grass, but this column has none");

        var border = new ColumnSettings((x, z) -> x < 2 ? List.of(grass(PLAINS)) : List.of());
        blocks = generate(border);
        assertEquals(GRASS, blocks.get(1, TerrainModule.surfaceHeight(1, 0) - 1, 0));
        assertEquals(TerrainModule.STONE, blocks.get(2, TerrainModule.surfaceHeight(2, 0) - 1, 0));
    }

    @Test
    void levelsStackAndTheBodyGetsEveryDistinctEntryOfTheChunkInLevelOrder() {
        var dimension = new Entry<>(Target.DIMENSION, DIMENSION, new Marker());
        var plains = new Entry<>(Target.BIOME, PLAINS, new Marker());
        var swamp = new Entry<>(Target.BIOME, SWAMP, new Marker());
        var received = new ArrayList<List<ResourceId>>();
        var stage = Stage.chunk("test:levels/read");
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:levels", "Levels", "1"); }
            public void install(StageGraph graph) {
                graph.define(stage).when(Marker.class).run((context, entries) -> {
                    received.add(entries.stream().map(Entry::source).toList());
                    received.add(context.settings().at(Marker.class, 0, 0).stream().map(Entry::source).toList());
                });
            }
        };
        var builder = new StageGraphBuilder();
        builder.install(DiscoveredModule.builtIn(module));
        var settings = new ColumnSettings((x, z) -> List.of(dimension, x < 8 ? swamp : plains));

        new ChunkRunner(builder.resolve(Pipeline.CHUNK)).generate(TestContexts.generation(Map.of(), settings, null), TestContexts.SEED);

        assertEquals(List.of(List.of(DIMENSION, SWAMP, PLAINS), List.of(DIMENSION, SWAMP)), received,
                "the body sees every level of every column, a column read that column's levels");
    }

    @Test
    void mantleStagesWriteBeyondTheirChunkAndChunkStagesReadTheirOwnPart() {
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(new VeinModule())), Set.of(), Set.of());
        var vein = new Entry<>(Target.BIOME, PLAINS, new VeinSettings(12, 4));
        int reach = loaded.mantle().stages().getFirst().reachBlocks(new DeclaredSettings(vein));
        assertEquals(3, reach, "a 12 block vein from the centre column runs 3 blocks into the next chunk");
        var mantle = new MemoryMantle();
        var reachArea = new BlockBox(-reach, BOTTOM, -reach, CHUNK + reach, TOP, CHUNK + reach);

        new MantleRunner(loaded.mantle()).generate(VeinModule.PLAN,
                TestContexts.mantle(0, 0, ColumnSettings.everywhere(vein), mantle.within(reachArea)), TestContexts.SEED);
        assertEquals(1, mantle.get(VeinModule.ORE, 18, 4, 7), "the vein runs on into the next chunk");

        var blocks = new ArrayHunk<>(BlockState.class, CHUNK, HEIGHT, CHUNK, AIR);
        var chunkArea = new BlockBox(0, BOTTOM, 0, CHUNK, TOP, CHUNK);
        new ChunkRunner(loaded.chunk()).generate(
                TestContexts.generation(Map.of(BlockState.class, blocks), ColumnSettings.NONE, mantle.within(chunkArea)), TestContexts.SEED);
        assertEquals(VeinModule.ORE_BLOCK, blocks.get(7, 4, 7));
        assertEquals(VeinModule.ORE_BLOCK, blocks.get(15, 4, 7));
        assertEquals(AIR, blocks.get(6, 4, 7));
    }

    @Test
    void chunkStagesReadTheMantleButCannotWriteIt() {
        var stage = Stage.chunk("test:writer/work");
        var mantle = new MemoryMantle();
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:writer", "Writer", "1"); }
            public void install(StageGraph graph) {
                graph.define(stage).run(context -> context.mantle().layer(VeinModule.ORE).set(0, 0, 0, 1));
            }
        };
        var builder = new StageGraphBuilder();
        builder.install(DiscoveredModule.builtIn(module));
        var inputs = TestContexts.generation(Map.of(), ColumnSettings.NONE, mantle.within(new BlockBox(0, BOTTOM, 0, CHUNK, TOP, CHUNK)));

        var failure = assertThrows(UnsupportedOperationException.class,
                () -> new ChunkRunner(builder.resolve(Pipeline.CHUNK)).generate(inputs, TestContexts.SEED));
        assertEquals("Chunk stages read the mantle but do not write it; write it from a mantle stage (Stage.mantle),"
                + " which runs before the chunks it reaches", failure.getMessage());
        assertNull(mantle.get(VeinModule.ORE, 0, 0, 0));
    }
}
