package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
import com.volmit.config.Level;
import com.volmit.placement.PlacementOwner;
import com.volmit.resource.ResourceId;
import com.volmit.world.BiomeSample;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static art.arcane.iris.engine.framework.StructurePlacementFixtures.placement;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructurePlacementScopeTest {
    private static final ResourceId CAVE_BIOME = ResourceId.parse("test:biome/caves");

    @Test
    public void caveBiomeContributesOnlyCaveAnchoredPlacements() {
        StructurePlacement surfacePlacement = new StructurePlacement();
        StructurePlacement cavePlacement = placement("anchor", StructureAnchorMode.CAVE_FLOOR);
        StructurePlacement invalidCaveScopePlacement = placement("anchor", StructureAnchorMode.SURFACE);
        StructurePlacement regionPlacement = new StructurePlacement();
        StructurePlacement dimensionPlacement = new StructurePlacement();
        StructureTestWorld world = new StructureTestWorld()
                .biome(StructureTestWorld.SURFACE_BIOME, surfacePlacement)
                .biome(CAVE_BIOME, cavePlacement, invalidCaveScopePlacement)
                .region(StructureTestWorld.REGION, regionPlacement)
                .dimension(dimensionPlacement, cavePlacement)
                .biomes((x, y, z) -> new BiomeSample(
                        StructureTestWorld.REGION, StructureTestWorld.SURFACE_BIOME, Optional.of(CAVE_BIOME)));

        List<StructurePlacement> placements = StructurePlacementScope.placementsAt(world, 0, 0);

        assertEquals(4, placements.size());
        assertTrue(placements.contains(surfacePlacement));
        assertTrue(placements.contains(cavePlacement));
        assertTrue(placements.contains(regionPlacement));
        assertTrue(placements.contains(dimensionPlacement));
        assertFalse(placements.contains(invalidCaveScopePlacement));
    }

    @Test
    public void theOwnerIsTheFirstLevelThatNamesThePlacement() {
        StructurePlacement surfacePlacement = new StructurePlacement();
        StructurePlacement cavePlacement = placement("anchor", StructureAnchorMode.CAVE_FLOOR);
        StructurePlacement regionPlacement = new StructurePlacement();
        StructurePlacement dimensionPlacement = new StructurePlacement();
        StructurePlacement undeclared = new StructurePlacement();
        StructureTestWorld world = new StructureTestWorld()
                .biome(StructureTestWorld.SURFACE_BIOME, surfacePlacement)
                .biome(CAVE_BIOME, cavePlacement)
                .region(StructureTestWorld.REGION, regionPlacement, surfacePlacement)
                .dimension(dimensionPlacement, cavePlacement)
                .biomes((x, y, z) -> new BiomeSample(
                        StructureTestWorld.REGION, StructureTestWorld.SURFACE_BIOME, Optional.of(CAVE_BIOME)));

        assertEquals(Optional.of(PlacementOwner.biome(StructureTestWorld.SURFACE_BIOME)),
                StructurePlacementScope.ownerOf(world, surfacePlacement, 0, 0));
        assertEquals(Optional.of(PlacementOwner.biome(CAVE_BIOME)), StructurePlacementScope.ownerOf(world, cavePlacement, 0, 0));
        assertEquals(Optional.of(PlacementOwner.region(StructureTestWorld.REGION)),
                StructurePlacementScope.ownerOf(world, regionPlacement, 0, 0));
        assertEquals(Optional.of(new PlacementOwner(Level.DIMENSION, StructureTestWorld.DIMENSION)),
                StructurePlacementScope.ownerOf(world, dimensionPlacement, 0, 0));
        assertEquals(Optional.empty(), StructurePlacementScope.ownerOf(world, undeclared, 0, 0));
    }

    @Test
    public void dimensionOnlyPlacementsDoNotResolveBiomeScopes() {
        StructurePlacement dimensionPlacement = new StructurePlacement();
        StructureTestWorld world = new StructureTestWorld().dimension(dimensionPlacement);

        List<StructurePlacement> placements = StructurePlacementScope.placementsAt(world, 0, 0);

        assertEquals(1, placements.size());
        assertTrue(placements.contains(dimensionPlacement));
        assertEquals(0, world.columnReads());
    }
}
