package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacementEdit;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.Pipeline;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.stage.StageRef;
import com.volmit.support.MemoryEdit;
import com.volmit.support.TestContexts;
import com.volmit.support.TestTerrain;
import com.volmit.world.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class StageGraphTest {
    private static final BlockState STONE = new BlockState("minecraft:stone");

    @Feature(id = "test:toggle", name = "Toggle", field = "toggle", targets = FeatureAttachment.Target.OBJECT_PLACEMENT)
    static final class Toggle implements IrisFeature {}

    static final class Unannotated implements IrisFeature {}

    private static IrisModule module(String id, Consumer<StageGraph> install) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "1"); }
            public void install(StageGraph graph) { install.accept(graph); }
        };
    }

    private static ResolvedGraph resolve(Pipeline pipeline, IrisModule... modules) {
        var builder = new StageGraphBuilder();
        for (var module : modules) builder.install(DiscoveredModule.builtIn(module));
        return builder.resolve(pipeline);
    }

    private static List<String> order(ResolvedGraph graph) {
        return graph.stages().stream().map(stage -> stage.stage().id().toString()).toList();
    }

    private static List<List<String>> passes(ResolvedGraph graph) {
        return graph.blockPasses().stream().map(pass -> pass.stream().map(ResourceId::toString).toList()).toList();
    }

    private static PlacementRequest twoBlockRequest() {
        var object = new GeneratedObject(new BlockBox(0, 0, 0, 2, 1, 1), new BlockPosition(0, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(0, 0, 0), STONE), new ObjectBlock(new BlockPosition(1, 0, 0), STONE)),
                List.of());
        return new PlacementRequest(ResourceId.parse("test:attempt"), object, new BlockPosition(0, 10, 0));
    }

    private static MemoryEdit flatEdit() {
        return new MemoryEdit(new TestTerrain((x, z) -> 5));
    }

    private static PlacementOutcome place(ResolvedGraph graph, MemoryEdit edit, ConfiguredSettings settings) {
        return new PlacementRunner(graph).place(twoBlockRequest(), edit, TestContexts.placement(), settings);
    }

    @Test
    void adjacentBlockStagesShareOnePassAndRunPerBlockInOrder() {
        var first = Stage.placementBlock("test:first");
        var second = Stage.placementBlock("test:second");
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:blocks", stages -> {
            stages.define(second).requires(first).run(context -> events.add("second@" + context.block().position().x()));
            stages.define(first).run(context -> events.add("first@" + context.block().position().x()));
        }));

        assertEquals(List.of(List.of("test:first", "test:second")), passes(graph));
        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(List.of("first@0", "second@0", "first@1", "second@1"), events);
    }

    @Test
    void requiredInvocationStageBetweenBlockStagesSplitsThePass() {
        var collect = Stage.placementBlock("test:collect", Counter.class);
        var middle = Stage.placement("test:middle", Integer.class);
        var paint = Stage.placementBlock("test:paint");
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:split", stages -> {
            stages.define(collect).accumulate(Counter::new, (context, counter) -> {
                counter.count++;
                events.add("collect");
            });
            stages.define(middle).requires(collect).produce(context -> {
                events.add("middle");
                return context.output(collect).count;
            });
            stages.define(paint).requires(middle).run(context -> events.add("paint:" + context.output(middle)));
        }));

        assertEquals(List.of(List.of("test:collect"), List.of("test:paint")), passes(graph));
        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(List.of("collect", "collect", "middle", "paint:2", "paint:2"), events);
    }

    @Test
    void unrelatedInvocationStagesRunOutsideBlockPassesSoTheyDoNotSplitThem() {
        var graph = resolve(Pipeline.PLACEMENT, module("test:fusion", stages -> {
            stages.define(Stage.placementBlock("test:a")).run(context -> {});
            stages.define(Stage.placement("test:b")).run(context -> {});
            stages.define(Stage.placementBlock("test:c")).run(context -> {});
        }));

        assertEquals(List.of("test:b", "test:a", "test:c"), order(graph));
        assertEquals(List.of(List.of("test:a", "test:c")), passes(graph));
    }

    @Test
    void unorderedStagesFollowLexicalIdsRegardlessOfInstallOrder() {
        var zebra = module("zeta:module", stages -> stages.define(Stage.placement("zeta:work")).run(context -> {}));
        var alpha = module("alpha:module", stages -> stages.define(Stage.placement("alpha:work")).run(context -> {}));
        var forward = resolve(Pipeline.PLACEMENT, zebra, alpha);
        var reversed = resolve(Pipeline.PLACEMENT, alpha, zebra);

        assertEquals(List.of("alpha:work", "zeta:work"), order(forward));
        assertEquals(forward.describe(), reversed.describe());
        assertEquals(List.of(new ResolvedGraph.UnorderedPair(ResourceId.parse("alpha:work"), ResourceId.parse("zeta:work"))),
                forward.unorderedPairs());
    }

    @Test
    void softEdgesOrderOnlyInstalledStagesAndReportAbsentTargets() {
        var late = Stage.placement("test:late");
        var early = Stage.placement("test:early");
        var graph = resolve(Pipeline.PLACEMENT, module("test:soft", stages -> {
            stages.define(early).softAfter(StageRef.of("test:late")).run(context -> {});
            stages.define(late).softBefore(StageRef.of("other:missing")).run(context -> {});
        }));

        assertEquals(List.of("test:late", "test:early"), order(graph));
        var earlyStage = graph.stages().get(1);
        assertEquals(List.of(new StageEdge(late.id(), early.id(), StageEdge.Kind.SOFT)), earlyStage.incoming());
        assertEquals(List.of(StageRef.of("other:missing")), graph.stages().get(0).absentSoftTargets());
        assertTrue(graph.describe().contains("absent soft targets other:missing"));
        assertTrue(graph.unorderedPairs().isEmpty());
    }

    @Test
    void missingRequirementNamesBothStagesAndTheirModules() {
        var absent = Stage.placement("other:absent");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:needy", stages -> stages.define(Stage.placement("test:needy/work")).requires(absent).run(context -> {}))));

        assertEquals("Stage test:needy/work (module test:needy) requires other:absent, which no installed module "
                + "defines (expected a module in namespace 'other')", error.getMessage());
    }

    @Test
    void requirementMustBeTheOwnersDeclarationNotACopyWithTheSameId() {
        var owned = Stage.placement("test:owned");
        var copy = Stage.placement("test:owned");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:owner", stages -> stages.define(owned).run(context -> {})),
                module("test:user", stages -> stages.define(Stage.placement("test:user/work")).requires(copy).run(context -> {}))));

        assertTrue(error.getMessage().contains("different declaration of test:owned (module test:owner)"), error.getMessage());
    }

    @Test
    void cyclesListEveryStageWithItsModuleAndEdgeKind() {
        var a = Stage.placement("test:a");
        var b = Stage.placement("other:b");
        var c = Stage.placement("test:c");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:first", stages -> {
                    stages.define(a).requires(b).run(context -> {});
                    stages.define(c).requires(a).run(context -> {});
                }),
                module("other:second", stages -> stages.define(b).softAfter(c.ref()).run(context -> {}))));

        assertEquals("Stage cycle in pipeline PLACEMENT: other:b (module other:second) -[required]-> "
                + "test:a (module test:first) -[required]-> test:c (module test:first) -[soft]-> "
                + "other:b (module other:second)", error.getMessage());
    }

    @Test
    void stageNamespaceMustMatchItsModuleAndDefinitionsAreUnique() {
        var foreign = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:module", stages -> stages.define(Stage.placement("iris:stolen")).run(context -> {}))));
        assertTrue(foreign.getMessage().contains("must use namespace 'test'"), foreign.getMessage());

        var shared = Stage.placement("test:shared");
        var duplicate = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:one", stages -> stages.define(shared).run(context -> {})),
                module("test:two", stages -> stages.define(shared).run(context -> {}))));
        assertTrue(duplicate.getMessage().contains("already defined by module test:one"), duplicate.getMessage());

        var module = module("test:twice", stages -> {});
        var builder = new StageGraphBuilder();
        builder.install(DiscoveredModule.builtIn(module));
        assertThrows(IllegalArgumentException.class, () -> builder.install(DiscoveredModule.builtIn(module)));
    }

    @Test
    void dependenciesCannotCrossPipelines() {
        var placementStage = Stage.placement("test:placed");
        ChunkStage<Void> chunkStage = Stage.chunk("test:chunk");
        var hard = module("test:hard", stages -> {
            stages.define(placementStage).run(context -> {});
            stages.define(chunkStage).requires(placementStage).run(context -> {});
        });
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.CHUNK, hard));
        assertTrue(error.getMessage().contains("in pipeline CHUNK depends on test:placed (module test:hard) in pipeline PLACEMENT"),
                error.getMessage());

        var soft = module("test:soft", stages -> {
            stages.define(Stage.placement("test:soft/placed")).run(context -> {});
            stages.define(Stage.chunk("test:soft/chunk")).softAfter(StageRef.of("test:soft/placed")).run(context -> {});
        });
        assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.CHUNK, soft));
    }

    @Test
    void definitionsNeedExactlyOneBodyOfTheRightKind() {
        var withOutput = Stage.placement("test:output", Integer.class);
        var withoutOutput = Stage.placement("test:plain");
        var accumulating = Stage.placementBlock("test:acc", Counter.class);

        var run = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:run", stages -> stages.define(withOutput).run(context -> {}))));
        assertTrue(run.getMessage().contains("declares output Integer; define it with produce(...)"), run.getMessage());
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:produce", stages -> stages.define(withoutOutput).produce(context -> null))));
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:accrun", stages -> stages.define(accumulating).run(context -> {}))));
        var missing = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:missing", stages -> stages.define(withoutOutput).requires())));
        assertTrue(missing.getMessage().contains("has no body"), missing.getMessage());
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT, module("test:again", stages -> {
            var definition = stages.define(withoutOutput);
            definition.run(context -> {});
            definition.requires(withOutput);
        })));
    }

    @Test
    void invalidDeclarationsFailWhileInstalling() {
        assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT, module("test:self", stages -> {
            var self = Stage.placement("test:self/work");
            stages.define(self).requires(self).run(context -> {});
        })));
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT, module("test:twice", stages -> {
            var definition = stages.define(Stage.placement("test:twice/work"));
            definition.when(Toggle.class);
            definition.when(Toggle.class);
        })));
        assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT, module("test:plain", stages ->
                stages.define(Stage.placement("test:plain/work")).when(Unannotated.class).run((context, settings) -> {}))));

        var retained = new ArrayList<StageGraph>();
        new StageGraphBuilder().install(DiscoveredModule.builtIn(module("test:retained", retained::add)));
        assertThrows(IllegalStateException.class, () -> retained.getFirst().define(Stage.placement("test:retained/late")));
    }

    @Test
    void outputsAreReadableOnlyThroughTransitiveRequirementsAndAfterTheirPass() {
        var height = Stage.placement("test:height", Integer.class);
        var offset = Stage.placement("test:offset", Integer.class);
        var counted = Stage.placementBlock("test:counted", Counter.class);
        var observed = new ArrayList<Object>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:outputs", stages -> {
            stages.define(height).produce(context -> 10);
            stages.define(offset).requires(height).produce(context -> context.output(height) + 1);
            stages.define(counted).requires(offset).accumulate(Counter::new, (context, counter) -> {
                counter.count++;
                observed.add(context.output(height));
            });
            stages.define(Stage.placementBlock("test:peek")).requires(counted).run(context -> observed.add(
                    assertThrows(IllegalStateException.class, () -> context.output(counted)).getMessage()));
            stages.define(Stage.placement("test:reader")).requires(counted).run(context -> observed.add(context.output(counted).count));
            stages.define(Stage.placement("test:snoop")).run(context -> observed.add(
                    assertThrows(IllegalStateException.class, () -> context.output(height)).getMessage()));
        }));

        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(10, observed.get(1));
        assertTrue(observed.get(2).toString().contains("before it completed"), observed.get(2).toString());
        assertTrue(observed.contains(2));
        assertTrue(observed.contains("Stage test:snoop read the output of test:height without requiring it; "
                + "add requires(test:height) to its definition"));
    }

    @Test
    void nullOutputsFailTheInvocation() {
        var graph = resolve(Pipeline.PLACEMENT, module("test:nulls", stages ->
                stages.define(Stage.placement("test:nulls/value", Integer.class)).produce(context -> null)));
        var edit = flatEdit();
        assertThrows(NullPointerException.class, () -> place(graph, edit, ConfiguredSettings.none()));
        assertEquals(MemoryEdit.Status.CLOSED, edit.status());
        assertFalse(edit.committed());
    }

    @Test
    void conditionalStagesCascadeToDependentsWithoutAnyError() {
        var toggled = Stage.placement("test:toggled", Integer.class);
        var dependent = Stage.placement("test:dependent");
        var unrelated = Stage.placement("test:unrelated");
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:cascade", stages -> {
            stages.define(toggled).when(Toggle.class).produce((context, toggle) -> {
                events.add("toggled");
                return 1;
            });
            stages.define(dependent).requires(toggled).run(context -> events.add("dependent:" + context.output(toggled)));
            stages.define(unrelated).run(context -> events.add("unrelated"));
        }));

        place(graph, flatEdit(), ConfiguredSettings.none());
        assertEquals(List.of("unrelated"), events);
        events.clear();
        place(graph, flatEdit(), ConfiguredSettings.of(new Toggle()));
        assertEquals(List.of("toggled", "dependent:1", "unrelated"), events);
    }

    @Test
    void skippedBlocksAreHiddenFromLaterStagesOfThePass() {
        var written = new ArrayList<Integer>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:skip", stages -> {
            var filter = Stage.placementBlock("test:filter");
            stages.define(filter).run(context -> {
                if (context.block().position().x() == 0) context.skip();
            });
            stages.define(Stage.placementBlock("test:write")).requires(filter).run(context -> written.add(context.block().position().x()));
        }));

        place(graph, flatEdit(), ConfiguredSettings.none());
        assertEquals(List.of(1), written);
    }

    @Test
    void rejectionStopsTheAttemptAndCommitsNothing() {
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:reject", stages -> {
            var write = Stage.placementBlock("test:write");
            stages.define(write).run(context -> {
                events.add("write");
                context.access().set(new PlacedBlock(new BlockPosition(0, 10, 0), STONE, Optional.empty()));
                context.reject("blocked");
                context.reject("ignored");
            });
            stages.define(Stage.placement("test:after")).requires(write).run(context -> events.add("after"));
        }));
        var edit = flatEdit();

        var outcome = place(graph, edit, ConfiguredSettings.none());
        assertEquals(PlacementOutcome.Status.REJECTED, outcome.status());
        assertEquals(Optional.of("blocked"), outcome.rejection());
        assertEquals(List.of("write"), events);
        assertFalse(edit.committed());
        assertEquals(MemoryEdit.Status.CLOSED, edit.status());
    }

    @Test
    void stagesGetBufferedAccessWithoutTransactionOwnershipAndViewsExpire() {
        var retained = new ArrayList<com.volmit.placement.PlacementAccess>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:access", stages ->
                stages.define(Stage.placement("test:access/write")).run(context -> {
                    assertFalse(context.access() instanceof PlacementEdit);
                    assertThrows(IllegalStateException.class, () -> ((BlockStageContext) context).block());
                    context.access().set(new PlacedBlock(new BlockPosition(1, 10, 1), STONE, Optional.empty()));
                    assertEquals(STONE, context.access().get(1, 10, 1));
                    retained.add(context.access());
                })));
        var edit = flatEdit();

        assertTrue(place(graph, edit, ConfiguredSettings.none()).isPlaced());
        assertEquals(STONE, edit.publishedAt(1, 10, 1));
        assertThrows(IllegalStateException.class, () -> retained.getFirst().get(1, 10, 1));
    }

    @Test
    void failuresDiscardBufferedWrites() {
        var graph = resolve(Pipeline.PLACEMENT, module("test:fail", stages ->
                stages.define(Stage.placement("test:fail/work")).run(context -> {
                    context.access().set(new PlacedBlock(new BlockPosition(1, 10, 1), STONE, Optional.empty()));
                    throw new IllegalStateException("boom");
                })));
        var edit = flatEdit();
        assertThrows(IllegalStateException.class, () -> place(graph, edit, ConfiguredSettings.none()));
        assertFalse(edit.committed());
        assertEquals(MemoryEdit.Status.CLOSED, edit.status());
    }

    @Test
    void runnersOnlyAcceptTheirOwnPipeline() {
        var empty = resolve(Pipeline.CHUNK);
        assertThrows(IllegalArgumentException.class, () -> new PlacementRunner(empty));
        assertThrows(IllegalArgumentException.class, () -> new ChunkRunner(resolve(Pipeline.PLACEMENT)));
        assertTrue(empty.describe().contains("CHUNK pipeline: 0 stages, 0 block passes"));
    }

    static final class Counter {
        int count;
    }
}
