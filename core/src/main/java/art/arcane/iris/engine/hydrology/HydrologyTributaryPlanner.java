package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.surface.SurfaceCourseResult;
import art.arcane.iris.engine.hydrology.surface.SurfaceTerminal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

final class HydrologyTributaryPlanner {
    private final HydrologyPlanner planner;

    HydrologyTributaryPlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    static final int TRIBUTARY_JUNCTION_ATTEMPTS = 4;
    static final int TRIBUTARY_BACKWATER_LIMIT = 3;
    static final int TRIBUTARY_JUNCTION_FLAT_STATIONS = 6;
    static final int TRIBUTARY_JUNCTION_PAIR_INDEX = 0x4a4f494e;

    List<RiverCourse> normalizeOutletContinuations(List<RiverCourse> courses) {
        HashMap<OutletCourseKey, RiverCourse> canonicalCourses = new HashMap<>();
        for (RiverCourse course : courses) {
            if (!hasInlandOutletContinuation(course) || course.outletId().isEmpty()) {
                continue;
            }
            OutletCourseKey key = new OutletCourseKey(course.outletId().getAsLong());
            canonicalCourses.merge(
                    key,
                    course,
                    this::preferredOutletContinuation
            );
        }
        ArrayList<RiverCourse> normalized = new ArrayList<>(courses.size());
        for (RiverCourse course : courses) {
            if (course.outletId().isEmpty()) {
                normalized.add(course);
                continue;
            }
            OutletCourseKey key = new OutletCourseKey(course.outletId().getAsLong());
            RiverCourse canonicalCourse = canonicalCourses.get(key);
            if (canonicalCourse == null || canonicalCourse.id() == course.id()) {
                normalized.add(course);
                continue;
            }
            List<HydraulicSegment> segments = course.segments();
            int retainedSize = course.surfaceSinkholeContinuation()
                    ? segments.size() - 2
                    : segments.getLast().type() == HydrologyFeatureType.INLAND_GROTTO
                    ? segments.size() - 1
                    : segments.size();
            retainedSize = trimCanonicalOutletOverlap(course, canonicalCourse, retainedSize);
            if (retainedSize < 1) {
                continue;
            }
            normalized.add(new RiverCourse(
                    course.id(),
                    course.type(),
                    course.sourceNodeId(),
                    course.outletId(),
                    course.profileKey(),
                    course.discharge(),
                    course.drainageEdges(),
                    segments.subList(0, retainedSize)
            ));
        }
        return List.copyOf(normalized);
    }

    int trimCanonicalOutletOverlap(
            RiverCourse course,
            RiverCourse canonicalCourse,
            int retainedSize
    ) {
        if (!canonicalCourse.surfaceSinkholeContinuation()
                || canonicalCourse.segments().isEmpty()
                || retainedSize < 1) {
            return retainedSize;
        }
        HydraulicSegment canonicalGrotto = canonicalCourse.segments().getLast();
        if (canonicalGrotto.type() != HydrologyFeatureType.INLAND_GROTTO) {
            return retainedSize;
        }
        int exclusionRadius = Math.addExact(
                planner.settings.outlets().inlandGrotto().horizontalRadius(),
                Math.addExact((int) StrictMath.ceil(planner.settings.underground().maximumWidth() / 2D), 2)
        );
        long exclusionRadiusSquared = (long) exclusionRadius * exclusionRadius;
        while (retainedSize > 0 && segmentOverlapsOutlet(
                course.segments().get(retainedSize - 1),
                canonicalGrotto,
                exclusionRadiusSquared
        )) {
            retainedSize--;
        }
        return retainedSize;
    }

    boolean segmentOverlapsOutlet(
            HydraulicSegment segment,
            HydraulicSegment outlet,
            long exclusionRadiusSquared
    ) {
        for (HydrologyPoint point : segment.centerline()) {
            for (HydrologyPoint outletPoint : outlet.centerline()) {
                if (point.distanceSquared2D(outletPoint) <= exclusionRadiusSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    List<RiverCourse> normalizeSharedTrunks(List<RiverCourse> courses, List<HydrologyDiagnosticCandidate> diagnostics) {
        HashMap<CourseEdgeKey, RiverCourse> edgeOwners = new HashMap<>();
        for (RiverCourse course : courses) {
            if (course.sourceNodeId().isEmpty()) {
                continue;
            }
            for (DrainageEdge edge : course.drainageEdges()) {
                CourseEdgeKey key = new CourseEdgeKey(course.type(), edge.id());
                edgeOwners.merge(
                        key,
                        course,
                        this::preferredTrunkCourse
                );
            }
        }
        ArrayList<RiverCourse> normalized = new ArrayList<>(courses.size());
        for (RiverCourse course : courses) {
            if (course.sourceNodeId().isEmpty() || course.drainageEdges().isEmpty()) {
                normalized.add(course);
                continue;
            }
            int ownedEdgeCount = 0;
            for (DrainageEdge edge : course.drainageEdges()) {
                RiverCourse owner = edgeOwners.get(new CourseEdgeKey(course.type(), edge.id()));
                if (owner == null || owner.id() != course.id()) {
                    break;
                }
                ownedEdgeCount++;
            }
            if (ownedEdgeCount == course.drainageEdges().size()) {
                normalized.add(course);
                continue;
            }
            if (ownedEdgeCount < 1) {
                continue;
            }
            HydrologyPoint boundary = course.drainageEdges()
                    .get(ownedEdgeCount - 1)
                    .centerline()
                    .getLast();
            if (course.type() == RiverCourseType.SURFACE) {
                // Surface tributaries are cut at their junction before they are built, so a surface
                // course still sharing drainage with a longer trunk has no junction to keep it.
                addTributaryDiagnostic(course.id(), course.segments().getFirst().start(),
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH, ownedEdgeCount, diagnostics);
                continue;
            }
            int retainedSegmentCount = retainedSegmentCount(course.segments(), boundary);
            if (retainedSegmentCount < 1) {
                normalized.add(course);
                continue;
            }
            normalized.add(new RiverCourse(
                    course.id(),
                    course.type(),
                    course.sourceNodeId(),
                    course.outletId(),
                    course.profileKey(),
                    course.discharge(),
                    course.drainageEdges().subList(0, ownedEdgeCount),
                    course.segments().subList(0, retainedSegmentCount)
            ));
        }
        return List.copyOf(normalized);
    }

    /**
     * Builds a later draft to the stem's outlet as a tributary. The draft's path is cut where it first
     * comes within a stem width of the stem or first enters drainage the stem already owns, and it is
     * shaped with the stem's water level at that station as its terminal, so it arrives at or above the
     * stem and steps down into it. When the tributary would arrive below the stem's water there, the
     * junction slides downstream along the stem to the first station whose water is low enough and the
     * course is shaped again. The tributary owns only the drainage edges upstream of the junction.
     */
    RiverCourse buildSurfaceTributary(
            SurfaceCourseDraft draft,
            RiverCourse stem,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyCoursePath path = draft.path();
        HydrologyPoint sourcePoint = path.points().getFirst();
        HashSet<Long> stemEdges = new HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        ArrayList<HydrologyPoint> stemStations = new ArrayList<>();
        ArrayList<Integer> stemWidths = new ArrayList<>();
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                stemStations.add(point);
                stemWidths.add(segment.width());
            }
        }
        int minimumStemIndex = 0;
        HydrologyCandidateRejection rejection = HydrologyCandidateRejection.NO_DRAINAGE_PATH;
        int rejectionDetail = 0;
        for (int attempt = 0; attempt < TRIBUTARY_JUNCTION_ATTEMPTS; attempt++) {
            int[] junction = tributaryJunction(path, stemStations, stemWidths, stemEdges, minimumStemIndex);
            if (junction == null) {
                break;
            }
            int joinIndex = junction[0];
            int stemIndex = junction[1];
            HydrologyPoint joinStation = stemStations.get(stemIndex);
            if (joinIndex < 1) {
                rejection = HydrologyCandidateRejection.COURSE_TOO_SHORT;
                rejectionDetail = 0;
                break;
            }
            TributaryBuild build = buildSurfaceTributaryAt(draft, joinIndex, stemIndex, stemStations, stemWidths, stemEdges);
            if (build.course() != null) {
                return build.course();
            }
            rejection = build.rejection();
            rejectionDetail = build.rejectionDetail();
            if (rejection != HydrologyCandidateRejection.SURFACE_HEAD_RANGE) {
                break;
            }
            // The tributary arrives rejectionDetail blocks below the stem's water: slide the junction
            // downstream to the first stem station whose water is that much lower.
            int target = joinStation.y() - rejectionDetail;
            int nextStemIndex = -1;
            for (int index = stemIndex + 1; index < stemStations.size(); index++) {
                if (stemStations.get(index).y() <= target) {
                    nextStemIndex = index;
                    break;
                }
            }
            if (nextStemIndex < 0) {
                break;
            }
            minimumStemIndex = nextStemIndex;
        }
        addTributaryDiagnostic(draft.courseId(), sourcePoint, rejection, rejectionDetail, diagnostics);
        return null;
    }

    /**
     * The first draft point, in path order, that lies within a stem width of a stem station at or past
     * {@code minimumStemIndex} or that enters drainage the stem owns, as {draft point index, stem station
     * index}; null when the draft never meets the stem there. Among the stations the point can reach the
     * lowest water wins, then the flattest stretch, so the tributary's mouth sits at the river's level
     * where the two channels touch and never above the stem's shore.
     */
    static int[] tributaryJunction(
            HydrologyCoursePath path,
            List<HydrologyPoint> stemStations,
            List<Integer> stemWidths,
            Set<Long> stemEdges,
            int minimumStemIndex
    ) {
        for (int index = 0; index < path.points().size(); index++) {
            HydrologyPoint point = path.points().get(index);
            boolean sharedEdge = index > 0 && stemEdges.contains(path.pairEdges().get(index - 1).id());
            int best = -1;
            int bestFlatness = -1;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (int stemIndex = minimumStemIndex; stemIndex < stemStations.size(); stemIndex++) {
                double distance = point.distanceSquared2D(stemStations.get(stemIndex));
                double reach = stemWidths.get(stemIndex) + 1D;
                boolean reachable = distance <= reach * reach;
                if (!reachable && !(sharedEdge && best < 0 && distance < bestDistance)) {
                    continue;
                }
                int flatness = reachable ? stemFlatness(stemStations, stemIndex) : -1;
                int level = stemStations.get(stemIndex).y();
                int bestLevel = best < 0 ? Integer.MAX_VALUE : stemStations.get(best).y();
                if (level < bestLevel
                        || level == bestLevel && (flatness > bestFlatness || flatness == bestFlatness && distance < bestDistance)) {
                    best = stemIndex;
                    bestFlatness = flatness;
                    bestDistance = distance;
                }
            }
            if (best >= 0) {
                return new int[] {index, best};
            }
        }
        return null;
    }

    /** How many of the stem stations around {@code stemIndex} share its water level. */
    static int stemFlatness(List<HydrologyPoint> stemStations, int stemIndex) {
        int level = stemStations.get(stemIndex).y();
        int flat = 0;
        for (int offset = -TRIBUTARY_JUNCTION_FLAT_STATIONS; offset <= TRIBUTARY_JUNCTION_FLAT_STATIONS; offset++) {
            int index = stemIndex + offset;
            if (index >= 0 && index < stemStations.size() && stemStations.get(index).y() == level) {
                flat++;
            }
        }
        return flat;
    }

    /** Raises every trailing station below {@code level} to it, so the tail is a flat pool at the stem's water. */
    static void backwater(List<HydraulicSegment> segments, int level) {
        for (int index = segments.size() - 1; index >= 0; index--) {
            HydraulicSegment segment = segments.get(index);
            if (segment.upstreamHeadY() >= level && segment.downstreamHeadY() >= level) {
                break;
            }
            ArrayList<HydrologyPoint> centerline = new ArrayList<>(segment.centerline().size());
            for (HydrologyPoint point : segment.centerline()) {
                centerline.add(point.y() < level ? new HydrologyPoint(point.x(), level, point.z()) : point);
            }
            int upstream = Math.max(segment.upstreamHeadY(), level);
            int downstream = Math.max(segment.downstreamHeadY(), level);
            segments.set(index, new HydraulicSegment(
                    segment.id(),
                    segment.courseId(),
                    upstream == downstream ? HydrologyFeatureType.SURFACE_POOL : segment.type(),
                    upstream,
                    downstream,
                    segment.width(),
                    segment.depth(),
                    segment.fallingFluid(),
                    segment.receivingPool(),
                    List.copyOf(centerline)
            ));
            if (segment.upstreamHeadY() >= level) {
                break;
            }
        }
    }

    /**
     * Lowers the trailing stations of a tributary towards {@code level}, one block per cascade run of
     * centerline distance from the junction and never deeper than the channel may cut, so the reach
     * grades down into the stem instead of hanging above it. Where the ground holds a station up, the
     * stations upstream of it are held with it, so the graded reach still descends into the stem.
     */
    void levelApproach(List<HydraulicSegment> segments, int level) {
        int run = Math.max(1, planner.settings.surface().banks().cascadeRun());
        int maximumIncision = planner.settings.surface().maximumIncision();
        double distance = 0D;
        HydrologyPoint previous = null;
        int downstreamHead = Integer.MIN_VALUE;
        for (int index = segments.size() - 1; index >= 0; index--) {
            HydraulicSegment segment = segments.get(index);
            ArrayList<HydrologyPoint> centerline = new ArrayList<>(segment.centerline());
            boolean changed = false;
            boolean settled = false;
            for (int pointIndex = centerline.size() - 1; pointIndex >= 0; pointIndex--) {
                HydrologyPoint point = centerline.get(pointIndex);
                if (previous != null) {
                    distance += StrictMath.hypot(point.x() - previous.x(), point.z() - previous.z());
                }
                previous = point;
                int allowed = level + (int) StrictMath.floor(distance / run);
                if (point.y() <= allowed) {
                    settled = true;
                    break;
                }
                int floor = planner.sampleBasisWithoutSlope(point.x(), point.z()).naturalHeight() - maximumIncision;
                int lowered = Math.min(point.y(), Math.max(Math.max(allowed, floor), downstreamHead));
                if (lowered < point.y()) {
                    centerline.set(pointIndex, new HydrologyPoint(point.x(), lowered, point.z()));
                    changed = true;
                }
                downstreamHead = lowered;
            }
            if (changed) {
                // A level reach may carry a bump above its heads (a pool's centerline is not graded);
                // once its tail is cut down the reach becomes a graded transition, which must never rise
                // downstream, so every station is held at or below the one before it.
                int ceiling = centerline.getFirst().y();
                for (int pointIndex = 1; pointIndex < centerline.size(); pointIndex++) {
                    HydrologyPoint point = centerline.get(pointIndex);
                    if (point.y() > ceiling) {
                        centerline.set(pointIndex, new HydrologyPoint(point.x(), ceiling, point.z()));
                    } else {
                        ceiling = point.y();
                    }
                }
                int upstream = centerline.getFirst().y();
                int downstream = centerline.getLast().y();
                HydrologyFeatureType type = segment.type();
                if (upstream > downstream && !type.isDrop()) {
                    type = upstream - downstream >= planner.settings.hydraulics().waterfallMinimumDrop()
                            ? HydrologyFeatureType.WATERFALL
                            : upstream - downstream == 1 ? HydrologyFeatureType.RIFFLE : HydrologyFeatureType.CASCADE;
                } else if (upstream == downstream && type.isDrop()) {
                    type = HydrologyFeatureType.SURFACE_POOL;
                }
                segments.set(index, new HydraulicSegment(
                        segment.id(),
                        segment.courseId(),
                        type,
                        upstream,
                        downstream,
                        segment.width(),
                        segment.depth(),
                        segment.fallingFluid(),
                        segment.receivingPool(),
                        List.copyOf(centerline)
                ));
            }
            if (settled) {
                break;
            }
        }
    }

    static int lastWidth(SurfaceCourseResult result) {
        return Math.max(1, result.lastWidth());
    }

    record TributaryBuild(RiverCourse course, HydrologyCandidateRejection rejection, int rejectionDetail) {
        private static TributaryBuild rejected(HydrologyCandidateRejection rejection, int detail) {
            return new TributaryBuild(null, rejection, detail);
        }
    }

    TributaryBuild buildSurfaceTributaryAt(
            SurfaceCourseDraft draft,
            int joinIndex,
            int stemIndex,
            List<HydrologyPoint> stemStations,
            List<Integer> stemWidths,
            Set<Long> stemEdges
    ) {
        HydrologyCoursePath path = draft.path();
        HydrologyPoint joinStation = stemStations.get(stemIndex);
        ArrayList<HydrologyPoint> points = new ArrayList<>(path.points().subList(0, joinIndex + 1));
        HydrologyPoint last = points.getLast();
        if (last.x() != joinStation.x() || last.z() != joinStation.z()) {
            points.add(new HydrologyPoint(joinStation.x(), last.y(), joinStation.z()));
        }
        LinkedHashSet<DrainageEdge> ownedEdges = new LinkedHashSet<>();
        for (DrainageEdge edge : path.pairEdges().subList(0, joinIndex)) {
            if (!stemEdges.contains(edge.id())) {
                ownedEdges.add(edge);
            }
        }
        if (ownedEdges.isEmpty()) {
            return TributaryBuild.rejected(HydrologyCandidateRejection.COURSE_TOO_SHORT, joinIndex);
        }
        int stemHead = joinStation.y();
        SurfaceCourseResult result = planner.tributaryCourseBuilder.build(
                planner.worldSeed,
                draft.courseId(),
                draft.profileKey(),
                List.copyOf(points),
                SurfaceTerminal.TRIBUTARY,
                stemHead
        );
        if (!result.accepted()) {
            return TributaryBuild.rejected(result.rejection(), result.rejectionDetail());
        }
        if (path.organicSurfaceRequired()) {
            // The reach is judged without its approach into the stem: the turn a tributary makes to
            // enter its river is junction geometry, not a bend in the river itself.
            double approachRadius = 3D * lastWidth(result) + 8D;
            int judged = joinIndex;
            while (judged > 0 && path.points().get(judged).distanceSquared2D(joinStation) <= approachRadius * approachRadius) {
                judged--;
            }
            int shapeRejection = judged >= 2 ? planner.surfaceCourses.surfaceShapeRejection(path.points().subList(0, judged + 1), true) : 0;
            if (shapeRejection != 0) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_SHAPE_UNSUPPORTED, shapeRejection);
            }
        }
        ArrayList<HydraulicSegment> segments = new ArrayList<>(result.segments());
        // Where the two channels touch, the tributary must sit at the lowest stem water it reaches.
        double touch = stemWidths.get(stemIndex) + lastWidth(result) + 1D;
        for (int index = 0; index < stemStations.size(); index++) {
            HydrologyPoint station = stemStations.get(index);
            if (station.distanceSquared2D(joinStation) <= touch * touch) {
                stemHead = Math.min(stemHead, station.y());
            }
        }
        if (segments.getLast().downstreamHeadY() < stemHead) {
            int shortfall = stemHead - segments.getLast().downstreamHeadY();
            if (shortfall > TRIBUTARY_BACKWATER_LIMIT) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_HEAD_RANGE, shortfall);
            }
            // The stem's water stands a little above the tributary's floor at the junction: the tail of
            // the tributary backs up to the stem's level, a short still reach the bank lip contains.
            backwater(segments, stemHead);
        } else if (segments.getLast().downstreamHeadY() > stemHead) {
            // The tributary hangs above its river: its last stretch is cut down to the stem's water,
            // a block per cascade run, so the channel meets the river at its level and not above
            // the stem's shore.
            levelApproach(segments, stemHead);
        }
        HydraulicSegment lastSegment = segments.getLast();
        HydrologyPoint end = lastSegment.end();
        if (end.x() != joinStation.x() || end.z() != joinStation.z() || lastSegment.downstreamHeadY() != stemHead) {
            // The hop onto the stem is graded like any in-course drop, so the water steps down block
            // by block into the river instead of standing above the stem's shore.
            int drop = lastSegment.downstreamHeadY() - stemHead;
            HydrologyFeatureType type = drop == 0
                    ? HydrologyFeatureType.SURFACE_POOL
                    : drop >= planner.settings.hydraulics().waterfallMinimumDrop()
                            ? HydrologyFeatureType.WATERFALL
                            : drop == 1 ? HydrologyFeatureType.RIFFLE : HydrologyFeatureType.CASCADE;
            boolean graded = planner.segments.addHydraulicSegments(
                    draft.courseId(),
                    TRIBUTARY_JUNCTION_PAIR_INDEX,
                    type,
                    lastSegment.downstreamHeadY(),
                    stemHead,
                    lastSegment.width(),
                    lastSegment.depth(),
                    List.of(end, new HydrologyPoint(joinStation.x(), stemHead, joinStation.z())),
                    true,
                    segments
            );
            if (!graded) {
                return TributaryBuild.rejected(HydrologyCandidateRejection.SURFACE_DROP_UNSUPPORTED, drop);
            }
        }
        List<DrainageEdge> edges = List.copyOf(ownedEdges);
        return new TributaryBuild(new RiverCourse(
                draft.courseId(),
                RiverCourseType.SURFACE,
                OptionalLong.of(draft.source().id()),
                OptionalLong.of(path.outlet().id()),
                draft.profileKey(),
                planner.segments.maximumSurfaceDischarge(edges),
                edges,
                segments
        ), null, 0);
    }

    void addTributaryDiagnostic(
            long courseId,
            HydrologyPoint sourcePoint,
            HydrologyCandidateRejection rejection,
            int detail,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(courseId, HydrologySourcePlanner.DIAGNOSTIC_SALT, rejection.ordinal(), 0x5452494255544152L),
                HydrologyCandidateKind.TRIBUTARY,
                HydrologyFeatureType.SURFACE_POOL,
                sourcePoint,
                rejection,
                detail
        ));
    }

    RiverCourse preferredTrunkCourse(RiverCourse first, RiverCourse second) {
        double firstLength = edgeLength(first.drainageEdges(), first.drainageEdges().size());
        double secondLength = edgeLength(second.drainageEdges(), second.drainageEdges().size());
        if (firstLength != secondLength) {
            return firstLength > secondLength ? first : second;
        }
        if (first.discharge() != second.discharge()) {
            return first.discharge() > second.discharge() ? first : second;
        }
        return Long.compareUnsigned(first.id(), second.id()) <= 0 ? first : second;
    }

    double edgeLength(List<DrainageEdge> edges, int edgeCount) {
        double length = 0D;
        for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
            List<HydrologyPoint> centerline = edges.get(edgeIndex).centerline();
            for (int pointIndex = 1; pointIndex < centerline.size(); pointIndex++) {
                HydrologyPoint previous = centerline.get(pointIndex - 1);
                HydrologyPoint current = centerline.get(pointIndex);
                length += StrictMath.hypot(current.x() - previous.x(), current.z() - previous.z());
            }
        }
        return length;
    }

    int retainedSegmentCount(List<HydraulicSegment> segments, HydrologyPoint boundary) {
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            HydrologyPoint end = segments.get(segmentIndex).centerline().getLast();
            if (end.x() == boundary.x() && end.z() == boundary.z()) {
                return segmentIndex + 1;
            }
        }
        return 0;
    }

    RiverCourse preferredOutletContinuation(RiverCourse first, RiverCourse second) {
        boolean firstSurfaceSinkhole = first.surfaceSinkholeContinuation();
        boolean secondSurfaceSinkhole = second.surfaceSinkholeContinuation();
        if (firstSurfaceSinkhole != secondSurfaceSinkhole) {
            return firstSurfaceSinkhole ? first : second;
        }
        return Long.compareUnsigned(first.id(), second.id()) <= 0 ? first : second;
    }

    boolean hasInlandOutletContinuation(RiverCourse course) {
        return course.surfaceSinkholeContinuation()
                || course.segments().getLast().type() == HydrologyFeatureType.INLAND_GROTTO;
    }

    /**
     * Builds a later underground draft to the stem's outlet as a tributary: the path is cut at the first
     * point the stem's passage already runs through, and the passage is solved with the stem's fluid
     * level there as its outlet level, so it joins the stem at or above its water.
     */
    RiverCourse buildUndergroundTributary(
            UndergroundCourseDraft draft,
            RiverCourse stem,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyCoursePath path = draft.path();
        HashSet<Long> stemEdges = new HashSet<>();
        for (DrainageEdge edge : stem.drainageEdges()) {
            stemEdges.add(edge.id());
        }
        HashMap<Long, Integer> stemHeads = new HashMap<>();
        for (HydraulicSegment segment : stem.segments()) {
            for (HydrologyPoint point : segment.centerline()) {
                stemHeads.putIfAbsent(RiverFootprint.pack(point.x(), point.z()), point.y());
            }
        }
        int joinIndex = -1;
        for (int index = 0; index < path.points().size(); index++) {
            HydrologyPoint point = path.points().get(index);
            boolean sharedEdge = index > 0 && stemEdges.contains(path.pairEdges().get(index - 1).id());
            if (sharedEdge || stemHeads.containsKey(RiverFootprint.pack(point.x(), point.z()))) {
                joinIndex = index;
                break;
            }
        }
        if (joinIndex < 1 || !stemHeads.containsKey(RiverFootprint.pack(
                path.points().get(joinIndex).x(), path.points().get(joinIndex).z()))) {
            planner.undergroundCourses.addCompiledSourceDiagnostic(draft.source(), false, draft.courseId(),
                    joinIndex < 0 ? HydrologyCandidateRejection.NO_DRAINAGE_PATH : HydrologyCandidateRejection.COURSE_TOO_SHORT,
                    diagnostics);
            return null;
        }
        List<HydrologyPoint> points = path.points().subList(0, joinIndex + 1);
        List<DrainageEdge> pairEdges = path.pairEdges().subList(0, joinIndex);
        LinkedHashSet<DrainageEdge> ownedEdges = new LinkedHashSet<>();
        for (DrainageEdge edge : pairEdges) {
            if (!stemEdges.contains(edge.id())) {
                ownedEdges.add(edge);
            }
        }
        if (ownedEdges.isEmpty()
                || planner.surfaceCourses.centerlineLength(points) < planner.settings.routing().minimumUndergroundCourseLength() / 2D) {
            planner.undergroundCourses.addCompiledSourceDiagnostic(draft.source(), false, draft.courseId(),
                    HydrologyCandidateRejection.COURSE_TOO_SHORT, joinIndex, diagnostics);
            return null;
        }
        HydrologyPoint junction = points.getLast();
        HydrologyCoursePath junctionPath = new HydrologyCoursePath(
                List.copyOf(points),
                List.copyOf(pairEdges),
                List.copyOf(ownedEdges),
                path.outlet(),
                false,
                false,
                junction
        );
        int junctionHead = stemHeads.get(RiverFootprint.pack(junction.x(), junction.z()));
        return planner.undergroundCourses.buildUndergroundCourse(draft, junctionPath, junctionHead, diagnostics);
    }
}
