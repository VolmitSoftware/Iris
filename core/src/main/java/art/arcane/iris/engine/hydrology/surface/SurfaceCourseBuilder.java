package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.engine.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;

import java.util.List;
import java.util.Objects;

public final class SurfaceCourseBuilder {
    private final HydrologyPlannerSettings.Surface surface;
    private final HydrologyTerrainSampler sampler;
    private final HydrologyGeometrySampler geometry;
    private final int seaLevel;

    public SurfaceCourseBuilder(
            HydrologyPlannerSettings.Surface surface,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometry,
            int seaLevel
    ) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.seaLevel = seaLevel;
    }

    public SurfaceCourseResult build(
            long worldSeed,
            long courseId,
            String profileKey,
            List<HydrologyPoint> path,
            SurfaceTerminal terminal,
            int terminalHead,
            int minimumCourseLength
    ) {
        SurfaceCenterline centerline = SurfaceCenterline.densify(path);
        boolean directOcean = terminal == SurfaceTerminal.OCEAN_MOUTH;
        ChannelProfile channel = new ChannelProfileBuilder(surface, sampler, geometry)
                .build(centerline, profileKey, directOcean);
        ValleyProfile valley = new ValleyProfileSolver(surface, sampler, seaLevel, minimumCourseLength)
                .solve(centerline, channel, terminal, terminalHead);
        if (!valley.accepted()) {
            return SurfaceCourseResult.rejected(valley.rejection(), valley.rejectionDetail());
        }
        int exposed = valley.exposedStations();
        boolean coastalDrop = directOcean && valley.head()[exposed - 1] > seaLevel;
        int stations = coastalDrop ? exposed + 1 : exposed;
        int[] x = new int[stations];
        int[] z = new int[stations];
        int[] head = new int[stations];
        double[] width = new double[stations];
        double[] depth = new double[stations];
        for (int station = 0; station < exposed; station++) {
            x[station] = centerline.x()[station];
            z[station] = centerline.z()[station];
            head[station] = valley.head()[station];
            width[station] = channel.halfWidth()[station] * 2D;
            depth[station] = channel.depth()[station];
        }
        if (coastalDrop) {
            int last = exposed - 1;
            if (exposed < centerline.size()) {
                x[exposed] = centerline.x()[exposed];
                z[exposed] = centerline.z()[exposed];
            } else {
                x[exposed] = x[last] + (int) StrictMath.round(centerline.tangentX()[last]);
                z[exposed] = z[last] + (int) StrictMath.round(centerline.tangentZ()[last]);
                if (x[exposed] == x[last] && z[exposed] == z[last]) {
                    x[exposed] = x[last] + 1;
                }
            }
            head[exposed] = seaLevel;
            width[exposed] = width[last];
            depth[exposed] = depth[last];
        }
        List<HydraulicSegment> segments = SurfaceSegmentLabeler.label(
                worldSeed, courseId, x, z, head, width, depth, surface.banks());
        if (segments.isEmpty()) {
            return SurfaceCourseResult.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, stations);
        }
        HydraulicSegment last = segments.getLast();
        int lastStation = stations - 1;
        return new SurfaceCourseResult(
                segments,
                head[lastStation],
                last.width(),
                last.depth(),
                new HydrologyPoint(x[lastStation], head[lastStation], z[lastStation]),
                null,
                0
        );
    }
}
