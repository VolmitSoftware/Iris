package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

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
        IrisStructurePlacement surfacePlacement = new IrisStructurePlacement();
        IrisStructurePlacement cavePlacement = new IrisStructurePlacement()
                .setAnchor(IrisStructureAnchorMode.CAVE_FLOOR);
        IrisStructurePlacement invalidCaveScopePlacement = new IrisStructurePlacement()
                .setAnchor(IrisStructureAnchorMode.SURFACE);
        IrisStructurePlacement regionPlacement = new IrisStructurePlacement();
        IrisStructurePlacement dimensionPlacement = new IrisStructurePlacement();

        IrisBiome surfaceBiome = new IrisBiome();
        surfaceBiome.getStructures().add(surfacePlacement);
        IrisBiome caveBiome = new IrisBiome();
        caveBiome.getStructures().add(cavePlacement);
        caveBiome.getStructures().add(invalidCaveScopePlacement);
        IrisRegion region = new IrisRegion();
        region.getStructures().add(regionPlacement);
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisComplex complex = engine.getComplex();
        IrisDimension dimension = mock(IrisDimension.class);
        KList<IrisStructurePlacement> dimensionStructures = new KList<>();
        dimensionStructures.add(dimensionPlacement);
        dimensionStructures.add(cavePlacement);
        KList<IrisRegion> allRegions = new KList<>();
        allRegions.add(region);
        KList<IrisBiome> allBiomes = new KList<>();
        allBiomes.add(surfaceBiome);
        allBiomes.add(caveBiome);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(allBiomes);
        when(dimension.getStructures()).thenReturn(dimensionStructures);
        when(dimension.getAllRegions(engine)).thenReturn(allRegions);
        when(complex.getTrueBiomeStream().get(8, 8)).thenReturn(surfaceBiome);
        when(complex.getCaveBiomeStream().get(8, 8)).thenReturn(caveBiome);
        when(complex.getRegionStream().get(8, 8)).thenReturn(region);

        KList<IrisStructurePlacement> placements = StructurePlacementScope.placementsAt(engine, 0, 0);

        assertEquals(4, placements.size());
        assertTrue(placements.contains(surfacePlacement));
        assertTrue(placements.contains(cavePlacement));
        assertTrue(placements.contains(regionPlacement));
        assertTrue(placements.contains(dimensionPlacement));
        assertFalse(placements.contains(invalidCaveScopePlacement));
    }

    @Test
    public void dimensionOnlyPlacementsDoNotResolveRiverInclusiveBiomeScopes() {
        IrisStructurePlacement dimensionPlacement = new IrisStructurePlacement();
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        IrisComplex complex = engine.getComplex();
        IrisDimension dimension = mock(IrisDimension.class);
        KList<IrisStructurePlacement> dimensionStructures = new KList<>();
        dimensionStructures.add(dimensionPlacement);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getAllBiomes()).thenReturn(new KList<>());
        when(dimension.getStructures()).thenReturn(dimensionStructures);
        when(dimension.getAllRegions(engine)).thenReturn(new KList<>());

        KList<IrisStructurePlacement> placements = StructurePlacementScope.placementsAt(engine, 0, 0);

        assertEquals(1, placements.size());
        assertTrue(placements.contains(dimensionPlacement));
        verify(complex.getTrueBiomeStream(), never()).get(8, 8);
        verify(complex.getCaveBiomeStream(), never()).get(8, 8);
        verify(complex.getRegionStream(), never()).get(8, 8);
    }
}
