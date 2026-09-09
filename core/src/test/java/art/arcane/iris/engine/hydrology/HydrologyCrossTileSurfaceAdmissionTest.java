package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HydrologyCrossTileSurfaceAdmissionTest {
    @Test
    public void rejectsClusteredIndependentMouths() {
        HydrologyCrossTileSurfaceAdmission.Claim current = claim(
                10L,
                100L,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(100, 63, 0)),
                true,
                128
        );
        HydrologyCrossTileSurfaceAdmission.Claim blocker = claim(
                20L,
                200L,
                List.of(new HydrologyPoint(0, 70, 48), new HydrologyPoint(112, 63, 24)),
                true,
                128
        );

        HydrologyCrossTileSurfaceAdmission.Result result = HydrologyCrossTileSurfaceAdmission.admit(
                List.of(current),
                List.of(new HydrologyCrossTileSurfaceAdmission.RankedClaim(
                        new HydrologyTileKey(0, 0),
                        0,
                        blocker
                ))
        );

        assertEquals(1, result.rejections().size());
        assertEquals(current.courseId(), result.rejections().getFirst().loser().courseId());
    }

    @Test
    public void rejectsCrossingUnrelatedCourses() {
        HydrologyCrossTileSurfaceAdmission.Claim current = claim(
                10L,
                100L,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(100, 65, 100)),
                false,
                64
        );
        HydrologyCrossTileSurfaceAdmission.Claim blocker = claim(
                20L,
                200L,
                List.of(new HydrologyPoint(0, 70, 100), new HydrologyPoint(100, 65, 0)),
                false,
                64
        );

        HydrologyCrossTileSurfaceAdmission.Result result = HydrologyCrossTileSurfaceAdmission.admit(
                List.of(current),
                List.of(new HydrologyCrossTileSurfaceAdmission.RankedClaim(
                        new HydrologyTileKey(0, 0),
                        0,
                        blocker
                ))
        );

        assertEquals(1, result.rejections().size());
    }

    @Test
    public void rejectsNearbySourcesWhenTheirCoursesImmediatelyDiverge() {
        HydrologyCrossTileSurfaceAdmission.Claim current = claim(
                10L,
                100L,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(512, 63, 0)),
                true,
                192
        );
        HydrologyCrossTileSurfaceAdmission.Claim blocker = claim(
                20L,
                200L,
                List.of(new HydrologyPoint(0, 70, 128), new HydrologyPoint(-512, 63, 128)),
                true,
                192
        );

        HydrologyCrossTileSurfaceAdmission.Result result = HydrologyCrossTileSurfaceAdmission.admit(
                List.of(current),
                List.of(new HydrologyCrossTileSurfaceAdmission.RankedClaim(
                        new HydrologyTileKey(0, 0),
                        0,
                        blocker
                ))
        );

        assertEquals(1, result.rejections().size());
    }

    @Test
    public void keepsSeparatedNetworks() {
        HydrologyCrossTileSurfaceAdmission.Claim current = claim(
                10L,
                100L,
                List.of(new HydrologyPoint(0, 70, 0), new HydrologyPoint(100, 63, 0)),
                true,
                128
        );
        HydrologyCrossTileSurfaceAdmission.Claim blocker = claim(
                20L,
                200L,
                List.of(new HydrologyPoint(0, 70, 256), new HydrologyPoint(100, 63, 256)),
                true,
                128
        );

        HydrologyCrossTileSurfaceAdmission.Result result = HydrologyCrossTileSurfaceAdmission.admit(
                List.of(current),
                List.of(new HydrologyCrossTileSurfaceAdmission.RankedClaim(
                        new HydrologyTileKey(0, 0),
                        0,
                        blocker
                ))
        );

        assertTrue(result.rejections().isEmpty());
    }

    @Test
    public void localSpacingKeepsDenseNetworksButHonorsTheNeighboringAreaInEitherOrder() {
        HydrologyCrossTileSurfaceAdmission.Claim tropical = claim(10L, 100L,
                List.of(new HydrologyPoint(-64, 70, 0), new HydrologyPoint(800, 63, 0)), true, 160);
        HydrologyCrossTileSurfaceAdmission.Claim otherTropical = claim(20L, 200L,
                List.of(new HydrologyPoint(-64, 70, 200), new HydrologyPoint(800, 63, 200)), true, 160);
        HydrologyCrossTileSurfaceAdmission.Claim neighboringRegion = claim(30L, 300L,
                List.of(new HydrologyPoint(-64, 70, 200), new HydrologyPoint(800, 63, 200)), true, 384);

        assertTrue(HydrologyCrossTileSurfaceAdmission.admit(List.of(tropical), List.of(
                new HydrologyCrossTileSurfaceAdmission.RankedClaim(new HydrologyTileKey(-1, 0), 0, otherTropical)))
                .rejections().isEmpty());
        assertEquals(1, HydrologyCrossTileSurfaceAdmission.admit(List.of(tropical), List.of(
                new HydrologyCrossTileSurfaceAdmission.RankedClaim(new HydrologyTileKey(-1, 0), 0, neighboringRegion)))
                .rejections().size());
        assertEquals(1, HydrologyCrossTileSurfaceAdmission.admit(List.of(neighboringRegion), List.of(
                new HydrologyCrossTileSurfaceAdmission.RankedClaim(new HydrologyTileKey(-1, 0), 0, tropical)))
                .rejections().size());
    }

    private HydrologyCrossTileSurfaceAdmission.Claim claim(
            long courseId,
            long outletId,
            List<HydrologyPoint> centerline,
            boolean reachesOutlet,
            int sourceSpacing
    ) {
        return new HydrologyCrossTileSurfaceAdmission.Claim(
                courseId,
                outletId,
                centerline.getLast(),
                reachesOutlet,
                4,
                centerline,
                sourceSpacing
        );
    }
}
