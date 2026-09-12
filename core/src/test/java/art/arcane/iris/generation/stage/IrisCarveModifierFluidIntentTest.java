package art.arcane.iris.generation.stage;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCarveModifierFluidIntentTest {
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

        assertFalse(IrisCarveModifier.hasExplicitCarveIntent(null));
        assertTrue(IrisCarveModifier.shouldPreserveExistingFluid(airIntent, existingFluid));
        assertTrue(IrisCarveModifier.isFluidIntent(fluidIntent));
        assertFalse(IrisCarveModifier.shouldPreserveExistingFluid(fluidIntent, existingFluid));
        assertFalse(IrisCarveModifier.shouldPreserveExistingFluid(lavaIntent, existingFluid));
        assertFalse(IrisCarveModifier.shouldPreserveExistingFluid(forcedAirIntent, existingFluid));
        assertNull(IrisCarveModifier.resolveExplicitCarveState(null, fluid, lava, air));
        assertSame(fluid, IrisCarveModifier.resolveExplicitCarveState(fluidIntent, fluid, lava, air));
        assertSame(lava, IrisCarveModifier.resolveExplicitCarveState(lavaIntent, fluid, lava, air));
        assertSame(air, IrisCarveModifier.resolveExplicitCarveState(forcedAirIntent, fluid, lava, air));
        assertNull(IrisCarveModifier.resolveExplicitCarveState(airIntent, fluid, lava, air));
    }

    @Test
    public void defaultLavaIncludesConfiguredBoundary() {
        assertTrue(IrisCarveModifier.usesDefaultLava(18, 17));
        assertTrue(IrisCarveModifier.usesDefaultLava(18, 18));
        assertFalse(IrisCarveModifier.usesDefaultLava(18, 19));
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

        assertTrue(IrisCarveModifier.isUnsupportedSurfaceOre(ore, air));
        assertFalse(IrisCarveModifier.isUnsupportedSurfaceOre(ore, solid));
        assertFalse(IrisCarveModifier.isUnsupportedSurfaceOre(ordinarySurface, air));
    }
}
