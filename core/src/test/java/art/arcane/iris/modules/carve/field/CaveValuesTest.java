package art.arcane.iris.modules.carve.field;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.Cave;
import art.arcane.iris.modules.carve.CaveValue;
import com.google.gson.JsonParseException;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseStyle;
import org.junit.Test;

import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Every number that shapes a cave may be a plain number, a range a smooth noise picks from, or a range with the style
 * that picks. Where a value's noise is at its low end the cave carves as its min would, and at its high end as its max
 * would. The places are found by reading the value's own noise, seeded by its name as the carver seeds it.
 */
public class CaveValuesTest {
    private static final long SEED = 91_277L;
    private static final int CHUNKS = 8;
    private static final int WIDE_CHUNKS = 16;
    /** Patches about 50 blocks across, so a few of each end fit in the carved area. */
    private static final String PATCHES = "{\"style\": \"SIMPLEX\", \"zoom\": 0.6}";
    /** Patches about 160 blocks across, wide enough to hold rooms of the size and height they pick. */
    private static final String BROAD_PATCHES = "{\"style\": \"SIMPLEX\", \"zoom\": 2}";
    private static final String TUNNEL_SPACING = "\"spacing\": {\"horizontal\": 48, \"vertical\": 34}";
    private static final String OPEN_CAVERN_KEYS = "\"size\": 40, \"height\": 18, \"coverage\": 0.3";
    /** Rooms tall enough for the finer wall noise that roughness weighs: under about 20 blocks it is finer than the lattice. */
    private static final String TALL_CAVERN_KEYS = "\"size\": 40, \"height\": 24, \"coverage\": 0.3";
    private static final double UNUSED_CHANGE_BLOCKS = 1D;

    private final NoiseFactory noise = new CarveTestWorld().resources().noise();

    private static String range(double min, double max) {
        return "{\"min\": " + min + ", \"max\": " + max + ", \"style\": " + PATCHES + "}";
    }

    private static String broadRange(double min, double max) {
        return "{\"min\": " + min + ", \"max\": " + max + ", \"style\": " + BROAD_PATCHES + "}";
    }

    private CarvedArea carve(String caveJson) {
        return CarvedArea.carve(noise, caveJson, SEED, CHUNKS);
    }

    private CarvedArea carveWide(String caveJson) {
        return CarvedArea.carve(noise, caveJson, SEED, WIDE_CHUNKS);
    }

    private static String tunnel(String keys) {
        return "{\"tunnel\": {" + TUNNEL_SPACING + ", " + keys + "}, \"opensToSurface\": true}";
    }

    private static String cavern(String keys) {
        return "{\"cavern\": {" + keys + "}}";
    }

    @Test
    public void aRangeWithEqualEndsCarvesWhatThePlainNumberCarves() {
        String plain = """
                {"tunnel": {"width": 5, "height": 7, "spacing": {"horizontal": 48, "vertical": 34}, "verticalWarp": 0.3,
                            "coverage": {"share": 0.6, "softness": 0.5}},
                 "cavern": {"size": 40, "height": 24, "coverage": 0.2, "roughness": 0.5,
                            "pillars": {"spacing": 16, "thickness": 4, "flare": 3, "vertical": 24}},
                 "depth": {"min": 20, "max": 140}, "fade": 10, "surfaceClearance": 9, "ceilingFade": 6}
                """;
        String equalEnds = """
                {"tunnel": {"width": {"min": 5, "max": 5, "style": {"style": "SIMPLEX"}}, "height": {"min": 7, "max": 7},
                            "spacing": {"horizontal": 48, "vertical": 34}, "verticalWarp": {"min": 0.3, "max": 0.3},
                            "coverage": {"share": {"min": 0.6, "max": 0.6}, "softness": {"min": 0.5, "max": 0.5}}},
                 "cavern": {"size": {"min": 40, "max": 40}, "height": {"min": 24, "max": 24}, "coverage": {"min": 0.2, "max": 0.2},
                            "roughness": {"min": 0.5, "max": 0.5},
                            "pillars": {"spacing": {"min": 16, "max": 16}, "thickness": {"min": 4, "max": 4},
                                        "flare": {"min": 3, "max": 3}, "vertical": {"min": 24, "max": 24}}},
                 "depth": {"min": {"min": 20, "max": 20}, "max": {"min": 140, "max": 140}}, "fade": {"min": 10, "max": 10},
                 "surfaceClearance": {"min": 9, "max": 9}, "ceilingFade": {"min": 6, "max": 6}}
                """;

        BitSet byPlainNumbers = carve(plain).carved;

        assertTrue(byPlainNumbers.cardinality() > 10_000);
        assertEquals(byPlainNumbers, carve(equalEnds).carved);
    }

    @Test
    public void theNewKeysAtTheirDefaultsCarveWhatTheConstantsTheyReplaceCarved() {
        String leftOut = "{\"tunnel\": {" + TUNNEL_SPACING + ", \"coverage\": 0.5}, \"cavern\": {" + TALL_CAVERN_KEYS + "}}";
        String spelledOut = "{\"tunnel\": {" + TUNNEL_SPACING + ", \"verticalWarp\": 0.5, \"coverage\": {\"share\": 0.5, \"softness\": 0.3}},"
                + " \"cavern\": {" + TALL_CAVERN_KEYS + ", \"roughness\": 0.3}, \"ceilingFade\": 8}";

        assertEquals(carve(leftOut).carved, carve(spelledOut).carved);
    }

    @Test
    public void eachNewKeyChangesTheCaveAwayFromItsDefault() {
        String tunnels = "\"coverage\": {\"share\": 0.5}";
        CarvedArea defaultTunnels = carve(tunnel(tunnels));
        CarvedArea defaultCavern = carve(cavern(TALL_CAVERN_KEYS));
        CarvedArea closedTunnels = carve("{\"tunnel\": {" + TUNNEL_SPACING + "}}");

        assertChanged("verticalWarp", defaultTunnels, carve(tunnel(tunnels + ", \"verticalWarp\": 0")));
        assertChanged("softness", defaultTunnels, carve(tunnel("\"coverage\": {\"share\": 0.5, \"softness\": 1}")));
        assertChanged("roughness", defaultCavern, carve(cavern(TALL_CAVERN_KEYS + ", \"roughness\": 0")));
        assertTrue("ceilingFade", closedTunnels.differenceShare(carve("{\"tunnel\": {" + TUNNEL_SPACING + "}, \"ceilingFade\": 40}"), block -> true) > 0.005D);
    }

    private static void assertChanged(String key, CarvedArea byDefault, CarvedArea changed) {
        double difference = byDefault.differenceShare(changed, block -> true);
        assertTrue(key + " changes " + difference + " of the cave", difference > 0.05D);
    }

    @Test
    public void aShareAloneIsShorthandForATunnelCoverage() {
        assertEquals(carve(tunnel("\"coverage\": {\"share\": 0.4}")).carved, carve(tunnel("\"coverage\": 0.4")).carved);
    }

    @Test
    public void aSofterCoverageThinsTunnelsOutOverALongerStretch() {
        int abrupt = carve(tunnel("\"coverage\": {\"share\": 0.5, \"softness\": 0}")).carved.cardinality();
        int soft = carve(tunnel("\"coverage\": {\"share\": 0.5, \"softness\": 1}")).carved.cardinality();

        assertTrue(abrupt + " abrupt against " + soft + " soft", soft < abrupt * 0.8D);
    }

    @Test
    public void aRoughnessOfZeroGivesSmoothWalls() {
        double smooth = wallShare(carve(cavern(TALL_CAVERN_KEYS + ", \"roughness\": 0")));
        double rough = wallShare(carve(cavern(TALL_CAVERN_KEYS + ", \"roughness\": 1")));

        assertTrue(smooth + " smooth against " + rough + " rough", rough > smooth * 1.1D);
    }

    @Test
    public void aVerticalWarpOfZeroKeepsTheWarpFromMovingTunnelsUpAndDown() {
        String warp = "\"warp\": {\"style\": \"SIMPLEX\", \"zoom\": 0.3, \"multiplier\": 30}";
        double level = heightChangePerBlock(carve(tunnel(warp + ", \"verticalWarp\": 0")));
        double bobbing = heightChangePerBlock(carve(tunnel(warp + ", \"verticalWarp\": 1")));

        assertTrue(level + " level against " + bobbing + " bobbing", bobbing > level * 1.3D);
    }

    /**
     * How far a tunnel rises or falls from one column to the next, on average: the middle heights of neighbouring
     * columns that each cross exactly one tunnel.
     */
    private static double heightChangePerBlock(CarvedArea area) {
        double[] middleY = new double[area.size * area.size];
        for (int x = 0; x < area.size; x++) {
            for (int z = 0; z < area.size; z++) {
                middleY[(z * area.size) + x] = middleOfOnlyRun(area, x, z);
            }
        }
        double change = 0D;
        long pairs = 0;
        for (int x = 0; x < area.size - 1; x++) {
            for (int z = 0; z < area.size - 1; z++) {
                double here = middleY[(z * area.size) + x];
                for (double next : new double[]{middleY[(z * area.size) + x + 1], middleY[((z + 1) * area.size) + x]}) {
                    if (!Double.isNaN(here) && !Double.isNaN(next)) {
                        change += Math.abs(next - here);
                        pairs++;
                    }
                }
            }
        }
        return change / Math.max(1, pairs);
    }

    private static double middleOfOnlyRun(CarvedArea area, int x, int z) {
        int runs = 0;
        int lowest = -1;
        int highest = -1;
        boolean inRun = false;
        for (int y = 0; y < CarvedArea.LAYERS; y++) {
            boolean carved = area.carved.get(area.index(x, y, z));
            if (carved && !inRun) {
                runs++;
                lowest = y;
            }
            if (carved) {
                highest = y;
            }
            inRun = carved;
        }
        return runs == 1 ? (lowest + highest) / 2D : Double.NaN;
    }

    @Test
    public void aValueWrittenInNoFormFailsSayingWhichFormsThereAre() {
        JsonParseException text = assertThrows(JsonParseException.class, () -> TestSettings.of(Cave.class, "{\"fade\": \"wide\"}"));
        JsonParseException misspelt = assertThrows(JsonParseException.class, () -> TestSettings.of(Cave.class, "{\"fade\": {\"min\": 1, \"mx\": 4}}"));
        JsonParseException halfRange = assertThrows(JsonParseException.class, () -> TestSettings.of(Cave.class, "{\"fade\": {\"min\": 1}}"));

        assertTrue(text.getMessage(), text.getMessage().contains("a number or {min, max}"));
        assertTrue(misspelt.getMessage(), misspelt.getMessage().contains("not mx"));
        assertTrue(halfRange.getMessage(), halfRange.getMessage().contains("needs both min and max"));
    }

    @Test
    public void valuesOutsideTheirBoundsAreReportedByTheirPlaceInTheFile() {
        Cave cave = TestSettings.of(Cave.class, """
                {"fade": 200, "tunnel": {"width": {"min": 0, "max": 6}, "coverage": {"share": 2}},
                 "cavern": {"pillars": {"flare": 0.5}}}
                """);

        assertEquals(List.of("fade is 200; it must be at most 128", "tunnel.width.min is 0; it must be at least 1",
                "tunnel.coverage.share is 2; it must be at most 1", "cavern.pillars.flare is 0.5; it must be at least 1"), cave.valuesOutOfBounds());
        assertEquals(List.of(), TestSettings.of(Cave.class, "{\"tunnel\": {}, \"cavern\": {\"pillars\": {}}}").valuesOutOfBounds());
    }

    @Test
    public void aRangeWithoutAStyleChangesSmoothlyWhereAStaticStyleJumps() {
        CaveValue smooth = TestSettings.of(CaveValue.class, "{\"min\": 0, \"max\": 1}");
        CaveValue jumping = TestSettings.of(CaveValue.class, "{\"min\": 0, \"max\": 1, \"style\": {\"style\": \"STATIC\"}}");

        double smoothStep = largestStep(ValueField.inColumns(smooth, new CaveNoise(noise, SEED), "fade", Cave.VALUE_CHANGE_BLOCKS));
        double jumpingStep = largestStep(ValueField.inColumns(jumping, new CaveNoise(noise, SEED), "fade", Cave.VALUE_CHANGE_BLOCKS));

        assertTrue("smooth range moves up to " + smoothStep + " per block", smoothStep < 0.03D);
        assertTrue("static style moves up to " + jumpingStep + " per block", jumpingStep > 0.3D);
    }

    private static double largestStep(ValueField value) {
        double largest = 0D;
        double previous = value.at(0, 0);
        double spread = 0D;
        double lowest = previous;
        double highest = previous;
        for (int x = 1; x < 4096; x++) {
            double current = value.at(x, x * 0.37D);
            largest = Math.max(largest, Math.abs(current - previous));
            lowest = Math.min(lowest, current);
            highest = Math.max(highest, current);
            previous = current;
        }
        spread = highest - lowest;
        assertTrue("the range is used: " + spread, spread > 0.9D);
        return largest;
    }

    /** A value of the whole cave is read per column, so each column at an end of its noise carves exactly as that end does. */
    @Test
    public void aVaryingCaveValueCarvesEachColumnExactlyAsItsLowOrHighNumberWould() {
        String tunnels = "{\"tunnel\": {" + TUNNEL_SPACING + ", \"width\": 6}, ";
        assertMatchesItsEndsExactly("fade", tunnels + "\"depth\": {\"min\": 30, \"max\": 120}, \"fade\": %s}", 2, 40);
        assertMatchesItsEndsExactly("surfaceClearance", tunnels + "\"surfaceClearance\": %s}", 2, 40);
        assertMatchesItsEndsExactly("ceilingFade", tunnels + "\"ceilingFade\": %s}", 0, 60);
        assertMatchesItsEndsExactly("depth.min", tunnels + "\"depth\": {\"min\": %s, \"max\": 150}}", 10, 90);
        assertMatchesItsEndsExactly("depth.max", tunnels + "\"depth\": {\"min\": 10, \"max\": %s}}", 60, 150);
    }

    private void assertMatchesItsEndsExactly(String name, String caveFormat, double min, double max) {
        CarvedArea byMin = carve(caveFormat.formatted(min));
        CarvedArea byMax = carve(caveFormat.formatted(max));
        CarvedArea byRange = carve(caveFormat.formatted(range(min, max)));
        ValueField share = columnShares(name);
        IntPredicate atMin = byRange.cached(block -> share.at(byRange.x(block), byRange.z(block)) == 0D);
        IntPredicate atMax = byRange.cached(block -> share.at(byRange.x(block), byRange.z(block)) == 1D);

        assertTrue(name + " differs between its ends", byMin.differenceShare(byMax, block -> true) > 0.01D);
        assertEquals(name + " at its min", 0D, byRange.differenceShare(byMin, atMin), 0D);
        assertEquals(name + " at its max", 0D, byRange.differenceShare(byMax, atMax), 0D);
        assertTrue(name + " carves at both ends", byRange.carvedShare(atMin) > 0D && byRange.carvedShare(atMax) > 0D);
    }

    @Test
    public void aVaryingTunnelValueCarvesLikeItsLowOrHighNumberWhereItsNoiseIsAtThatEnd() {
        assertFollowsItsEnds("tunnel.verticalWarp", false,
                tunnel("\"warp\": {\"style\": \"SIMPLEX\", \"zoom\": 0.3, \"multiplier\": 30}, \"verticalWarp\": %s"), 0, 1);
        assertFollowsItsEnds("tunnel.coverage.share", false, tunnel("\"coverage\": {\"share\": %s}"), 0.2, 0.9);
        assertFollowsItsEnds("tunnel.coverage.softness", false, tunnel("\"coverage\": {\"share\": 0.5, \"softness\": %s}"), 0, 1);
        assertFollowsItsEnds("tunnel.height", false, tunnel("\"width\": 6, \"height\": %s"), 3, 12);
    }

    @Test
    public void aVaryingCavernValueCarvesLikeItsLowOrHighNumberWhereItsNoiseIsAtThatEnd() {
        assertFollowsItsEnds("cavern.coverage", false, cavern("\"size\": 40, \"height\": 18, \"coverage\": %s"), 0.05, 0.4);
        assertFollowsItsEnds("cavern.roughness", false, cavern(TALL_CAVERN_KEYS + ", \"roughness\": %s"), 0, 1);
        assertFollowsItsEnds("cavern.pillars.thickness", true, cavern(OPEN_CAVERN_KEYS + ", \"pillars\": {\"spacing\": 14, \"thickness\": %s}"), 2, 6);
        assertFollowsItsEnds("cavern.pillars.flare", true, cavern(OPEN_CAVERN_KEYS + ", \"pillars\": {\"spacing\": 14, \"thickness\": 4, \"flare\": %s}"), 1, 8);
    }

    /**
     * Where the value's noise is at its min, the cave carves much more like the min than the max, and the other way
     * round where it is at its max. Values read on the lattice are interpolated near the edge of an end, so they only
     * come close; values read per column match the ends exactly.
     */
    private void assertFollowsItsEnds(String name, boolean inColumns, String caveFormat, double min, double max) {
        CarvedArea byMin = carve(caveFormat.formatted(min));
        CarvedArea byMax = carve(caveFormat.formatted(max));
        CarvedArea byRange = carve(caveFormat.formatted(range(min, max)));
        CaveValue shares = TestSettings.of(CaveValue.class, range(0, 1));
        CaveNoise caveNoise = new CaveNoise(noise, SEED);
        ValueField share = inColumns
                ? ValueField.inColumns(shares, caveNoise, name, UNUSED_CHANGE_BLOCKS)
                : ValueField.inBlocks(shares, caveNoise, name, UNUSED_CHANGE_BLOCKS);
        IntPredicate atMin = byRange.cached(block -> share.at(byRange.x(block), byRange.y(block), byRange.z(block)) == 0D);
        IntPredicate atMax = byRange.cached(block -> share.at(byRange.x(block), byRange.y(block), byRange.z(block)) == 1D);

        double minAgainstMin = byRange.differenceShare(byMin, atMin);
        double minAgainstMax = byRange.differenceShare(byMax, atMin);
        double maxAgainstMax = byRange.differenceShare(byMax, atMax);
        double maxAgainstMin = byRange.differenceShare(byMin, atMax);

        assertNotEquals(name + " differs between its ends", byMin.carved, byMax.carved);
        assertTrue(name + " at its min: " + minAgainstMin + " from min, " + minAgainstMax + " from max", minAgainstMin < minAgainstMax * 0.5D);
        assertTrue(name + " at its max: " + maxAgainstMax + " from max, " + maxAgainstMin + " from min", maxAgainstMax < maxAgainstMin * 0.5D);
    }

    @Test
    public void aVaryingTunnelWidthIsNarrowWhereItsNoiseIsLowAndWideWhereItIsHigh() {
        CaveNoise caveNoise = new CaveNoise(noise, SEED);
        for (int field = 0; field < 6; field++) {
            caveNoise.smooth(48, 34);
        }
        Field3 widthNoise = caveNoise.inBlocks(TestSettings.of(NoiseStyle.class, PATCHES));
        CarvedArea varying = carve(tunnel("\"width\": " + range(2, 10)));

        double narrow = varying.horizontalMeanRun(block -> ValueField.shareAt(widthNoise.at(varying.x(block), varying.y(block), varying.z(block))) == 0D);
        double wide = varying.horizontalMeanRun(block -> ValueField.shareAt(widthNoise.at(varying.x(block), varying.y(block), varying.z(block))) == 1D);

        assertTrue("narrow end runs " + narrow + ", wide end " + wide, wide > narrow * 2D);
    }

    @Test
    public void aCavernCoverageRangeFromZeroLeavesAreasWithoutRooms() {
        CarvedArea varying = carve(cavern("\"size\": 40, \"height\": 18, \"coverage\": " + range(0, 0.4)));
        ValueField share = ValueField.inBlocks(TestSettings.of(CaveValue.class, range(0, 1)), new CaveNoise(noise, SEED), "cavern.coverage", UNUSED_CHANGE_BLOCKS);

        double withoutRooms = varying.carvedShare(block -> share.at(varying.x(block), varying.y(block), varying.z(block)) == 0D);
        double withRooms = varying.carvedShare(block -> share.at(varying.x(block), varying.y(block), varying.z(block)) == 1D);

        assertTrue("carved where coverage is 0: " + withoutRooms, withoutRooms < 0.005D);
        assertTrue("carved where coverage is 0.4: " + withRooms, withRooms > 0.2D);
    }

    @Test
    public void aVaryingPillarThicknessKeepsMoreRockWhereItsNoiseIsHigh() {
        CarvedArea open = carve(cavern(OPEN_CAVERN_KEYS));
        CarvedArea varying = carve(cavern(OPEN_CAVERN_KEYS + ", \"pillars\": {\"spacing\": 14, \"thickness\": " + range(2, 7) + "}"));
        ValueField share = columnShares("cavern.pillars.thickness");

        double thin = keptShare(open, varying, block -> share.at(open.x(block), open.z(block)) == 0D);
        double thick = keptShare(open, varying, block -> share.at(open.x(block), open.z(block)) == 1D);

        assertTrue("pillars keep " + thin + " of the room where thin, " + thick + " where thick", thick > thin * 2D);
    }

    @Test
    public void aVaryingRoomSizeMakesLargerRoomsWhereItsNoiseIsHighWithoutLooseRock() {
        String keys = "\"height\": 20, \"coverage\": 0.2}, \"depth\": {\"min\": 10, \"max\": 150}";
        CarvedArea small = carveWide("{\"cavern\": {\"size\": 30, " + keys + "}");
        CarvedArea large = carveWide("{\"cavern\": {\"size\": 90, " + keys + "}");
        CarvedArea varying = carveWide("{\"cavern\": {\"size\": " + broadRange(30, 90) + ", " + keys + "}");
        ValueField share = ValueField.inBlocks(TestSettings.of(CaveValue.class, broadRange(0, 1)), new CaveNoise(noise, SEED), "cavern.size", UNUSED_CHANGE_BLOCKS);

        double smallRuns = varying.horizontalMeanRun(block -> share.at(varying.x(block), 80, varying.z(block)) <= 0.15D);
        double largeRuns = varying.horizontalMeanRun(block -> share.at(varying.x(block), 80, varying.z(block)) >= 0.85D);

        assertTrue("rooms run " + smallRuns + " where small, " + largeRuns + " where large", largeRuns > smallRuns * 1.3D);
        assertNoMoreLooseRockThanEitherEnd(varying, small, large);
    }

    /**
     * The coverage threshold is measured once on the whole room field, so where size, height or roughness vary the
     * carved share is only close to what the ends carve: within a fifth of their average over nine far-apart areas.
     */
    @Test
    public void aRangeInSizeHeightOrRoughnessCarvesCloseToTheShareItsEndsCarve() {
        String depth = ", \"coverage\": 0.2}, \"depth\": {\"min\": 10, \"max\": 150}}";
        assertCarvesBetween("size", "{\"cavern\": {\"height\": 20, \"size\": %s" + depth, 30, 90);
        assertCarvesBetween("height", "{\"cavern\": {\"size\": 40, \"height\": %s" + depth, 12, 40);
        assertCarvesBetween("roughness", "{\"cavern\": {\"size\": 40, \"height\": 24, \"roughness\": %s" + depth, 0, 1);
    }

    private void assertCarvesBetween(String key, String caveFormat, double min, double max) {
        double byMin = carvedInFarApartAreas(caveFormat.formatted(min));
        double byMax = carvedInFarApartAreas(caveFormat.formatted(max));
        double byRange = carvedInFarApartAreas(caveFormat.formatted("{\"min\": " + min + ", \"max\": " + max + "}"));

        assertWithin(key + " range against its ends' average", byRange, (byMin + byMax) / 2D, 0.2D);
    }

    private double carvedInFarApartAreas(String caveJson) {
        long carved = 0;
        for (int area = 0; area < 9; area++) {
            carved += CarvedArea.carveAt(noise, caveJson, SEED, CHUNKS, (area % 3) * 7000, (area / 3) * 7000).carved.cardinality();
        }
        return carved;
    }

    @Test
    public void aVaryingRoomHeightMakesTallerRoomsWhereItsNoiseIsHighWithoutLooseRock() {
        String keys = "\"size\": 40, \"coverage\": 0.2}, \"depth\": {\"min\": 10, \"max\": 150}";
        CarvedArea low = carveWide("{\"cavern\": {\"height\": 12, " + keys + "}");
        CarvedArea tall = carveWide("{\"cavern\": {\"height\": 40, " + keys + "}");
        CarvedArea varying = carveWide("{\"cavern\": {\"height\": " + broadRange(12, 40) + ", " + keys + "}");
        ValueField share = ValueField.inColumns(TestSettings.of(CaveValue.class, broadRange(0, 1)), new CaveNoise(noise, SEED), "cavern.height", UNUSED_CHANGE_BLOCKS);

        double lowRuns = varying.meanRun(0, 1, 0, block -> share.at(varying.x(block), varying.z(block)) == 0D);
        double tallRuns = varying.meanRun(0, 1, 0, block -> share.at(varying.x(block), varying.z(block)) == 1D);

        assertTrue("rooms run " + lowRuns + " high where low, " + tallRuns + " where tall", tallRuns > lowRuns * 1.4D);
        assertNoMoreLooseRockThanEitherEnd(varying, low, tall);
    }

    @Test
    public void aVaryingPillarSpacingSpreadsThePillarsWhereItsNoiseIsHigh() {
        CarvedArea open = carve(cavern(OPEN_CAVERN_KEYS));
        CarvedArea varying = carve(cavern(OPEN_CAVERN_KEYS + ", \"pillars\": {\"spacing\": " + range(10, 40) + ", \"thickness\": 4}"));
        ValueField share = columnShares("cavern.pillars.spacing");

        double close = keptShare(open, varying, block -> share.at(open.x(block), open.z(block)) == 0D);
        double far = keptShare(open, varying, block -> share.at(open.x(block), open.z(block)) == 1D);

        assertTrue("pillars keep " + close + " of the room where close, " + far + " where far apart", close > far * 4D);
    }

    @Test
    public void aVaryingPillarVerticalBreaksPillarsMoreOftenWhereItIsLow() {
        String pillars = OPEN_CAVERN_KEYS + ", \"pillars\": {\"spacing\": 14, \"thickness\": 4, \"flare\": 1, \"vertical\": ";
        CarvedArea open = carve(cavern(OPEN_CAVERN_KEYS));
        CarvedArea varying = carve(cavern(pillars + range(12, 60) + "}"));
        ValueField share = columnShares("cavern.pillars.vertical");

        int often = partlyKeptColumns(open, varying, (x, z) -> share.at(x, z) == 0D);
        int seldom = partlyKeptColumns(open, varying, (x, z) -> share.at(x, z) == 1D);
        int oftenColumns = columns(open, (x, z) -> share.at(x, z) == 0D);
        int seldomColumns = columns(open, (x, z) -> share.at(x, z) == 1D);

        double oftenShare = often / (double) oftenColumns;
        double seldomShare = seldom / (double) seldomColumns;
        assertTrue("pillars change in " + oftenShare + " of columns where vertical is low, " + seldomShare + " where high", oftenShare > seldomShare * 1.3D);
    }

    private ValueField columnShares(String name) {
        return ValueField.inColumns(TestSettings.of(CaveValue.class, range(0, 1)), new CaveNoise(noise, SEED), name, UNUSED_CHANGE_BLOCKS);
    }

    private static void assertNoMoreLooseRockThanEitherEnd(CarvedArea varying, CarvedArea first, CarvedArea second) {
        int floating = varying.floatingRockSmallerThan(200);
        int fragments = varying.carvedPiecesSmallerThan(30);
        int mostFloating = Math.max(first.floatingRockSmallerThan(200), second.floatingRockSmallerThan(200));
        int mostFragments = Math.max(first.carvedPiecesSmallerThan(30), second.carvedPiecesSmallerThan(30));

        assertTrue("floating rock " + floating + " against at most " + mostFloating + " at either end", floating <= mostFloating + 2);
        assertTrue("small air pockets " + fragments + " against at most " + mostFragments + " at either end", fragments <= (mostFragments * 2) + 5);
    }

    private static void assertWithin(String what, double actual, double expected, double share) {
        assertTrue(what + ": " + actual + " against " + expected, Math.abs(actual - expected) <= expected * share);
    }

    /** Share of the blocks the open cavern carves where {@code where} holds that pillars keep as rock. */
    private static double keptShare(CarvedArea open, CarvedArea pillared, IntPredicate where) {
        long openBlocks = 0;
        long kept = 0;
        for (int block = open.carved.nextSetBit(0); block >= 0; block = open.carved.nextSetBit(block + 1)) {
            if (!where.test(block)) {
                continue;
            }
            openBlocks++;
            if (!pillared.carved.get(block)) {
                kept++;
            }
        }
        return kept / (double) Math.max(1, openBlocks);
    }

    @FunctionalInterface
    private interface ColumnTest {
        boolean holds(int x, int z);
    }

    private static int columns(CarvedArea open, ColumnTest where) {
        int count = 0;
        for (int x = 0; x < open.size; x++) {
            for (int z = 0; z < open.size; z++) {
                if (where.holds(x, z) && openColumn(open, x, z) > 0) {
                    count++;
                }
            }
        }
        return Math.max(1, count);
    }

    private static int openColumn(CarvedArea open, int x, int z) {
        int carved = 0;
        for (int y = 0; y < CarvedArea.LAYERS; y++) {
            if (open.carved.get(open.index(x, y, z))) {
                carved++;
            }
        }
        return carved;
    }

    /** Columns where pillars keep some, but not all, of the blocks the open cavern carves. */
    private static int partlyKeptColumns(CarvedArea open, CarvedArea pillared, ColumnTest where) {
        int partly = 0;
        for (int x = 0; x < open.size; x++) {
            for (int z = 0; z < open.size; z++) {
                if (!where.holds(x, z)) {
                    continue;
                }
                int kept = 0;
                int carvedOpen = 0;
                for (int y = 0; y < CarvedArea.LAYERS; y++) {
                    int block = open.index(x, y, z);
                    if (!open.carved.get(block)) {
                        continue;
                    }
                    carvedOpen++;
                    if (!pillared.carved.get(block)) {
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

    /** Share of carved blocks with rock beside them: rougher walls have more. */
    private static double wallShare(CarvedArea area) {
        long walls = 0;
        for (int block = area.carved.nextSetBit(0); block >= 0; block = area.carved.nextSetBit(block + 1)) {
            int x = area.x(block);
            int y = area.y(block);
            int z = area.z(block);
            if (x == 0 || z == 0 || y == 0 || x == area.size - 1 || z == area.size - 1 || y == CarvedArea.LAYERS - 1) {
                continue;
            }
            if (!area.carved.get(area.index(x + 1, y, z)) || !area.carved.get(area.index(x - 1, y, z))
                    || !area.carved.get(area.index(x, y + 1, z)) || !area.carved.get(area.index(x, y - 1, z))
                    || !area.carved.get(area.index(x, y, z + 1)) || !area.carved.get(area.index(x, y, z - 1))) {
                walls++;
            }
        }
        return walls / (double) Math.max(1, area.carved.cardinality());
    }
}
