package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

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
    static final double SURFACE_MAXIMUM_WIDTH_SCALE = 1.18D;

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
            HydrologyPoint trunkPoint = path.points().getFirst();
            HydrologyTerrainSample trunkTerrain = Objects.requireNonNull(
                    planner.sampleDetailed(trunkPoint.x(), trunkPoint.z()),
                    "Hydrology surface trunk left sampled terrain"
            );
            String profileKey = planner.segments.chooseProfile(trunkTerrain, path.outlet().id());
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
                if (mainCourse != null && tributaries >= planner.settings.routing().tributaries()) {
                    break;
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
        SurfaceCourseResult result = planner.surfaceCourseBuilder.build(
                planner.worldSeed,
                courseId,
                profileKey,
                path.points(),
                terminal,
                terminalHead
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
        ArrayList<HydrologyPoint> routedGuide = new ArrayList<>(guide);
        ArrayList<DrainageEdge> routedGuideEdges = new ArrayList<>(guideEdges);
        HydrologyPoint landward = outlet.landwardPoint();
        HydrologyPoint guideEnd = routedGuide.getLast();
        boolean terminateAtSinkhole = planner.segments.surfaceSinkhole(outlet)
                && (guideEnd.x() != landward.x() || guideEnd.z() != landward.z());
        if (terminateAtSinkhole) {
            routedGuide.set(routedGuide.size() - 1, landward);
        }
        List<HydrologyPoint> surfaceGuide = planner.routePaths.withoutDuplicateRoutePoints(routedGuide);
        long geometryId = HydrologyHash.mix(
                planner.worldSeed,
                COURSE_SALT,
                HydrologySourcePlanner.SURFACE_SOURCE_SALT,
                edges.getFirst().upstreamNodeId(),
                outlet.id()
        );
        Objects.requireNonNull(grid, "Surface course routing grid is required");
        HydrologyPlanner.PlanningSamples samples = planner.planningSamples.get();
        DraftProfile profile = planner.currentDraftProfile();
        profile.routeCalls++;
        List<HydrologyPoint> points;
        if (samples == null) {
            profile.routeSolves++;
            long solveStarted = System.nanoTime();
            points = surfaceCourseCenterline(grid, surfaceGuide, geometryId);
            profile.routeSolveNanos += System.nanoTime() - solveStarted;
        } else {
            HashMap<SurfaceRouteKey, List<HydrologyPoint>> routes = samples.surfaceRoutes.computeIfAbsent(
                    grid, ignored -> new HashMap<>()
            );
            SurfaceRouteKey routeKey = new SurfaceRouteKey(surfaceGuide, geometryId);
            points = routes.computeIfAbsent(routeKey, ignored -> {
                profile.routeSolves++;
                long solveStarted = System.nanoTime();
                List<HydrologyPoint> solved = surfaceCourseCenterline(grid, surfaceGuide, geometryId);
                profile.routeSolveNanos += System.nanoTime() - solveStarted;
                return solved;
            });
        }
        if (points.size() < 2) {
            return null;
        }
        List<DrainageEdge> pairEdges = mapCoursePairs(points, routedGuide, routedGuideEdges);
        if (pairEdges.size() != points.size() - 1) {
            return null;
        }
        return new HydrologyCoursePath(
                points,
                pairEdges,
                List.copyOf(edges),
                outlet,
                true,
                routing.organicSurfaceRequired(),
                null
        );
    }

    List<HydrologyPoint> surfaceCourseCenterline(
            HydrologySampledGrid grid,
            List<HydrologyPoint> guide,
            long geometryId
    ) {
        if (guide.size() < 2) {
            return List.of();
        }
        int refinement = planner.settings.routing().refinementSpacing();
        List<SurfaceCurvePoint> base = smoothSurfaceGuide(resampleSurfaceGuide(guide, refinement));
        List<SurfaceCurvePoint> nominal = meanderSurfaceGuide(base, geometryId, refinement);
        if (nominal.size() < 2) {
            return List.of();
        }
        ArrayList<List<RouteCandidate>> layers = new ArrayList<>(nominal.size());
        int transverseCandidates = HydrologyRouteGeometry.SURFACE_COURSE_TRANSVERSE_CANDIDATES;
        for (int pointIndex = 0; pointIndex < nominal.size(); pointIndex++) {
            SurfaceCurvePoint point = nominal.get(pointIndex);
            RouteDirection tangent = surfaceCurveTangent(nominal, pointIndex);
            if (pointIndex == 0 || pointIndex == nominal.size() - 1) {
                HydrologyPoint endpoint = pointIndex == 0 ? guide.getFirst() : guide.getLast();
                layers.add(List.of(planner.routeGeometry.routeEndpoint(endpoint, tangent)));
                continue;
            }
            double progress = pointIndex / (double) (nominal.size() - 1);
            SurfaceCurvePoint basePoint = base.get(pointIndex);
            RoutePosition position = new RoutePosition(
                    point.x(),
                    point.z(),
                    basePoint.x(),
                    basePoint.z(),
                    tangent
            );
            long candidateId = HydrologyHash.mix(geometryId, pointIndex);
            List<RouteCandidate> candidates = planner.routeGeometry.unresolvedSurfaceRouteCandidates(
                    grid,
                    geometryId,
                    candidateId,
                    position,
                    progress,
                    refinement,
                    transverseCandidates
            );
            if (candidates.isEmpty()) {
                return List.of();
            }
            layers.add(candidates);
        }
        ArrayList<List<RouteCandidate>> availableLayers = new ArrayList<>(layers);
        double maximumTurn = Math.min(
                HydrologyRouteGeometry.SURFACE_COURSE_MAXIMUM_TURN_DEGREES,
                planner.settings.geometry().meanders().maximumTurnDegrees()
        );
        while (true) {
            RouteCandidate[] route = planner.routeGeometry.selectCurvatureAwareTerrainRoute(
                    availableLayers,
                    refinement,
                    maximumTurn,
                    HydrologyRouteGeometry.SURFACE_COURSE_TURN_COST
            );
            if (route.length == 0) {
                if (addExhaustedSurfaceRouteFallbacks(
                        grid,
                        availableLayers,
                        nominal,
                        base,
                        geometryId
                )) {
                    continue;
                }
                return List.of();
            }
            int pitRemoval = planner.routePaths.removeTerrainPits(availableLayers, route);
            if (pitRemoval < 0) {
                return List.of();
            }
            if (pitRemoval > 0) {
                continue;
            }
            ArrayList<HydrologyPoint> points = new ArrayList<>(route.length);
            for (RouteCandidate candidate : route) {
                HydrologyPoint point = candidate.point();
                if (points.isEmpty()
                        || point.x() != points.getLast().x()
                        || point.z() != points.getLast().z()) {
                    points.add(point);
                }
            }
            if (points.size() < 2 || !planner.routePaths.traversableRoute(points)) {
                return List.of();
            }
            return planner.routePaths.smoothTerrainRoute(points);
        }
    }

    boolean addExhaustedSurfaceRouteFallbacks(
            HydrologySampledGrid grid,
            List<List<RouteCandidate>> layers,
            List<SurfaceCurvePoint> nominal,
            List<SurfaceCurvePoint> base,
            long geometryId
    ) {
        boolean added = false;
        for (int layerIndex = 1; layerIndex < layers.size() - 1; layerIndex++) {
            List<RouteCandidate> layer = layers.get(layerIndex);
            if (!exhaustedSurfaceRouteLayer(layer)) {
                continue;
            }
            SurfaceCurvePoint nominalPoint = nominal.get(layerIndex);
            SurfaceCurvePoint basePoint = base.get(layerIndex);
            RouteDirection tangent = surfaceCurveTangent(nominal, layerIndex);
            int fallbackX = (int) StrictMath.round(basePoint.x());
            int fallbackZ = (int) StrictMath.round(basePoint.z());
            long packed = RiverFootprint.pack(fallbackX, fallbackZ);
            boolean present = false;
            for (RouteCandidate candidate : layer) {
                if (RiverFootprint.pack(candidate.point().x(), candidate.point().z()) == packed) {
                    present = true;
                    break;
                }
            }
            if (present) {
                continue;
            }
            RouteTerrainEstimate estimate = planner.routeGeometry.routeTerrainEstimate(grid, fallbackX, fallbackZ);
            double offset = (basePoint.x() - nominalPoint.x()) * -tangent.z()
                    + (basePoint.z() - nominalPoint.z()) * tangent.x();
            double localScore = estimate.terrainScore()
                    + StrictMath.abs(offset) * 0.18D
                    + HydrologyHash.unit(HydrologyHash.mix(
                    planner.worldSeed,
                    geometryId,
                    HydrologyHash.mix(geometryId, layerIndex),
                    fallbackX,
                    fallbackZ
            )) * 1.0E-6D;
            ArrayList<RouteCandidate> expanded = new ArrayList<>(layer);
            expanded.add(new RouteCandidate(
                    new HydrologyPoint(fallbackX, estimate.height(), fallbackZ),
                    offset,
                    localScore,
                    estimate.terrainScore(),
                    tangent,
                    false
            ));
            layers.set(layerIndex, List.copyOf(expanded));
            added = true;
        }
        return added;
    }

    boolean exhaustedSurfaceRouteLayer(List<RouteCandidate> layer) {
        for (RouteCandidate candidate : layer) {
            if (!candidate.terrainResolved() || Double.isFinite(candidate.localScore())) {
                return false;
            }
        }
        return true;
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

    double surfaceRouteCandidateBankPenalty(RouteCandidate candidate) {
        int minimumBankDistance = surfaceBankDistance(planner.settings.surface().minimumWidth());
        int maximumBankDistance = surfaceBankDistance(planner.settings.surface().maximumWidth());
        int maximumBankRise = Math.addExact(
                planner.settings.surface().maximumIncision(),
                (int) StrictMath.ceil(planner.settings.surface().banks().minimumBlendWidth() * 0.5D)
        );
        HydrologyPoint point = candidate.point();
        HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
        if (terrain == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (terrain.slope() >= planner.settings.hydraulics().waterfallMinimumDrop()) {
            return 0D;
        }
        int head = Math.subtractExact(
                terrain.naturalHeight(),
                planner.settings.surface().banks().sink()
        );
        return surfaceRouteBankPenalty(
                point,
                candidate.tangent(),
                minimumBankDistance,
                maximumBankDistance,
                head,
                maximumBankRise
        );
    }

    double surfaceRouteBankPenalty(
            HydrologyPoint point,
            RouteDirection tangent,
            int minimumDistance,
            int maximumDistance,
            int head,
            int maximumBankRise
    ) {
        double penalty = 0D;
        for (int distance = minimumDistance; distance <= maximumDistance; distance++) {
            penalty += surfaceRouteBankPenalty(point, tangent, distance, head, maximumBankRise);
            penalty += surfaceRouteBankPenalty(point, tangent, -distance, head, maximumBankRise);
        }
        return penalty;
    }

    double surfaceRouteBankPenalty(
            HydrologyPoint point,
            RouteDirection tangent,
            int signedDistance,
            int head,
            int maximumBankRise
    ) {
        HydrologyTerrainSample bank = surfaceBankTerrain(point, tangent, signedDistance);
        if (bank == null) {
            return 1.0E12D;
        }
        int rise = bank.naturalHeight() - head;
        int shortfall = HydrologyRouteGeometry.SURFACE_MINIMUM_NATURAL_BANK_RISE - rise;
        if (shortfall > 0) {
            return (double) shortfall * shortfall * 4096D;
        }
        int excess = Math.max(0, rise - maximumBankRise);
        return (double) excess * excess * 128D;
    }

    int surfaceBankDistance(int width) {
        int channelRadius = (int) StrictMath.ceil(width / 2D);
        return Math.max(
                channelRadius + 1,
                (int) StrictMath.ceil(
                        channelRadius * SURFACE_MAXIMUM_WIDTH_SCALE + planner.settings.surface().shoreWidth()
                ) + 1
        );
    }

    List<SurfaceCurvePoint> resampleSurfaceGuide(List<HydrologyPoint> guide, int spacing) {
        double[] cumulative = new double[guide.size()];
        for (int pointIndex = 1; pointIndex < guide.size(); pointIndex++) {
            HydrologyPoint previous = guide.get(pointIndex - 1);
            HydrologyPoint point = guide.get(pointIndex);
            cumulative[pointIndex] = cumulative[pointIndex - 1]
                    + StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
        }
        double totalLength = cumulative[cumulative.length - 1];
        if (totalLength <= 0D) {
            return List.of();
        }
        int capacity = Math.addExact(2, (int) StrictMath.ceil(totalLength / spacing));
        ArrayList<SurfaceCurvePoint> sampled = new ArrayList<>(capacity);
        int segmentIndex = 0;
        for (double distance = 0D; distance < totalLength; distance += spacing) {
            while (segmentIndex < guide.size() - 2
                    && cumulative[segmentIndex + 1] < distance) {
                segmentIndex++;
            }
            HydrologyPoint start = guide.get(segmentIndex);
            HydrologyPoint end = guide.get(segmentIndex + 1);
            double segmentLength = cumulative[segmentIndex + 1] - cumulative[segmentIndex];
            double progress = segmentLength <= 0D
                    ? 0D
                    : (distance - cumulative[segmentIndex]) / segmentLength;
            sampled.add(new SurfaceCurvePoint(
                    start.x() + (end.x() - start.x()) * progress,
                    start.z() + (end.z() - start.z()) * progress,
                    distance
            ));
        }
        HydrologyPoint end = guide.getLast();
        sampled.add(new SurfaceCurvePoint(end.x(), end.z(), totalLength));
        return List.copyOf(sampled);
    }

    List<SurfaceCurvePoint> smoothSurfaceGuide(List<SurfaceCurvePoint> guide) {
        if (guide.size() < 3) {
            return guide;
        }
        List<SurfaceCurvePoint> current = guide;
        int passes = Math.max(2, planner.settings.geometry().meanders().smoothingPasses() + 1);
        for (int pass = 0; pass < passes; pass++) {
            ArrayList<SurfaceCurvePoint> smoothed = new ArrayList<>(current.size());
            smoothed.add(current.getFirst());
            for (int pointIndex = 1; pointIndex < current.size() - 1; pointIndex++) {
                SurfaceCurvePoint previous = current.get(pointIndex - 1);
                SurfaceCurvePoint point = current.get(pointIndex);
                SurfaceCurvePoint next = current.get(pointIndex + 1);
                smoothed.add(new SurfaceCurvePoint(
                        previous.x() * 0.25D + point.x() * 0.5D + next.x() * 0.25D,
                        previous.z() * 0.25D + point.z() * 0.5D + next.z() * 0.25D,
                        point.distance()
                ));
            }
            smoothed.add(current.getLast());
            current = List.copyOf(smoothed);
        }
        return current;
    }

    List<SurfaceCurvePoint> meanderSurfaceGuide(
            List<SurfaceCurvePoint> guide,
            long geometryId,
            int refinement
    ) {
        HydrologyPlannerSettings.Meanders meanders = planner.settings.geometry().meanders();
        double detailWeight = meanders.detailStrength() * 0.08D;
        double totalWeight = meanders.primaryStrength() + detailWeight;
        if (guide.size() < 3 || totalWeight <= 0D || meanders.maximumOffsetRatio() <= 0D) {
            return guide;
        }
        double maximumOffset = planner.settings.routing().sampleSpacing() * Math.min(
                0.16D,
                meanders.maximumOffsetRatio() * 0.32D
        );
        double primaryPhase = HydrologyHash.unit(HydrologyHash.mix(geometryId, HydrologyRouteGeometry.ROUTE_WORM_PRIMARY_SALT))
                * StrictMath.PI * 2D;
        double detailPhase = HydrologyHash.unit(HydrologyHash.mix(geometryId, HydrologyRouteGeometry.ROUTE_WORM_DETAIL_SALT))
                * StrictMath.PI * 2D;
        double totalLength = guide.getLast().distance();
        double taperLength = Math.max(
                refinement * 8D,
                Math.min(planner.settings.routing().sampleSpacing() * 0.75D, meanders.primaryWavelength() * 0.35D)
        );
        ArrayList<SurfaceCurvePoint> meandered = new ArrayList<>(guide.size());
        for (int pointIndex = 0; pointIndex < guide.size(); pointIndex++) {
            SurfaceCurvePoint point = guide.get(pointIndex);
            RouteDirection tangent = surfaceCurveTangent(guide, pointIndex);
            double endpointDistance = Math.min(point.distance(), totalLength - point.distance());
            double taperProgress = Math.min(1D, endpointDistance / taperLength);
            double taper = StrictMath.pow(StrictMath.sin(taperProgress * StrictMath.PI * 0.5D), 2D);
            double primary = StrictMath.sin(
                    point.distance() * StrictMath.PI * 2D / meanders.primaryWavelength() + primaryPhase
            ) * meanders.primaryStrength();
            double detail = StrictMath.sin(
                    point.distance() * StrictMath.PI * 2D / meanders.detailWavelength() + detailPhase
            ) * detailWeight;
            double offset = maximumOffset * taper * (primary + detail) / totalWeight;
            meandered.add(new SurfaceCurvePoint(
                    point.x() - tangent.z() * offset,
                    point.z() + tangent.x() * offset,
                    point.distance()
            ));
        }
        return List.copyOf(meandered);
    }

    RouteDirection surfaceCurveTangent(List<SurfaceCurvePoint> points, int pointIndex) {
        SurfaceCurvePoint start = points.get(Math.max(0, pointIndex - 2));
        SurfaceCurvePoint end = points.get(Math.min(points.size() - 1, pointIndex + 2));
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double length = StrictMath.hypot(deltaX, deltaZ);
        return length <= 0D
                ? new RouteDirection(1D, 0D)
                : new RouteDirection(deltaX / length, deltaZ / length);
    }

    List<DrainageEdge> mapCoursePairs(
            List<HydrologyPoint> points,
            List<HydrologyPoint> guide,
            List<DrainageEdge> guideEdges
    ) {
        ArrayList<DrainageEdge> mapped = new ArrayList<>(points.size() - 1);
        int minimumGuideIndex = 0;
        for (int pointIndex = 0; pointIndex < points.size() - 1; pointIndex++) {
            HydrologyPoint first = points.get(pointIndex);
            HydrologyPoint second = points.get(pointIndex + 1);
            double x = (first.x() + second.x()) * 0.5D;
            double z = (first.z() + second.z()) * 0.5D;
            int selectedIndex = minimumGuideIndex;
            double selectedDistance = Double.POSITIVE_INFINITY;
            for (int guideIndex = minimumGuideIndex; guideIndex < guideEdges.size(); guideIndex++) {
                double distance = pointToSegmentDistanceSquared(
                        x,
                        z,
                        guide.get(guideIndex),
                        guide.get(guideIndex + 1)
                );
                if (distance < selectedDistance) {
                    selectedIndex = guideIndex;
                    selectedDistance = distance;
                }
            }
            minimumGuideIndex = selectedIndex;
            mapped.add(guideEdges.get(selectedIndex));
        }
        return List.copyOf(mapped);
    }

    double pointToSegmentDistanceSquared(
            double x,
            double z,
            HydrologyPoint start,
            HydrologyPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (lengthSquared == 0D) {
            double pointX = x - start.x();
            double pointZ = z - start.z();
            return pointX * pointX + pointZ * pointZ;
        }
        double progress = ((x - start.x()) * deltaX + (z - start.z()) * deltaZ) / lengthSquared;
        progress = Math.max(0D, Math.min(1D, progress));
        double nearestX = start.x() + deltaX * progress;
        double nearestZ = start.z() + deltaZ * progress;
        double pointX = x - nearestX;
        double pointZ = z - nearestZ;
        return pointX * pointX + pointZ * pointZ;
    }

    HydrologyTerrainSample surfaceBankTerrain(
            HydrologyPoint point,
            RouteDirection tangent,
            int signedDistance
    ) {
        HydrologyPoint bankPoint = surfaceBankPoint(point, tangent, signedDistance);
        int bankX = bankPoint.x();
        int bankZ = bankPoint.z();
        return planner.sampleLandBasisWithoutSlope(bankX, bankZ);
    }

    HydrologyPoint surfaceBankPoint(HydrologyPoint point, RouteDirection tangent, int signedDistance) {
        return new HydrologyPoint(
                (int) StrictMath.round(point.x() - tangent.z() * signedDistance),
                point.y(),
                (int) StrictMath.round(point.z() + tangent.x() * signedDistance)
        );
    }

    record SurfaceCurvePoint(double x, double z, double distance) {
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
