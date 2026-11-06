package com.volmit.host;

import com.volmit.FeatureRegistry;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.data.Codec;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLifetime;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacementEdit;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.Pipeline;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.stage.StageRef;
import com.volmit.support.ColumnSettings;
import com.volmit.support.DeclaredSettings;
import com.volmit.support.MemoryEdit;
import com.volmit.support.MemoryMantle;
import com.volmit.support.TestContexts;
import com.volmit.support.TestSetups;
import com.volmit.support.TestTerrain;
import com.volmit.example.placement.SnowModule;
import com.volmit.example.placement.SnowSettings;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class StageGraphTest {
    private static final BlockState STONE = new BlockState("minecraft:stone");

    @Feature(id = "test:owner/toggle", name = "Toggle", key = "ownerToggle", targets = Level.PLACEMENT)
    static final class Toggle implements IrisFeature {}

    static final class Unannotated implements IrisFeature {}

    private static IrisModule module(String id, Consumer<StageGraph> install) {
        return new IrisModule() {
            public ModuleInfo info() { return ModuleInfo.of(id, "Test module " + id, "1"); }
            public void setup(ModuleSetup setup) { install.accept(setup.stages()); }
        };
    }

    private static ResolvedGraph resolve(Pipeline pipeline, IrisModule... modules) {
        return TestSetups.stages(modules).resolve(pipeline);
    }

    private static List<Class<? extends IrisFeature>> resolveSettings(IrisModule... modules) {
        return TestSetups.stages(modules).settingsTypes(type -> false);
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
        return new PlacementRequest(object,new BlockPosition(0, 10, 0));
    }

    private static MemoryEdit flatEdit() {
        return new MemoryEdit(new TestTerrain((x, z) -> 5));
    }

    private static PlacementOutcome place(ResolvedGraph graph, MemoryEdit edit, ConfiguredSettings settings) {
        return place(graph, edit, settings, StageTimings.NONE);
    }

    private static PlacementOutcome place(ResolvedGraph graph, MemoryEdit edit, ConfiguredSettings settings, StageTimings timings) {
        return new PlacementRunner(graph).place(twoBlockRequest(), edit, TestContexts.placement(), TestContexts.SEED, settings, timings);
    }

    @Test
    void adjacentBlockStagesShareOnePassAndRunPerBlockInOrder() {
        var first = Stage.placementBlock("test:blocks/first");
        var second = Stage.placementBlock("test:blocks/second");
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:blocks", stages -> {
            stages.define(second).requires(first).run(context -> events.add("second@" + context.block().position().x()));
            stages.define(first).run(context -> events.add("first@" + context.block().position().x()));
        }));

        assertEquals(List.of(List.of("test:blocks/first", "test:blocks/second")), passes(graph));
        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(List.of("first@0", "second@0", "first@1", "second@1"), events);
    }

    @Test
    void requiredInvocationStageBetweenBlockStagesSplitsThePass() {
        var collect = Stage.placementBlock("test:split/collect", Counter.class);
        var middle = Stage.placement("test:split/middle", Integer.class);
        var paint = Stage.placementBlock("test:split/paint");
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

        assertEquals(List.of(List.of("test:split/collect"), List.of("test:split/paint")), passes(graph));
        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(List.of("collect", "collect", "middle", "paint:2", "paint:2"), events);
    }

    @Test
    void unrelatedInvocationStagesRunOutsideBlockPassesSoTheyDoNotSplitThem() {
        var graph = resolve(Pipeline.PLACEMENT, module("test:fusion", stages -> {
            stages.define(Stage.placementBlock("test:fusion/a")).run(context -> {});
            stages.define(Stage.placement("test:fusion/b")).run(context -> {});
            stages.define(Stage.placementBlock("test:fusion/c")).run(context -> {});
        }));

        assertEquals(List.of("test:fusion/b", "test:fusion/a", "test:fusion/c"), order(graph));
        assertEquals(List.of(List.of("test:fusion/a", "test:fusion/c")), passes(graph));
    }

    @Test
    void unorderedStagesFollowLexicalIdsRegardlessOfInstallOrder() {
        var zebra = module("zeta:module", stages -> stages.define(Stage.placement("zeta:module/work")).run(context -> {}));
        var alpha = module("alpha:module", stages -> stages.define(Stage.placement("alpha:module/work")).run(context -> {}));
        var forward = resolve(Pipeline.PLACEMENT, zebra, alpha);
        var reversed = resolve(Pipeline.PLACEMENT, alpha, zebra);

        assertEquals(List.of("alpha:module/work", "zeta:module/work"), order(forward));
        assertEquals(forward.describe(), reversed.describe());
        assertEquals(List.of(new ResolvedGraph.UnorderedPair(ResourceId.parse("alpha:module/work"), ResourceId.parse("zeta:module/work"))),
                forward.unorderedPairs());
    }

    @Test
    void softEdgesOrderOnlyInstalledStagesAndReportAbsentTargets() {
        var late = Stage.placement("test:soft/late");
        var early = Stage.placement("test:soft/early");
        var graph = resolve(Pipeline.PLACEMENT, module("test:soft", stages -> {
            stages.define(early).softAfter(StageRef.of("test:soft/late")).run(context -> {});
            stages.define(late).softBefore(StageRef.of("other:gone/missing")).run(context -> {});
        }));

        assertEquals(List.of("test:soft/late", "test:soft/early"), order(graph));
        var earlyStage = graph.stages().get(1);
        assertEquals(List.of(new StageEdge(late.id(), early.id(), StageEdge.Kind.SOFT)), earlyStage.incoming());
        assertEquals(List.of(StageRef.of("other:gone/missing")), graph.stages().get(0).absentSoftTargets());
        assertTrue(graph.describe().contains("absent soft targets other:gone/missing"));
        assertTrue(graph.unorderedPairs().isEmpty());
    }

    @Test
    void softEdgesTakeTheStageConstantLikeItsId() {
        var late = Stage.placement("test:soft/late");
        var early = Stage.placement("test:soft/early");
        var last = Stage.placement("test:soft/last");
        var graph = resolve(Pipeline.PLACEMENT, module("test:soft", stages -> {
            stages.define(early).softAfter(late).run(context -> {});
            stages.define(last).run(context -> {});
            stages.define(late).softBefore(last).run(context -> {});
        }));

        assertEquals(List.of("test:soft/late", "test:soft/early", "test:soft/last"), order(graph));
        assertEquals(List.of(new StageEdge(late.id(), early.id(), StageEdge.Kind.SOFT)), graph.stages().get(1).incoming());
    }

    @Test
    void missingRequirementNamesBothStagesAndTheirModules() {
        var absent = Stage.placement("other:gone/absent");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:needy", stages -> stages.define(Stage.placement("test:needy/work")).requires(absent).run(context -> {}))));

        assertEquals("Stage test:needy/work (module test:needy) requires other:gone/absent, which no installed module "
                + "defines (expected a module in namespace 'other')", error.getMessage());
    }

    @Test
    void requirementMustBeTheOwnersDeclarationNotACopyWithTheSameId() {
        var owned = Stage.placement("test:owner/owned");
        var copy = Stage.placement("test:owner/owned");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:owner", stages -> stages.define(owned).run(context -> {})),
                module("test:user", stages -> stages.define(Stage.placement("test:user/work")).requires(copy).run(context -> {}))));

        assertTrue(error.getMessage().contains("different declaration of test:owner/owned (module test:owner)"), error.getMessage());
    }

    @Test
    void cyclesListEveryStageWithItsModuleAndEdgeKind() {
        var a = Stage.placement("test:first/a");
        var b = Stage.placement("other:second/b");
        var c = Stage.placement("test:first/c");
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:first", stages -> {
                    stages.define(a).requires(b).run(context -> {});
                    stages.define(c).requires(a).run(context -> {});
                }),
                module("other:second", stages -> stages.define(b).softAfter(c).run(context -> {}))));

        assertEquals("Stage cycle in pipeline PLACEMENT: other:second/b (module other:second) -[required]-> "
                + "test:first/a (module test:first) -[required]-> test:first/c (module test:first) -[soft]-> "
                + "other:second/b (module other:second)", error.getMessage());
    }

    @Test
    void stageIdsMustStartWithTheirModuleIdAndDefinitionsAreUnique() {
        var foreign = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:module", stages -> stages.define(Stage.placement("iris:stolen/work")).run(context -> {}))));
        assertEquals("Stage iris:stolen/work must start with test:module/, the id of its defining module test:module "
                + "from host classpath", foreign.getMessage());

        for (var outside : List.of("test:other/work", "test:module", "test:modules/work")) {
            var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                    module("test:module", stages -> stages.define(Stage.placement(outside)).run(context -> {}))));
            assertTrue(error.getMessage().contains("must start with test:module/"), error.getMessage());
        }

        var shared = Stage.placement("test:one/shared");
        var duplicate = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:one", stages -> {
                    stages.define(shared).run(context -> {});
                    stages.define(shared).run(context -> {});
                })));
        assertTrue(duplicate.getMessage().contains("already defined by module test:one"), duplicate.getMessage());

        var module = module("test:twice", stages -> {});
        var builder = TestSetups.stages(module);
        assertThrows(IllegalArgumentException.class, () -> TestSetups.install(builder, DiscoveredModule.builtIn(module)));
    }

    @Test
    void settingsMustBeOwnedByAModuleThatNamesThem() {
        var foreign = assertThrows(IllegalArgumentException.class, () -> resolveSettings(
                module("other:user", stages -> stages.define(Stage.placement("other:user/work")).when(Toggle.class).run(context -> {}))));
        assertEquals("Settings type " + Toggle.class.getName() + " has feature id test:owner/toggle, which none of the modules "
                + "naming it in when(...) owns: other:user from host classpath. A feature id is the id of the module that owns "
                + "the settings, such as other:user, or starts with it and '/', such as other:user/toggle", foreign.getMessage());

        var sameNamespace = assertThrows(IllegalArgumentException.class, () -> resolveSettings(
                module("test:user", stages -> stages.define(Stage.placement("test:user/work")).when(Toggle.class).run(context -> {}))));
        assertTrue(sameNamespace.getMessage().contains("none of the modules naming it in when(...) owns: test:user from host classpath"),
                sameNamespace.getMessage());

        var shared = resolveSettings(
                module("test:owner", stages -> stages.define(Stage.placement("test:owner/work")).when(Toggle.class).run(context -> {})),
                module("other:user", stages -> stages.define(Stage.placement("other:user/work")).when(Toggle.class).run(context -> {})));
        assertEquals(List.of(Toggle.class), shared);

        var snowOnMoss = resolveSettings(new SnowModule(), module("example:moss", stages ->
                stages.define(Stage.placement("example:moss/on-snow")).when(SnowSettings.class).run(context -> {})));
        assertTrue(snowOnMoss.contains(SnowSettings.class), "moss may condition on iris:snow, which iris:snow owns");
    }

    @Test
    void dependenciesCannotCrossPipelines() {
        var placementStage = Stage.placement("test:hard/placed");
        ChunkStage<Void> chunkStage = Stage.chunk("test:hard/chunk");
        var hard = module("test:hard", stages -> {
            stages.define(placementStage).run(context -> {});
            stages.define(chunkStage).requires(placementStage).run(context -> {});
        });
        var error = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.CHUNK, hard));
        assertTrue(error.getMessage().contains("in pipeline CHUNK depends on test:hard/placed (module test:hard) in pipeline PLACEMENT"),
                error.getMessage());

        var soft = module("test:soft", stages -> {
            stages.define(Stage.placement("test:soft/placed")).run(context -> {});
            stages.define(Stage.chunk("test:soft/chunk")).softAfter(StageRef.of("test:soft/placed")).run(context -> {});
        });
        assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.CHUNK, soft));
    }

    @Test
    void definitionsNeedExactlyOneBodyOfTheRightKind() {
        var withOutput = Stage.placement("test:bodies/output", Integer.class);
        var withoutOutput = Stage.placement("test:bodies/plain");
        var accumulating = Stage.placementBlock("test:bodies/acc", Counter.class);

        var run = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:bodies", stages -> stages.define(withOutput).run(context -> {}))));
        assertTrue(run.getMessage().contains("declares output Integer; define it with produce(...)"), run.getMessage());
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:bodies", stages -> stages.define(withoutOutput).produce(context -> null))));
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:bodies", stages -> stages.define(accumulating).run(context -> {}))));
        var missing = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT,
                module("test:bodies", stages -> stages.define(withoutOutput).requires())));
        assertTrue(missing.getMessage().contains("has no body"), missing.getMessage());
        assertThrows(IllegalStateException.class, () -> resolve(Pipeline.PLACEMENT, module("test:bodies", stages -> {
            var definition = stages.define(withoutOutput);
            definition.run(context -> {});
            definition.requires(withOutput);
        })));
    }

    @Test
    void invalidDeclarationsFailDuringSetup() {
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
                stages.define(Stage.placement("test:plain/work")).when(Unannotated.class).run(context -> {}))));

        var retained = new ArrayList<StageGraph>();
        TestSetups.stages(module("test:retained", retained::add));
        assertThrows(IllegalStateException.class, () -> retained.getFirst().define(Stage.placement("test:retained/late")));
    }

    @Test
    void outputsAreReadableOnlyThroughTransitiveRequirementsAndAfterTheirPass() {
        var height = Stage.placement("test:outputs/height", Integer.class);
        var offset = Stage.placement("test:outputs/offset", Integer.class);
        var counted = Stage.placementBlock("test:outputs/counted", Counter.class);
        var observed = new ArrayList<Object>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:outputs", stages -> {
            stages.define(height).produce(context -> 10);
            stages.define(offset).requires(height).produce(context -> context.output(height) + 1);
            stages.define(counted).requires(offset).accumulate(Counter::new, (context, counter) -> {
                counter.count++;
                observed.add(context.output(height));
            });
            stages.define(Stage.placementBlock("test:outputs/peek")).requires(counted).run(context -> observed.add(
                    assertThrows(IllegalStateException.class, () -> context.output(counted)).getMessage()));
            stages.define(Stage.placement("test:outputs/reader")).requires(counted).run(context -> observed.add(context.output(counted).count));
            stages.define(Stage.placement("test:outputs/snoop")).run(context -> observed.add(
                    assertThrows(IllegalStateException.class, () -> context.output(height)).getMessage()));
        }));

        assertTrue(place(graph, flatEdit(), ConfiguredSettings.none()).isPlaced());
        assertEquals(10, observed.get(1));
        assertTrue(observed.get(2).toString().contains("before it completed"), observed.get(2).toString());
        assertTrue(observed.contains(2));
        assertTrue(observed.contains("Stage test:outputs/snoop read the output of test:outputs/height without requiring it; "
                + "add requires(test:outputs/height) to its definition"));
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
        var toggled = Stage.placement("test:cascade/toggled", Integer.class);
        var dependent = Stage.placement("test:cascade/dependent");
        var unrelated = Stage.placement("test:cascade/unrelated");
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:cascade", stages -> {
            stages.define(toggled).when(Toggle.class).produce(context -> {
                events.add("toggled");
                return 1;
            });
            stages.define(dependent).requires(toggled).run(context -> events.add("dependent:" + context.output(toggled)));
            stages.define(unrelated).run(context -> events.add("unrelated"));
        }));

        place(graph, flatEdit(), ConfiguredSettings.none());
        assertEquals(List.of("unrelated"), events);
        events.clear();
        place(graph, flatEdit(), ConfiguredSettings.placement(ResourceId.parse("test:placement"), new Toggle()));
        assertEquals(List.of("toggled", "dependent:1", "unrelated"), events);
    }

    @Test
    void aConditionedStageReadsOnlyTheSettingsItIsConditionedOn() {
        var configured = new ArrayList<Object>();
        var refusals = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:owner", stages -> {
            stages.define(Stage.placement("test:owner/toggled")).when(Toggle.class).run(context -> {
                configured.add(context.configured(Toggle.class).mostSpecific().orElseThrow());
                refusals.add(assertThrows(IllegalStateException.class, () -> context.configured(SnowSettings.class)).getMessage());
            });
            stages.define(Stage.placement("test:owner/plain")).run(context -> refusals.add(
                    assertThrows(IllegalStateException.class, () -> context.configured(Toggle.class)).getMessage()));
        }));
        var toggle = new Toggle();

        place(graph, flatEdit(), ConfiguredSettings.placement(ResourceId.parse("test:placement"), toggle));

        assertEquals(List.of(toggle), configured);
        assertEquals(List.of(
                "Stage test:owner/plain read configured(Toggle) but is not conditioned on it; add when(Toggle.class) to its"
                        + " definition, or read other settings through settings()",
                "Stage test:owner/toggled read configured(SnowSettings) but is not conditioned on it; add when(SnowSettings.class)"
                        + " to its definition, or read other settings through settings()"), refusals);
    }

    @Test
    void aProducerThatRejectsTheAttemptMayReturnNull() {
        var graph = resolve(Pipeline.PLACEMENT, module("test:reject", stages ->
                stages.define(Stage.placement("test:reject/height", Integer.class)).produce(context -> {
                    context.reject("no ground");
                    return null;
                })));
        var edit = flatEdit();

        var outcome = place(graph, edit, ConfiguredSettings.none());
        assertEquals(PlacementOutcome.Status.REJECTED, outcome.status());
        assertEquals(Optional.of("no ground"), outcome.rejection());
        assertFalse(edit.committed());
    }

    @Test
    void placementTimesEachStageThatRanOnceAndABlockStageOncePerPass() {
        var prepare = Stage.placement("test:timed/prepare");
        var write = Stage.placementBlock("test:timed/write");
        var graph = resolve(Pipeline.PLACEMENT, module("test:timed", stages -> {
            stages.define(prepare).run(context -> {});
            stages.define(write).requires(prepare).run(context -> {});
            stages.define(Stage.placement("test:timed/off")).when(Toggle.class).run(context -> {});
        }));
        var timed = new ArrayList<String>();

        place(graph, flatEdit(), ConfiguredSettings.none(), (stage, nanos) -> {
            assertTrue(nanos >= 0);
            timed.add(stage.stage().id().toString());
        });

        assertEquals(List.of("test:timed/prepare", "test:timed/write"), timed);
    }

    @Test
    void skippedBlocksAreHiddenFromLaterStagesOfThePass() {
        var written = new ArrayList<Integer>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:skip", stages -> {
            var filter = Stage.placementBlock("test:skip/filter");
            stages.define(filter).run(context -> {
                if (context.block().position().x() == 0) context.skip();
            });
            stages.define(Stage.placementBlock("test:skip/write")).requires(filter).run(context -> written.add(context.block().position().x()));
        }));

        place(graph, flatEdit(), ConfiguredSettings.none());
        assertEquals(List.of(1), written);
    }

    @Test
    void rejectionStopsTheAttemptAndCommitsNothing() {
        var events = new ArrayList<String>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:reject", stages -> {
            var write = Stage.placementBlock("test:reject/write");
            stages.define(write).run(context -> {
                events.add("write");
                context.access().set(new PlacedBlock(new BlockPosition(0, 10, 0), STONE, Optional.empty()));
                context.reject("blocked");
                context.reject("ignored");
            });
            stages.define(Stage.placement("test:reject/after")).requires(write).run(context -> events.add("after"));
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
                    context.access().set(2, 10, 1, STONE);
                    assertEquals(STONE, context.access().get(1, 10, 1));
                    var outside = context.access().bounds().maxX();
                    assertThrows(IndexOutOfBoundsException.class, () -> context.access().set(outside, 10, 1, STONE));
                    retained.add(context.access());
                })));
        var edit = flatEdit();

        assertTrue(place(graph, edit, ConfiguredSettings.none()).isPlaced());
        assertEquals(STONE, edit.publishedAt(1, 10, 1));
        assertEquals(STONE, edit.publishedAt(2, 10, 1));
        assertThrows(IllegalStateException.class, () -> retained.getFirst().get(1, 10, 1));
    }

    @Test
    void onePlacementInvocationMakesItsMantleViewsOnce() {
        var key = new MantleKey<>("test:views/depth", Integer.class);
        var views = new ArrayList<Object>();
        var graph = resolve(Pipeline.PLACEMENT, module("test:views", stages ->
                stages.define(Stage.placement("test:views/read")).run(context -> {
                    var mantle = context.access().mantle();
                    views.add(mantle);
                    views.add(context.access().mantle());
                    views.add(mantle.layer(key));
                    views.add(mantle.layer(key));
                    views.add(mantle.blocks());
                    views.add(mantle.blocks());
                })));
        var terrain = new TestTerrain((x, z) -> 5);
        var edit = new MemoryEdit(terrain, new MemoryMantle().within(terrain.bounds()));

        assertTrue(place(graph, edit, ConfiguredSettings.none()).isPlaced());
        assertSame(views.get(0), views.get(1));
        assertSame(views.get(2), views.get(3));
        assertSame(views.get(4), views.get(5));
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
        assertThrows(IllegalArgumentException.class, () -> new MantleRunner(empty));
        assertThrows(IllegalArgumentException.class, () -> new ChunkRunner(resolve(Pipeline.MANTLE)));
        assertTrue(empty.describe().contains("CHUNK pipeline: 0 stages, 0 block passes"));
    }

    static final class Counter {
        int count;
    }

    @Test
    void reachIsDeclaredPerStageAndCollectedPerPipelineAndLayer() {
        var depth = new MantleKey<>("test:reach/depth", Integer.class);
        var marks = new MantleKey<>("test:reach/marks", String.class);
        var carve = Stage.mantle("test:reach/carve");
        var mark = Stage.mantle("test:reach/mark");
        var plain = Stage.mantle("test:reach/plain");
        var paint = Stage.placementBlock("test:reach/paint");
        var reach = module("test:reach", stages -> {
            stages.define(carve).reach(24).writesMantle(depth).run(context -> {});
            stages.define(mark).reach(8).writesMantle(depth, marks).run(context -> {});
            stages.define(plain).run(context -> {});
            stages.define(paint).reach(2).run(context -> {});
        });
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(withMantle(reach, depth, marks))), Map.of(), Set.of(), Set.of());

        assertEquals(new StageReach(24, List.of(depth)), loaded.mantle().stages().getFirst().reach());
        assertEquals(StageReach.NONE, loaded.mantle().stages().get(2).reach(), "an undeclared reach is 0 with no layers");
        assertEquals(new PipelineReach(24, Map.of("test:reach/depth", 24, "test:reach/marks", 8)), loaded.mantle().reach());
        assertEquals(new PipelineReach(2, Map.of()), loaded.placement().reach());
        assertEquals(new PipelineReach(0, Map.of()), loaded.chunk().reach());
        assertTrue(loaded.mantle().describe().contains("      reach 24 blocks, writes mantle test:reach/depth\n"), loaded.mantle().describe());
        assertTrue(loaded.mantle().describe().contains("      reach 8 blocks, writes mantle test:reach/depth, test:reach/marks\n"));
    }

    @Test
    void aMantleStageWorksOutItsReachWhenThePackLoads() {
        var carve = Stage.mantle("test:reach/carve");
        var mark = Stage.mantle("test:reach/mark");
        var loaded = LoadedModules.load(List.of(DiscoveredModule.builtIn(module("test:reach", stages -> {
            stages.define(carve).reach(settings -> settings.declared(Toggle.class).isEmpty() ? 4 : 40).run(context -> {});
            stages.define(mark).reach(2).run(context -> {});
        }))), Map.of(), Set.of(), Set.of());
        var computed = loaded.mantle().stages().getFirst();
        var toggled = new DeclaredSettings(new Entry<>(Level.DIMENSION, ResourceId.parse("pack:dimension/main"), new Toggle()));

        assertEquals(4, computed.reachBlocks(DeclaredSettings.NONE));
        assertEquals(40, computed.reachBlocks(toggled));
        assertEquals(2, loaded.mantle().stages().get(1).reachBlocks(toggled), "a declared number is the same for every pack");
        assertTrue(loaded.mantle().describe().contains("      reach worked out when the pack loads\n"), loaded.mantle().describe());
        var pipeline = assertThrows(IllegalStateException.class, () -> loaded.mantle().reach());
        assertEquals("Stage test:reach/carve of module test:reach works out its reach when the pack loads;"
                + " ask each mantle stage with ResolvedStage.reachBlocks(settings)", pipeline.getMessage());
    }

    @Test
    void aWorkedOutReachIsNeverNegativeAndIsDeclaredOnce() {
        var negative = LoadedModules.load(List.of(DiscoveredModule.builtIn(module("test:reach", stages ->
                stages.define(Stage.mantle("test:reach/carve")).reach(settings -> -1).run(context -> {})))), Map.of(), Set.of(), Set.of());
        var failure = assertThrows(IllegalStateException.class, () -> negative.mantle().stages().getFirst().reachBlocks(DeclaredSettings.NONE));
        assertEquals("Stage test:reach/carve of module test:reach worked out a negative reach of -1 blocks for this pack", failure.getMessage());

        var twice = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.MANTLE, module("test:reach", stages ->
                stages.define(Stage.mantle("test:reach/carve")).reach(2).reach(settings -> 1).run(context -> {}))));
        assertEquals("Stage test:reach/carve (module test:reach) already declares a reach of 2 blocks", twice.getMessage());
    }

    @Test
    void theMantleRunnerRunsOneStageWhereItAndWhatItRequiresAreActive() {
        var toggled = Stage.mantle("test:owner/toggled");
        var follower = Stage.mantle("test:owner/follower");
        var ran = new ArrayList<String>();
        var graph = resolve(Pipeline.MANTLE, module("test:owner", stages -> {
            stages.define(toggled).when(Toggle.class).run(context -> ran.add("toggled"));
            stages.define(follower).requires(toggled).run(context -> ran.add("follower"));
        }));
        var runner = new MantleRunner(graph);
        var toggle = new Entry<>(Level.BIOME, ResourceId.parse("pack:biome/plains"), new Toggle());
        var nowhere = TestContexts.mantle(0, 0, ColumnSettings.NONE, null);
        var oneColumn = TestContexts.mantle(0, 0, new ColumnSettings((x, z) -> x == 15 && z == 0 ? List.of(toggle) : List.of()), null);

        var timed = new ArrayList<String>();
        StageTimings timings = (stage, nanos) -> timed.add(stage.stage().id().toString());

        runner.generate(follower, nowhere, TestContexts.SEED, timings);
        assertEquals(List.of(), ran, "a stage whose requirement is inactive in the chunk is inactive too");
        runner.generate(follower, oneColumn, TestContexts.SEED, timings);
        assertEquals(List.of("follower"), ran, "only the asked stage runs; its requirement ran in its own pass");
        runner.generate(toggled, oneColumn, TestContexts.SEED, timings);
        assertEquals(List.of("follower", "toggled"), ran);
        assertEquals(List.of("test:owner/follower", "test:owner/toggled"), timed, "only stages that ran are timed");
        var foreign = assertThrows(IllegalArgumentException.class,
                () -> runner.generate(Stage.mantle("test:owner/absent"), nowhere, TestContexts.SEED, StageTimings.NONE));
        assertEquals("Stage test:owner/absent is not a stage of this mantle graph", foreign.getMessage());
    }

    @Test
    void reachMustBeValidAndNameRegisteredLayers() {
        var depth = new MantleKey<>("test:reach/depth", Integer.class);
        var carve = Stage.mantle("test:reach/carve");

        var negative = assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.MANTLE,
                module("test:reach", stages -> stages.define(carve).reach(-1).run(context -> {}))));
        assertEquals("Stage test:reach/carve (module test:reach) declares a negative reach of -1 blocks", negative.getMessage());
        var twice = assertThrows(IllegalStateException.class, () -> resolve(Pipeline.MANTLE,
                module("test:reach", stages -> stages.define(carve).reach(1).reach(2).run(context -> {}))));
        assertEquals("Stage test:reach/carve (module test:reach) already declares a reach of 1 blocks", twice.getMessage());
        assertThrows(IllegalArgumentException.class, () -> resolve(Pipeline.MANTLE,
                module("test:reach", stages -> stages.define(carve).writesMantle(depth, depth).run(context -> {}))));

        var unregistered = assertThrows(ModuleLoadException.class, () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(
                module("test:reach", stages -> stages.define(carve).writesMantle(depth).run(context -> {})))), Map.of(), Set.of(), Set.of()));
        assertEquals("Stage test:reach/carve of module test:reach writes mantle layer test:reach/depth, which no module registers;"
                + " its owner registers it with FeatureRegistry.registerMantle", unregistered.getMessage());

        var asText = new MantleKey<>("test:reach/depth", String.class);
        var mismatch = assertThrows(ModuleLoadException.class, () -> LoadedModules.load(List.of(DiscoveredModule.builtIn(withMantle(
                module("test:reach", stages -> stages.define(carve).writesMantle(asText).run(context -> {})), depth))), Map.of(), Set.of(), Set.of()));
        assertEquals("Stage test:reach/carve of module test:reach writes mantle layer test:reach/depth as java.lang.String,"
                + " but it is registered as java.lang.Integer", mismatch.getMessage());
    }

    /** {@code module}, also registering {@code layers}. */
    private static IrisModule withMantle(IrisModule module, MantleKey<?>... layers) {
        return new IrisModule() {
            public ModuleInfo info() { return module.info(); }
            public void setup(ModuleSetup setup) {
                module.setup(setup);
                for (var layer : layers) registerLayer(setup.registry(), layer);
            }
        };
    }

    private static <T> void registerLayer(FeatureRegistry registry, MantleKey<T> layer) {
        registry.registerMantle(layer, MantleLifetime.GENERATION, codec(layer));
    }

    private static <T> Codec<T> codec(MantleKey<T> layer) {
        return new Codec<>() {
            public int version() { return 0; }
            public void write(java.io.DataOutput output, T value) {}
            public T read(java.io.DataInput input, int storedVersion) { throw new UnsupportedOperationException(); }
        };
    }
}
