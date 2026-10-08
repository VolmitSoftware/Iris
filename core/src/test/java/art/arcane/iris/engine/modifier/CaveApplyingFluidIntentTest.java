package art.arcane.iris.engine.modifier;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaveApplyingFluidIntentTest {
    @Test
    public void explicitCavernIntentsOverrideExistingFluid() {
        MatterCavern airIntent = new MatterCavern(true, "", (byte) 0);
        MatterCavern fluidIntent = new MatterCavern(true, "", (byte) 1);
        MatterCavern lavaIntent = new MatterCavern(true, "", (byte) 2);
        MatterCavern forcedAirIntent = new MatterCavern(true, "", (byte) 3);
        PlatformBlockState existingFluid = mock(PlatformBlockState.class);
        PlatformBlockState fluid = mock(PlatformBlockState.class);
        PlatformBlockState lava = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(existingFluid.isFluid()).thenReturn(true);

        assertFalse(CaveApplying.hasExplicitCarveIntent(null));
        assertTrue(CaveApplying.shouldPreserveExistingFluid(airIntent, existingFluid));
        assertTrue(CaveApplying.isFluidIntent(fluidIntent));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(fluidIntent, existingFluid));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(lavaIntent, existingFluid));
        assertFalse(CaveApplying.shouldPreserveExistingFluid(forcedAirIntent, existingFluid));
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
        PlatformBlockState ore = mock(PlatformBlockState.class);
        PlatformBlockState ordinarySurface = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(ore.isOre()).thenReturn(true);
        when(ordinarySurface.isOre()).thenReturn(false);
        when(air.isSolid()).thenReturn(false);
        when(solid.isSolid()).thenReturn(true);

        assertTrue(CaveApplying.isUnsupportedSurfaceOre(ore, air));
        assertFalse(CaveApplying.isUnsupportedSurfaceOre(ore, solid));
        assertFalse(CaveApplying.isUnsupportedSurfaceOre(ordinarySurface, air));
    }
}
