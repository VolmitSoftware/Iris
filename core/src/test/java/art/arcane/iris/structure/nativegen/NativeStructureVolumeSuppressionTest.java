package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.placement.StructureDistribution;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The gate the native structure volume index applies before it assembles anything: a structure the pack suppresses
 * contributes no piece volumes, so it can never veto an object placement.
 */
public class NativeStructureVolumeSuppressionTest {
    @Test
    public void packDisabledStructuresContributeNoVolumes() {
        Engine engine = engine();

        assertFalse(NativeStructureGenerationPolicy
                .resolve(engine, "minecraft:village_swamp", false).generate());
        assertFalse(NativeStructureGenerationPolicy
                .resolve(engine, "minecraft:village_plains", false).generate());
    }

    @Test
    public void enabledStructuresContributeVolumes() {
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

    private Engine engine() {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        control.getDisabled().add("minecraft:village");
        IrisDimension dimension = mock(IrisDimension.class);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getImportedStructures()).thenReturn(control);
        when(dimension.getStructures()).thenReturn(new KList<>());
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
        return engine;
    }
}
