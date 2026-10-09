package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.IrisStructureTerrain;
import art.arcane.volmlib.util.collection.KList;
import com.volmit.host.LoadedPack;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gate the native structure volume index applies before it assembles anything: a structure the pack suppresses
 * contributes no piece volumes, so it can never veto an object placement.
 */
public class NativeStructureVolumeSuppressionTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<LoadedPack> packs = new ArrayList<>();

    @After
    public void closePacks() {
        packs.forEach(LoadedPack::close);
    }

    @Test
    public void packDisabledStructuresContributeNoVolumes() throws IOException {
        Engine engine = engine();

        assertFalse(NativeStructureGenerationPolicy
                .resolve(engine, "minecraft:village_swamp", false).generate());
        assertFalse(NativeStructureGenerationPolicy
                .resolve(engine, "minecraft:village_plains", false).generate());
    }

    @Test
    public void enabledStructuresContributeVolumes() throws IOException {
        Engine engine = engine();

        assertTrue(NativeStructureGenerationPolicy
                .resolve(engine, "towns_and_towers:village_swamp", false).generate());
    }

    @Test
    public void plannedModuleStartsContributeVolumesThroughTheirOwnDecision() {
        NativeStructureStartPlan plan = new NativeStructureStartPlan("minecraft:ancient_city", 3, 5, -30, true, false, 7L,
                null, null, new IrisStructureTerrain());

        assertTrue(plan.decision().generate());
    }

    private Engine engine() throws IOException {
        StructureSettings settings = new StructureSettings(List.of("minecraft:village"), List.of(), 0, true, List.of(), List.of());
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        packs.add(BuiltInPack.serveWithResources(data, folder, List.of()));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(settings));
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
