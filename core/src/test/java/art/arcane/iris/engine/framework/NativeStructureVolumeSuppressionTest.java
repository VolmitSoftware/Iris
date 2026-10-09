package art.arcane.iris.engine.framework;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.vanilla.IrisImportedStructureControl;
import art.arcane.iris.engine.object.structure.vanilla.IrisNativeStructure;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.StructureDistribution;
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
    public void plannedIrisStartsContributeVolumesThroughTheirOwnDecision() {
        IrisStructurePlacement placement = new IrisStructurePlacement()
                .setDistribution(StructureDistribution.DENSITY)
                .setDensity(1D);
        placement.getNativeStructures().add(new IrisNativeStructure()
                .setStructure("minecraft:ancient_city")
                .setWeight(1));
        NativeStructureStartPlan plan = new NativeStructureStartPlan(
                placement, placement.getNativeStructures().getFirst(), 3, 5, -30);

        assertTrue(NativeStructurePlacementPlanner.decisionFor(plan).generate());
    }

    private Engine engine() throws IOException {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabled().add("minecraft:village");
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        packs.add(BuiltInPack.serveWithResources(data, folder, List.of()));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getImportedStructures()).thenReturn(control);
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
