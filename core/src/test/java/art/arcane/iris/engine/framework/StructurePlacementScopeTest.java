package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.structure.StructureAnchorMode;
import art.arcane.iris.builtin.structure.StructurePlacement;
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
    public void dimensionOnlyPlacementsDoNotResolveBiomeScopes() {
        StructurePlacement dimensionPlacement = new StructurePlacement();
        StructureTestWorld world = new StructureTestWorld().dimension(dimensionPlacement);

        List<StructurePlacement> placements = StructurePlacementScope.placementsAt(world, 0, 0);

        assertEquals(1, placements.size());
        assertTrue(placements.contains(dimensionPlacement));
        assertEquals(0, world.columnReads());
    }
}
