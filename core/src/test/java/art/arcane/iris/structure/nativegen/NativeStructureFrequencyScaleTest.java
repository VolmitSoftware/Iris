package art.arcane.iris.structure.nativegen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class NativeStructureFrequencyScaleTest {
    @Test
    public void netherRandomSpreadSetsResolveToNearestLegalSpacing() {
        NativeStructureFrequencyScale complexes = NativeStructureFrequencyScale.randomSpread(
                1F, 27, 4, 1.1D);
        NativeStructureFrequencyScale portals = NativeStructureFrequencyScale.randomSpread(
                1F, 40, 15, 1.1D);
        NativeStructureFrequencyScale fossils = NativeStructureFrequencyScale.randomSpread(
                1F, 2, 1, 1.1D);

        assertEquals(26, complexes.spacing());
        assertEquals(38, portals.spacing());
        assertEquals(2, fossils.spacing());
        assertEquals(1F, complexes.frequency(), 0F);
    }

    @Test
    public void probabilityScalesBeforeIntegerSpacing() {
        NativeStructureFrequencyScale increased = NativeStructureFrequencyScale.randomSpread(
                0.5F, 32, 8, 1.5D);
        NativeStructureFrequencyScale decreased = NativeStructureFrequencyScale.randomSpread(
                1F, 32, 8, 0.25D);

        assertEquals(32, increased.spacing());
        assertEquals(0.75F, increased.frequency(), 0F);
        assertEquals(32, decreased.spacing());
        assertEquals(0.25F, decreased.frequency(), 0F);
    }

    @Test
    public void invalidPlacementInputsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructureFrequencyScale.randomSpread(1F, 8, 8, 1.1D));
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructureFrequencyScale.randomSpread(1F, 32, 8, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> NativeStructureFrequencyScale.probability(1F, 17D));
    }
}
