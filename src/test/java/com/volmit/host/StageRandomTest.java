package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.stage.StageContext;
import com.volmit.stage.StageGraph;
import com.volmit.support.MemoryEdit;
import com.volmit.support.TestContexts;
import com.volmit.support.TestTerrain;
import com.volmit.world.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class StageRandomTest {
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final int BLOCKS = 3;
    private static final int BOUND = 1_000_000;
    private static final int INVOCATION_DRAWS = 4;
    private static final int NOISY_DRAWS = 37;

    /** Every draw each stage made, by stage id, in the order it made them. */
    private final Map<String, List<Long>> draws = new LinkedHashMap<>();

    private static IrisModule module(String id, Consumer<StageGraph> install) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "Test module " + id, "1"); }
            public void install(StageGraph graph) { install.accept(graph); }
        };
    }

    private static ResolvedGraph resolve(Pipeline pipeline, IrisModule... modules) {
        var builder = new StageGraphBuilder();
        for (var module : modules) builder.install(DiscoveredModule.builtIn(module));
        return builder.resolve(pipeline);
    }

    private static PlacementRequest request() {
        var blocks = IntStream.range(0, BLOCKS).mapToObj(x -> new ObjectBlock(new BlockPosition(x, 0, 0), STONE)).toList();
        var object = new GeneratedObject(new BlockBox(0, 0, 0, BLOCKS, 1, 1), new BlockPosition(0, 0, 0), blocks, List.of());
        return new PlacementRequest(ResourceId.parse("test:attempt"), object, new BlockPosition(0, 10, 0));
    }

    private Map<String, List<Long>> place(ResolvedGraph graph, long invocationSeed) {
        draws.clear();
        var outcome = new PlacementRunner(graph).place(request(), new MemoryEdit(new TestTerrain((x, z) -> 5)),
                TestContexts.placement(), invocationSeed, ConfiguredSettings.none());
        assertTrue(outcome.isPlaced());
        return Map.copyOf(draws);
    }

    private void draw(String stage, StageContext context, int count) {
        var drawn = draws.computeIfAbsent(stage, ignored -> new ArrayList<>());
        for (int index = 0; index < count; index++) drawn.add((long) context.random().nextInt(BOUND));
    }

    /** One invocation stage and one block stage that draw from their streams. */
    private IrisModule drawingModule(String moduleId, int invocationDraws, int drawsPerBlock) {
        var pick = moduleId + "/pick";
        var scatter = moduleId + "/scatter";
        return module(moduleId, graph -> {
            graph.define(Stage.placement(pick)).run(context -> draw(pick, context, invocationDraws));
            graph.define(Stage.placementBlock(scatter)).run(context -> draw(scatter, context, drawsPerBlock));
        });
    }

    private static List<Long> firstDraws(long invocationSeed, String stageId, int count) {
        var stream = StageRandom.stream(invocationSeed, ResourceId.parse(stageId));
        return IntStream.range(0, count).mapToObj(ignored -> (long) stream.nextInt(BOUND)).toList();
    }

    @Test
    void insertingAModuleThatDrawsLeavesOtherStagesDrawsUnchanged() {
        var first = drawingModule("a:mod", INVOCATION_DRAWS, 1);
        var inserted = drawingModule("b:mod", NOISY_DRAWS, NOISY_DRAWS);
        var last = drawingModule("c:mod", INVOCATION_DRAWS, 1);

        var without = place(resolve(Pipeline.PLACEMENT, first, last), TestContexts.SEED);
        var with = resolve(Pipeline.PLACEMENT, first, inserted, last);
        assertEquals(List.of("a:mod/pick", "b:mod/pick", "c:mod/pick", "a:mod/scatter", "b:mod/scatter", "c:mod/scatter"),
                with.stages().stream().map(stage -> stage.stage().id().toString()).toList());
        assertEquals(1, with.blockPasses().size(), "the inserted block stage interleaves with the others per block");
        var withInserted = place(with, TestContexts.SEED);

        for (var stage : List.of("a:mod/pick", "a:mod/scatter", "c:mod/pick", "c:mod/scatter")) {
            assertEquals(without.get(stage), withInserted.get(stage), stage);
        }
    }

    @Test
    void sameStageInTheSameInvocationDrawsTheSameSequenceEveryRun() {
        var graph = resolve(Pipeline.PLACEMENT, drawingModule("a:mod", INVOCATION_DRAWS, 1));

        assertEquals(place(graph, TestContexts.SEED), place(graph, TestContexts.SEED));
        assertNotEquals(place(graph, TestContexts.SEED), place(graph, TestContexts.SEED + 1));
        assertEquals(firstDraws(TestContexts.SEED, "a:mod/pick", INVOCATION_DRAWS), place(graph, TestContexts.SEED).get("a:mod/pick"));
    }

    @Test
    void blockStageDrawsOneStreamAcrossTheBlocksOfItsPass() {
        var graph = resolve(Pipeline.PLACEMENT, drawingModule("a:mod", 0, 2));

        assertEquals(firstDraws(TestContexts.SEED, "a:mod/scatter", 2 * BLOCKS), place(graph, TestContexts.SEED).get("a:mod/scatter"));
    }

    @Test
    void differentStagesDrawFromDifferentStreams() {
        var graph = resolve(Pipeline.PLACEMENT, drawingModule("a:mod", INVOCATION_DRAWS, 0), drawingModule("c:mod", INVOCATION_DRAWS, 0));
        var drawn = place(graph, TestContexts.SEED);

        assertNotEquals(drawn.get("a:mod/pick"), drawn.get("c:mod/pick"));
        assertNotEquals(StageRandom.idHash(ResourceId.parse("a:mod/pick")), StageRandom.idHash(ResourceId.parse("c:mod/pick")));
    }

    @Test
    void chunkStagesDrawFromTheirOwnStreams() {
        var drawn = new ArrayList<Long>();
        var graph = resolve(Pipeline.CHUNK, module("a:mod", stages -> stages.define(Stage.chunk("a:mod/ores"))
                .run(context -> drawn.add((long) context.random().nextInt(BOUND)))));

        new ChunkRunner(graph).generate(TestContexts.generation(Map.of()), TestContexts.SEED, ConfiguredSettings.none());

        assertEquals(firstDraws(TestContexts.SEED, "a:mod/ores", 1), drawn);
    }

    @Test
    void streamIsOnlyAvailableWhileItsStageRuns() {
        var retained = new ArrayList<StageContext>();
        var graph = resolve(Pipeline.PLACEMENT, module("a:mod", stages -> stages.define(Stage.placement("a:mod/keep")).run(retained::add)));
        place(graph, TestContexts.SEED);

        assertThrows(IllegalStateException.class, () -> retained.getFirst().random());
    }

    /** Pins the documented derivation with values computed outside Java from its specification, so a
     * change to the derivation or to the JDK's Random fails here instead of silently changing worlds.
     */
    @Test
    void derivationMatchesItsSpecification() {
        var stage = ResourceId.parse("iris:stilt/fill");
        assertEquals(0xd11cde74d6a9508aL, StageRandom.idHash(stage), "64-bit FNV-1a of iris:stilt/fill");

        RandomGenerator stream = StageRandom.stream(TestContexts.SEED, stage);
        assertEquals(4203081479670090427L, stream.nextLong());
        assertEquals(-978353470, stream.nextInt());
        assertEquals(943449, stream.nextInt(BOUND));
    }
}
