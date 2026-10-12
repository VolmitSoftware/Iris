package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declare;
import static art.arcane.iris.engine.framework.StructurePlacementFixtures.declaring;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StructurePlacementScopeTest {
    @Test
    public void caveBiomeContributesOnlyCaveAnchoredPlacements() {
        StructurePlacement surfacePlacement = new StructurePlacement();
        StructurePlacement cavePlacement = placement("anchor", StructureAnchorMode.CAVE_FLOOR);
        StructurePlacement invalidCaveScopePlacement = placement("anchor", StructureAnchorMode.SURFACE);
        StructurePlacement regionPlacement = new StructurePlacement();
        StructurePlacement dimensionPlacement = new StructurePlacement();

        IrisBiome surfaceBiome = declare(new IrisBiome(), surfacePlacement);
        IrisBiome caveBiome = declare(new IrisBiome(), cavePlacement, invalidCaveScopePlacement);
        IrisRegion region = declare(new IrisRegion(), regionPlacement);
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisComplex complex = engine.getComplex();
        IrisDimension dimension = mock(IrisDimension.class);
        KList<StructurePlacement> dimensionStructures = new KList<>();
        dimensionStructures.add(dimensionPlacement);
        dimensionStructures.add(cavePlacement);
        KList<IrisRegion> allRegions = new KList<>();
        allRegions.add(region);
        KList<IrisBiome> allBiomes = new KList<>();
        allBiomes.add(surfaceBiome);
        allBiomes.add(caveBiome);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(allBiomes);
        when(dimension.getModuleSettings()).thenReturn(declaring(dimensionStructures));
        when(dimension.getAllRegions(engine)).thenReturn(allRegions);
        when(complex.getTrueBiomeStream().get(8, 8)).thenReturn(surfaceBiome);
        when(complex.getCaveBiomeStream().get(8, 8)).thenReturn(caveBiome);
        when(complex.getRegionStream().get(8, 8)).thenReturn(region);

        KList<StructurePlacement> placements = StructurePlacementScope.placementsAt(engine, 0, 0);

        assertEquals(4, placements.size());
        assertTrue(placements.contains(surfacePlacement));
        assertTrue(placements.contains(cavePlacement));
        assertTrue(placements.contains(regionPlacement));
        assertTrue(placements.contains(dimensionPlacement));
        assertFalse(placements.contains(invalidCaveScopePlacement));
    }

    @Test
    public void dimensionOnlyPlacementsDoNotResolveBiomeScopes() {
        StructurePlacement dimensionPlacement = new StructurePlacement();
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        IrisComplex complex = engine.getComplex();
        IrisDimension dimension = mock(IrisDimension.class);
        KList<StructurePlacement> dimensionStructures = new KList<>();
        dimensionStructures.add(dimensionPlacement);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        when(dimension.getModuleSettings()).thenReturn(declaring(dimensionStructures));
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());

        KList<StructurePlacement> placements = StructurePlacementScope.placementsAt(engine, 0, 0);

        assertEquals(1, placements.size());
        assertTrue(placements.contains(dimensionPlacement));
        verify(complex.getTrueBiomeStream(), never()).get(8, 8);
        verify(complex.getCaveBiomeStream(), never()).get(8, 8);
        verify(complex.getRegionStream(), never()).get(8, 8);
    }
}
