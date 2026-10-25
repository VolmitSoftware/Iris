package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class FineLatticeSourceTest {
    @Test
    public void windowLatticeReadsItsNodesFromTheFineLattice() {
        CountingTerrain terrain = new CountingTerrain();
        HeightLattice fine = new HeightLattice(terrain, 4, false, 1 << 16);
        HeightLattice window = new HeightLattice(new FineLatticeSource(fine, terrain), 8, false, 1 << 16);

        fine.fill(0, 0, 64, 64, new HydrologySampleBuffer(64 * 64));
        assertEquals(4096L, terrain.samples.get());
        HydrologySampleBuffer held = new HydrologySampleBuffer(32 * 32);
        window.fill(0, 0, 32, 32, held);
        assertEquals(4096L, terrain.samples.get());
        assertHeights(held, 0, 0, 32);

        HydrologySampleBuffer fresh = new HydrologySampleBuffer(16 * 16);
        window.fill(32, 0, 16, 16, fresh);
        assertEquals(4096L + 256L, terrain.samples.get());
        assertHeights(fresh, 32, 0, 16);
        fine.fill(64, 0, 32, 32, new HydrologySampleBuffer(32 * 32));
        assertEquals(4096L + 1024L, terrain.samples.get());

        FineLatticeSource source = new FineLatticeSource(fine, terrain);
        assertThrows(IllegalArgumentException.class, () -> source.sample(new int[]{8}, new int[]{8}, new HydrologySampleBuffer(1), true));
        assertThrows(IllegalArgumentException.class, () -> source.sample(new int[]{6}, new int[]{8}, new HydrologySampleBuffer(1), false));
    }

    private static void assertHeights(HydrologySampleBuffer samples, int cellX0, int cellZ0, int side) {
        for (int index = 0; index < side * side; index++) {
            int x = (cellX0 + index % side) * 8;
            int z = (cellZ0 + index / side) * 8;
            assertEquals(x + "," + z, Float.floatToRawIntBits(CountingTerrain.height(x, z)), Float.floatToRawIntBits(samples.height()[index]));
        }
    }

    private static final class CountingTerrain implements HydrologyTerrainSource {
        private final AtomicLong samples = new AtomicLong();

        private static float height(int x, int z) {
            return 64f + (float) Math.sin(x * 0.013D) * 9f + (float) Math.cos(z * 0.021D) * 7f;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            samples.addAndGet(xs.length);
            for (int index = 0; index < xs.length; index++) {
                out.height()[index] = height(xs[index], zs[index]);
                out.permission()[index] = HydrologySampleBuffer.ALLOW_STREAMS | HydrologySampleBuffer.ALLOW_PONDS;
                out.rainfall()[index] = 1f;
                out.pondDensity()[index] = 1f;
            }
        }

        @Override
        public int seaLevel() {
            return 40;
        }

        @Override
        public int minY() {
            return 0;
        }

        @Override
        public int maxY() {
            return 384;
        }
    }
}
