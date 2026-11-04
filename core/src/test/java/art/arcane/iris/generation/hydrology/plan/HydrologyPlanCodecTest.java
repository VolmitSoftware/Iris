package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HydrologyPlanCodecTest {
    private static final long SEED = 1337L;
    private static final float NAN_PAYLOAD = Float.intBitsToFloat(0x7fc00123);
    private static final float NEGATIVE_NAN = Float.intBitsToFloat(0xffa00001);
    private static final WindowKey LAND = new WindowKey(4, 4);
    private static final int FINGERPRINT_OFFSET = Integer.BYTES + 1;
    private static final String PINNED_WINDOW = """
            4948595057000000000000000000000002fffffffd000000010000000000000029000000000000006100000000444b20
            0000000006000000004080000041000000414000004180000041a00000c1000000c0c00000c0800000c0000000000000
            0040000000000000480000004700000046000000420000003f0000003e3f8000003fa000003fc000003fe00000400000
            00402000003f8000003f8000003fc0000040000000400000003f8000000001020304050000000000000004000000000000000500
            00000046000000420000000cfffffffc428d0000fffffffc000000080000000400000000000000000000000000000000
            0000002900000000ffffffff0000000afffffffa0000000000000006010000004a000000470000001e00000008429480
            0000000003fffffffd000000010000004000000001000000018000000000000001ffffffffffffffff00000001000000
            0577617465720000001c000000060000000000000007020000004400000040ffffffec0000001042898000fffffffc00
            00000800000004000000000000000000000000000000000000002900000002000000046c617661ffffffea0000000e00
            0000000000000803000000500000003c00000040ffffffc042a10000fffffffc00000008000000040000000000000000
            00000000ffffffffffffffff00000000ffffffff0000003cffffffc4
            """;

    @Test
    public void codecRoundTripsBitExact() {
        HydrologySettings settings = settings(new IrisHydrology());
        List<HydrologyTier> tiers = settings.tiers();
        Reach odd = new Reach(-9_000_000_000L, tiers.get(2), -1L,
                new float[]{-0f, Float.MIN_VALUE, NAN_PAYLOAD, 1e-40f, -4096.75f, Float.MAX_VALUE},
                new float[]{Float.NEGATIVE_INFINITY, 0f, -0f, NEGATIVE_NAN, 3.25f, -1f},
                new int[]{Integer.MIN_VALUE, -64, 0, 63, 319, Integer.MAX_VALUE},
                new float[]{0.5f, 1f, 2.75f, NAN_PAYLOAD, 12f, 0f},
                new float[]{1f, Float.POSITIVE_INFINITY, 0.4f, 1e-42f, -0f, 3f},
                Arrays.copyOf(ReachStyle.values(), 6), NEGATIVE_NAN);
        Reach plain = new Reach(77L, tiers.get(0), -9_000_000_000L, new float[]{10f, 11.5f}, new float[]{-3f, -2.5f}, new int[]{70, 69},
                new float[]{0.5f, 0.75f}, new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.MOUTH}, 1501.25f).withStraightFalls(new int[]{0});
        List<Pond> ponds = new ArrayList<>();
        PondKind[] kinds = PondKind.values();
        for (int index = 0; index < kinds.length; index++) {
            ponds.add(new Pond(index * 31L - 40L, kinds[index], 60 + index, 50 - index, -700 + index, 900 - index,
                    index == 0 ? NAN_PAYLOAD : 61.5f + index, mask(index), index == 1 ? -1L : 77L, index * 2, fluid(index),
                    -650 + index, 880 + index));
        }
        WindowPlan window = new WindowPlan(new WindowKey(-3, 7), List.of(odd, plain), ponds);
        WindowPlan decodedWindow = HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, window)).orElseThrow();
        assertBitExact(window, decodedWindow, tiers);

        RegionPlan region = new RegionPlan(new RegionKey(-1, 2), List.of(plain, odd), ponds.subList(1, 3));
        RegionPlan decodedRegion = HydrologyPlanCodec.decodeRegion(settings, HydrologyPlanCodec.encode(settings, region)).orElseThrow();
        assertEquals(region, decodedRegion);
        assertReachesBitExact(region.majorReaches(), decodedRegion.majorReaches(), tiers);
        assertPondsBitExact(region.lakes(), decodedRegion.lakes());

        WindowPlan empty = new WindowPlan(new WindowKey(Integer.MIN_VALUE, Integer.MAX_VALUE), List.of(), List.of());
        assertEquals(empty, HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, empty)).orElseThrow());

        SyntheticTerrain terrain = SyntheticTerrain.openFractal(SEED);
        List<RegionPlan> regions = new ArrayList<>();
        RegionPlanner regionPlanner = new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20), new PondPlanner(settings), new ReachShaper(settings));
        for (RegionKey key : WindowPlanner.lakeRegions(LAND)) {
            RegionPlan planned = regionPlanner.plan(key);
            assertEquals(planned, HydrologyPlanCodec.decodeRegion(settings, HydrologyPlanCodec.encode(settings, planned)).orElseThrow());
            regions.add(planned);
        }
        WindowPlan planned = new WindowPlanner(settings,
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21), new PondPlanner(settings), new ReachShaper(settings))
                .plan(LAND, regions);
        assertFalse(planned.reaches().isEmpty());
        assertFalse(planned.ponds().isEmpty());
        assertBitExact(planned, HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, planned)).orElseThrow(), tiers);
    }

    @Test
    public void tidalReachesRoundTripOnTheTidalTier() {
        HydrologySettings settings = settings(new IrisHydrology());
        int sea = settings.seaLevel();
        Reach tidal = new Reach(404L, settings.tidalTier(), 0L, new float[]{0f, 8f, 16f}, new float[]{0f, 4f, 2f}, new int[]{sea, sea, sea},
                new float[]{2f, 6f, 12f}, new float[]{1f, 4f, 1f}, new ReachStyle[]{ReachStyle.TIDAL, ReachStyle.TIDAL, ReachStyle.TIDAL}, 0f);
        WindowPlan window = new WindowPlan(new WindowKey(0, 0), List.of(tidal), List.of());
        WindowPlan decoded = HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, window)).orElseThrow();
        assertEquals(window, decoded);
        assertSame(settings.tidalTier(), decoded.reaches().get(0).tier());
    }

    @Test
    public void codecDecodesMismatchedOrCorruptDataAsEmpty() {
        HydrologySettings settings = settings(new IrisHydrology());
        HydrologyTier tier = settings.tiers().get(1);
        Reach reach = new Reach(5L, tier, -1L, new float[]{1f, 2f, 3f}, new float[]{4f, 5f, 6f}, new int[]{70, 69, 68},
                new float[]{1f, 1f, 1.5f}, new float[]{1f, 1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RIFFLE, ReachStyle.MOUTH}, 2000f);
        Pond pond = new Pond(9L, PondKind.SEEDED, 66, 62, 10, 12, 66.5f, mask(3), 5L, 0, "water", 11, 13);
        WindowPlan window = new WindowPlan(new WindowKey(1, 2), List.of(reach), List.of(pond));
        byte[] encoded = HydrologyPlanCodec.encode(settings, window);

        HydrologySettings reseeded = HydrologySettings.compile(new IrisHydrology(), SEED + 1L, SyntheticTerrain.SEA_LEVEL,
                SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
        assertTrue(reseeded.fingerprint() != settings.fingerprint());
        assertEquals(Optional.empty(), HydrologyPlanCodec.decodeWindow(reseeded, encoded));
        assertEquals(Optional.empty(), HydrologyPlanCodec.decodeRegion(settings, encoded));
        assertEquals(Optional.empty(), HydrologyPlanCodec.decodeWindow(settings, new byte[0]));

        byte[] badMagic = encoded.clone();
        badMagic[0] ^= 0x40;
        assertEquals(Optional.empty(), HydrologyPlanCodec.decodeWindow(settings, badMagic));
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        assertEquals(Optional.empty(), HydrologyPlanCodec.decodeWindow(settings, trailing));
        for (int length = 0; length < encoded.length; length++) {
            assertEquals("prefix " + length, Optional.empty(), HydrologyPlanCodec.decodeWindow(settings, Arrays.copyOf(encoded, length)));
        }
        SplittableRandom random = new SplittableRandom(SEED);
        for (int trial = 0; trial < 2000; trial++) {
            byte[] damaged = encoded.clone();
            int flips = 1 + random.nextInt(4);
            for (int flip = 0; flip < flips; flip++) {
                damaged[random.nextInt(damaged.length)] = (byte) random.nextInt(256);
            }
            HydrologyPlanCodec.decodeWindow(settings, damaged);
        }

        HydrologyTier foreign = new HydrologyTier("foreign", 0f, 1f, 2f, 1f, 0.4f, 1, true, 6, 2, 0, IrisBankStyle.SMOOTH,
                new HydrologyTier.Estuary(48, 2.5f), new HydrologyTier.Delta(0, 35f, 64), new IrisHydrologyMaterials(), "water", List.of());
        Reach stray = new Reach(6L, foreign, -1L, new float[]{1f, 2f}, new float[]{1f, 2f}, new int[]{1, 1}, new float[]{1f, 1f},
                new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 1f);
        try {
            HydrologyPlanCodec.encode(settings, new WindowPlan(new WindowKey(0, 0), List.of(stray), List.of()));
            fail("a tier outside the settings has no index");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("foreign"));
        }
    }

    @Test
    public void decodesPinnedBytes() {
        HydrologySettings settings = settings(new IrisHydrology());
        WindowPlan expected = pinnedWindow(settings.tiers().get(0));
        byte[] fixture = HexFormat.of().parseHex(PINNED_WINDOW.replaceAll("\\s", ""));
        ByteBuffer.wrap(fixture).putLong(FINGERPRINT_OFFSET, settings.fingerprint());
        assertBitExact(expected, HydrologyPlanCodec.decodeWindow(settings, fixture).orElseThrow(), settings.tiers());
        assertArrayEquals(fixture, HydrologyPlanCodec.encode(settings, expected));
    }

    private static WindowPlan pinnedWindow(HydrologyTier tier) {
        Reach reach = new Reach(41L, tier, 97L, new float[]{0f, 4f, 8f, 12f, 16f, 20f}, new float[]{-8f, -6f, -4f, -2f, 0f, 2f},
                new int[]{72, 71, 70, 66, 63, 62}, new float[]{1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f}, new float[]{1f, 1f, 1.5f, 2f, 2f, 1f},
                new ReachStyle[]{ReachStyle.RUN, ReachStyle.RIFFLE, ReachStyle.CASCADE, ReachStyle.WATERFALL, ReachStyle.POND_OUTLET,
                        ReachStyle.MOUTH}, 812.5f);
        List<Pond> ponds = List.of(
                new Pond(5L, PondKind.NATURAL, 70, 66, 12, -4, 70.5f, mask(0), 41L, 0, null, 10, -6),
                new Pond(6L, PondKind.SEEDED, 74, 71, 30, 8, 74.25f, mask(2), -1L, 1, "water", 28, 6),
                new Pond(7L, PondKind.CHAINED, 68, 64, -20, 16, 68.75f, mask(0), 41L, 2, "lava", -22, 14),
                new Pond(8L, PondKind.LAKE, 80, 60, 64, -64, 80.5f, mask(0), -1L, 0, null, 60, -60));
        return new WindowPlan(new WindowKey(2, -3), List.of(reach), ponds);
    }

    private static void assertBitExact(WindowPlan expected, WindowPlan actual, List<HydrologyTier> tiers) {
        assertEquals(expected, actual);
        assertReachesBitExact(expected.reaches(), actual.reaches(), tiers);
        assertPondsBitExact(expected.ponds(), actual.ponds());
    }

    private static void assertReachesBitExact(List<Reach> expected, List<Reach> actual, List<HydrologyTier> tiers) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            Reach want = expected.get(index);
            Reach got = actual.get(index);
            assertSame(tiers.get(tiers.indexOf(want.tier())), got.tier());
            assertEquals(Float.floatToRawIntBits(want.catchment()), Float.floatToRawIntBits(got.catchment()));
            assertArrayEquals(rawBits(want.xs()), rawBits(got.xs()));
            assertArrayEquals(rawBits(want.zs()), rawBits(got.zs()));
            assertArrayEquals(want.grade(), got.grade());
            assertArrayEquals(rawBits(want.halfWidth()), rawBits(got.halfWidth()));
            assertArrayEquals(rawBits(want.depth()), rawBits(got.depth()));
            assertArrayEquals(want.style(), got.style());
            assertArrayEquals(want.straightFalls(), got.straightFalls());
        }
    }

    private static void assertPondsBitExact(List<Pond> expected, List<Pond> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            Pond want = expected.get(index);
            Pond got = actual.get(index);
            assertEquals(Float.floatToRawIntBits(want.spillHeight()), Float.floatToRawIntBits(got.spillHeight()));
            assertArrayEquals(want.mask().bits(), got.mask().bits());
            assertEquals(want.fluid(), got.fluid());
        }
    }

    private static int[] rawBits(float[] values) {
        int[] bits = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            bits[index] = Float.floatToRawIntBits(values[index]);
        }
        return bits;
    }

    private static PondMask mask(int index) {
        return switch (index) {
            case 0 -> new PondMask(-4, 8, 4, 0, 0, new long[0]);
            case 1 -> new PondMask(-1_000_000, 2_000_000, 16, 9, 11, new long[]{0xF0F0_1234_5678_9ABCL, 0x5L, -1L});
            case 2 -> new PondMask(3, -3, 1, 64, 1, new long[]{Long.MIN_VALUE | 1L});
            default -> new PondMask(0, 0, 8, 5, 5, new long[]{0x1_FFFF_FFL});
        };
    }

    private static String fluid(int index) {
        return switch (index) {
            case 0 -> null;
            case 1 -> "lava";
            case 2 -> "wässer-水";
            default -> "";
        };
    }

    private static HydrologySettings settings(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SyntheticTerrain.SEA_LEVEL, SyntheticTerrain.MIN_Y, SyntheticTerrain.MAX_Y);
    }
}
