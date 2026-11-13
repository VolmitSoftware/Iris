package art.arcane.iris.modules.carve.field;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseStyle;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

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
    private static final String TUNNELS = "{\"tunnel\": {\"width\": {\"min\": 4, \"max\": 6}, \"spacing\": {\"horizontal\": 48, \"vertical\": 34}}, \"opensToSurface\": true}";
    private static final String OPEN_CAVERN = "{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3}}";

    private final NoiseFactory noise = new CarveTestWorld().resources().noise();

    /**
     * Each cave written with the new keys, and the size and hash of what the same cave written with the keys it replaced
     * carved before them (stretch, flatness, a plain coverage, no flare): the defaults reproduce it block for block.
     */
    private record Before(String cave, int carved, int hash) {
    }

    private static final List<Before> CARVED_BEFORE = List.of(
            new Before("{\"tunnel\": {\"width\": {\"min\": 4, \"max\": 6}, \"spacing\": {\"horizontal\": 48, \"vertical\": 32}}, \"opensToSurface\": true}",
                    232_825, 1_872_688_103),
            new Before("{\"tunnel\": {\"width\": {\"min\": 3, \"max\": 6}, \"spacing\": {\"horizontal\": 64, \"vertical\": 32}, \"coverage\": {\"share\": 0.5}}, \"opensToSurface\": true}",
                    48_888, 132_276_646),
            new Before("{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3, \"pillars\": {\"spacing\": 14, \"thickness\": 4}}}",
                    562_402, 1_318_811_955),
            new Before("{\"tunnel\": {\"width\": {\"min\": 3, \"max\": 5}, \"spacing\": {\"horizontal\": 40, \"vertical\": 32}, \"coverage\": {\"share\": 0.6}},"
                    + " \"cavern\": {\"size\": 32, \"height\": 14, \"coverage\": 0.12, \"pillars\": {\"spacing\": 12, \"thickness\": 3}}}",
                    290_404, 533_697_483),
            new Before("{\"cavern\": {\"size\": 110, \"height\": 40, \"coverage\": 0.18, \"pillars\": {\"spacing\": 26, \"thickness\": 5}}, \"fade\": 14, \"surfaceClearance\": 16}",
                    478_805, -890_025_259));

    @Test
    public void theDefaultsCarveBlockForBlockWhatTheReplacedKeysCarved() {
        for (Before before : CARVED_BEFORE) {
            BitSet carved = carve(before.cave(), SEED, false);

            assertEquals(before.cave(), before.carved(), carved.cardinality());
            assertEquals(before.cave(), before.hash(), carved.hashCode());
        }
    }

    @Test
    public void spelledOutStylesCarveRoughlyWhatTheirDefaultsCarve() {
        String defaults = "{\"tunnel\": {\"width\": {\"min\": 2, \"max\": 3}, \"spacing\": {\"horizontal\": 64, \"vertical\": 53}, \"coverage\": {\"share\": 0.4}}, \"surfaceClearance\": 6}";
        String spelledOut = """
                {"tunnel": {
                  "width": {"min": 2, "max": 3, "style": {"style": "SIMPLEX", "zoom": 1.2}},
                  "spacing": {"horizontal": 64, "vertical": 53},
                  "warp": {"style": "SIMPLEX", "zoom": 0.25, "multiplier": 8},
                  "coverage": {"share": 0.4, "style": {"style": "SIMPLEX", "zoom": 2.4}}},
                 "surfaceClearance": 6}
                """;

        int byDefaults = carve(defaults, SEED, false).cardinality();
        int bySpelledOut = carve(spelledOut, SEED, false).cardinality();

        assertWithin("spelled out against defaults", bySpelledOut, byDefaults, 0.2D);
    }

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
        BitSet carved = carve(tunnel("\"width\": {\"min\": 2, \"max\": 3}, \"spacing\": {\"horizontal\": 48, \"vertical\": 34}"), SEED, false);

        double largestShare = largestConnectedShare(carved);

        assertTrue("largest connected share " + largestShare, largestShare > 0.5D);
    }

    @Test
    public void widerSpacingCarvesSparserTunnels() {
        int close = carve(tunnel("\"spacing\": {\"horizontal\": 48, \"vertical\": 34}"), SEED, false).cardinality();
        int wide = carve(tunnel("\"spacing\": {\"horizontal\": 96, \"vertical\": 34}"), SEED, false).cardinality();

        assertTrue(close + " close against " + wide + " wide", wide < close * 0.75D);
    }

    @Test
    public void closerVerticalSpacingStacksMoreTunnels() {
        int far = carve(tunnel("\"spacing\": {\"horizontal\": 64, \"vertical\": 64}"), SEED, false).cardinality();
        int close = carve(tunnel("\"spacing\": {\"horizontal\": 64, \"vertical\": 24}"), SEED, false).cardinality();

        assertTrue(far + " far apart against " + close + " close", close > far * 1.4D);
    }

    @Test
    public void aFlatWarpLeavesTheTunnelsWhereTheSheetsMeet() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";
        BitSet flat = carve(tunnel(spacing + "\"warp\": {\"style\": \"FLAT\", \"multiplier\": 30}"), SEED, false);
        BitSet barelyMoved = carve(tunnel(spacing + "\"warp\": {\"style\": \"SIMPLEX\", \"multiplier\": 0.0001}"), SEED, false);
        BitSet swung = carve(tunnel(spacing + "\"warp\": {\"style\": \"SIMPLEX\", \"zoom\": 0.3, \"multiplier\": 16}"), SEED, false);

        assertTrue("a flat warp moves nothing: " + differenceShare(flat, barelyMoved), differenceShare(flat, barelyMoved) < 0.002D);
        assertTrue("a warp moves the tunnels: " + differenceShare(flat, swung), differenceShare(flat, swung) > 0.5D);
    }

    @Test
    public void aLargerWarpMultiplierSwingsTheTunnelsFurther() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";
        BitSet flat = carve(tunnel(spacing + "\"warp\": {}"), SEED, false);
        BitSet small = carve(tunnel(spacing + "\"warp\": {\"style\": \"SIMPLEX\", \"zoom\": 0.3, \"multiplier\": 1}"), SEED, false);
        BitSet large = carve(tunnel(spacing + "\"warp\": {\"style\": \"SIMPLEX\", \"zoom\": 0.3, \"multiplier\": 12}"), SEED, false);

        assertTrue(differenceShare(flat, small) < differenceShare(flat, large));
    }

    @Test
    public void aWidthStyleDecidesWhereTunnelsAreNarrowOrWide() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";
        BitSet slow = carve(tunnel(spacing + "\"width\": {\"min\": 2, \"max\": 8}"), SEED, false);
        BitSet styled = carve(tunnel(spacing + "\"width\": {\"min\": 2, \"max\": 8, \"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.2}}"), SEED, false);
        BitSet even = carve(tunnel(spacing + "\"width\": {\"min\": 5, \"max\": 5}"), SEED, false);

        assertTrue(differenceShare(slow, styled) > 0.2D);
        assertTrue(differenceShare(slow, even) > 0.2D);
    }

    @Test
    public void aHeightAsLargeAsTheWidthCarvesRoundTunnels() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";

        BitSet round = carve(tunnel(spacing + "\"width\": {\"min\": 3, \"max\": 6}"), SEED, false);
        BitSet ovalAsRound = carve(tunnel(spacing + "\"width\": {\"min\": 3, \"max\": 6}, \"height\": {\"min\": 3, \"max\": 6}"), SEED, false);

        assertEquals(round, ovalAsRound);
    }

    /**
     * Runs along an axis also cross sloping tunnels lengthwise, so they change less than the height does: a sloping
     * tunnel's vertical chord is partly its length, and its horizontal runs grow with its height.
     */
    @Test
    public void aHeightChangesHowTallTunnelsAreRatherThanHowWide() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";
        BitSet round = carve(tunnel(spacing + "\"width\": {\"min\": 6, \"max\": 6}"), SEED, false);
        BitSet tall = carve(tunnel(spacing + "\"width\": {\"min\": 6, \"max\": 6}, \"height\": {\"min\": 12, \"max\": 12}"), SEED, false);
        BitSet low = carve(tunnel(spacing + "\"width\": {\"min\": 6, \"max\": 6}, \"height\": {\"min\": 3, \"max\": 3}"), SEED, false);

        double tallHeight = meanRun(tall, 0, 1, 0) / meanRun(round, 0, 1, 0);
        double tallWidth = horizontalMeanRun(tall) / horizontalMeanRun(round);
        double lowHeight = meanRun(low, 0, 1, 0) / meanRun(round, 0, 1, 0);
        double lowWidth = horizontalMeanRun(low) / horizontalMeanRun(round);

        assertTrue("tall: height " + tallHeight + ", width " + tallWidth, tallHeight > 1.3D && tallWidth < 1.15D);
        assertTrue("low: height " + lowHeight + ", width " + lowWidth, lowHeight < 0.75D && lowWidth > 0.85D);
    }

    @Test
    public void aLowerCoverageShareLeavesMoreRockAndAStyleShapesWhere() {
        String spacing = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}, ";
        BitSet everywhere = carve(tunnel(spacing + "\"coverage\": {\"share\": 1}"), SEED, false);
        BitSet half = carve(tunnel(spacing + "\"coverage\": {\"share\": 0.5}"), SEED, false);
        BitSet styledHalf = carve(tunnel(spacing + "\"coverage\": {\"share\": 0.5, \"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.6}}"), SEED, false);

        assertTrue(half.cardinality() < everywhere.cardinality() * 0.8D);
        assertTrue(styledHalf.cardinality() < everywhere.cardinality() * 0.8D);
        assertTrue(differenceShare(half, styledHalf) > 0.2D);
    }

    @Test
    public void aRoomStyleIsScaledToTheRoomSize() {
        NoiseStyle cellular = TestSettings.of(NoiseStyle.class, "{\"style\": \"CELLULAR\"}");
        NoiseStyle simplex = TestSettings.of(NoiseStyle.class, "{\"style\": \"SIMPLEX\", \"zoom\": 7}");

        for (NoiseStyle style : List.of(cellular, simplex)) {
            for (double spacing : new double[]{30D, 150D}) {
                Field3 field = new CaveNoise(noise, SEED).scaledTo(style, spacing, spacing);

                double measured = FieldStats.featureBlocks(field, spacing).orElseThrow();

                assertWithin(style + " at " + spacing, measured, spacing, 0.15D);
            }
        }
    }

    @Test
    public void aCavernWithAStyleCarvesItsShareInOtherRooms() {
        String styled = "{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3, \"style\": {\"style\": \"CELLULAR\", \"fracture\": {\"style\": \"SIMPLEX\", \"multiplier\": 12}}}}";

        BitSet byDefault = carve(OPEN_CAVERN, SEED, false);
        BitSet byStyle = carve(styled, SEED, false);

        assertWithin("styled against default", byStyle.cardinality(), byDefault.cardinality(), 0.5D);
        assertTrue(differenceShare(byDefault, byStyle) > 0.5D);
    }

    @Test
    public void pillarsLeaveRockStandingInACavern() {
        BitSet withoutPillars = carve(OPEN_CAVERN, SEED, false);
        BitSet withPillars = carve(pillared("\"spacing\": 14, \"thickness\": 4"), SEED, false);

        assertTrue(withPillars.cardinality() < withoutPillars.cardinality());
        BitSet onlyWithPillars = (BitSet) withPillars.clone();
        onlyWithPillars.andNot(withoutPillars);
        assertTrue("pillars only ever keep rock", onlyWithPillars.isEmpty());
    }

    @Test
    public void pillarsWithoutVerticalAndFlareAreTheSameFromFloorToCeiling() {
        BitSet open = carve(OPEN_CAVERN, SEED, false);
        BitSet straight = carve(pillared("\"spacing\": 14, \"thickness\": 4, \"flare\": 1"), SEED, false);

        assertEquals(0, columnsPartlyKept(open, straight));
        assertTrue(open.cardinality() > straight.cardinality());
    }

    @Test
    public void flarePillarsWidenTowardsFloorAndCeiling() {
        BitSet straight = carve(pillared("\"spacing\": 14, \"thickness\": 4, \"flare\": 1"), SEED, false);
        BitSet flared = carve(pillared("\"spacing\": 14, \"thickness\": 4, \"flare\": 6"), SEED, false);
        BitSet open = carve(OPEN_CAVERN, SEED, false);

        assertTrue(flared.cardinality() < straight.cardinality());
        assertTrue(columnsPartlyKept(open, flared) > 0);
    }

    @Test
    public void verticalLetsPillarsChangeWithHeight() {
        BitSet open = carve(OPEN_CAVERN, SEED, false);
        BitSet changing = carve(pillared("\"spacing\": 14, \"thickness\": 4, \"flare\": 1, \"vertical\": 16"), SEED, false);

        assertTrue(columnsPartlyKept(open, changing) > 0);
        BitSet onlyWithPillars = (BitSet) changing.clone();
        onlyWithPillars.andNot(open);
        assertTrue("pillars only ever keep rock", onlyWithPillars.isEmpty());
    }

    @Test
    public void aPillarStyleMovesThePillars() {
        BitSet byDefault = carve(pillared("\"spacing\": 14, \"thickness\": 4"), SEED, false);
        BitSet byStyle = carve(pillared("\"spacing\": 14, \"thickness\": 4, \"style\": {\"style\": \"CELLULAR\"}"), SEED, false);
        BitSet open = carve(OPEN_CAVERN, SEED, false);

        assertNotEquals(byDefault, byStyle);
        assertTrue(byStyle.cardinality() < open.cardinality());
    }

    @Test
    public void aCaveOpensTheSurfaceOnlyWhereItMayAndKeepsItsRockElsewhere() {
        String cave = "{\"tunnel\": {\"width\": {\"min\": 5, \"max\": 7}, \"spacing\": {\"horizontal\": 32, \"vertical\": 23}}, \"opensToSurface\": true, \"surfaceClearance\": 10}";

        BitSet open = carve(cave, SEED, false);
        BitSet sealed = carve(cave, SEED, true);

        assertTrue("an open cave reaches the surface somewhere", highestCarvedY(open) == SURFACE_Y);
        assertTrue("a sealed cave keeps its clearance: " + highestCarvedY(sealed), highestCarvedY(sealed) <= SURFACE_Y - 10);
    }

    private static String tunnel(String keys) {
        return "{\"tunnel\": {" + keys + "}, \"opensToSurface\": true}";
    }

    private static String pillared(String pillarKeys) {
        return "{\"cavern\": {\"size\": 40, \"height\": 18, \"coverage\": 0.3, \"pillars\": {" + pillarKeys + "}}}";
    }

    private static void assertWithin(String what, double actual, double expected, double share) {
        assertTrue(what + ": " + actual + " against " + expected, Math.abs(actual - expected) <= expected * share);
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

    /** Share of the blocks either set carves that only one of them carves. */
    private static double differenceShare(BitSet first, BitSet second) {
        BitSet either = (BitSet) first.clone();
        either.or(second);
        BitSet onlyOne = (BitSet) first.clone();
        onlyOne.xor(second);
        return onlyOne.cardinality() / (double) Math.max(1, either.cardinality());
    }

    /** Columns where pillars keep some, but not all, of the blocks the open cavern carves. */
    private static int columnsPartlyKept(BitSet open, BitSet pillared) {
        int partly = 0;
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int kept = 0;
                int carvedOpen = 0;
                for (int y = 0; y < LAYERS; y++) {
                    int block = index(x, y, z);
                    if (!open.get(block)) {
                        continue;
                    }
                    carvedOpen++;
                    if (!pillared.get(block)) {
                        kept++;
                    }
                }
                if (kept > 0 && kept < carvedOpen) {
                    partly++;
                }
            }
        }
        return partly;
    }

    private static double horizontalMeanRun(BitSet carved) {
        return (meanRun(carved, 1, 0, 0) + meanRun(carved, 0, 0, 1)) / 2D;
    }

    /** Mean length of the unbroken runs of carved blocks along one axis. */
    private static double meanRun(BitSet carved, int stepX, int stepY, int stepZ) {
        long blocks = 0;
        long runs = 0;
        for (int block = carved.nextSetBit(0); block >= 0; block = carved.nextSetBit(block + 1)) {
            int x = block % SIZE;
            int z = (block / SIZE) % SIZE;
            int y = block / (SIZE * SIZE);
            blocks++;
            int previousX = x - stepX;
            int previousY = y - stepY;
            int previousZ = z - stepZ;
            boolean previousInside = previousX >= 0 && previousY >= 0 && previousZ >= 0;
            if (!previousInside || !carved.get(index(previousX, previousY, previousZ))) {
                runs++;
            }
        }
        return blocks / (double) Math.max(1, runs);
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
