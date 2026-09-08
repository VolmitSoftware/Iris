package art.arcane.iris.engine.mantle;

import art.arcane.iris.engine.object.IrisPosition;
import org.junit.Test;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleWriterNeighborhoodTest {
    @Test
    public void balloonedVoxelsMatchTheReferenceSphereForFractionalRadii() {
        for (double radius : new double[]{0.0D, 0.5D, 1.0D, 1.5D, 2.0D, 3.7D, 5.0D}) {
            Set<IrisPosition> seeds = line(-3, 60, 7, 4, 63, 9);
            assertEquals("radius " + radius, referenceBallooned(seeds, radius), MantleWriter.getBallooned(seeds, radius));
        }
    }

    @Test
    public void hollowedVoxelsMatchTheReferenceSurfaceExtraction() {
        Set<IrisPosition> ballooned = MantleWriter.getBallooned(line(0, 40, 0, 6, 44, 3), 4.0D);

        assertEquals(referenceHollowed(ballooned), MantleWriter.getHollowed(ballooned));
    }

    @Test
    public void hollowingASolidBlockKeepsOnlyItsShell() {
        Set<IrisPosition> solid = new LinkedHashSet<>();
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    solid.add(new IrisPosition(x, y, z));
                }
            }
        }

        Set<IrisPosition> hollow = MantleWriter.getHollowed(solid);

        assertEquals(98, hollow.size());
        assertFalse(hollow.contains(new IrisPosition(2, 2, 2)));
        assertTrue(hollow.contains(new IrisPosition(0, 2, 2)));
    }

    @Test
    public void aSetTooWideToPackFallsBackToPositionEqualityWithoutChangingTheResult() {
        Set<IrisPosition> spread = new LinkedHashSet<>();
        spread.add(new IrisPosition(-20_000_000, 60, 0));
        spread.add(new IrisPosition(20_000_000, 60, 0));
        spread.add(new IrisPosition(20_000_001, 60, 0));

        assertEquals(referenceHollowed(spread), MantleWriter.getHollowed(spread));
        assertEquals(spread, MantleWriter.getHollowed(spread));
    }

    @Test
    public void maskedVoxelsMatchTheReferenceMaskExpansion() {
        Set<IrisPosition> seeds = line(0, 70, 0, 3, 70, 4);
        Set<IrisPosition> masks = new LinkedHashSet<>();
        masks.add(new IrisPosition(0, 0, 0));
        masks.add(new IrisPosition(1, 0, 0));
        masks.add(new IrisPosition(0, 1, 0));
        masks.add(new IrisPosition(-1, 0, 1));
        masks.add(new IrisPosition(0, 0, -2));

        assertEquals(referenceMasked(seeds, masks, 3.0D), MantleWriter.getMasked(seeds, masks, 3.0D));
    }

    @Test
    public void anEmptyMaskProducesNoVoxels() {
        assertTrue(MantleWriter.getMasked(line(0, 70, 0, 2, 70, 2), new LinkedHashSet<>(), 2.0D).isEmpty());
    }

    @Test
    public void aPositionLookupReportsMembershipOutsideItsBoundingBoxAsAbsent() {
        Set<IrisPosition> positions = new LinkedHashSet<>();
        positions.add(new IrisPosition(10, 20, 30));
        MantleWriter.PositionLookup lookup = MantleWriter.PositionLookup.of(positions);

        assertTrue(lookup.contains(10, 20, 30));
        assertFalse(lookup.contains(9, 20, 30));
        assertFalse(lookup.contains(10, 20, 31));
        assertFalse(lookup.contains(Integer.MIN_VALUE, 20, 30));
        assertFalse(lookup.contains(Integer.MAX_VALUE, 20, 30));
    }

    @Test
    public void anEmptyPositionLookupContainsNothing() {
        MantleWriter.PositionLookup lookup = MantleWriter.PositionLookup.of(new LinkedHashSet<>());

        assertFalse(lookup.contains(0, 0, 0));
        assertFalse(lookup.contains(-1, -1, -1));
    }

    private static Set<IrisPosition> line(int x1, int y1, int z1, int x2, int y2, int z2) {
        Set<IrisPosition> positions = new LinkedHashSet<>();
        int steps = Math.max(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)), Math.abs(z2 - z1));
        for (int step = 0; step <= steps; step++) {
            double t = steps == 0 ? 0D : (double) step / steps;
            positions.add(new IrisPosition(
                    (int) Math.round(x1 + t * (x2 - x1)),
                    (int) Math.round(y1 + t * (y2 - y1)),
                    (int) Math.round(z1 + t * (z2 - z1))));
        }
        return positions;
    }

    static Set<IrisPosition> referenceBallooned(Set<IrisPosition> vset, double radius) {
        Set<IrisPosition> returnset = new HashSet<>();
        int ceilrad = (int) Math.ceil(radius);
        double r2 = Math.pow(radius, 2);

        for (IrisPosition v : vset) {
            int tipx = v.getX();
            int tipy = v.getY();
            int tipz = v.getZ();

            for (int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for (int loopy = tipy - ceilrad; loopy <= tipy + ceilrad; loopy++) {
                    for (int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                        if (referenceHypot(loopx - tipx, loopy - tipy, loopz - tipz) <= r2) {
                            returnset.add(new IrisPosition(loopx, loopy, loopz));
                        }
                    }
                }
            }
        }
        return returnset;
    }

    static Set<IrisPosition> referenceHollowed(Set<IrisPosition> vset) {
        Set<IrisPosition> returnset = new LinkedHashSet<>();
        for (IrisPosition v : vset) {
            double x = v.getX();
            double y = v.getY();
            double z = v.getZ();
            if (!(vset.contains(new IrisPosition(x + 1, y, z))
                    && vset.contains(new IrisPosition(x - 1, y, z))
                    && vset.contains(new IrisPosition(x, y + 1, z))
                    && vset.contains(new IrisPosition(x, y - 1, z))
                    && vset.contains(new IrisPosition(x, y, z + 1))
                    && vset.contains(new IrisPosition(x, y, z - 1)))) {
                returnset.add(v);
            }
        }
        return returnset;
    }

    static Set<IrisPosition> referenceMasked(Set<IrisPosition> vectors, Set<IrisPosition> masks, double radius) {
        Set<IrisPosition> vset = new LinkedHashSet<>();
        int ceil = (int) Math.ceil(radius);
        double r2 = Math.pow(radius, 2);

        for (IrisPosition v : vectors) {
            int tipX = v.getX();
            int tipY = v.getY();
            int tipZ = v.getZ();

            for (int x = -ceil; x <= ceil; x++) {
                for (int y = -ceil; y <= ceil; y++) {
                    for (int z = -ceil; z <= ceil; z++) {
                        if (referenceHypot(x, y, z) > r2 || !masks.contains(new IrisPosition(x, y, z))) {
                            continue;
                        }
                        vset.add(new IrisPosition(tipX + x, tipY + y, tipZ + z));
                    }
                }
            }
        }

        return vset;
    }

    private static double referenceHypot(double... pars) {
        double sum = 0;
        for (double d : pars) {
            sum += Math.pow(d, 2);
        }
        return sum;
    }
}
