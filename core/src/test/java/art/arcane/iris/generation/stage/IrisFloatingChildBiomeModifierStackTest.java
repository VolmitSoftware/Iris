package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisFloatingChildBiomeModifierStackTest {
    @Test
    public void floatingPaletteSeedDependsOnWorldAndBiomeInsteadOfColumnVisitOrder() {
        IrisFloatingChildBiomes first = new IrisFloatingChildBiomes().setBiome("magnetics/glass-shard");
        IrisFloatingChildBiomes repeated = new IrisFloatingChildBiomes().setBiome("magnetics/glass-shard");
        IrisFloatingChildBiomes other = new IrisFloatingChildBiomes().setBiome("other/floating");
        RNG world = new RNG(7191L ^ 0x7EB0A73F1DCE514DL);
        long expected = IrisFloatingChildBiomeModifier.paletteRng(world, first).getSeed();
        for (int column = -256; column < 256; column++) {
            world.nextLong();
            assertEquals(expected, IrisFloatingChildBiomeModifier.paletteRng(world, repeated).getSeed());
        }
        assertNotEquals(expected, IrisFloatingChildBiomeModifier.paletteRng(world, other).getSeed());
        assertNotEquals(expected, IrisFloatingChildBiomeModifier.paletteRng(new RNG(8191L), first).getSeed());
    }

    @Test
    public void floatingBiomeMarkersSkipStackProtectedCells() {
        DimensionStackLayout layout = mock(DimensionStackLayout.class);
        when(layout.isHostFeatureProtectedY(60)).thenReturn(true);
        when(layout.isHostFeatureProtectedY(30)).thenReturn(false);

        assertFalse(IrisFloatingChildBiomeModifier.shouldWriteHostBiomeMarker(layout, 60));
        assertTrue(IrisFloatingChildBiomeModifier.shouldWriteHostBiomeMarker(layout, 30));
        assertTrue(IrisFloatingChildBiomeModifier.shouldWriteHostBiomeMarker(null, 60));
    }
}
