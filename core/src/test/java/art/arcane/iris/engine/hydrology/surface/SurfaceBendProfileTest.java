package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SurfaceBendProfileTest {
    private static final HydrologyTerrainSampler FLAT = (x, z) -> HydrologyTerrainSample.openLand(80, 0D, "land");

    @Test
    public void curvedChannelMovesDepthToTheOuterBendWithinTheExistingEnvelope() {
        SurfaceCenterline centerline = curve(false);
        ErosionField field = compile(centerline, SurfaceRasterContext.none());
        SurfaceColumn outer = field.column(38, 10);
        SurfaceColumn inner = field.column(30, 18);

        assertNotNull(outer);
        assertNotNull(inner);
        assertEquals(SurfaceRole.CHANNEL, outer.role());
        assertEquals(SurfaceRole.CHANNEL, inner.role());
        assertTrue(outer.height() < symmetricBed(centerline, outer));
        assertTrue(inner.height() > symmetricBed(centerline, inner));
        assertTrue(outer.height() < inner.height());
        assertNull(field.rejection());
        assertEquals(0, field.uncontainedWetCells());
        for (SurfaceColumn column : field.columns().values()) {
            assertTrue(column.height() <= column.terrain().naturalHeight());
            if (column.role() == SurfaceRole.CHANNEL) {
                assertEquals(80, column.headY());
                assertTrue(column.height() >= 74 && column.height() <= 79);
            } else {
                assertEquals(80, column.height());
            }
        }
    }

    @Test
    public void mirroredBendsMirrorTheBedAndKeepBothChannelsContained() {
        ErosionField first = compile(curve(false), SurfaceRasterContext.none());
        ErosionField mirrored = compile(curve(true), SurfaceRasterContext.none());

        assertNull(mirrored.rejection());
        assertEquals(0, mirrored.uncontainedWetCells());
        for (SurfaceColumn column : first.columns().values()) {
            SurfaceColumn other = mirrored.column(-column.x(), column.z());
            assertNotNull(other);
            assertEquals(column.height(), other.height());
            assertEquals(column.headY(), other.headY());
            assertEquals(column.role(), other.role());
        }
    }

    @Test
    public void tileClippingCannotChangeTheBendBed() {
        SurfaceCenterline centerline = curve(false);
        ErosionField complete = compile(centerline, SurfaceRasterContext.none());
        SurfaceBounds bounds = new SurfaceBounds(24, 0, 39, 31);
        ErosionField clipped = compile(centerline, SurfaceRasterContext.bounded(bounds));

        assertNull(clipped.rejection());
        for (int z = bounds.minimumZ(); z <= bounds.maximumZ(); z++) {
            for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
                assertEquals(complete.column(x, z), clipped.column(x, z));
            }
        }
    }

    @Test
    public void straightAndPointChannelsKeepTheirSymmetricProfiles() {
        for (List<HydrologyPoint> points : List.of(List.of(new HydrologyPoint(0, 80, 0)),
                List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(200, 80, 0)))) {
            SurfaceCenterline centerline = SurfaceCenterline.densify(points);
            double[] offsets = SurfaceBendProfile.offsets(centerline, channel(centerline));
            for (double offset : offsets) {
                assertEquals(0D, offset, 0D);
            }
        }
        for (double distance = 0D; distance <= 1D; distance += 0.125D) {
            assertEquals(distance, SurfaceBendProfile.normalizedDistance(distance, -1D, 0D), 0D);
            assertEquals(distance, SurfaceBendProfile.normalizedDistance(distance, 1D, 0D), 0D);
        }
    }

    @Test
    public void alternatingBendsStayBoundedAndFadeAtTheirTerminals() {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int x = 0; x <= 512; x += 2) {
            points.add(new HydrologyPoint(x, 80, (int) StrictMath.round(32D * StrictMath.sin(x / 64D))));
        }
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        double[] offsets = SurfaceBendProfile.offsets(centerline, channel(centerline));
        double minimum = 0D;
        double maximum = 0D;
        for (int station = 0; station < offsets.length; station++) {
            minimum = Math.min(minimum, offsets[station]);
            maximum = Math.max(maximum, offsets[station]);
            assertTrue(StrictMath.abs(offsets[station]) <= 0.35D);
            if (station > 0) {
                assertTrue(StrictMath.abs(offsets[station] - offsets[station - 1]) < 0.06D);
            }
        }
        assertTrue(minimum < -0.05D && maximum > 0.05D);
        assertEquals(0D, offsets[0], 0D);
        assertEquals(0D, offsets[offsets.length - 1], 0D);
    }

    @Test
    public void mirroredHairpinsRetainOppositeBendDirectionsAcrossAHalfTurn() {
        List<HydrologyPoint> points = List.of(new HydrologyPoint(0, 80, 0), new HydrologyPoint(32, 80, 0),
                new HydrologyPoint(44, 80, 12), new HydrologyPoint(32, 80, 24), new HydrologyPoint(0, 80, 24));
        ArrayList<HydrologyPoint> reflected = new ArrayList<>(points.size());
        for (HydrologyPoint point : points) {
            reflected.add(new HydrologyPoint(point.x(), point.y(), -point.z()));
        }
        SurfaceCenterline first = SurfaceCenterline.densify(points);
        SurfaceCenterline second = SurfaceCenterline.densify(reflected);
        double[] left = SurfaceBendProfile.offsets(first, channel(first));
        double[] right = SurfaceBendProfile.offsets(second, channel(second));

        assertEquals(left.length, right.length);
        assertTrue(left[44] < -0.3D);
        assertTrue(right[44] > 0.3D);
        for (int station = 0; station < left.length; station++) {
            assertEquals(-left[station], right[station], 1.0E-12D);
        }
    }

    private static SurfaceCenterline curve(boolean mirrored) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (int degrees = 0; degrees <= 90; degrees += 3) {
            double angle = StrictMath.toRadians(degrees);
            int x = (int) StrictMath.round(48D * StrictMath.sin(angle));
            int z = (int) StrictMath.round(48D * (1D - StrictMath.cos(angle)));
            points.add(new HydrologyPoint(x, 80, z));
        }
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        if (!mirrored) {
            return centerline;
        }
        int[] xs = centerline.x().clone();
        double[] tangents = centerline.tangentX().clone();
        for (int station = 0; station < xs.length; station++) {
            xs[station] = -xs[station];
            tangents[station] = -tangents[station];
        }
        return new SurfaceCenterline(xs, centerline.z().clone(), tangents,
                centerline.tangentZ().clone(), centerline.pathIndex().clone());
    }

    private static int symmetricBed(SurfaceCenterline centerline, SurfaceColumn column) {
        double normalized = Math.min(1D, centerline.distanceToSegment(column.station(), column.x(), column.z()) / 8D);
        double depth = 1D + 5D * ErosionFieldCompiler.bedFactor(normalized, surface().banks().erosion());
        return 80 - (int) StrictMath.round(depth);
    }

    private static ChannelProfile channel(SurfaceCenterline centerline) {
        double[] halfWidth = new double[centerline.size()];
        double[] depth = new double[centerline.size()];
        double[] bank = new double[centerline.size()];
        Arrays.fill(halfWidth, 8D);
        Arrays.fill(depth, 6D);
        Arrays.fill(bank, 1D);
        return new ChannelProfile(halfWidth, depth, bank);
    }

    private static ErosionField compile(SurfaceCenterline centerline, SurfaceRasterContext context) {
        HydrologyPlannerSettings.Surface surface = surface();
        ChannelProfile channel = channel(centerline);
        ValleyProfile valley = new ValleyProfileSolver(surface, FLAT, 60, 32)
                .solve(centerline, channel, SurfaceTerminal.SINKHOLE, 40);
        assertTrue(valley.accepted());
        return new ErosionFieldCompiler(surface, FLAT, 60).compile(42L, centerline, channel, valley,
                SurfaceTerminal.SINKHOLE, 8, HydrologyPlannerSettings.Ponds.none(), context);
    }

    private static HydrologyPlannerSettings.Surface surface() {
        HydrologyPlannerSettings.Surface defaults = HydrologyPlannerSettings.defaults().surface();
        return new HydrologyPlannerSettings.Surface(true, defaults.sources(), 16, 16, 6, 6, 16, 1.5D,
                HydrologyPlannerSettings.Banks.of(0, 3D, 4, 32, 0D, 16, 2, 6, 1.6D,
                        HydrologyPlannerSettings.Inlet.none(), 2.5D, 24, true,
                        HydrologyPlannerSettings.Erosion.defaults(), HydrologyPlannerSettings.Ponds.none()));
    }
}
