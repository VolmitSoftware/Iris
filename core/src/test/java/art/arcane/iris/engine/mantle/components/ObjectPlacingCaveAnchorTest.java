package art.arcane.iris.engine.mantle.components;

import com.volmit.mantle.CarvedVoid;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectPlacingCaveAnchorTest {

    @Test
    public void biomeOwnedPlacementsRejectForeignCaveBands() {
        ResourceId frozen = biome("carving/ice");
        ResourceId deepDark = biome("carving/standard-deepdark");
        ResourceId surface = biome("frozen/ice-spikes");

        assertFalse(CaveAnchors.biomeConflicts(frozen, surface, frozen));
        assertTrue(CaveAnchors.biomeConflicts(deepDark, surface, frozen));
        assertFalse(CaveAnchors.biomeConflicts(deepDark, surface, null));
    }

    @Test
    public void dryPlacementsRejectFluidAndDefaultLavaCells() {
        assertFalse(CaveAnchors.acceptsFluid(false, Optional.of(CarvedVoid.FLUID), 20, 8));
        assertFalse(CaveAnchors.acceptsFluid(false, Optional.of(CarvedVoid.LAVA), 20, 8));
        assertFalse(CaveAnchors.acceptsFluid(false, Optional.of(CarvedVoid.OPEN), 8, 8));
        assertTrue(CaveAnchors.acceptsFluid(false, Optional.of(CarvedVoid.OPEN), 9, 8));
    }

    @Test
    public void explicitWetAndForcedAirPlacementsRemainEligible() {
        assertTrue(CaveAnchors.acceptsFluid(true, Optional.of(CarvedVoid.FLUID), 20, 8));
        assertTrue(CaveAnchors.acceptsFluid(false, Optional.of(CarvedVoid.AIR), 0, 8));
        assertFalse(CaveAnchors.acceptsFluid(true, Optional.empty(), 20, 8));
    }

    private static ResourceId biome(String key) {
        return new ResourceId("test", key);
    }
}
