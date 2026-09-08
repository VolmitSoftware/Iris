package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.List;

final class HydrologySegmentBuilder {
    private final HydrologyPlanner planner;

    HydrologySegmentBuilder(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final long SEGMENT_SALT = 0x5345474d454e54L;
    static final long GRADED_DROP_APPROACH_SALT = 0x474441505052L;
    static final long GRADED_DROP_RUN_SALT = 0x474452554eL;
    static final long GRADED_DROP_OUTFLOW_SALT = 0x47444f5554L;

    boolean addHydraulicSegments(
            long courseId,
            int pairIndex,
            HydrologyFeatureType type,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        int drop = upstreamHead - downstreamHead;
        if (drop <= 0 || centerline.size() < 2) {
            long segmentId = HydrologyHash.mix(planner.worldSeed, SEGMENT_SALT, courseId, pairIndex, type.ordinal());
            segments.add(new HydraulicSegment(
                    segmentId,
                    courseId,
                    type,
                    upstreamHead,
                    downstreamHead,
                    width,
                    depth,
                    false,
                    false,
                    centerline
            ));
            return true;
        }
        if (type == HydrologyFeatureType.WATERFALL) {
            return addFallingDropSegments(
                    courseId,
                    pairIndex,
                    upstreamHead,
                    downstreamHead,
                    width,
                    depth,
                    centerline,
                    surfaceCourse,
                    segments
            );
        }
        return addGradedDropSegments(
                courseId,
                pairIndex,
                type,
                upstreamHead,
                downstreamHead,
                width,
                depth,
                centerline,
                surfaceCourse,
                segments
        );
    }

    boolean addFallingDropSegments(
            long courseId,
            int pairIndex,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        List<HydrologyPoint> raster = rasterCenterline(centerline);
        if (raster.size() < 2) {
            return false;
        }
        int lipIndex = Math.min(waterfallLipIndex(raster), raster.size() - 2);
        int receiverIndex = lipIndex + 1;
        addGradedTransitionSegment(
                courseId,
                pairIndex,
                GRADED_DROP_APPROACH_SALT,
                HydrologyFeatureType.SURFACE_POOL,
                upstreamHead,
                width,
                depth,
                levelSlice(raster, 0, lipIndex, upstreamHead),
                segments
        );
        HydrologyPlannerSettings.Drops drops = planner.settings.geometry().drops();
        int flowWidth = hydraulicDropWidth(width, surfaceCourse);
        int flowDepth = drops.flowDepth(depth);
        HydrologyPoint lip = HydrologyPlanner.withY(raster.get(lipIndex), upstreamHead);
        HydrologyPoint receiver = HydrologyPlanner.withY(raster.get(receiverIndex), downstreamHead);
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(planner.worldSeed, SEGMENT_SALT, courseId, pairIndex, GRADED_DROP_RUN_SALT),
                courseId,
                HydrologyFeatureType.WATERFALL,
                upstreamHead,
                downstreamHead,
                flowWidth,
                flowDepth,
                true,
                true,
                List.of(lip, receiver)
        ));
        addGradedTransitionSegment(
                courseId,
                pairIndex,
                GRADED_DROP_OUTFLOW_SALT,
                HydrologyFeatureType.SURFACE_POOL,
                downstreamHead,
                width,
                depth,
                levelSlice(raster, receiverIndex, raster.size() - 1, downstreamHead),
                segments
        );
        return true;
    }

    boolean addGradedDropSegments(
            long courseId,
            int pairIndex,
            HydrologyFeatureType type,
            int upstreamHead,
            int downstreamHead,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            boolean surfaceCourse,
            List<HydraulicSegment> segments
    ) {
        int drop = upstreamHead - downstreamHead;
        HydrologyPlannerSettings.Drops drops = planner.settings.geometry().drops();
        boolean continuousSurfaceBore = surfaceCourse && type == HydrologyFeatureType.UNDERGROUND_DROP;
        List<HydrologyPoint> directRaster = continuousSurfaceBore
                ? rasterCenterline(centerline)
                : List.of();
        int maximumStep = drops.stepLimit(type);
        if (continuousSurfaceBore) {
            if (directRaster.size() < 2) {
                return false;
            }
            int requiredStep = Math.floorDiv(drop + directRaster.size() - 2, directRaster.size() - 1);
            if (requiredStep > HydrologyRouteGeometry.MAXIMUM_SURFACE_BORE_STEP) {
                return false;
            }
            maximumStep = Math.max(maximumStep, requiredStep);
        }
        int minimumRun = Math.floorDiv(drop + maximumStep - 1, maximumStep) + 1;
        int maximumGeneratedRun = Math.addExact(planner.settings.routing().maximumRouteLength(), 1);
        if (minimumRun > maximumGeneratedRun) {
            return false;
        }
        int preferredRun = Math.addExact(Math.multiplyExact(drop, drops.cascadeRunPerBlock()), 1);
        int undergroundRun = drops.undergroundCascadeRunPerBlock();
        int desiredRun = type.isUnderground()
                ? (undergroundRun == 0
                        ? minimumRun
                        : Math.min(
                                Math.max(minimumRun, Math.addExact(Math.multiplyExact(drop, undergroundRun), 1)),
                                maximumGeneratedRun
                        ))
                : Math.min(preferredRun, maximumGeneratedRun);
        List<HydrologyPoint> raster = continuousSurfaceBore
                ? directRaster
                : type.isUnderground()
                ? organicDropRaster(courseId, pairIndex, centerline, desiredRun)
                : rasterCenterline(centerline);
        if (raster.size() < minimumRun) {
            return false;
        }
        int runLength = Math.min(raster.size(), Math.max(minimumRun, desiredRun));
        int lipIndex = waterfallLipIndex(raster);
        int runStart = continuousSurfaceBore
                ? 0
                : type.isUnderground()
                ? HydrologyPlanner.clamp(lipIndex - runLength / 3, 0, raster.size() - runLength)
                : 0;
        int runEnd = continuousSurfaceBore
                ? raster.size() - 1
                : type.isUnderground() ? runStart + runLength - 1 : raster.size() - 1;
        HydrologyFeatureType flatType = type.isUnderground()
                ? HydrologyFeatureType.UNDERGROUND_POOL
                : HydrologyFeatureType.SURFACE_POOL;
        if (!continuousSurfaceBore) {
            addGradedTransitionSegment(
                    courseId,
                    pairIndex,
                    GRADED_DROP_APPROACH_SALT,
                    flatType,
                    upstreamHead,
                    width,
                    depth,
                    levelSlice(raster, 0, runStart, upstreamHead),
                    segments
            );
        }
        List<HydrologyPoint> graded = gradedDropCenterline(
                raster.subList(runStart, runEnd + 1),
                upstreamHead,
                downstreamHead,
                drops,
                maximumStep
        );
        int dropWidth = hydraulicDropWidth(width, surfaceCourse);
        int dropDepth = drops.flowDepth(depth);
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(planner.worldSeed, SEGMENT_SALT, courseId, pairIndex, GRADED_DROP_RUN_SALT),
                courseId,
                type,
                upstreamHead,
                downstreamHead,
                dropWidth,
                dropDepth,
                false,
                !continuousSurfaceBore,
                graded
        ));
        if (!continuousSurfaceBore) {
            addGradedTransitionSegment(
                    courseId,
                    pairIndex,
                    GRADED_DROP_OUTFLOW_SALT,
                    flatType,
                    downstreamHead,
                    width,
                    depth,
                    levelSlice(raster, runEnd, raster.size() - 1, downstreamHead),
                    segments
            );
        }
        return true;
    }

    int hydraulicDropWidth(int channelWidth, boolean surfaceCourse) {
        int dropWidth = planner.settings.geometry().drops().flowWidth(channelWidth);
        return surfaceCourse ? Math.max(planner.settings.surface().minimumWidth(), dropWidth) : dropWidth;
    }

    List<HydrologyPoint> organicDropRaster(
            long courseId,
            int pairIndex,
            List<HydrologyPoint> centerline,
            int desiredRun
    ) {
        List<HydrologyPoint> base = rasterCenterline(centerline);
        if (base.size() >= desiredRun) {
            return base;
        }
        int wavelength = planner.settings.geometry().meanders().detailWavelength();
        double phase = HydrologyHash.unit(HydrologyHash.mix(planner.worldSeed, courseId, pairIndex, GRADED_DROP_RUN_SALT))
                * StrictMath.PI * 2D;
        int maximumAmplitude = Math.max(3, planner.settings.routing().sampleSpacing() / 2);
        List<HydrologyPoint> longest = base;
        for (int amplitude = 3; amplitude <= maximumAmplitude; amplitude += 2) {
            int cycles = Math.max(1, (int) StrictMath.ceil(
                    Math.max(0, desiredRun - base.size()) / (2D * amplitude)
            ));
            int samples = Math.max(base.size(), desiredRun * 3);
            ArrayList<HydrologyPoint> worm = new ArrayList<>(samples);
            for (int sampleIndex = 0; sampleIndex < samples; sampleIndex++) {
                double progress = sampleIndex / (double) (samples - 1);
                double baseIndex = progress * (base.size() - 1);
                int lowerIndex = Math.min(base.size() - 2, (int) StrictMath.floor(baseIndex));
                int upperIndex = lowerIndex + 1;
                double localProgress = baseIndex - lowerIndex;
                HydrologyPoint lower = base.get(lowerIndex);
                HydrologyPoint upper = base.get(upperIndex);
                double baseX = lower.x() + (upper.x() - lower.x()) * localProgress;
                double baseZ = lower.z() + (upper.z() - lower.z()) * localProgress;
                double tangentX = upper.x() - lower.x();
                double tangentZ = upper.z() - lower.z();
                double tangentLength = StrictMath.hypot(tangentX, tangentZ);
                if (tangentLength <= 0D) {
                    continue;
                }
                double envelope = StrictMath.pow(StrictMath.sin(StrictMath.PI * progress), 2D);
                double displacement = StrictMath.sin(
                        progress * StrictMath.PI * 2D * cycles
                                + baseIndex * StrictMath.PI * 2D / wavelength
                                + phase
                ) * amplitude * envelope;
                int x = (int) StrictMath.round(baseX - tangentZ / tangentLength * displacement);
                int z = (int) StrictMath.round(baseZ + tangentX / tangentLength * displacement);
                if (worm.isEmpty() || worm.getLast().x() != x || worm.getLast().z() != z) {
                    worm.add(new HydrologyPoint(x, lower.y(), z));
                }
            }
            List<HydrologyPoint> raster = rasterCenterline(worm);
            if (raster.size() > longest.size()) {
                longest = raster;
            }
            if (raster.size() >= desiredRun) {
                return raster;
            }
        }
        return longest;
    }

    void addGradedTransitionSegment(
            long courseId,
            int pairIndex,
            long salt,
            HydrologyFeatureType type,
            int head,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            List<HydraulicSegment> segments
    ) {
        if (centerline.size() < 2) {
            return;
        }
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(planner.worldSeed, SEGMENT_SALT, courseId, pairIndex, salt),
                courseId,
                type,
                head,
                head,
                width,
                depth,
                false,
                false,
                List.copyOf(centerline)
        ));
    }

    List<HydrologyPoint> gradedDropCenterline(
            List<HydrologyPoint> points,
            int upstreamHead,
            int downstreamHead,
            HydrologyPlannerSettings.Drops drops,
            int maximumStep
    ) {
        int pointCount = points.size();
        int drop = upstreamHead - downstreamHead;
        if ((long) (pointCount - 1) * maximumStep < drop) {
            throw new IllegalArgumentException("Graded drop centerline cannot contain its configured head loss.");
        }
        ArrayList<HydrologyPoint> graded = new ArrayList<>(pointCount);
        int previousHead = upstreamHead;
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double progress = pointIndex / (double) (pointCount - 1);
            int idealHead = upstreamHead - (int) StrictMath.round(
                    drop * StrictMath.pow(progress, drops.cascadeExponent())
            );
            int remainingSteps = pointCount - pointIndex - 1;
            int latestReachableHead = downstreamHead + remainingSteps * maximumStep;
            int head = Math.min(previousHead, Math.min(idealHead, latestReachableHead));
            head = Math.max(downstreamHead, Math.max(previousHead - maximumStep, head));
            if (pointIndex == pointCount - 1) {
                head = downstreamHead;
            }
            HydrologyPoint point = points.get(pointIndex);
            graded.add(new HydrologyPoint(point.x(), head, point.z()));
            previousHead = head;
        }
        return List.copyOf(graded);
    }

    List<HydrologyPoint> levelSlice(
            List<HydrologyPoint> points,
            int startIndex,
            int endIndex,
            int head
    ) {
        ArrayList<HydrologyPoint> leveled = new ArrayList<>(Math.max(0, endIndex - startIndex + 1));
        for (int pointIndex = startIndex; pointIndex <= endIndex; pointIndex++) {
            leveled.add(HydrologyPlanner.withY(points.get(pointIndex), head));
        }
        return List.copyOf(leveled);
    }

    int waterfallLipIndex(List<HydrologyPoint> raster) {
        return waterfallLipIndex(raster, 0);
    }

    int waterfallLipIndex(List<HydrologyPoint> raster, int requiredDecline) {
        int fallback = Math.max(0, raster.size() / 2 - 1);
        int selected = fallback;
        long strongestDecline = Long.MIN_VALUE;
        for (int pointIndex = 0; pointIndex < raster.size() - 1; pointIndex++) {
            HydrologyPoint current = raster.get(pointIndex);
            HydrologyPoint downstream = raster.get(pointIndex + 1);
            HydrologyTerrainSample currentTerrain = planner.sampleBasisWithoutSlope(current.x(), current.z());
            HydrologyTerrainSample downstreamTerrain = planner.sampleBasisWithoutSlope(downstream.x(), downstream.z());
            long decline = (long) currentTerrain.naturalHeight() - downstreamTerrain.naturalHeight();
            if (decline > strongestDecline) {
                strongestDecline = decline;
                selected = pointIndex;
            }
        }
        return requiredDecline <= 0 || strongestDecline >= requiredDecline ? selected : -1;
    }

    List<HydrologyPoint> rasterCenterline(List<HydrologyPoint> centerline) {
        ArrayList<HydrologyPoint> raster = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < centerline.size() - 1; pointIndex++) {
            List<HydrologyPoint> span = rasterLine(centerline.get(pointIndex), centerline.get(pointIndex + 1));
            int first = raster.isEmpty() ? 0 : 1;
            for (int spanIndex = first; spanIndex < span.size(); spanIndex++) {
                raster.add(span.get(spanIndex));
            }
        }
        return List.copyOf(raster);
    }

    List<HydrologyPoint> rasterLine(HydrologyPoint start, HydrologyPoint end) {
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
        if (steps == 0) {
            return List.of(start);
        }
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            points.add(new HydrologyPoint(x, start.y(), z));
        }
        return List.copyOf(points);
    }

    void appendOutletSegments(
            RiverCourseType courseType,
            long courseId,
            HydrologyCoursePath path,
            int head,
            int[] widths,
            int[] depths,
            List<HydraulicSegment> segments
    ) {
        RiverOutlet outlet = path.outlet();
        int width = widths.length == 0 ? 1 : widths[widths.length - 1];
        int depth = depths.length == 0 ? 1 : depths[depths.length - 1];
        if (courseType != RiverCourseType.SURFACE
                && outlet.type() == HydrologyFeatureType.INLAND_GROTTO) {
            width = planner.settings.underground().minimumWidth();
            depth = planner.settings.underground().minimumDepth();
        }
        HydrologyPoint pathEnd = HydrologyPlanner.withY(path.points().getLast(), head);
        HydrologyPoint landward = HydrologyPlanner.withY(outlet.landwardPoint(), head);
        if (courseType == RiverCourseType.SURFACE
                && outlet.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            appendSurfaceCoastalGrotto(
                    courseId,
                    outlet,
                    head,
                    width,
                    depth,
                    pathEnd,
                    landward,
                    segments
            );
            return;
        }
        if (outlet.type() == HydrologyFeatureType.MOUTH) {
            if (courseType == RiverCourseType.SURFACE) {
                appendSurfaceMouth(courseId, outlet, head, width, depth, pathEnd, segments);
                return;
            }
            HydrologyPoint connection = HydrologyPlanner.withY(outlet.connectionPoint(), head);
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.MOUTH,
                    head,
                    width,
                    depth,
                    line(pathEnd, connection, planner.settings.routing().refinementSpacing()),
                    segments
            );
            return;
        }
        if (courseType == RiverCourseType.SURFACE && surfaceSinkhole(outlet)) {
            appendSurfaceSinkhole(courseId, outlet, head, width, depth, pathEnd, landward, segments);
            return;
        }
        HydrologyPoint connection = HydrologyPlanner.withY(outlet.connectionPoint(), head);
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            HydrologyFeatureType connectorType = courseType == RiverCourseType.SURFACE
                    ? HydrologyFeatureType.MOUTH
                    : HydrologyFeatureType.UNDERGROUND_POOL;
            addFlatSegment(
                    courseId,
                    connectorType,
                    head,
                    width,
                    depth,
                    line(pathEnd, landward, planner.settings.routing().refinementSpacing()),
                    segments
            );
        }
        List<HydrologyPoint> centerline = landward.x() == connection.x() && landward.z() == connection.z()
                ? List.of(landward)
                : List.of(landward, connection);
        addFlatSegment(courseId, outlet.type(), head, width, depth, centerline, segments);
    }

    void appendSurfaceMouth(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int width,
            int depth,
            HydrologyPoint pathEnd,
            List<HydraulicSegment> segments
    ) {
        int seaLevel = outletHead(outlet);
        HydrologyPoint connection = HydrologyPlanner.withY(outlet.connectionPoint(), seaLevel);
        if (surfaceHead != seaLevel) {
            throw new IllegalStateException("A surface mouth reached the coastal crossing above sea level.");
        }
        addFlatSegment(
                courseId,
                HydrologyFeatureType.MOUTH,
                seaLevel,
                width,
                depth,
                line(pathEnd, connection, planner.settings.routing().refinementSpacing()),
                segments
        );
    }

    void appendSurfaceCoastalGrotto(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int width,
            int depth,
            HydrologyPoint pathEnd,
            HydrologyPoint landward,
            List<HydraulicSegment> segments
    ) {
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.SURFACE_POOL,
                    surfaceHead,
                    width,
                    depth,
                    line(pathEnd, landward, planner.settings.routing().refinementSpacing()),
                    segments
            );
        }
        int seaLevel = outletHead(outlet);
        HydrologyPoint connection = HydrologyPlanner.withY(outlet.connectionPoint(), seaLevel);
        if (surfaceHead > seaLevel) {
            addFallingDropSegments(
                    courseId,
                    segments.size(),
                    surfaceHead,
                    seaLevel,
                    width,
                    depth,
                    List.of(landward, connection),
                    true,
                    segments
            );
        }
        addFlatSegment(
                courseId,
                HydrologyFeatureType.COASTAL_GROTTO,
                seaLevel,
                width,
                depth,
                List.of(HydrologyPlanner.withY(outlet.landwardPoint(), seaLevel), connection),
                segments
        );
    }

    void appendSurfaceSinkhole(
            long courseId,
            RiverOutlet outlet,
            int surfaceHead,
            int surfaceWidth,
            int surfaceDepth,
            HydrologyPoint pathEnd,
            HydrologyPoint landward,
            List<HydraulicSegment> segments
    ) {
        if (pathEnd.x() != landward.x() || pathEnd.z() != landward.z()) {
            addFlatSegment(
                    courseId,
                    HydrologyFeatureType.SURFACE_POOL,
                    surfaceHead,
                    surfaceWidth,
                    surfaceDepth,
                    line(pathEnd, landward, planner.settings.routing().refinementSpacing()),
                    segments
            );
        }
        int undergroundHead = outletHead(outlet);
        int throatWidth = Math.max(planner.settings.underground().minimumWidth(), surfaceWidth - 1);
        int throatDepth = planner.settings.underground().minimumDepth();
        HydrologyPoint receiving = HydrologyPlanner.withY(outlet.connectionPoint(), undergroundHead);
        List<HydrologyPoint> descent = sinkholeDescent(
                courseId,
                landward,
                receiving,
                surfaceHead - undergroundHead
        );
        List<HydrologyPoint> gradedDescent = gradedDropCenterline(
                rasterCenterline(descent),
                surfaceHead,
                undergroundHead,
                planner.settings.geometry().drops(),
                planner.settings.geometry().drops().stepLimit(HydrologyFeatureType.SINKHOLE)
        );
        int descentWidth = Math.max(throatWidth, hydraulicDropWidth(throatWidth, true));
        segments.add(new HydraulicSegment(
                HydrologyHash.mix(
                        planner.worldSeed,
                        SEGMENT_SALT,
                        courseId,
                        segments.size(),
                        HydrologyFeatureType.SINKHOLE.ordinal()
                ),
                courseId,
                HydrologyFeatureType.SINKHOLE,
                surfaceHead,
                undergroundHead,
                descentWidth,
                throatDepth,
                false,
                true,
                gradedDescent
        ));
        addFlatSegment(
                courseId,
                HydrologyFeatureType.INLAND_GROTTO,
                undergroundHead,
                throatWidth,
                throatDepth,
                List.of(receiving),
                segments
        );
    }

    List<HydrologyPoint> sinkholeDescent(
            long courseId,
            HydrologyPoint landward,
            HydrologyPoint receiving,
            int drop
    ) {
        int desiredRun = Math.max(
                4,
                (int) StrictMath.ceil(drop / (double) planner.settings.geometry().drops().maximumCascadeStep()) + 1
        );
        double startX = landward.x() - receiving.x();
        double startZ = landward.z() - receiving.z();
        double startRadius = StrictMath.hypot(startX, startZ);
        double startAngle = startRadius <= 0D
                ? HydrologyHash.unit(HydrologyHash.mix(planner.worldSeed, courseId, SEGMENT_SALT)) * StrictMath.PI * 2D
                : StrictMath.atan2(startZ, startX);
        double maximumRadius = Math.max(2D, planner.settings.outlets().inlandGrotto().horizontalRadius() - 1D);
        int turns = Math.max(1, (int) StrictMath.ceil(desiredRun / (StrictMath.PI * maximumRadius)));
        ArrayList<HydrologyPoint> points = new ArrayList<>(desiredRun * 4 + 1);
        int samples = desiredRun * 4;
        for (int sample = 0; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double radius = Math.min(
                    maximumRadius,
                    startRadius * (1D - progress) + maximumRadius * StrictMath.sin(StrictMath.PI * progress)
            );
            double angle = startAngle + turns * StrictMath.PI * 2D * progress;
            int x = (int) StrictMath.round(receiving.x() + StrictMath.cos(angle) * radius);
            int z = (int) StrictMath.round(receiving.z() + StrictMath.sin(angle) * radius);
            if (points.isEmpty() || points.getLast().x() != x || points.getLast().z() != z) {
                points.add(new HydrologyPoint(x, landward.y(), z));
            }
        }
        if (points.getLast().x() != receiving.x() || points.getLast().z() != receiving.z()) {
            points.add(receiving);
        }
        return List.copyOf(points);
    }

    boolean surfaceSinkhole(RiverOutlet outlet) {
        return outlet.type() == HydrologyFeatureType.INLAND_GROTTO
                && planner.settings.outlets().surfaceSinkholesEnabled();
    }

    void addFlatSegment(
            long courseId,
            HydrologyFeatureType type,
            int head,
            int width,
            int depth,
            List<HydrologyPoint> centerline,
            List<HydraulicSegment> segments
    ) {
        long segmentId = HydrologyHash.mix(planner.worldSeed, SEGMENT_SALT, courseId, segments.size(), type.ordinal());
        segments.add(new HydraulicSegment(
                segmentId,
                courseId,
                type,
                head,
                head,
                width,
                depth,
                false,
                false,
                centerline
        ));
    }

    double signedOrganicOffset(long stableId, long salt, double envelope) {
        double unit = HydrologyHash.unit(HydrologyHash.mix(stableId, salt));
        double magnitude = envelope * (0.55D + unit * 0.45D);
        return (HydrologyHash.mix(stableId, salt, 1L) & 1L) == 0L ? magnitude : -magnitude;
    }

    List<HydrologyPoint> line(HydrologyPoint start, HydrologyPoint end, int spacing) {
        double distance = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        int steps = Math.max(1, (int) StrictMath.ceil(distance / spacing));
        ArrayList<HydrologyPoint> points = new ArrayList<>(steps + 1);
        for (int step = 0; step <= steps; step++) {
            double progress = step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            HydrologyPoint point = new HydrologyPoint(x, start.y(), z);
            if (points.isEmpty() || point.x() != points.getLast().x() || point.z() != points.getLast().z()) {
                points.add(point);
            }
        }
        return List.copyOf(points);
    }

    int scaledDimension(
            int styledBase,
            int minimum,
            int maximum,
            int discharge,
            double multiplier
    ) {
        double flowScale = Math.min(
                1D,
                StrictMath.log(discharge + 1D) / StrictMath.log(planner.settings.underground().wideningSources() + 1D)
        );
        int base = styledBase + (int) StrictMath.round((maximum - styledBase) * flowScale);
        return HydrologyPlanner.clamp((int) StrictMath.round(base * multiplier), minimum, maximum);
    }

    int maximumSurfaceDischarge(List<DrainageEdge> edges) {
        int maximum = 1;
        for (DrainageEdge edge : edges) {
            maximum = Math.max(maximum, edge.contributingSurfaceSources());
        }
        return maximum;
    }

    int maximumUndergroundDischarge(List<DrainageEdge> edges) {
        int maximum = 1;
        for (DrainageEdge edge : edges) {
            maximum = Math.max(maximum, edge.contributingUndergroundSources());
        }
        return maximum;
    }

    String chooseProfile(HydrologyTerrainSample terrain, long courseId) {
        List<String> profiles = terrain.preferredProfileKeys();
        int index = HydrologyHash.between(HydrologyHash.mix(planner.worldSeed, courseId, HydrologyHash.text("profile")), 0, profiles.size() - 1);
        return profiles.get(index);
    }

    int outletHead(RiverOutlet outlet) {
        return outlet.directOcean() ? outlet.seaLevel() : outlet.connectionPoint().y();
    }
}
