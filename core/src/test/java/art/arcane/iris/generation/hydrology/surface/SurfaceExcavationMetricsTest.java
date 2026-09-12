package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydrologyPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class SurfaceExcavationMetricsTest {
    @Test
    public void compactSourceExcavationIsAveragedAcrossSixteenBlocks() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(32, 70, 0)));
        int[] volumes = new int[centerline.size()];
        volumes[0] = 1000;
        assertEquals(63, SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, volumes));
    }

    @Test
    public void centeredWindowIncludesBothEightBlockEndpoints() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(32, 70, 0)));
        int[] volumes = new int[centerline.size()];
        volumes[0] = 16;
        volumes[16] = 16;
        assertEquals(2, SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, volumes));
        volumes[16] = 0;
        volumes[17] = 16;
        assertEquals(1, SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, volumes));
    }

    @Test
    public void diagonalStationSpacingUsesArcLength() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(24, 70, 24)));
        int[] volumes = new int[centerline.size()];
        Arrays.fill(volumes, 16);
        assertEquals(11, SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, volumes));
    }

    @Test
    public void invalidVolumesFailWithoutWrappingTheirSum() {
        SurfaceCenterline centerline = SurfaceCenterline.densify(List.of(
                new HydrologyPoint(0, 70, 0), new HydrologyPoint(16, 70, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, new int[1]));
        int[] negative = new int[centerline.size()];
        negative[0] = -1;
        assertThrows(IllegalArgumentException.class,
                () -> SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, negative));
        int[] large = new int[centerline.size()];
        Arrays.fill(large, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, large));
    }
}
