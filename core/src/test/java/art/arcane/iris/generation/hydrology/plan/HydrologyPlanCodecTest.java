package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
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
                ReachStyle.values(), NEGATIVE_NAN);
        Reach plain = new Reach(77L, tiers.get(0), -9_000_000_000L, new float[]{10f, 11.5f}, new float[]{-3f, -2.5f}, new int[]{70, 69},
                new float[]{0.5f, 0.75f}, new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.MOUTH}, 1501.25f);
        List<Pond> ponds = new ArrayList<>();
        PondKind[] kinds = PondKind.values();
        for (int index = 0; index < kinds.length; index++) {
            ponds.add(new Pond(index * 31L - 40L, kinds[index], 60 + index, 50 - index, -700 + index, 900 - index,
                    index == 0 ? NAN_PAYLOAD : 61.5f + index, mask(index), index == 1 ? -1L : 77L, index * 2, fluid(index),
                    -650 + index, 880 + index));
        }
        SeaInlet oddInlet = new SeaInlet(-5_000_000_000L, new float[]{-0f, NAN_PAYLOAD, 1e-40f}, new float[]{NEGATIVE_NAN, 3.5f, -0f},
                new float[]{Float.MIN_VALUE, 5f, 2.25f}, 63, new int[]{Integer.MIN_VALUE, 60, 62}, new int[]{63, Integer.MAX_VALUE, 70},
                -12, 40, 9);
        SeaInlet plainInlet = new SeaInlet(91L, new float[]{8f, 16f}, new float[]{-24f, -16f}, new float[]{5f, 4f}, 306,
                new int[]{303, 302}, new int[]{340, 312}, 16, -16, 6);
        WindowPlan window = new WindowPlan(new WindowKey(-3, 7), List.of(odd, plain), ponds, List.of(oddInlet, plainInlet));
        WindowPlan decodedWindow = HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, window)).orElseThrow();
        assertBitExact(window, decodedWindow, tiers);

        RegionPlan region = new RegionPlan(new RegionKey(-1, 2), List.of(plain, odd), ponds.subList(1, 3));
        RegionPlan decodedRegion = HydrologyPlanCodec.decodeRegion(settings, HydrologyPlanCodec.encode(settings, region)).orElseThrow();
        assertEquals(region, decodedRegion);
        assertReachesBitExact(region.majorReaches(), decodedRegion.majorReaches(), tiers);
        assertPondsBitExact(region.lakes(), decodedRegion.lakes());

        WindowPlan empty = new WindowPlan(new WindowKey(Integer.MIN_VALUE, Integer.MAX_VALUE), List.of(), List.of(), List.of());
        assertEquals(empty, HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, empty)).orElseThrow());

        SyntheticTerrain terrain = SyntheticTerrain.fractal(SEED);
        List<RegionPlan> regions = new ArrayList<>();
        RegionPlanner regionPlanner = new RegionPlanner(settings, new HeightLattice(terrain, RegionPlanner.CELL, true, 1 << 18),
                new HeightLattice(terrain, RegionPlanner.FINE_CELL, false, 1 << 20), new PondPlanner(settings), new ReachShaper(settings));
        for (RegionKey key : WindowPlanner.lakeRegions(LAND)) {
            RegionPlan planned = regionPlanner.plan(key);
            assertEquals(planned, HydrologyPlanCodec.decodeRegion(settings, HydrologyPlanCodec.encode(settings, planned)).orElseThrow());
            regions.add(planned);
        }
        WindowPlan planned = new WindowPlanner(settings, new HeightLattice(terrain, WindowPlanner.CELL, false, 1 << 18),
                new HeightLattice(terrain, WindowPlanner.FINE_CELL, false, 1 << 21), new PondPlanner(settings), new ReachShaper(settings))
                .plan(LAND, regions);
        assertFalse(planned.reaches().isEmpty());
        assertFalse(planned.ponds().isEmpty());
        assertBitExact(planned, HydrologyPlanCodec.decodeWindow(settings, HydrologyPlanCodec.encode(settings, planned)).orElseThrow(), tiers);
    }

    @Test
    public void codecDecodesMismatchedOrCorruptDataAsEmpty() {
        HydrologySettings settings = settings(new IrisHydrology());
        HydrologyTier tier = settings.tiers().get(1);
        Reach reach = new Reach(5L, tier, -1L, new float[]{1f, 2f, 3f}, new float[]{4f, 5f, 6f}, new int[]{70, 69, 68},
                new float[]{1f, 1f, 1.5f}, new float[]{1f, 1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RIFFLE, ReachStyle.MOUTH}, 2000f);
        Pond pond = new Pond(9L, PondKind.SEEDED, 66, 62, 10, 12, 66.5f, mask(3), 5L, 0, "water", 11, 13);
        SeaInlet inlet = new SeaInlet(12L, new float[]{8f, 16f, 24f}, new float[]{0f, 0f, 8f}, new float[]{5f, 4f, 3f}, 63,
                new int[]{60, 61, 59}, new int[]{90, 68, 70}, 24, 8, 7);
        WindowPlan window = new WindowPlan(new WindowKey(1, 2), List.of(reach), List.of(pond), List.of(inlet));
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

        HydrologyTier foreign = new HydrologyTier("foreign", 0f, 1f, 2f, 1f, 0.4f, 1, true, 6, 0, IrisBankStyle.SMOOTH, false,
                new IrisHydrologyMaterials(), "water", List.of());
        Reach stray = new Reach(6L, foreign, -1L, new float[]{1f, 2f}, new float[]{1f, 2f}, new int[]{1, 1}, new float[]{1f, 1f},
                new float[]{1f, 1f}, new ReachStyle[]{ReachStyle.RUN, ReachStyle.RUN}, 1f);
        try {
            HydrologyPlanCodec.encode(settings, new WindowPlan(new WindowKey(0, 0), List.of(stray), List.of(), List.of()));
            fail("a tier outside the settings has no index");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("foreign"));
        }
    }

    private static void assertBitExact(WindowPlan expected, WindowPlan actual, List<HydrologyTier> tiers) {
        assertEquals(expected, actual);
        assertReachesBitExact(expected.reaches(), actual.reaches(), tiers);
        assertPondsBitExact(expected.ponds(), actual.ponds());
        assertEquals(expected.inlets().size(), actual.inlets().size());
        for (int index = 0; index < expected.inlets().size(); index++) {
            SeaInlet want = expected.inlets().get(index);
            SeaInlet got = actual.inlets().get(index);
            assertArrayEquals(rawBits(want.xs()), rawBits(got.xs()));
            assertArrayEquals(rawBits(want.zs()), rawBits(got.zs()));
            assertArrayEquals(rawBits(want.halfWidth()), rawBits(got.halfWidth()));
        }
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
