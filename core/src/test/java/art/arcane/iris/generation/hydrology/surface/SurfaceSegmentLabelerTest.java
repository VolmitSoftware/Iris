package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicChannelProfile;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class SurfaceSegmentLabelerTest {
    @Test
    public void longLevelReachRetainsEveryAcceptedWidthAndDepth() {
        double[] widths = new double[]{4D, 5.5D, 8.25D, 6D};
        double[] depths = new double[]{2D, 3D, 4.5D, 2.5D};
        List<HydraulicSegment> segments = SurfaceSegmentLabeler.label(1L, 2L,
                new int[]{0, 10, 20, 30}, new int[]{0, 0, 0, 0}, new int[]{80, 80, 80, 80},
                widths, depths, HydrologyPlannerSettings.Banks.defaults());

        assertEquals(1, segments.size());
        HydraulicSegment segment = segments.getFirst();
        assertEquals(HydrologyFeatureType.SURFACE_POOL, segment.type());
        assertEquals(9, segment.width());
        assertEquals(5, segment.depth());
        for (int station = 0; station < widths.length; station++) {
            assertEquals(widths[station], segment.channelProfile().widthAt(station), 0D);
            assertEquals(depths[station], segment.channelProfile().depthAt(station), 0D);
        }
    }

    @Test
    public void storedDimensionsCannotBeMutatedThroughInputOrAccessors() {
        double[] widths = new double[]{4D, 6D};
        double[] depths = new double[]{2D, 3D};
        HydraulicChannelProfile profile = new HydraulicChannelProfile(widths, depths);
        widths[0] = 80D;
        depths[1] = 90D;
        profile.widths()[0] = 100D;
        profile.depths()[1] = 100D;

        assertEquals(4D, profile.widthAt(0), 0D);
        assertEquals(3D, profile.depthAt(1), 0D);
        assertEquals(new HydraulicChannelProfile(new double[]{4D, 6D}, new double[]{2D, 3D}), profile);
    }

    @Test
    public void rejectsInvalidCurrentFormatDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
                new HydraulicChannelProfile(new double[]{4D, 6D}, new double[]{2D}));
        assertThrows(IllegalArgumentException.class, () -> HydraulicChannelProfile.uniform(Double.NaN, 2D));
        assertThrows(IllegalArgumentException.class, () -> HydraulicChannelProfile.uniform(4D, 0D));
    }
}
