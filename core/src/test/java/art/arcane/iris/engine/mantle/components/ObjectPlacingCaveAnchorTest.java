package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.Cavern;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectPlacingCaveAnchorTest {
    private static final byte AIR = 0;
    private static final byte WATER = 1;
    private static final byte LAVA = 2;

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
        assertFalse(CaveAnchors.acceptsFluid(false, cavern(WATER), 20, 8));
        assertFalse(CaveAnchors.acceptsFluid(false, cavern(LAVA), 20, 8));
        assertFalse(CaveAnchors.acceptsFluid(false, cavern(AIR), 8, 8));
        assertTrue(CaveAnchors.acceptsFluid(false, cavern(AIR), 9, 8));
    }

    @Test
    public void explicitWetAndForcedAirPlacementsRemainEligible() {
        assertTrue(CaveAnchors.acceptsFluid(true, cavern(WATER), 20, 8));
        assertTrue(CaveAnchors.acceptsFluid(false, cavern(Cavern.FORCED_AIR_LIQUID), 0, 8));
        assertFalse(CaveAnchors.acceptsFluid(true, Optional.empty(), 20, 8));
    }

    private static Optional<Cavern> cavern(byte liquid) {
        return Optional.of(new Cavern(liquid == AIR, liquid));
    }

    private static ResourceId biome(String key) {
        return new ResourceId("test", key);
    }
}
