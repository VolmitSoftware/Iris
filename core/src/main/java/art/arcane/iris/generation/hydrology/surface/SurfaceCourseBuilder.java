package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicChannelProfile;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyHash;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SurfaceCourseBuilder {
    private static final long COASTAL_DROP_SALT = 0x434f41535446414cL;

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
        ValleyProfile valley = new RiverProfileSolver(new RiverProfileSolver.Options(surface, seaLevel), sampler)
                .solve(path, centerline, channel, terminal, terminalHead, minimumCourseLength);
        if (!valley.accepted()) {
            return SurfaceCourseResult.rejected(valley.rejection(), valley.rejectionDetail());
        }
        int exposed = valley.exposedStations();
        boolean coastalDrop = directOcean && valley.head()[exposed - 1] > seaLevel;
        int stations = exposed;
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
        ArrayList<HydraulicSegment> segments = fallingTransitions(SurfaceSegmentLabeler.label(
                worldSeed, courseId, x, z, head, width, depth, surface.banks()), directOcean);
        if (segments.isEmpty()) {
            return SurfaceCourseResult.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, stations);
        }
        if (coastalDrop) {
            int last = exposed - 1;
            int receiverX;
            int receiverZ;
            if (exposed < centerline.size()) {
                receiverX = centerline.x()[exposed];
                receiverZ = centerline.z()[exposed];
            } else {
                receiverX = x[last] + (int) StrictMath.round(centerline.tangentX()[last]);
                receiverZ = z[last] + (int) StrictMath.round(centerline.tangentZ()[last]);
                if (receiverX == x[last] && receiverZ == z[last]) {
                    receiverX = x[last] + 1;
                }
            }
            int drop = head[last] - seaLevel;
            HydrologyFeatureType type = drop == 1 ? HydrologyFeatureType.RIFFLE
                    : drop >= surface.banks().waterfallMinimumDrop()
                    ? HydrologyFeatureType.WATERFALL : HydrologyFeatureType.CASCADE;
            boolean receiving = sampler.receivingWater(receiverX, receiverZ, seaLevel);
            segments.add(new HydraulicSegment(HydrologyHash.mix(worldSeed, COASTAL_DROP_SALT, courseId),
                    courseId, type, head[last], seaLevel,
                    Math.max(1, (int) StrictMath.ceil(width[last])),
                    Math.max(1, (int) StrictMath.ceil(depth[last])), receiving, receiving,
                    List.of(new HydrologyPoint(x[last], head[last], z[last]),
                            new HydrologyPoint(receiverX, seaLevel, receiverZ)),
                    new HydraulicChannelProfile(new double[]{width[last], width[last]},
                            new double[]{depth[last], depth[last]})));
        }
        HydraulicSegment last = segments.getLast();
        return new SurfaceCourseResult(
                segments,
                last.downstreamHeadY(),
                Math.max(1, (int) StrictMath.ceil(last.channelProfile().widthAt(last.centerline().size() - 1))),
                Math.max(1, (int) StrictMath.ceil(last.channelProfile().depthAt(last.centerline().size() - 1))),
                last.end(),
                null,
                0
        );
    }

    private ArrayList<HydraulicSegment> fallingTransitions(List<HydraulicSegment> labelled, boolean directOcean) {
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
            if (directOcean && fall.downstreamHeadY() == seaLevel) {
                incision = Math.max(incision, surface.banks().inlet().maximumIncision());
            }
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
        return segments;
    }

}
