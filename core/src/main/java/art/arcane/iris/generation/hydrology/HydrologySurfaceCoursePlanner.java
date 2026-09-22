package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceCourseBuilder;
import art.arcane.iris.generation.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.generation.hydrology.surface.SurfaceTerminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalLong;
import java.util.Objects;

final class HydrologySurfaceCoursePlanner {
    private final HydrologyPlanner planner;

    HydrologySurfaceCoursePlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final long COURSE_SALT = 0x434f55525345L;
    static final double SURFACE_MAXIMUM_RENDERED_TURN_DEGREES = 50D;
    static final double SURFACE_MAXIMUM_ISOLATED_TURN_DEGREES = 35D;
    static final double SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES = 10D;
    static final double SURFACE_MAXIMUM_P95_TURN_DEGREES = 35D;
    static final double SURFACE_MAXIMUM_SINUOSITY = 1.45D;
    static final double SURFACE_GRID_LOCK_TOLERANCE_DEGREES = 5D;
    static final double SURFACE_MAXIMUM_GRID_LOCKED_FRACTION = 0.5D;
    static final double SURFACE_MAXIMUM_GRID_LOCKED_RUN = 64D;

    void compileSurfaceCourses(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            CompiledGraph graph,
            List<Integer> sources,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, ArrayList<SurfaceCourseDraft>> draftsByOutlet = new LinkedHashMap<>();
        for (int source : sources) {
            HydrologyGridNode sourceNode = grid.node(source);
            long sourceCourseId = HydrologyHash.mix(
                    planner.worldSeed,
                    COURSE_SALT,
                    HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                    sourceNode.id()
            );
            HydrologyCoursePath path = coursePath(grid, routing, graph, source, true);
            if (path == null) {
                planner.undergroundCourses.addCompiledSourceDiagnostic(
                        sourceNode,
                        true,
                        sourceCourseId,
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH,
                        diagnostics
                );
                continue;
            }
            long courseId = HydrologyHash.mix(sourceCourseId, path.outlet().id());
            String profileKey = HydrologySurfaceProfiles.chooseProfile(planner, path, sourceNode.terrain());
            if (profileKey == null) {
                planner.undergroundCourses.addCompiledSourceDiagnostic(sourceNode, true, sourceCourseId,
                        HydrologyCandidateRejection.POLICY_EXCLUDED, diagnostics);
                continue;
            }
            draftsByOutlet.computeIfAbsent(path.outlet().id(), (Long ignored) -> new ArrayList<>())
                    .add(new SurfaceCourseDraft(sourceNode, courseId, profileKey, path));
        }
        for (ArrayList<SurfaceCourseDraft> outletDrafts : draftsByOutlet.values()) {
            outletDrafts.sort(Comparator
                    .comparingDouble((SurfaceCourseDraft draft) -> planner.tributaries.edgeLength(
                            draft.path().edges(),
                            draft.path().edges().size()
                    ))
                    .reversed()
                    .thenComparingLong(SurfaceCourseDraft::courseId));
            RiverCourse mainCourse = null;
            HashMap<Long, SurfaceCourseBuild> mainRejections = new HashMap<>();
            int tributaries = 0;
            for (SurfaceCourseDraft draft : outletDrafts) {
                int tributaryLimit = draft.source().terrain().surfacePolicy().tributaries(planner.settings.routing().tributaries());
                if (mainCourse != null && tributaries >= tributaryLimit) {
                    continue;
                }
                if (mainCourse == null) {
                    SurfaceCourseBuild candidate = buildSurfaceCourse(
                            draft.courseId(),
                            draft.profileKey(),
                            draft.source(),
                            draft.path()
                    );
                    if (candidate.course() == null) {
                        mainRejections.put(draft.courseId(), candidate);
                        continue;
                    }
                    mainCourse = candidate.course();
                    courses.add(mainCourse);
                    continue;
                }
                RiverCourse tributary = planner.tributaries.buildSurfaceTributary(draft, mainCourse, diagnostics);
                if (tributary != null) {
                    courses.add(tributary);
                    tributaries++;
                }
            }
            if (mainCourse == null) {
                for (SurfaceCourseDraft draft : outletDrafts) {
                    SurfaceCourseBuild rejected = mainRejections.get(draft.courseId());
                    planner.undergroundCourses.addCompiledSourceDiagnostic(
                            draft.source(),
                            true,
                            draft.courseId(),
                            rejected.rejection(),
                            rejected.rejectionDetail(),
                            diagnostics
                    );
                }
            }
        }
    }

    SurfaceCourseBuild buildSurfaceCourse(
            long courseId,
            String profileKey,
            HydrologyGridNode source,
            HydrologyCoursePath initialPath
    ) {
        return buildSurfaceCourseAtPath(courseId, profileKey, source, initialPath);
    }

    SurfaceCourseBuild buildSurfaceCourseAtPath(
            long courseId,
            String profileKey,
            HydrologyGridNode source,
            HydrologyCoursePath path
    ) {
        if (path.points().size() < 2) {
            return SurfaceCourseBuild.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, 0);
        }
        RiverOutlet outlet = path.outlet();
        boolean surfaceSinkhole = path.reachesOutlet() && planner.segments.surfaceSinkhole(outlet);
        SurfaceTerminal terminal;
        if (!path.reachesOutlet() || outlet.type() == HydrologyFeatureType.INLAND_GROTTO) {
            terminal = SurfaceTerminal.SINKHOLE;
        } else if (outlet.type() == HydrologyFeatureType.COASTAL_GROTTO) {
            terminal = SurfaceTerminal.COASTAL_GROTTO;
        } else {
            terminal = SurfaceTerminal.OCEAN_MOUTH;
        }
        int outletHead = planner.segments.outletHead(outlet);
        int terminalHead = surfaceSinkhole ? Math.addExact(outletHead, 1) : outletHead;
        SurfaceCourseBuilder builder = terminal == SurfaceTerminal.OCEAN_MOUTH
                ? new SurfaceCourseBuilder(planner.settings.surface(), HydrologyOceanReceiver.forOutlet(
                planner.settings, planner::sampleBasisWithoutSlope, outlet), planner.geometrySampler, planner.settings.seaLevel())
                : planner.surfaceCourseBuilder;
        SurfaceCourseResult result = builder.build(
                planner.worldSeed,
                courseId,
                profileKey,
                path.points(),
                terminal,
                terminalHead,
                planner.sourcePlanner.minimumCourseLength(source.terrain(), true)
        );
        if (!result.accepted()) {
            return SurfaceCourseBuild.rejected(result.rejection(), result.rejectionDetail());
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
        if (path.reachesOutlet()) {
            HydrologyCoursePath terminalPath = new HydrologyCoursePath(
                    List.of(result.pathEnd()),
                    path.pairEdges(),
                    path.edges(),
                    outlet,
                    true,
                    path.organicSurfaceRequired(),
                    null
            );
            planner.segments.appendOutletSegments(
                    RiverCourseType.SURFACE,
                    courseId,
                    terminalPath,
                    result.lastHead(),
                    new int[] {result.lastWidth()},
                    new int[] {result.lastDepth()},
                    segments
            );
        }
        if (path.organicSurfaceRequired()) {
            int shapeRejection = surfaceShapeRejection(path.points());
            if (shapeRejection != 0) {
                return SurfaceCourseBuild.rejected(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, shapeRejection);
            }
        }
        int discharge = planner.segments.maximumSurfaceDischarge(path.edges());
        return SurfaceCourseBuild.accepted(new RiverCourse(
                courseId,
                RiverCourseType.SURFACE,
                OptionalLong.of(source.id()),
                OptionalLong.of(outlet.id()),
                profileKey,
                discharge,
                path.edges(),
                segments
        ));
    }

    /** 0 when the shape is organic; otherwise 1 too short, 2 too sinuous, 3 grid-locked, 4 sharp turns. */
    int surfaceShapeRejection(List<HydrologyPoint> routedCenterline) {
        return surfaceShapeRejection(routedCenterline, false);
    }

    /**
     * As above; a tributary reach keeps the hard turn cap and the overall turn budget but may bend
     * once where it leaves its own valley for the stem's, which a main river is not allowed to do.
     */
    int surfaceShapeRejection(List<HydrologyPoint> routedCenterline, boolean tributary) {
        List<HydrologyPoint> centerline = planner.routePaths.withoutDuplicateRoutePoints(routedCenterline);
        if (centerline.size() < 3) {
            return 1;
        }
        double routedLength = centerlineLength(centerline);
        HydrologyPoint start = centerline.getFirst();
        HydrologyPoint end = centerline.getLast();
        double directLength = StrictMath.hypot(end.x() - start.x(), end.z() - start.z());
        if (directLength <= 0D
                || routedLength / directLength > SURFACE_MAXIMUM_SINUOSITY) {
            return 2;
        }
        List<HydrologyPoint> sampled = resampleRouteCenterline(
                centerline,
                Math.max(16D, planner.settings.routing().refinementSpacing() * 4D)
        );
        double lockedLength = 0D;
        double currentLockedRun = 0D;
        double longestLockedRun = 0D;
        for (int pointIndex = 1; pointIndex < sampled.size(); pointIndex++) {
            HydrologyPoint previous = sampled.get(pointIndex - 1);
            HydrologyPoint point = sampled.get(pointIndex);
            double deltaX = point.x() - previous.x();
            double deltaZ = point.z() - previous.z();
            double length = StrictMath.hypot(deltaX, deltaZ);
            if (length <= 0D) {
                continue;
            }
            double angle = StrictMath.toDegrees(StrictMath.atan2(deltaZ, deltaX));
            double remainder = Math.floorMod(
                    (int) StrictMath.round(angle * 1_000_000D),
                    45_000_000
            ) / 1_000_000D;
            double gridDistance = Math.min(remainder, 45D - remainder);
            if (gridDistance <= SURFACE_GRID_LOCK_TOLERANCE_DEGREES) {
                lockedLength += length;
                currentLockedRun += length;
                longestLockedRun = Math.max(longestLockedRun, currentLockedRun);
            } else {
                currentLockedRun = 0D;
            }
        }
        if (routedLength >= 96D
                && (lockedLength / routedLength > SURFACE_MAXIMUM_GRID_LOCKED_FRACTION
                || longestLockedRun > SURFACE_MAXIMUM_GRID_LOCKED_RUN)) {
            return 3;
        }
        ArrayList<Double> turnAngles = new ArrayList<>();
        for (int pointIndex = 1; pointIndex < sampled.size() - 1; pointIndex++) {
            double turn = HydrologyRoutePath.routeTurnDegrees(
                    sampled.get(pointIndex - 1),
                    sampled.get(pointIndex),
                    sampled.get(pointIndex + 1)
            );
            if (turn > SURFACE_MAXIMUM_RENDERED_TURN_DEGREES) {
                return 4;
            }
            turnAngles.add(turn);
        }
        if (turnAngles.isEmpty()) {
            return 1;
        }
        for (int turnIndex = 0; turnIndex < turnAngles.size() && !tributary; turnIndex++) {
            double turn = turnAngles.get(turnIndex);
            double previous = turnIndex == 0 ? 0D : turnAngles.get(turnIndex - 1);
            double next = turnIndex + 1 == turnAngles.size() ? 0D : turnAngles.get(turnIndex + 1);
            if (turn > SURFACE_MAXIMUM_ISOLATED_TURN_DEGREES
                    && previous <= SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES
                    && next <= SURFACE_MAXIMUM_ISOLATED_NEIGHBOR_TURN_DEGREES) {
                return 4;
            }
        }
        turnAngles.sort(Double::compare);
        int percentileIndex = (int) StrictMath.ceil(0.95D * turnAngles.size()) - 1;
        double percentile = turnAngles.get(
                Math.max(0, Math.min(percentileIndex, turnAngles.size() - 1))
        );
        if (percentile > (tributary ? SURFACE_MAXIMUM_RENDERED_TURN_DEGREES : SURFACE_MAXIMUM_P95_TURN_DEGREES)) {
            return 4;
        }
        return 0;
    }

    double centerlineLength(List<HydrologyPoint> points) {
        double length = 0D;
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            HydrologyPoint previous = points.get(pointIndex - 1);
            HydrologyPoint current = points.get(pointIndex);
            length += StrictMath.hypot(current.x() - previous.x(), current.z() - previous.z());
        }
        return length;
    }

    List<HydrologyPoint> resampleRouteCenterline(
            List<HydrologyPoint> points,
            double spacing
    ) {
        double[] cumulative = new double[points.size()];
        for (int pointIndex = 1; pointIndex < points.size(); pointIndex++) {
            HydrologyPoint previous = points.get(pointIndex - 1);
            HydrologyPoint point = points.get(pointIndex);
            cumulative[pointIndex] = cumulative[pointIndex - 1]
                    + StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        double totalLength = cumulative[cumulative.length - 1];
        if (totalLength <= spacing) {
            return points;
        }
        int samples = Math.max(2, (int) StrictMath.floor(totalLength / spacing));
        ArrayList<HydrologyPoint> sampled = new ArrayList<>(samples + 2);
        int segmentIndex = 0;
        for (double distance = 0D; distance < totalLength; distance += spacing) {
            while (segmentIndex < points.size() - 2
                    && cumulative[segmentIndex + 1] < distance) {
                segmentIndex++;
            }
            HydrologyPoint start = points.get(segmentIndex);
            HydrologyPoint end = points.get(segmentIndex + 1);
            double segmentLength = cumulative[segmentIndex + 1] - cumulative[segmentIndex];
            double progress = segmentLength <= 0D
                    ? 0D
                    : (distance - cumulative[segmentIndex]) / segmentLength;
            HydrologyPoint point = new HydrologyPoint(
                    (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress),
                    (int) StrictMath.round(start.y() + (end.y() - start.y()) * progress),
                    (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress)
            );
            if (sampled.isEmpty()
                    || point.x() != sampled.getLast().x()
                    || point.z() != sampled.getLast().z()) {
                sampled.add(point);
            }
        }
        HydrologyPoint last = points.getLast();
        HydrologyPoint sampledLast = sampled.isEmpty() ? null : sampled.getLast();
        double remaining = sampledLast == null
                ? Double.POSITIVE_INFINITY
                : StrictMath.hypot(last.x() - sampledLast.x(), last.z() - sampledLast.z());
        if (remaining >= spacing * 0.5D) {
            sampled.add(last);
        }
        return List.copyOf(sampled);
    }

    HydrologyCoursePath coursePath(
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            CompiledGraph graph,
            int sourceIndex,
            boolean surface
    ) {
        ArrayList<DrainageEdge> edges = new ArrayList<>();
        ArrayList<HydrologyPoint> guide = new ArrayList<>();
        ArrayList<DrainageEdge> guideEdges = new ArrayList<>();
        int current = sourceIndex;
        int steps = 0;
        while (routing.parent()[current] >= 0) {
            DrainageEdge edge = graph.edgeByUpstream().get(current);
            if (edge == null) {
                return null;
            }
            edges.add(edge);
            int downstream = routing.parent()[current];
            if (surface) {
                for (HydrologyPoint point : edge.centerline()) {
                    if (guide.isEmpty()) {
                        guide.add(point);
                    } else if (point.x() != guide.getLast().x() || point.z() != guide.getLast().z()) {
                        guide.add(point);
                        guideEdges.add(edge);
                    }
                }
            }
            current = downstream;
            if (++steps > planner.settings.routing().maximumRouteNodes()) {
                return null;
            }
        }
        int outletIndex = routing.outletIndex()[sourceIndex];
        if (outletIndex < 0 || edges.isEmpty()) {
            return null;
        }
        RiverOutlet outlet = routing.outlets().get(outletIndex).outlet();
        if (!surface) {
            return planner.undergroundCourses.undergroundCoursePath(edges, outlet);
        }
        if (guide.size() < 2 || guideEdges.size() != guide.size() - 1) {
            return null;
        }
        return new HydrologyCoursePath(List.copyOf(guide), List.copyOf(guideEdges), List.copyOf(edges),
                outlet, true, routing.organicSurfaceRequired(), null);
    }

    double routeTurnScore(List<HydrologyPoint> route) {
        List<HydrologyPoint> sampled = resampleRouteCenterline(
                route,
                Math.max(16D, planner.settings.routing().refinementSpacing() * 4D)
        );
        if (sampled.size() < 3) {
            return Double.POSITIVE_INFINITY;
        }
        ArrayList<Double> turns = new ArrayList<>(sampled.size() - 2);
        double maximum = 0D;
        for (int pointIndex = 1; pointIndex < sampled.size() - 1; pointIndex++) {
            double turn = HydrologyRoutePath.routeTurnDegrees(
                    sampled.get(pointIndex - 1),
                    sampled.get(pointIndex),
                    sampled.get(pointIndex + 1)
            );
            turns.add(turn);
            maximum = Math.max(maximum, turn);
        }
        turns.sort(Double::compare);
        int percentileIndex = (int) StrictMath.ceil(0.95D * turns.size()) - 1;
        double percentile = turns.get(Math.max(0, Math.min(percentileIndex, turns.size() - 1)));
        return percentile + maximum * 0.05D;
    }

    record SurfaceCourseBuild(
            RiverCourse course,
            HydrologyCandidateRejection rejection,
            int rejectionDetail
    ) {
        SurfaceCourseBuild {
            if ((course == null) == (rejection == null)) {
                throw new IllegalArgumentException("Surface course builds require exactly one result.");
            }
        }

        private static SurfaceCourseBuild accepted(RiverCourse course) {
            return new SurfaceCourseBuild(Objects.requireNonNull(course, "course"), null, 0);
        }

        private static SurfaceCourseBuild rejected(HydrologyCandidateRejection rejection, int detail) {
            return new SurfaceCourseBuild(null, Objects.requireNonNull(rejection, "rejection"), detail);
        }
    }
}
