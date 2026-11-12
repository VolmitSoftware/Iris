package art.arcane.iris.modules.carve.field;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import com.volmit.noise.NoiseFactory;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaveCarverTest {
    private static final int CHUNKS = 8;
    private static final int SIZE = CHUNKS * 16;
    private static final int SURFACE_Y = 160;
    private static final int LAYERS = SURFACE_Y + 1;
    private static final int WORLD_BOTTOM_Y = 0;
    private static final int WORLD_HEIGHT = 256;
    private static final long SEED = 91_277L;
    private static final String TUNNELS = "{\"tunnel\": {\"width\": {\"min\": 4, \"max\": 6}, \"stretch\": 48}, \"opensToSurface\": true}";

    private final NoiseFactory noise = new CarveTestWorld().resources().noise();

    @Test
    public void theSameSeedCarvesTheSameCaveAndAnotherSeedADifferentOne() {
        BitSet first = carve(TUNNELS, SEED, false);

        assertFalse(first.isEmpty());
        assertEquals(first, carve(TUNNELS, SEED, false));
        assertNotEquals(first, carve(TUNNELS, SEED + 1, false));
    }

    @Test
    public void tunnelsJoinIntoOneNetwork() {
        BitSet carved = carve(TUNNELS, SEED, false);

        double largestShare = largestConnectedShare(carved);

        assertTrue("largest connected share " + largestShare, largestShare > 0.7D);
    }

    @Test
    public void thinTunnelsSurviveBetweenTheLatticePoints() {
        BitSet carved = carve("{\"tunnel\": {\"width\": {\"min\": 2, \"max\": 3}, \"stretch\": 48}, \"opensToSurface\": true}", SEED, false);

        double largestShare = largestConnectedShare(carved);

        assertTrue("largest connected share " + largestShare, largestShare > 0.5D);
    }

    @Test
    public void pillarsLeaveRockStandingInACavern() {
        String open = "{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3}}";
        String pillared = "{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3, \"pillars\": {\"spacing\": 14, \"thickness\": 4}}}";

        BitSet withoutPillars = carve(open, SEED, false);
        BitSet withPillars = carve(pillared, SEED, false);

        assertTrue(withPillars.cardinality() < withoutPillars.cardinality());
        BitSet onlyWithPillars = (BitSet) withPillars.clone();
        onlyWithPillars.andNot(withoutPillars);
        assertTrue("pillars only ever keep rock", onlyWithPillars.isEmpty());
    }

    @Test
    public void aCaveOpensTheSurfaceOnlyWhereItMayAndKeepsItsRockElsewhere() {
        String cave = "{\"tunnel\": {\"width\": {\"min\": 5, \"max\": 7}, \"stretch\": 32}, \"opensToSurface\": true, \"surfaceClearance\": 10}";

        BitSet open = carve(cave, SEED, false);
        BitSet sealed = carve(cave, SEED, true);

        assertTrue("an open cave reaches the surface somewhere", highestCarvedY(open) == SURFACE_Y);
        assertTrue("a sealed cave keeps its clearance: " + highestCarvedY(sealed), highestCarvedY(sealed) <= SURFACE_Y - 10);
    }

    private BitSet carve(String caveJson, long seed, boolean sealEveryColumn) {
        CaveCarver carver = new CaveCarver(TestSettings.of(Cave.class, caveJson), noise, seed, WORLD_BOTTOM_Y, WORLD_HEIGHT);
        int[] surfaceY = new int[256];
        Arrays.fill(surfaceY, SURFACE_Y);
        double[] weight = new double[256];
        Arrays.fill(weight, 1D);
        boolean[] sealed = new boolean[256];
        Arrays.fill(sealed, sealEveryColumn);
        BitSet carved = new BitSet(SIZE * SIZE * LAYERS);
        for (int chunkX = 0; chunkX < CHUNKS; chunkX++) {
            for (int chunkZ = 0; chunkZ < CHUNKS; chunkZ++) {
                int originX = chunkX * 16;
                int originZ = chunkZ * 16;
                carver.carve(originX, originZ, surfaceY, weight, sealed, (localX, y, localZ) -> carved.set(index(originX + localX, y, originZ + localZ)));
            }
        }
        return carved;
    }

    private static int index(int x, int y, int z) {
        return (y * SIZE * SIZE) + (z * SIZE) + x;
    }

    private static int highestCarvedY(BitSet carved) {
        return (carved.length() - 1) / (SIZE * SIZE);
    }

    private static double largestConnectedShare(BitSet carved) {
        BitSet seen = new BitSet(carved.size());
        ArrayDeque<Integer> open = new ArrayDeque<>();
        int largest = 0;
        for (int root = carved.nextSetBit(0); root >= 0; root = carved.nextSetBit(root + 1)) {
            if (seen.get(root)) {
                continue;
            }
            int size = 0;
            seen.set(root);
            open.add(root);
            while (!open.isEmpty()) {
                int block = open.poll();
                size++;
                int x = block % SIZE;
                int z = (block / SIZE) % SIZE;
                int y = block / (SIZE * SIZE);
                visit(carved, seen, open, x + 1, y, z);
                visit(carved, seen, open, x - 1, y, z);
                visit(carved, seen, open, x, y + 1, z);
                visit(carved, seen, open, x, y - 1, z);
                visit(carved, seen, open, x, y, z + 1);
                visit(carved, seen, open, x, y, z - 1);
            }
            largest = Math.max(largest, size);
        }
        return largest / (double) carved.cardinality();
    }

    private static void visit(BitSet carved, BitSet seen, ArrayDeque<Integer> open, int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= SIZE || z >= SIZE || y >= LAYERS) {
            return;
        }
        int block = index(x, y, z);
        if (carved.get(block) && !seen.get(block)) {
            seen.set(block);
            open.add(block);
        }
    }
}
