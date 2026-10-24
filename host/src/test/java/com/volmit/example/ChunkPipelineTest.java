package com.volmit.example;

import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
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
import com.volmit.host.StageTimings;
import com.volmit.hunk.Hunk;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.MantleLayer;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.support.ColumnSettings;
import com.volmit.support.DeclaredSettings;
import com.volmit.support.MemoryMantle;
import com.volmit.support.TestContexts;
import com.volmit.support.TestSetups;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
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

    @Feature(id = "test:levels", name = "Levels", key = "levels", targets = {Level.DIMENSION, Level.BIOME})
    static final class Marker implements IrisFeature {}

    private static ChunkRunner runner() {
        var graph = TestSetups.stages(new GrassModule(), new TerrainModule()).resolve(Pipeline.CHUNK);
        assertEquals(List.of("iris:terrain/surface", "example:grass/top"),
                graph.stages().stream().map(stage -> stage.stage().id().toString()).toList());
        assertTrue(graph.blockPasses().isEmpty());
        return new ChunkRunner(graph);
    }

    private static Hunk<BlockState> generate(ColumnSettings settings) {
        var blocks = new ArrayHunk<>(BlockState.class, SIZE, HEIGHT, SIZE, AIR);
        runner().generate(TestContexts.generation(blocks, settings, null), TestContexts.SEED, StageTimings.NONE);
        return blocks;
    }

    private static Entry<GrassSettings> grass(ResourceId biome) {
        return new Entry<>(Level.BIOME, biome, new GrassSettings());
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
        var dimension = new Entry<>(Level.DIMENSION, DIMENSION, new Marker());
        var plains = new Entry<>(Level.BIOME, PLAINS, new Marker());
        var swamp = new Entry<>(Level.BIOME, SWAMP, new Marker());
        var received = new ArrayList<List<ResourceId>>();
        var stage = Stage.chunk("test:levels/read");
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:levels", "Levels", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(stage).when(Marker.class).run(context -> {
                    received.add(context.configured(Marker.class).entries().stream().map(Entry::source).toList());
                    received.add(context.settings().at(Marker.class, 0, 0).entries().stream().map(Entry::source).toList());
                });
            }
        };
        var settings = new ColumnSettings((x, z) -> List.of(dimension, x < 8 ? swamp : plains));

        new ChunkRunner(TestSetups.stages(module).resolve(Pipeline.CHUNK))
                .generate(TestContexts.generation(null,settings, null), TestContexts.SEED, StageTimings.NONE);

        assertEquals(List.of(List.of(DIMENSION, SWAMP, PLAINS), List.of(DIMENSION, SWAMP)), received,
                "the body sees every level of every column, a column read that column's levels");
    }

    @Test
    void mantleStagesWriteBeyondTheirChunkAndChunkStagesReadTheirOwnPart() {
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(new VeinModule())), Map.of(), Set.of(), Set.of());
        var vein = new Entry<>(Level.BIOME, PLAINS, new VeinSettings(12, 4));
        int reach = loaded.mantle().stages().getFirst().reachBlocks(new DeclaredSettings(vein));
        assertEquals(3, reach, "a 12 block vein from the centre column runs 3 blocks into the next chunk");
        var mantle = new MemoryMantle();
        var reachArea = new BlockBox(-reach, BOTTOM, -reach, CHUNK + reach, TOP, CHUNK + reach);

        new MantleRunner(loaded.mantle()).generate(VeinModule.PLAN,
                TestContexts.mantle(0, 0, ColumnSettings.everywhere(vein), mantle.within(reachArea)), TestContexts.SEED, StageTimings.NONE);
        assertEquals(1, mantle.get(VeinModule.ORE, 18, 4, 7), "the vein runs on into the next chunk");

        var blocks = new ArrayHunk<>(BlockState.class, CHUNK, HEIGHT, CHUNK, AIR);
        var chunkArea = new BlockBox(0, BOTTOM, 0, CHUNK, TOP, CHUNK);
        new ChunkRunner(loaded.chunk()).generate(
                TestContexts.generation(blocks, ColumnSettings.NONE, mantle.within(chunkArea)), TestContexts.SEED,
                StageTimings.NONE);
        assertEquals(VeinModule.ORE_BLOCK, blocks.get(7, 4, 7));
        assertEquals(VeinModule.ORE_BLOCK, blocks.get(15, 4, 7));
        assertEquals(AIR, blocks.get(6, 4, 7));
    }

    @Test
    void chunkStagesCopyTheBlocksPlacementsPlannedButCannotPlanAny() {
        var mantle = new MemoryMantle();
        var stone = new BlockState("minecraft:stone");
        mantle.plan(3, 2, 5, stone);
        mantle.plan(4, 2, 5, AIR);
        var chunkArea = new BlockBox(0, BOTTOM, 0, CHUNK, TOP, CHUNK);
        var copy = Stage.chunk("test:copy/insert");
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:copy", "Copy", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(copy).run(context -> {
                    var blocks = context.blocks();
                    context.mantle().blocks().visit(chunkArea, (x, y, z, block) -> blocks.set(x, y, z, block));
                });
            }
        };
        var blocks = new ArrayHunk<>(BlockState.class, CHUNK, HEIGHT, CHUNK, GRASS);

        new ChunkRunner(TestSetups.stages(module).resolve(Pipeline.CHUNK)).generate(
                TestContexts.generation(blocks, ColumnSettings.NONE, mantle.within(chunkArea)), TestContexts.SEED,
                StageTimings.NONE);

        assertEquals(stone, blocks.get(3, 2, 5));
        assertEquals(AIR, blocks.get(4, 2, 5), "air a placement planned replaces what the chunk had");
        assertEquals(GRASS, blocks.get(5, 2, 5));
        assertThrows(UnsupportedOperationException.class, () -> mantle.within(chunkArea).blocks().set(0, 0, 0, stone));
    }

    @Test
    void chunkStagesReadWhereTheMantleCarvedVoidsButCannotCarveAny() {
        var mantle = new MemoryMantle();
        mantle.carve(6, 9, 2);
        var chunkArea = new BlockBox(0, BOTTOM, 0, CHUNK, TOP, CHUNK);
        var carved = new ArrayList<String>();
        var probe = Stage.chunk("test:caves/probe");
        IrisModule module = new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:caves", "Caves", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(probe).run(context -> {
                    carved.add(String.valueOf(context.mantle().isCarved(6, 9, 2)));
                    carved.add(String.valueOf(context.mantle().isCarved(6, 10, 2)));
                });
            }
        };
        var blocks = new ArrayHunk<>(BlockState.class, CHUNK, HEIGHT, CHUNK, AIR);

        new ChunkRunner(TestSetups.stages(module).resolve(Pipeline.CHUNK)).generate(
                TestContexts.generation(blocks, ColumnSettings.NONE, mantle.within(chunkArea)), TestContexts.SEED,
                StageTimings.NONE);

        assertEquals(List.of("true", "false"), carved);
    }

    @Test
    void theRunnerReportsHowLongEachStageThatRanTook() {
        var timed = new ArrayList<String>();
        var blocks = new ArrayHunk<>(BlockState.class, SIZE, HEIGHT, SIZE, AIR);

        runner().generate(TestContexts.generation(blocks, ColumnSettings.NONE, null), TestContexts.SEED,
                (stage, nanos) -> timed.add(stage.stage().id() + (nanos >= 0 ? "" : " ran backwards")));

        assertEquals(List.of("iris:terrain/surface"), timed, "grass has no settings here, so it does not run and is not reported");
    }

    @Test
    void chunkStagesWriteTheMantleOfTheirOwnChunkOnly() {
        var mantle = new MemoryMantle();
        var aroundTheChunk = new BlockBox(-CHUNK, BOTTOM, -CHUNK, 2 * CHUNK, TOP, 2 * CHUNK);
        var graph = TestSetups.stages(oreWriter((context, layer) -> layer.set(15, 3, 0, 1))).resolve(Pipeline.CHUNK);

        new ChunkRunner(graph).generate(TestContexts.generation(null,ColumnSettings.NONE, mantle.within(aroundTheChunk)),
                TestContexts.SEED, StageTimings.NONE);

        assertEquals(1, mantle.get(VeinModule.ORE, 15, 3, 0));
    }

    @Test
    void oneChunkInvocationMakesItsMantleViewsOnce() {
        var mantle = new MemoryMantle();
        var aroundTheChunk = new BlockBox(-CHUNK, BOTTOM, -CHUNK, 2 * CHUNK, TOP, 2 * CHUNK);
        var views = new ArrayList<Object>();
        var graph = TestSetups.stages(oreWriter((context, layer) -> {
            views.add(context.mantle());
            views.add(context.mantle());
            views.add(layer);
            views.add(context.mantle().layer(VeinModule.ORE));
            views.add(context.mantle().blocks());
            views.add(context.mantle().blocks());
        })).resolve(Pipeline.CHUNK);

        new ChunkRunner(graph).generate(TestContexts.generation(null,ColumnSettings.NONE, mantle.within(aroundTheChunk)),
                TestContexts.SEED, StageTimings.NONE);

        assertSame(views.get(0), views.get(1));
        assertSame(views.get(2), views.get(3));
        assertSame(views.get(4), views.get(5));
    }

    @Test
    void chunkStagesReadTheirNeighboursMantleButCannotWriteIt() {
        var mantle = new MemoryMantle();
        var aroundTheChunk = new BlockBox(-CHUNK, BOTTOM, -CHUNK, 2 * CHUNK, TOP, 2 * CHUNK);
        var read = new ArrayList<Integer>();
        var graph = TestSetups.stages(oreWriter((context, layer) -> {
            read.add(layer.get(CHUNK, 0, 0));
            layer.set(CHUNK, 0, 0, 1);
        })).resolve(Pipeline.CHUNK);
        var inputs = TestContexts.generation(null,ColumnSettings.NONE, mantle.within(aroundTheChunk));

        var failure = assertThrows(UnsupportedOperationException.class,
                () -> new ChunkRunner(graph).generate(inputs, TestContexts.SEED, StageTimings.NONE));
        assertEquals("A chunk stage writes the mantle of its own chunk only; write around it from a mantle stage"
                + " (Stage.mantle), which runs before the chunks it reaches; 16,0,0 is outside BlockBox[minX=0, minY=0,"
                + " minZ=0, maxX=16, maxY=384, maxZ=16]", failure.getMessage());
        assertEquals(Collections.singletonList(null), read, "the neighbour is read before the write fails");
        assertNull(mantle.get(VeinModule.ORE, CHUNK, 0, 0));
    }

    private static IrisModule oreWriter(BiConsumer<ChunkStageContext, MantleLayer<Integer>> body) {
        var stage = Stage.chunk("test:writer/work");
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of("test:writer", "Writer", "1"); }
            public void setup(ModuleSetup setup) {
                setup.stages().define(stage).run(context -> body.accept(context, context.mantle().layer(VeinModule.ORE)));
            }
        };
    }
}
