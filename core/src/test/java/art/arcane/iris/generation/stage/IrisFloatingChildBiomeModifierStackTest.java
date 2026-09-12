package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.DimensionStackLayout;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisFloatingChildBiomeModifierStackTest {
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
