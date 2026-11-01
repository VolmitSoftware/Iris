package art.arcane.iris.generation.hydrology.raster;

import org.junit.Test;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FluidSeamsTest {
    private static final long SEED = 4242L;
    private static final Map<String, String> LAVA_CRUST = Map.of("lava", "crust");

    @Test
    public void lavaSealsEveryColumnTouchingWater() {
        FluidSeams.Columns columns = columns(x -> x < 100 ? "lava" : "water", x -> false);
        String[] sealed = FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns);
        int spread = 0;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = 96 + lx;
                String seam = sealed[lz << 4 | lx];
                if (x == 99) {
                    assertEquals("touching column " + x + "," + lz, "crust", seam);
                } else if (x == 98) {
                    spread += seam == null ? 0 : 1;
                } else {
                    assertNull("column " + x + "," + lz, seam);
                }
            }
        }
        assertTrue("spread columns " + spread, spread > 0 && spread < 16);
    }

    @Test
    public void spreadReachesAcrossTheChunkEdge() {
        FluidSeams.Columns columns = columns(x -> x < 97 ? "lava" : "water", x -> false);
        int spread = 0;
        int rows = 0;
        for (int chunkZ = 0; chunkZ < 8; chunkZ++) {
            String[] west = FluidSeams.seal(LAVA_CRUST, SEED, 5, chunkZ, columns);
            String[] east = FluidSeams.seal(LAVA_CRUST, SEED, 6, chunkZ, columns);
            for (int lz = 0; lz < 16; lz++) {
                rows++;
                assertEquals("crust", east[lz << 4]);
                assertNull(west[lz << 4 | 14]);
                spread += west[lz << 4 | 15] == null ? 0 : 1;
            }
        }
        double fraction = spread / (double) rows;
        assertTrue("spread fraction at the chunk edge " + fraction, fraction > 0.45D && fraction < 0.75D);
    }

    @Test
    public void dryGroundBelowSeaLevelCountsAsTheSea() {
        String[] sea = FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns(x -> x < 100 ? "lava" : null, x -> x >= 100));
        for (int lz = 0; lz < 16; lz++) {
            assertEquals("crust", sea[lz << 4 | 3]);
        }
        assertNull(FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns(x -> x < 100 ? "lava" : null, x -> false)));
    }

    @Test
    public void nothingSealsWithoutASeamOrAnotherFluid() {
        assertNull(FluidSeams.seal(Map.of(), SEED, 6, 0, columns(x -> x < 100 ? "lava" : "water", x -> false)));
        assertNull(FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns(x -> "water", x -> true)));
        assertNull(FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns(x -> "lava", x -> false)));
        assertNull(FluidSeams.seal(LAVA_CRUST, SEED, 6, 0, columns(x -> x < 100 ? "lava" : "crust", x -> false)));
    }

    private static FluidSeams.Columns columns(IntFunction<String> fluid, IntPredicate sea) {
        return new FluidSeams.Columns() {
            @Override
            public String fluid(int x, int z) {
                return fluid.apply(x);
            }

            @Override
            public boolean sea(int x, int z) {
                return sea.test(x);
            }
        };
    }
}
