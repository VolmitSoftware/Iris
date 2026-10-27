package art.arcane.iris.generation.noise;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerrainBatchScratchTest {
    @Test
    public void integerLatticesDistributeWithoutLongLinearProbeClusters() throws Exception {
        TerrainBatchScratch scratch = new TerrainBatchScratch();
        for (int origin : new int[]{0, -1536, 1536, 16_777_212}) {
            scratch.clearLattice(4096);
            for (int index = 0; index < 1024; index++) {
                double x = origin + (index & 31) * 6D;
                double z = origin + (index >> 5) * 6D;
                assertEquals(index, scratch.lattice(x, z));
            }
            assertEquals(1024, scratch.latticeCount());
            for (int index = 1023; index >= 0; index--) {
                double x = origin + (index & 31) * 6D;
                double z = origin + (index >> 5) * 6D;
                assertEquals(index, scratch.lattice(x, z));
                assertEquals(x, scratch.latticeX(index), 0D);
                assertEquals(z, scratch.latticeZ(index), 0D);
            }
            assertTrue("clustered lattice at " + origin, longestCluster(scratch) < 32);
        }
    }

    @Test
    public void rawCoordinateIdentityAndInsertionOrderSurviveRehashingAndReset() {
        TerrainBatchScratch scratch = new TerrainBatchScratch();
        long[] bits = {0L, Long.MIN_VALUE, Double.doubleToRawLongBits(1D), Double.doubleToRawLongBits(-1D),
                Double.doubleToRawLongBits(0.125D), Double.doubleToRawLongBits(-0.125D),
                Double.doubleToRawLongBits(Double.POSITIVE_INFINITY), Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY),
                0x7FF8000000000001L, 0x7FF8000000000002L};
        for (int pass = 0; pass < 2; pass++) {
            scratch.clearLattice(bits.length * bits.length);
            for (int x = 0; x < bits.length; x++) {
                for (int z = 0; z < bits.length; z++) {
                    int expected = x * bits.length + z;
                    double worldX = Double.longBitsToDouble(bits[x]);
                    double worldZ = Double.longBitsToDouble(bits[z]);
                    assertEquals(expected, scratch.lattice(worldX, worldZ));
                    assertEquals(expected, scratch.lattice(worldX, worldZ));
                    assertEquals(bits[x], Double.doubleToRawLongBits(scratch.latticeX(expected)));
                    assertEquals(bits[z], Double.doubleToRawLongBits(scratch.latticeZ(expected)));
                }
            }
            assertEquals(bits.length * bits.length, scratch.latticeCount());
        }
    }

    private static int longestCluster(TerrainBatchScratch scratch) throws Exception {
        Field field = TerrainBatchScratch.class.getDeclaredField("latticeSlots");
        field.setAccessible(true);
        int[] slots = (int[]) field.get(scratch);
        int longest = 0;
        int length = 0;
        for (int index = 0; index < slots.length * 2; index++) {
            length = slots[index % slots.length] == 0 ? 0 : length + 1;
            longest = Math.max(longest, length);
        }
        return longest;
    }
}
