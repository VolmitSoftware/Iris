package art.arcane.iris.studio.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class NoisePaletteTest {
    @Test
    public void invalidSamplesUseVisibleDiagnosticColor() {
        assertEquals(NoisePalette.INVALID_COLOR, NoisePalette.TERRAIN.color(Double.NaN));
        assertEquals(NoisePalette.INVALID_COLOR, NoisePalette.TERRAIN.color(Double.POSITIVE_INFINITY));
    }

    @Test
    public void paletteClipsValuesToItsDocumentedRange() {
        assertEquals(NoisePalette.SIGNED.color(-1D), NoisePalette.SIGNED.color(-10D));
        assertEquals(NoisePalette.SIGNED.color(1D), NoisePalette.SIGNED.color(10D));
        assertNotEquals(NoisePalette.SIGNED.color(-1D), NoisePalette.SIGNED.color(1D));
    }

    @Test
    public void finiteHotPathAndCachedDisplayColorsMatchCanonicalLookup() {
        for (NoisePalette palette : NoisePalette.values()) {
            for (int index = -200; index <= 200; index++) {
                double value = index / 100D;
                assertEquals(palette.color(value), palette.colorFinite(value));
                double normalized = (value - palette.minimum()) / (palette.maximum() - palette.minimum());
                assertEquals(
                        palette.colorNormalized(normalized),
                        palette.displayColorNormalized(normalized).getRGB() & 0xFFFFFF
                );
            }
        }
    }
}
