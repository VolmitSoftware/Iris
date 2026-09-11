package art.arcane.iris.engine.hydrology.surface;

import art.arcane.iris.engine.hydrology.HydraulicChannelProfile;
import art.arcane.iris.engine.hydrology.HydraulicSegment;
import art.arcane.iris.engine.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.engine.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.engine.hydrology.HydrologyPoint;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSample;
import art.arcane.iris.engine.hydrology.HydrologyTerrainSampler;

import java.util.ArrayList;
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
        List<HydraulicSegment> segments = fallingTransitions(SurfaceSegmentLabeler.label(
                worldSeed, courseId, x, z, head, width, depth, surface.banks()));
        if (segments.isEmpty()) {
            return SurfaceCourseResult.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, stations);
        }
        HydraulicSegment last = segments.getLast();
        int lastStation = stations - 1;
        return new SurfaceCourseResult(
                segments,
                head[lastStation],
                Math.max(1, (int) StrictMath.ceil(last.channelProfile().widthAt(last.centerline().size() - 1))),
                Math.max(1, (int) StrictMath.ceil(last.channelProfile().depthAt(last.centerline().size() - 1))),
                new HydrologyPoint(x[lastStation], head[lastStation], z[lastStation]),
                null,
                0
        );
    }
    private List<HydraulicSegment> fallingTransitions(List<HydraulicSegment> labelled) {
        ArrayList<HydraulicSegment> segments = new ArrayList<>(labelled);
        for (int index = 0; index + 1 < segments.size(); index++) {
            HydraulicSegment fall = segments.get(index);
            HydraulicSegment receiver = segments.get(index + 1);
            if (fall.type() != HydrologyFeatureType.WATERFALL || fall.centerline().size() != 2
                    || receiver.type() != HydrologyFeatureType.SURFACE_POOL || receiver.centerline().size() < 2) {
                continue;
            }
            HydrologyTerrainSample terrain = sampler.sample(fall.end().x(), fall.end().z());
            if (!SurfaceCellAdmission.writable(terrain, seaLevel)) {
                continue;
            }
            int incision = terrain.surfacePolicy().maximumIncision(surface.maximumIncision());
            incision = Math.min(incision, (int) StrictMath.floor(incision * terrain.incisionMultiplier()));
            if (terrain.naturalHeight() - fall.downstreamHeadY() + fall.depth() > incision) {
                continue;
            }
            HydrologyPoint throat = new HydrologyPoint(fall.end().x(), fall.upstreamHeadY(), fall.end().z());
            HydrologyPoint outflow = receiver.centerline().get(1);
            HydrologyPlannerSettings.Channel channel = surface.banks().channel();
            double outlineRatio = Math.max(channel.outlineMinimumRatio(),
                    Math.min(channel.outlineMaximumRatio(), 1D + surface.banks().roughness()));
            double inletWidth = fall.channelProfile().maximumWidth();
            if (index > 0) {
                HydraulicChannelProfile approach = segments.get(index - 1).channelProfile();
                inletWidth = Math.max(inletWidth, approach.widthAt(approach.size() - 1));
            }
            HydraulicChannelProfile fallProfile = new HydraulicChannelProfile(
                    new double[]{StrictMath.ceil(inletWidth * outlineRatio + 2D), receiver.channelProfile().widthAt(1)},
                    new double[]{fall.channelProfile().maximumDepth(), receiver.channelProfile().depthAt(1)});
            segments.set(index, new HydraulicSegment(fall.id(), fall.courseId(), fall.type(),
                    fall.upstreamHeadY(), fall.downstreamHeadY(),
                    Math.max(1, (int) StrictMath.ceil(fallProfile.maximumWidth())),
                    Math.max(1, (int) StrictMath.ceil(fallProfile.maximumDepth())), true, true,
                    List.of(throat, outflow), fallProfile));
            HydraulicChannelProfile receiverProfile = receiver.channelProfile().size() == 1 ? receiver.channelProfile()
                    : HydraulicChannelProfile.range(receiver.channelProfile().widths(), receiver.channelProfile().depths(),
                    1, receiver.channelProfile().size());
            segments.set(index + 1, new HydraulicSegment(receiver.id(), receiver.courseId(), receiver.type(),
                    receiver.upstreamHeadY(), receiver.downstreamHeadY(), receiver.width(), receiver.depth(),
                    receiver.fallingFluid(), receiver.receivingPool(),
                    receiver.centerline().subList(1, receiver.centerline().size()), receiverProfile));
        }
        return List.copyOf(segments);
    }

}
