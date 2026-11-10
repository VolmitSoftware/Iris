package art.arcane.iris.core.studio.noise;

import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NoiseLatticeTest {
    private static final double EXACT = 0D;
    private static final int TILE_SIZE = 32;
    private static final double HILL_WAVELENGTH_BLOCKS = 384D;
    private static final double HILL_HEIGHT_BLOCKS = 40D;
    private static final double SMOOTH_TOLERANCE_BLOCKS = 0.5D;
    private static final double FLOAT_TOLERANCE = 1e-4D;
    private static final NoiseProvider HILLS = (x, z) ->
            HILL_HEIGHT_BLOCKS * Math.sin(x * 2 * Math.PI / HILL_WAVELENGTH_BLOCKS) * Math.cos(z * 2 * Math.PI / HILL_WAVELENGTH_BLOCKS);

    @Test
    public void spacingIsAPowerOfTwoOfAtLeastFourPixelsAndOneBlock() {
        assertEquals(4D, NoiseLattice.spacingBlocks(1D), EXACT);
        assertEquals(16D, NoiseLattice.spacingBlocks(3D), EXACT);
        assertEquals(1D, NoiseLattice.spacingBlocks(0.0625D), EXACT);
        assertEquals(1D, NoiseLattice.spacingBlocks(0.25D), EXACT);
        assertEquals(2D, NoiseLattice.spacingBlocks(0.5D), EXACT);
    }

    @Test
    public void latticePointsAreTheSourceExactly() {
        NoiseProvider ramps = (x, z) -> x * x - 3 * z;
        float[] values = NoiseLattice.values(ramps, -64, 32, 1D, TILE_SIZE, () -> false);
        for (int pixelRow = 0; pixelRow < TILE_SIZE; pixelRow += 4) {
            for (int pixelColumn = 0; pixelColumn < TILE_SIZE; pixelColumn += 4) {
                assertEquals(ramps.noise(-64 + pixelColumn, 32 + pixelRow), values[pixelRow * TILE_SIZE + pixelColumn], FLOAT_TOLERANCE);
            }
        }
    }

    @Test
    public void smoothNoiseStaysCloseToTheExactNoiseAtEveryScale() {
        for (double blocksPerPixel : new double[]{0.0625D, 0.5D, 1D, 4D}) {
            NoiseTile tile = NoiseTile.sample(HILLS, -3, 2, blocksPerPixel, TILE_SIZE, true, () -> false);
            NoiseTile exact = NoiseTile.sample(HILLS, -3, 2, blocksPerPixel, TILE_SIZE, false, () -> false);
            for (int pixel = 0; pixel < tile.values().length; pixel++) {
                assertEquals("scale " + blocksPerPixel, exact.values()[pixel], tile.values()[pixel], SMOOTH_TOLERANCE_BLOCKS);
            }
        }
    }

    @Test
    public void tilesOfTheSameScaleMeetWithoutASeam() {
        NoiseTile whole = NoiseTile.sample(HILLS, -1, 0, 1D, 2 * TILE_SIZE, true, () -> false);
        NoiseTile west = NoiseTile.sample(HILLS, -2, 0, 1D, TILE_SIZE, true, () -> false);
        NoiseTile east = NoiseTile.sample(HILLS, -1, 0, 1D, TILE_SIZE, true, () -> false);
        for (int row = 0; row < TILE_SIZE; row++) {
            for (int column = 0; column < TILE_SIZE; column++) {
                assertEquals(whole.values()[row * 2 * TILE_SIZE + column], west.values()[row * TILE_SIZE + column], FLOAT_TOLERANCE);
                assertEquals(whole.values()[row * 2 * TILE_SIZE + TILE_SIZE + column], east.values()[row * TILE_SIZE + column], FLOAT_TOLERANCE);
            }
        }
    }

    @Test
    public void aTileAsksFarFewerPointsThanItHasPixels() {
        AtomicInteger asked = new AtomicInteger();
        NoiseProvider counting = (x, z) -> {
            asked.incrementAndGet();
            return 0;
        };
        NoiseLattice.values(counting, 0, 0, 1D, TILE_SIZE, () -> false);
        assertTrue(asked.get() * 8 < TILE_SIZE * TILE_SIZE);
    }

    @Test
    public void stopsWhenNoLongerWanted() {
        assertThrows(CancellationException.class, () -> NoiseLattice.values((x, z) -> 0, 0, 0, 1D, TILE_SIZE, () -> true));
    }
}
