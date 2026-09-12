package art.arcane.iris.generation.stage;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.hydrology.IrisRiverMaterialConfig;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisCarveModifierHydrologyMaterialTest {
    private final PlatformBlockState biomeLayer = mock(PlatformBlockState.class);
    private final PlatformBlockState painted = mock(PlatformBlockState.class);
    private final IrisMaterialPalette palette = mock(IrisMaterialPalette.class);
    private final IrisData data = mock(IrisData.class);
    private final RNG rng = new RNG(13L);

    public IrisCarveModifierHydrologyMaterialTest() {
        when(palette.get(same(rng), eq(48.0), eq(31.0), eq(-9.0), same(data))).thenReturn(painted);
    }

    @Test
    public void undergroundBedMaterialPaintsTheFloorUnderAHydrologyCell() {
        IrisRiverMaterialConfig material = enabled(2);
        HydrologyCaveCell wet = HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE);

        assertSame(painted, paint(material, wet, 0));
        assertSame(painted, paint(material, wet, 1));
        assertSame(biomeLayer, paint(material, wet, 2));
        assertSame(painted, paint(material, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR), 0));
    }

    @Test
    public void undergroundBedMaterialSkipsSealGuardsAndNonHydrologyFloors() {
        IrisRiverMaterialConfig material = enabled(3);

        assertSame(biomeLayer, paint(material, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD), 0));
        assertSame(biomeLayer, paint(material, null, 0));
        verify(palette, never()).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    public void disabledUndergroundBedMaterialKeepsTheBiomeLayers() {
        HydrologyCaveCell wet = HydrologyCaveCell.of(HydrologyCaveAction.WET_SOURCE);
        IrisRiverMaterialConfig disabled = new IrisRiverMaterialConfig().setPalette(palette).setDepth(3);

        for (int index = 0; index < 4; index++) {
            assertSame(biomeLayer, paint(disabled, wet, index));
            assertSame(biomeLayer, paint(null, wet, index));
        }

        verify(palette, never()).get(any(RNG.class), anyDouble(), anyDouble(), anyDouble(), any());
    }

    private IrisRiverMaterialConfig enabled(int depth) {
        return new IrisRiverMaterialConfig().setEnabled(true).setPalette(palette).setDepth(depth);
    }

    private PlatformBlockState paint(IrisRiverMaterialConfig material, HydrologyCaveCell floorHydrology, int index) {
        return IrisCarveModifier.paintUndergroundBedMaterial(
                biomeLayer, material, floorHydrology, index, rng, 48, 31, -9, data);
    }
}
