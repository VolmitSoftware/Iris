package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPoint;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SurfaceCenterlineTest {
    @Test
    public void straightPathDensifiesToOneBlockStationsWithAxisTangent() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0),
                new HydrologyPoint(8, 0, 0)
        ));

        assertEquals(9, centerline.size());
        for (int station = 0; station < centerline.size(); station++) {
            assertEquals(station, centerline.x()[station]);
            assertEquals(0, centerline.z()[station]);
            assertEquals(1D, centerline.tangentX()[station], 1.0E-9D);
            assertEquals(0D, centerline.tangentZ()[station], 1.0E-9D);
            assertEquals(0, centerline.pathIndex()[station]);
        }
        assertEquals(0D, centerline.normalX(4), 1.0E-9D);
        assertEquals(1D, centerline.normalZ(4), 1.0E-9D);
    }

    @Test
    public void diagonalAndRepeatedPointsProduceNoDuplicateStations() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0),
                new HydrologyPoint(4, 0, 4),
                new HydrologyPoint(4, 0, 4),
                new HydrologyPoint(4, 0, 9)
        ));

        HashSet<Long> seen = new HashSet<>();
        for (int station = 0; station < centerline.size(); station++) {
            long key = ((long) centerline.x()[station] << 32) ^ (centerline.z()[station] & 0xffffffffL);
            assertTrue(seen.add(key));
        }
        assertEquals(10, centerline.size());
        assertEquals(0, centerline.pathIndex()[0]);
        assertEquals(2, centerline.pathIndex()[9]);
    }

    @Test
    public void distanceToSegmentMeasuresPerpendicularOffsetBesideAStraightRun() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0),
                new HydrologyPoint(20, 0, 0)
        ));

        assertEquals(3D, centerline.distanceToSegment(7, 7.5D, 3D), 1.0E-9D);
        assertEquals(0D, centerline.distanceToSegment(7, 7.5D, 0D), 1.0E-9D);
        assertEquals(2D, centerline.distanceToSegment(20, 22D, 0D), 1.0E-9D);
    }

    @Test
    public void truncateKeepsTheLeadingStations() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 0, 0),
                new HydrologyPoint(20, 0, 0)
        ));

        SurfaceCenterline truncated = centerline.truncate(5);

        assertEquals(5, truncated.size());
        assertEquals(4, truncated.x()[4]);
        assertEquals(1D, truncated.tangentX()[4], 1.0E-9D);
    }
}
