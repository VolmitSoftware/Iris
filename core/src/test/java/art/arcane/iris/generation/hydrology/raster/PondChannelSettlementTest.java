package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PondChannelSettlementTest {
    private static final int SEA_LEVEL = 40;

    @Test
    public void aLowerChannelDrainsThePondWhereOnlyItsWidthOverlaps() {
        for (int offset : new int[]{-512, -19, 0, 513}) {
            Pond pond = pond(1L, PondKind.CHAINED, 100, circle(offset, offset, 8));
            Reach river = line(2L, offset + 12, offset - 20, offset + 12, offset + 20, 5f, 95, 95);
            assertEquals(94, settle(List.of(river), pond));
        }
    }

    @Test
    public void cardinalWaterContactDrainsButDiagonalProximityDoesNot() {
        Pond pond = pond(1L, PondKind.NATURAL, 100, circle(0, 0, 0));
        Reach cardinal = line(2L, 1, 0, 1, 1, 0.25f, 94, 94);
        Reach diagonal = line(2L, 1, 1, 1, 2, 0.25f, 94, 94);

        assertEquals(94, settle(List.of(cardinal), pond));
        assertEquals(100, settle(List.of(diagonal), pond));
    }

    @Test
    public void drySeparationHigherChannelsAndFixedLakesKeepTheirLevels() {
        PondMask mask = circle(0, 0, 8);
        Pond pond = pond(1L, PondKind.NATURAL, 100, mask);
        Pond lake = pond(1L, PondKind.LAKE, 100, mask);
        Reach separate = line(2L, 15, -20, 15, 20, 4f, 94, 94);
        Reach higher = line(2L, 0, -20, 0, 20, 4f, 104, 104);
        Reach lower = line(2L, 0, -20, 0, 20, 4f, 94, 94);

        assertEquals(100, settle(List.of(separate), pond));
        assertEquals(100, settle(List.of(higher), pond));
        assertEquals(100, settle(List.of(lower), lake));
    }

    @Test
    public void equalCanonicalHeadsDoNotTriggerAnotherReflood() {
        Pond pond = pond(1L, PondKind.CHAINED, 101, circle(0, 0, 8));
        Reach river = line(2L, 0, -20, 0, 20, 4f, 100, 100);

        assertEquals(101, settle(List.of(river), pond));
    }

    @Test
    public void nearerChannelsAndIdentityTiesMatchTheRasterWinner() {
        Pond pond = pond(1L, PondKind.CHAINED, 100, circle(0, 0, 4));
        Reach lower = line(4L, 2, -20, 2, 20, 8f, 94, 94);
        Reach nearer = line(3L, 0, -20, 0, 20, 8f, 100, 100);
        Pond single = pond(2L, PondKind.NATURAL, 100, circle(0, 0, 0));
        Reach tied = line(3L, 2, -20, 2, 20, 8f, 100, 100);

        assertEquals(100, settle(List.of(lower, nearer), single));
        assertEquals(100, settle(List.of(lower, tied), pond));
        assertEquals(100, settle(List.of(tied, lower), pond));
    }

    @Test
    public void projectedStationChoosesOnlyTheTouchingSideOfADrop() {
        Reach river = line(2L, -20, 0, 20, 0, 2f, 100, 94);

        assertEquals(100, settle(List.of(river), pond(1L, PondKind.NATURAL, 100, circle(-5, 0, 0))));
        assertEquals(94, settle(List.of(river), pond(1L, PondKind.NATURAL, 100, circle(5, 0, 0))));
    }

    @Test
    public void adjacentPondsSettleIndependentlyOfReachAndPondOrder() {
        Pond first = pond(1L, PondKind.CHAINED, 104, circle(0, 0, 8));
        Pond second = pond(2L, PondKind.CHAINED, 102, circle(17, 0, 8));
        Reach across = line(3L, -20, 0, 40, 0, 4f, 96, 96);
        Reach lower = line(4L, 17, -20, 17, 20, 4f, 94, 94);

        assertArrayEquals(new int[]{96, 94}, PondChannelSettlement.levels(List.of(across, lower), List.of(first, second),
                new int[]{104, 102}, SEA_LEVEL));
        assertArrayEquals(new int[]{94, 96}, PondChannelSettlement.levels(List.of(lower, across), List.of(second, first),
                new int[]{102, 104}, SEA_LEVEL));
    }

    private static int settle(List<Reach> reaches, Pond pond) {
        return PondChannelSettlement.levels(reaches, List.of(pond), new int[]{pond.level()}, SEA_LEVEL)[0];
    }

    private static Pond pond(long id, PondKind kind, int level, PondMask mask) {
        return new Pond(id, kind, level, 90, mask.originX(), mask.originZ(), level + 1f, mask, 0L, 0, "water",
                mask.originX(), mask.originZ());
    }

    private static PondMask circle(int x, int z, int radius) {
        int size = 2 * radius + 1;
        long[] bits = new long[(size * size + 63) >>> 6];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    int cell = (dz + radius) * size + dx + radius;
                    bits[cell >>> 6] |= 1L << cell;
                }
            }
        }
        return new PondMask(x - radius, z - radius, 1, size, size, bits);
    }

    private static Reach line(long id, int x0, int z0, int x1, int z1, float width, int first, int last) {
        HydrologyTier tier = new HydrologyTier("river", 0f, width, width, 0f, 0.5f, 2, true, 6, 0, IrisBankStyle.SMOOTH,
                false, null, "water", List.of());
        float[] widths = new float[]{width, width};
        float[] depth = new float[]{2f, 2f};
        ReachStyle[] styles = new ReachStyle[2];
        Arrays.fill(styles, ReachStyle.RUN);
        return new Reach(id, tier, 0L, new float[]{x0, x1}, new float[]{z0, z1}, new int[]{first, last}, widths, depth, styles, 1000f);
    }
}
