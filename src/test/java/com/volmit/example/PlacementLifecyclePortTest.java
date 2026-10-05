package com.volmit.example;

import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.example.placement.*;
import com.volmit.host.ConfiguredSettings;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.LoadedModules;
import com.volmit.host.PlacementRunner;
import com.volmit.host.ResolvedGraph;
import com.volmit.host.StageGraphBuilder;
import com.volmit.host.StageRandom;
import com.volmit.mantle.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacementRequest;
import com.volmit.resource.ResourceId;
import com.volmit.stage.Pipeline;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;
import com.volmit.stage.StageGraph;
import com.volmit.support.MemoryEdit;
import com.volmit.support.TestContexts;
import com.volmit.support.TestTerrain;
import com.volmit.world.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** The ported slice of Iris's place() lifecycle, composed only from module-owned stages. */
class PlacementLifecyclePortTest {
    private static final BlockState PLANKS = new BlockState("minecraft:oak_planks");
    private static final int ANCHOR_GROUND = 10;
    private static final int SURROUNDING_GROUND = 7;
    private static final BlockPosition SURFACE_ANCHOR = new BlockPosition(0, ANCHOR_GROUND, 0);

    private static final List<String> EXPECTED_ORDER = List.of(
            "iris:prepare/transforms", "iris:anchor/height", "iris:prepare/rotate", "iris:blocks/write",
            "iris:snow/collect", "iris:stilt/bounds", "iris:blocks/complete", "iris:stilt/fill",
            "iris:snow/finish", "example:moss/spread");

    private static final String EXPECTED_DESCRIPTION = """
            PLACEMENT pipeline: 10 stages, 1 block pass
            order:
              1. iris:prepare/transforms [invocation] module iris:prepare
              2. iris:anchor/height [invocation] module iris:anchor
                  requires iris:prepare/transforms
              3. iris:prepare/rotate [block pass 1] module iris:prepare
                  requires iris:prepare/transforms
              4. iris:blocks/write [block pass 1] module iris:blocks
                  requires iris:anchor/height, iris:prepare/rotate
              5. iris:snow/collect [block pass 1] module iris:snow when iris:snow
                  requires iris:blocks/write
              6. iris:stilt/bounds [block pass 1] module iris:stilt when iris:stilt
                  requires iris:anchor/height, iris:prepare/rotate
              7. iris:blocks/complete [invocation] module iris:blocks
                  requires iris:blocks/write
              8. iris:stilt/fill [invocation] module iris:stilt when iris:stilt
                  requires iris:stilt/bounds, iris:blocks/complete
              9. iris:snow/finish [invocation] module iris:snow when iris:snow
                  requires iris:snow/collect, iris:blocks/complete
                  soft after iris:stilt/fill
                  absent soft targets iris:vacuum/finish
              10. example:moss/spread [invocation] module example:moss when example:moss
                  requires iris:anchor/height, iris:blocks/complete
                  soft after iris:snow/finish
            block passes:
              pass 1: iris:prepare/rotate, iris:blocks/write, iris:snow/collect, iris:stilt/bounds
            mutually unordered:
              iris:anchor/height <-> iris:prepare/rotate
              iris:blocks/write <-> iris:stilt/bounds
              iris:snow/collect <-> iris:stilt/bounds
              iris:snow/collect <-> iris:blocks/complete
              iris:snow/collect <-> iris:stilt/fill
              iris:stilt/bounds <-> iris:blocks/complete
            """;

    private static ResolvedGraph resolve(List<? extends IrisModule> modules) {
        var builder = new StageGraphBuilder();
        modules.stream().map(DiscoveredModule::builtIn).forEach(builder::install);
        return builder.resolve(Pipeline.PLACEMENT);
    }

    private static List<String> order(ResolvedGraph graph) {
        return graph.stages().stream().map(stage -> stage.stage().id().toString()).toList();
    }

    /** An L-shaped object: two bottom blocks and one block stacked on the second. */
    private static PlacementRequest request() {
        var object = new GeneratedObject(new BlockBox(0, 0, 0, 2, 2, 1), new BlockPosition(0, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(0, 0, 0), PLANKS),
                        new ObjectBlock(new BlockPosition(1, 0, 0), PLANKS),
                        new ObjectBlock(new BlockPosition(1, 1, 0), PLANKS)),
                List.of());
        return new PlacementRequest(ResourceId.parse("test:attempt"), object, new BlockPosition(0, 0, 0));
    }

    private static TestTerrain terrain() {
        return new TestTerrain((x, z) -> x == 0 && z == 0 ? ANCHOR_GROUND : SURROUNDING_GROUND);
    }

    private static MemoryEdit place(ResolvedGraph graph, ConfiguredSettings settings) {
        var terrain = terrain();
        var edit = new MemoryEdit(terrain);
        var outcome = new PlacementRunner(graph).place(request(), edit, TestContexts.placement(terrain), TestContexts.SEED, settings);
        assertTrue(outcome.isPlaced());
        return edit;
    }

    /** World position of a local block after the seeded rotation and surface anchoring. The rotation
     * is the first draw of the transforms stage's own stream.
     */
    private static BlockPosition placed(int x, int y, int z) {
        var transforms = new Transforms(StageRandom.stream(TestContexts.SEED, PrepareModule.TRANSFORMS.id()).nextInt(4));
        var rotated = transforms.rotate(new BlockPosition(x, y, z), new BlockPosition(0, 0, 0));
        return rotated.add(SURFACE_ANCHOR);
    }

    @Test
    void resolvesThePortedLifecycleWithOneFusedBlockPass() {
        var graph = resolve(PlacementModules.all());

        assertEquals(EXPECTED_ORDER, order(graph));
        assertEquals(1, graph.blockPasses().size());
        assertEquals(EXPECTED_DESCRIPTION, graph.describe());
    }

    @Test
    void resolvedOrderIsIndependentOfModuleInstallOrder() {
        var expected = resolve(PlacementModules.all()).describe();
        var modules = new ArrayList<>(PlacementModules.all());
        Collections.reverse(modules);
        assertEquals(expected, resolve(modules).describe());
        for (long seed = 0; seed < 16; seed++) {
            Collections.shuffle(modules, new Random(seed));
            assertEquals(expected, resolve(modules).describe(), "install order " + modules);
        }
    }

    @Test
    void placesObjectThenStiltSnowAndMossFromTypedOutputs() {
        var edit = place(resolve(PlacementModules.all()),
                ConfiguredSettings.of(new StiltSettings(), new SnowSettings(2), new MossSettings()));

        var bottom = placed(0, 0, 0);
        var corner = placed(1, 0, 0);
        var top = placed(1, 1, 0);
        var snow = SnowModule.SNOW.withProperty("layers", "2");
        var expected = new HashMap<BlockPosition, BlockState>();
        expected.put(bottom, PLANKS);
        expected.put(corner, PLANKS);
        expected.put(top, PLANKS);
        for (int y = SURROUNDING_GROUND; y < ANCHOR_GROUND; y++) {
            expected.put(new BlockPosition(corner.x(), y, corner.z()), PLANKS);
        }
        expected.put(bottom.add(new BlockPosition(0, 1, 0)), snow);
        expected.put(top.add(new BlockPosition(0, 1, 0)), snow);
        expected.put(new BlockPosition(0, ANCHOR_GROUND - 1, 0), MossModule.MOSS);

        assertTrue(edit.committed());
        assertEquals(expected, edit.published());
    }

    @Test
    void unconfiguredFeaturesStayInactiveWhileTheCoreStillPlaces() {
        var edit = place(resolve(PlacementModules.all()), ConfiguredSettings.none());

        assertEquals(Map.of(placed(0, 0, 0), PLANKS, placed(1, 0, 0), PLANKS, placed(1, 1, 0), PLANKS), edit.published());
    }

    @Test
    void inactiveRequirementDeactivatesThirdPartyDependents() {
        var capModule = new IrisModule() {
            static final PlacementStage<Void> CAP = Stage.placement("example:cap/top");
            public ModuleInfo info() { return ModuleInfo.of("example:cap", "Cap", "1"); }
            public void install(StageGraph graph) {
                graph.define(CAP).requires(StiltModule.FILL).run(context -> context.access().set(
                        new PlacedBlock(new BlockPosition(5, 20, 5), PLANKS, Optional.empty())));
            }
        };
        var modules = new ArrayList<IrisModule>(PlacementModules.all());
        modules.add(capModule);
        var graph = resolve(modules);

        var withoutStilt = place(graph, ConfiguredSettings.of(new SnowSettings(), new MossSettings()));
        assertEquals(TestTerrain.AIR, withoutStilt.publishedAt(5, 20, 5));
        assertEquals(MossModule.MOSS, withoutStilt.publishedAt(0, ANCHOR_GROUND - 1, 0));

        var withStilt = place(graph, ConfiguredSettings.of(new StiltSettings()));
        assertEquals(PLANKS, withStilt.publishedAt(5, 20, 5));
    }

    @Test
    void loadingThePortCatalogsEveryConditioningSettingsType() {
        var loaded = LoadedModules.load(PlacementModules.builtIns(), Map.of(), Set.of());

        assertEquals(List.of("example:moss", "iris:snow", "iris:stilt"),
                loaded.catalog().settings().stream().map(settings -> settings.id().toString()).toList());
        assertEquals(EXPECTED_ORDER, order(loaded.placement()));
    }
}
