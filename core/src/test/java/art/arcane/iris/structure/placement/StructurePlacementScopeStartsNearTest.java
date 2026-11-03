package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructurePlacementScopeStartsNearTest {
    @Test
    public void reportsAStartWithinTheReachAndNothingOutsideIt() {
        IrisStructurePlacement dense = new IrisStructurePlacement().setDistribution(StructureDistribution.DENSITY).setDensity(1D);
        assertTrue(StructurePlacementScope.startsNear(engine(new KList<>(dense)), 3, -2, 1));

        IrisStructurePlacement never = new IrisStructurePlacement().setDistribution(StructureDistribution.DENSITY).setDensity(0D);
        assertFalse(StructurePlacementScope.startsNear(engine(new KList<>(never)), 3, -2, 8));
        assertFalse(StructurePlacementScope.startsNear(engine(new KList<>()), 3, -2, 8));
    }

    private static Engine engine(KList<IrisStructurePlacement> placements) {
        Engine engine = mock(Engine.class);
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getMantle()).thenReturn(1337L);
        when(engine.getSeedManager()).thenReturn(seeds);
        IrisDimension dimension = new IrisDimension();
        dimension.setStructures(placements);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        return engine;
    }
}
