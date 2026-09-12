package art.arcane.iris.generation.hydrology.surface;

import art.arcane.iris.generation.hydrology.HydraulicChannelProfile;
import art.arcane.iris.generation.hydrology.HydraulicSegment;
import art.arcane.iris.generation.hydrology.HydrologyCandidateRejection;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.HydrologyGeometrySampler;
import art.arcane.iris.generation.hydrology.HydrologyHash;
import art.arcane.iris.generation.hydrology.HydrologyOceanReceiver;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyPoint;
import art.arcane.iris.generation.hydrology.HydrologySurfaceDropRaster;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSampler;
import art.arcane.iris.generation.hydrology.RiverCourse;
import art.arcane.iris.generation.hydrology.RiverCourseType;
import art.arcane.iris.generation.hydrology.RiverFootprint;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SurfaceFootprintCompiler {
    private static final long COURSE_SEED_SALT = 0x535552464143455fL;

    private final HydrologyPlannerSettings settings;
    private final HydrologyTerrainSampler sampler;
    private final HydrologyGeometrySampler geometry;

    public SurfaceFootprintCompiler(
            HydrologyPlannerSettings settings,
            HydrologyTerrainSampler sampler,
            HydrologyGeometrySampler geometry
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
    }

    public static boolean exposedSegment(HydraulicSegment segment) {
        return segment.type().isSurface() && !segment.fallingFluid();
    }

    public SurfaceFootprint compile(RiverCourse course) {
        return compile(course, null);
    }

    public SurfaceFootprint compile(RiverCourse course, SurfaceBounds bounds) {
        boolean pool = course.type() == RiverCourseType.SURFACE_POOL;
        if (course.type() != RiverCourseType.SURFACE && !pool) {
            return SurfaceFootprint.empty();
        }
        HydrologyTerrainSampler receivingSampler = HydrologyOceanReceiver.forCourse(settings, sampler, course);
        HydrologySurfaceDropRaster drops = HydrologySurfaceDropRaster.compile(settings, receivingSampler, geometry, course,
                bounds == null ? null : bounds.expand(2));
        int[] offsets = stationOffsets(course.segments());
        ArrayList<SurfaceLayerColumn> columns = new ArrayList<>();
        int uncontained = 0;
        long excavation = 0L;
        HydrologyCandidateRejection rejection = null;
        int detail = 0;
        int first = 0;
        while (first < course.segments().size()) {
            if (!exposedSegment(course.segments().get(first))) {
                first++;
                continue;
            }
            int after = first + 1;
            while (after < course.segments().size() && exposedSegment(course.segments().get(after))) {
                after++;
            }
            SurfaceFootprint run = compileRun(course, first, after, offsets[first], bounds, drops, pool, receivingSampler);
            columns.addAll(run.columns());
            uncontained += run.uncontainedWetCells();
            excavation += run.bankExcavation();
            if (rejection == null && run.rejection() != null) {
                rejection = run.rejection();
                detail = run.rejectionDetail();
            }
            first = after;
        }
        if (rejection == null && !drops.containsRequiredFluid(course, bounds)) {
            rejection = HydrologyCandidateRejection.SURFACE_DROP_UNSUPPORTED;
        }
        SurfaceFootprint footprint = new SurfaceFootprint(columns, uncontained, rejection, detail, excavation);
        if (drops.columns().isEmpty()) {
            return footprint;
        }
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        for (HydraulicSegment segment : course.segments()) {
            points.addAll(segment.centerline());
        }
        SurfaceCenterline centerline = SurfaceCenterline.densify(points);
        int[] stationVolumes = new int[centerline.size()];
        excavation = SurfaceExcavationMetrics.accumulate(course, centerline, footprint, drops, bounds, stationVolumes);
        int volume = SurfaceExcavationMetrics.maximumVolumePerBlock(centerline, stationVolumes);
        if (rejection == null && volume > settings.surface().banks().erosion().excavation().maximumVolumePerBlock()) {
            rejection = HydrologyCandidateRejection.SURFACE_BANK_BUDGET;
            detail = volume;
        }
        return new SurfaceFootprint(columns, uncontained, rejection, detail, excavation);
    }

    private SurfaceFootprint compileRun(RiverCourse course, int first, int after, int stationOffset,
                                        SurfaceBounds bounds, HydrologySurfaceDropRaster drops, boolean pool,
                                        HydrologyTerrainSampler receivingSampler) {
        List<HydraulicSegment> exposed = course.segments().subList(first, after);
        Stations stations = stations(exposed);
        if (stations.count() < 1) {
            return SurfaceFootprint.empty();
        }
        boolean fallingEnd = after < course.segments().size() && course.segments().get(after).fallingFluid();
        SurfaceTerminal terminal = pool ? SurfaceTerminal.SINKHOLE
                : fallingEnd ? SurfaceTerminal.TRIBUTARY : terminal(course, exposed);
        boolean coastalChannel = first == 0 && after == course.segments().size()
                && exposed.getFirst().type() == HydrologyFeatureType.MOUTH
                && exposed.getLast().type() == HydrologyFeatureType.MOUTH;
        SurfaceCenterline centerline = SurfaceCenterline.densify(stations.points());
        ChannelProfile channel = pool ? poolProfile(exposed.getFirst(), centerline) : acceptedProfile(stations, centerline);
        String poolBiome = pool ? poolBiome(course.profileKey()) : null;
        ValleyProfile valley = ValleyProfile.fromHeads(stations.head(), stations.exposedStations());
        HydrologyPlannerSettings.Ponds disabled = HydrologyPlannerSettings.Ponds.none();
        HydrologyPlannerSettings.Ponds ponds = pool || coastalChannel ? disabled
                : new HydrologyPlannerSettings.Ponds(first == 0 ? settings.surface().banks().ponds().source() : disabled.source(),
                fallingEnd ? disabled.terminal() : settings.surface().banks().ponds().terminal());
        SurfaceRunBoundary boundary = runBoundary(course, first, after, centerline, channel);
        ErosionField field = new ErosionFieldCompiler(settings.surface(), receivingSampler, settings.seaLevel()).compile(
                HydrologyHash.mix(course.id(), COURSE_SEED_SALT), centerline, channel, valley, terminal,
                settings.outlets().maximumOceanApron(), ponds, new SurfaceRasterContext(bounds, drops, boundary));
        ArrayList<SurfaceColumn> ordered = new ArrayList<>(field.columns().values());
        ordered.sort(Comparator.comparingInt(SurfaceColumn::station)
                .thenComparingLong((SurfaceColumn column) -> RiverFootprint.pack(column.x(), column.z())));
        SurfaceFeatureRefs features = new SurfaceFeatureRefs(course.id());
        ArrayList<SurfaceLayerColumn> columns = new ArrayList<>(ordered.size());
        for (SurfaceColumn column : ordered) {
            if (bounds != null && !bounds.contains(column.x(), column.z())) {
                continue;
            }
            HydraulicSegment segment = exposed.get(stations.segmentIndex()[column.station()]);
            int flowX = (int) StrictMath.round(centerline.tangentX()[column.station()]);
            int flowZ = (int) StrictMath.round(centerline.tangentZ()[column.station()]);
            boolean source = first == 0 && !coastalChannel && column.station() == 0
                    && column.role() == SurfaceRole.CHANNEL && !column.apron()
                    && column.x() == centerline.x()[0] && column.z() == centerline.z()[0];
            HydrologyFeatureRef feature = features.feature(segment, column.role(), source, flowX, flowZ);
            String biomeOverride = pool ? poolBiome == null ? column.terrain().parentBiomeKey() : poolBiome : null;
            columns.add(new SurfaceLayerColumn(column.x(), column.z(), column.terrain(),
                    layer(feature, column, course.profileKey(), biomeOverride), column.role(), column.apron(),
                    stationOffset + column.station()));
        }
        return new SurfaceFootprint(columns, field.uncontainedWetCells(), field.rejection(), field.rejectionDetail(), field.bankExcavation());
    }

    private SurfaceRunBoundary runBoundary(RiverCourse course, int first, int after,
                                            SurfaceCenterline centerline, ChannelProfile channel) {
        SurfaceRunBoundary.Plane start = null;
        SurfaceRunBoundary.Plane end = null;
        if (first > 0 && course.segments().get(first - 1).fallingFluid()) {
            HydraulicSegment fall = course.segments().get(first - 1);
            start = SurfaceRunBoundary.Plane.between(fall.end(), fall.start(), fall.end(), boundaryRadius(channel, 0));
        }
        if (after < course.segments().size() && course.segments().get(after).fallingFluid()) {
            HydraulicSegment fall = course.segments().get(after);
            HydrologyPoint last = course.segments().get(after - 1).end();
            HydrologyPoint direction = last.x() == fall.start().x() && last.z() == fall.start().z() ? fall.end() : fall.start();
            end = SurfaceRunBoundary.Plane.between(fall.start(), last, direction, boundaryRadius(channel, centerline.size() - 1));
        }
        return new SurfaceRunBoundary(start, end);
    }

    private double boundaryRadius(ChannelProfile channel, int station) {
        return channel.halfWidth()[station] * (1D + settings.surface().banks().roughness())
                + settings.surface().banks().maximumBlendWidth() + settings.surface().shoreWidth() + 2D;
    }

    private static int[] stationOffsets(List<HydraulicSegment> segments) {
        int[] offsets = new int[segments.size()];
        int station = 0;
        HydrologyPoint previous = null;
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            List<HydrologyPoint> points = segments.get(segmentIndex).centerline();
            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                HydrologyPoint point = points.get(pointIndex);
                if (previous != null) {
                    station += Math.max(Math.abs(point.x() - previous.x()), Math.abs(point.z() - previous.z()));
                }
                if (pointIndex == 0) {
                    offsets[segmentIndex] = station;
                }
                previous = point;
            }
        }
        return offsets;
    }

    private ChannelProfile acceptedProfile(Stations stations, SurfaceCenterline centerline) {
        int count = centerline.size();
        double[] halfWidth = new double[count];
        double[] depth = new double[count];
        double[] bank = new double[count];
        for (int station = 0; station < count; station++) {
            halfWidth[station] = Math.max(0.5D, stations.width()[station] / 2D);
            depth[station] = Math.max(1D, stations.depth()[station]);
            HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station], centerline.z()[station]);
            bank[station] = terrain == null ? 1D : terrain.bankMultiplier();
        }
        return new ChannelProfile(halfWidth, depth, bank);
    }

    private ChannelProfile poolProfile(HydraulicSegment segment, SurfaceCenterline centerline) {
        int count = centerline.size();
        double[] halfWidth = new double[count];
        double[] depth = new double[count];
        double[] bank = new double[count];
        for (int station = 0; station < count; station++) {
            HydrologyTerrainSample terrain = sampler.sample(centerline.x()[station], centerline.z()[station]);
            halfWidth[station] = Math.max(0.5D, segment.width() / 2D);
            depth[station] = Math.max(1D, segment.depth());
            bank[station] = terrain == null ? 1D : terrain.bankMultiplier();
        }
        return new ChannelProfile(halfWidth, depth, bank);
    }

    private String poolBiome(String poolId) {
        for (HydrologyPlannerSettings.SurfacePool pool : settings.surfacePools()) {
            if (pool.id().equals(poolId)) {
                return pool.biomeKey();
            }
        }
        return null;
    }

    /** A pool's bowl and rim carry the pool biome when one is configured, otherwise the surrounding biome. */
    private static HydrologyColumnLayer layer(
            HydrologyFeatureRef feature,
            SurfaceColumn column,
            String profileKey,
            String biomeOverride
    ) {
        HydrologyTerrainSample terrain = column.terrain();
        if (column.apron() && biomeOverride == null) {
            return new HydrologyColumnLayer(
                    feature,
                    column.headY(),
                    column.headY(),
                    column.headY(),
                    true, false, false, true, false, false, false, false, true,
                    profileKey,
                    terrain.surfaceBiomeKey(),
                    terrain.mouthBiomeKey(),
                    terrain.shoreBiomeKey(),
                    terrain.bankBiomeKey(),
                    terrain.floodedCaveBiomeKey()
            );
        }
        boolean channel = column.role() == SurfaceRole.CHANNEL;
        boolean shore = column.role() == SurfaceRole.SHORE;
        return new HydrologyColumnLayer(
                feature,
                column.height(),
                column.headY(),
                column.headY(),
                channel,
                shore,
                !channel,
                channel,
                false,
                false,
                channel || column.height() != terrain.naturalHeight(),
                channel,
                false,
                profileKey,
                biomeOverride == null ? terrain.surfaceBiomeKey() : biomeOverride,
                biomeOverride == null ? terrain.mouthBiomeKey() : biomeOverride,
                biomeOverride == null ? terrain.shoreBiomeKey() : biomeOverride,
                biomeOverride == null ? terrain.bankBiomeKey() : biomeOverride,
                terrain.floodedCaveBiomeKey()
        );
    }

    private static SurfaceTerminal terminal(RiverCourse course, List<HydraulicSegment> exposed) {
        if (exposed.getLast().type() == HydrologyFeatureType.MOUTH) {
            return SurfaceTerminal.OCEAN_MOUTH;
        }
        for (HydraulicSegment segment : course.segments()) {
            if (segment.type() == HydrologyFeatureType.MOUTH) {
                return SurfaceTerminal.OCEAN_MOUTH;
            }
            if (segment.type() == HydrologyFeatureType.COASTAL_GROTTO) {
                return SurfaceTerminal.COASTAL_GROTTO;
            }
        }
        return course.surfaceSinkholeContinuation() ? SurfaceTerminal.SINKHOLE : SurfaceTerminal.TRIBUTARY;
    }

    private static Stations stations(List<HydraulicSegment> exposed) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        IntArrayList segmentIndices = new IntArrayList();
        IntArrayList heads = new IntArrayList();
        DoubleArrayList widths = new DoubleArrayList();
        DoubleArrayList depths = new DoubleArrayList();
        int exposedStations = 0;
        for (int segmentIndex = 0; segmentIndex < exposed.size(); segmentIndex++) {
            HydraulicSegment segment = exposed.get(segmentIndex);
            boolean mouth = segment.type() == HydrologyFeatureType.MOUTH;
            List<HydrologyPoint> centerline = segment.centerline();
            for (int pointIndex = 0; pointIndex < centerline.size(); pointIndex++) {
                HydrologyPoint current = centerline.get(pointIndex);
                if (pointIndex == 0) {
                    appendStation(points, segmentIndices, heads, widths, depths, current, segmentIndex,
                            segment.channelProfile().widthAt(pointIndex), segment.channelProfile().depthAt(pointIndex));
                    continue;
                }
                HydrologyPoint previous = centerline.get(pointIndex - 1);
                int steps = Math.max(Math.abs(current.x() - previous.x()), Math.abs(current.z() - previous.z()));
                for (int step = 1; step <= steps; step++) {
                    double progress = step / (double) steps;
                    HydrologyPoint cell = new HydrologyPoint(
                            (int) StrictMath.round(previous.x() + (current.x() - previous.x()) * progress),
                            (int) StrictMath.round(previous.y() + (current.y() - previous.y()) * progress),
                            (int) StrictMath.round(previous.z() + (current.z() - previous.z()) * progress)
                    );
                    HydraulicChannelProfile profile = segment.channelProfile();
                    double width = profile.widthAt(pointIndex - 1)
                            + (profile.widthAt(pointIndex) - profile.widthAt(pointIndex - 1)) * progress;
                    double depth = profile.depthAt(pointIndex - 1)
                            + (profile.depthAt(pointIndex) - profile.depthAt(pointIndex - 1)) * progress;
                    appendStation(points, segmentIndices, heads, widths, depths, cell, segmentIndex, width, depth);
                }
            }
            if (!mouth) {
                exposedStations = points.size();
            }
        }
        return new Stations(points, segmentIndices.toIntArray(), heads.toIntArray(), widths.toDoubleArray(),
                depths.toDoubleArray(), exposedStations);
    }

    private static void appendStation(
            ArrayList<HydrologyPoint> points,
            IntArrayList segmentIndices,
            IntArrayList heads,
            DoubleArrayList widths,
            DoubleArrayList depths,
            HydrologyPoint cell,
            int segmentIndex,
            double width,
            double depth
    ) {
        if (!points.isEmpty()) {
            HydrologyPoint last = points.getLast();
            if (last.x() == cell.x() && last.z() == cell.z()) {
                return;
            }
        }
        points.add(cell);
        segmentIndices.add(segmentIndex);
        heads.add(cell.y());
        widths.add(width);
        depths.add(depth);
    }

    private record Stations(List<HydrologyPoint> points, int[] segmentIndex, int[] head, double[] width,
                            double[] depth, int exposedStations) {
        private int count() {
            return points.size();
        }
    }
}
