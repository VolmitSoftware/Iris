package art.arcane.iris.structure.placement;

import art.arcane.iris.structure.object.IrisObjectPlacement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureLootPlacementTest {
    @Test
    public void createsPiecePlacementWithAuthoredLootOrderAndDefaultWeights() {
        IrisStructure structure = new IrisStructure();
        structure.getLoot().add("village_common");
        structure.getLoot().add("village_rare");

        IrisObjectPlacement placement = structure.createLootPlacement("pieces/village_house");

        assertEquals(1, placement.getPlace().size());
        assertEquals("pieces/village_house", placement.getPlace().get(0));
        assertEquals(2, placement.getLoot().size());
        assertEquals("village_common", placement.getLoot().get(0).getName());
        assertEquals(1, placement.getLoot().get(0).getWeight());
        assertTrue(placement.getLoot().get(0).getFilter().isEmpty());
        assertEquals("village_rare", placement.getLoot().get(1).getName());
        assertEquals(1, placement.getLoot().get(1).getWeight());
        assertTrue(placement.getLoot().get(1).getFilter().isEmpty());
        assertFalse(placement.isOverrideGlobalLoot());
    }

    @Test
    public void createsEmptyLootPlacementWhenStructureHasNoLoot() {
        IrisObjectPlacement placement = new IrisStructure().createLootPlacement("pieces/empty_house");

        assertEquals("pieces/empty_house", placement.getPlace().get(0));
        assertTrue(placement.getLoot().isEmpty());
        assertFalse(placement.isOverrideGlobalLoot());
    }
}
