package art.arcane.iris.engine.modifier;

import art.arcane.volmlib.util.matter.MatterCavern;
import com.volmit.block.BlockState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CaveApplyingFluidIntentTest {
    @Test
    public void explicitCavernIntentsOverrideExistingFluid() {
        MatterCavern airIntent = new MatterCavern(true, "", (byte) 0);
        MatterCavern fluidIntent = new MatterCavern(true, "", (byte) 1);
        MatterCavern lavaIntent = new MatterCavern(true, "", (byte) 2);
        MatterCavern forcedAirIntent = new MatterCavern(true, "", (byte) 3);
        BlockState fluid = new BlockState("minecraft:water");
        BlockState lava = new BlockState("minecraft:lava");
        BlockState air = new BlockState("minecraft:cave_air");

        assertFalse(CaveApplying.hasExplicitCarveIntent(null));
        assertTrue(CaveApplying.shouldPreserveExistingFluid(airIntent, true));
        assertTrue(CaveApplying.isFluidIntent(fluidIntent));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(fluidIntent, true));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(lavaIntent, true));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(forcedAirIntent, true));
        assertFalse("nothing to preserve where there is no fluid", CaveApplying.shouldPreserveExistingFluid(airIntent, false));
        assertNull(CaveApplying.resolveExplicitCarveState(null, fluid, lava, air));
        assertSame(fluid, CaveApplying.resolveExplicitCarveState(fluidIntent, fluid, lava, air));
        assertSame(lava, CaveApplying.resolveExplicitCarveState(lavaIntent, fluid, lava, air));
        assertSame(air, CaveApplying.resolveExplicitCarveState(forcedAirIntent, fluid, lava, air));
        assertNull(CaveApplying.resolveExplicitCarveState(airIntent, fluid, lava, air));
    }

    @Test
    public void defaultLavaIncludesConfiguredBoundary() {
        assertTrue(CaveApplying.usesDefaultLava(18, 17));
        assertTrue(CaveApplying.usesDefaultLava(18, 18));
        assertFalse(CaveApplying.usesDefaultLava(18, 19));
    }

    @Test
    public void unsupportedSurfaceOreIsRemovedOnlyOverCarvedAir() {
        assertTrue(CaveApplying.isUnsupportedSurfaceOre(true, false));
        assertFalse(CaveApplying.isUnsupportedSurfaceOre(true, true));
        assertFalse(CaveApplying.isUnsupportedSurfaceOre(false, false));
    }
}
