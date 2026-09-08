package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CaveVoxel;
import art.arcane.iris.engine.hydrology.cave.CaveVoxelView;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveContainmentPlanner;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlannerSettings;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveRejection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class HydrologyCaveCourseFilter {
    static final long DIAGNOSTIC_SALT = 0x43415645464c5452L;

    static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    }

;

    static final int[][] HORIZONTAL_NEIGHBORS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    }

;

    static final CaveVoxelView GENERATED_CHANNEL_VIEW = new CaveVoxelView() {
        @Override
        public boolean isInWorld(CavePosition position) {
            return true;
        }

        @Override
        public CaveVoxel voxelAt(CavePosition position) {
            return CaveVoxel.UNCONDITIONAL;
        }

        @Override
        public boolean isOpenToSurface(CavePosition position) {
            return false;
        }

        @Override
        public boolean isAboveTerrainSurface(CavePosition position) {
            return false;
        }
    }

;

    final HydrologyCaveContainmentPlanner planner;
    final CaveVoxelView view;
    final Options options;
    final Map<CandidateKey, HydrologyCaveCandidate> candidateCache;
    final HydrologyCaveContainmentPlanner.ValidationCache validationCache;
    final HydrologyObservedPlannedSurface plannedSurface;

    HydrologyCaveCourseFilter(CaveVoxelView view, Options options) {
        this(view, options, null, null, null);
    }

    HydrologyCaveCourseFilter(
            CaveVoxelView view,
            Options options,
            Map<CandidateKey, HydrologyCaveCandidate> candidateCache
    ) {
        this(view, options, candidateCache, null, null);
    }

    HydrologyCaveCourseFilter(
            CaveVoxelView view,
            Options options,
            Map<CandidateKey, HydrologyCaveCandidate> candidateCache,
            HydrologyCaveContainmentPlanner.ValidationCache validationCache,
            HydrologyObservedPlannedSurface plannedSurface
    ) {
        this.planner = new HydrologyCaveContainmentPlanner();
        this.options = Objects.requireNonNull(options);
        CaveVoxelView observedView = Objects.requireNonNull(view);
        this.view = options.connectToExistingCaves() ? observedView : GENERATED_CHANNEL_VIEW;
        this.candidateCache = candidateCache;
        this.validationCache = validationCache;
        this.plannedSurface = plannedSurface;
    }

    Result filter(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses,
            Iterable<HydrologyColumnSample> columns,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        return filter(nodes, edges, outlets, courses, columns, null, diagnostics);
    }

    Result filter(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses,
            HydrologyFootprintCompiler.ValidationRaster validation,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        return filter(nodes, edges, outlets, courses, null, validation, diagnostics);
    }

    Result filter(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses,
            Iterable<HydrologyColumnSample> columns,
            HydrologyFootprintCompiler.ValidationRaster validation,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        List<RiverCourse> normalizedCourses = withoutZeroContributionEdges(courses);
        LinkedHashMap<Long, RiverCourse> publishedCoursesById = new LinkedHashMap<>(normalizedCourses.size());
        for (RiverCourse course : normalizedCourses) {
            publishedCoursesById.put(course.id(), course);
        }
        ArrayList<RiverCourse> caveCourses = new ArrayList<>(normalizedCourses.size());
        for (RiverCourse course : normalizedCourses) {
            if (representativeCaveSegment(course) != null) {
                caveCourses.add(course);
            }
        }
        LinkedHashMap<Long, CandidateKey> candidateKeys = new LinkedHashMap<>(caveCourses.size());
        LinkedHashMap<Long, HydrologyCaveCandidate> cachedCandidates = new LinkedHashMap<>(caveCourses.size());
        ArrayList<RiverCourse> uncachedCourses = new ArrayList<>(caveCourses.size());
        for (RiverCourse course : caveCourses) {
            CandidateKey key = CandidateKey.create(course, options);
            candidateKeys.put(course.id(), key);
            HydrologyCaveCandidate cached = candidateCache == null ? null : candidateCache.get(key);
            if (cached == null) {
                uncachedCourses.add(course);
            } else {
                cachedCandidates.put(course.id(), cached);
            }
        }
        Set<Long> preflightRejectedCourseIds = validation == null
                ? preflightRejectedCourseIds(uncachedCourses, columns, diagnostics)
                : preflightRejectedCourseIds(uncachedCourses, validation, diagnostics);
        ArrayList<RiverCourse> candidateCourses = new ArrayList<>(caveCourses.size());
        for (RiverCourse course : caveCourses) {
            if (!preflightRejectedCourseIds.contains(course.id())) {
                candidateCourses.add(course);
            }
        }
        uncachedCourses.removeIf((RiverCourse course) -> preflightRejectedCourseIds.contains(course.id()));
        LinkedHashMap<Long, CaveCandidateBuilder> builders = validation == null
                ? candidateBuilders(uncachedCourses, columns)
                : candidateBuilders(uncachedCourses, validation);
        if (candidateCourses.isEmpty() && preflightRejectedCourseIds.isEmpty()) {
            CaveCourseGraph graph = acceptedGraph(nodes, edges, outlets, normalizedCourses);
            return new Result(
                    graph.nodes(),
                    graph.edges(),
                    graph.outlets(),
                    normalizedCourses,
                    List.of()
            );
        }

        HashSet<Long> rejectedCourseIds = new HashSet<>(preflightRejectedCourseIds);
        ArrayList<HydrologyCaveCandidate> candidates = new ArrayList<>(candidateCourses.size());
        Set<HydrologyCaveCandidate> exposureValidatedCandidates =
                Collections.newSetFromMap(new IdentityHashMap<>());
        LinkedHashMap<Long, RiverCourse> coursesById = new LinkedHashMap<>(candidateCourses.size());
        for (RiverCourse course : candidateCourses) {
            CandidateKey key = candidateKeys.get(course.id());
            HydrologyCaveCandidate candidate = cachedCandidates.get(course.id());
            if (candidate == null) {
                CaveCandidateBuilder builder = builders.get(course.id());
                if (builder == null) {
                    throw new IllegalStateException("Hydrology cave candidate raster was empty.");
                }
                candidate = builder.build(options);
                if (candidateCache != null) {
                    candidateCache.put(key, candidate);
                }
                exposureValidatedCandidates.add(candidate);
            }
            CaveSurfaceComposition composition = validation == null
                    ? CaveSurfaceComposition.accepted(candidate)
                    : composeSurfacePublication(candidate, course, validation, publishedCoursesById);
            if (!composition.accepted()) {
                if (rejectedCourseIds.add(course.id())) {
                    HydraulicSegment representative = representativeCaveSegment(course);
                    addDiagnostic(
                            course,
                            representative,
                            representative.type(),
                            HydrologyCandidateRejection.CAVE_CONTAINMENT,
                            diagnostics,
                            HydrologyCaveRejection.OVERLAPPING_SOURCE.ordinal()
                    );
                }
                continue;
            }
            candidate = composition.candidate();
            if (course.surfaceSinkholeContinuation()) {
                exposureValidatedCandidates.add(candidate);
            }
            candidates.add(candidate);
            coursesById.put(course.id(), course);
        }
        alignSharedTerminalCandidateActions(candidates, coursesById, exposureValidatedCandidates);
        List<HydrologyCavePlan> plans = planner.validateAllPlans(
                view,
                candidates,
                validationCache,
                plannedSurface,
                exposureValidatedCandidates,
                (HydrologyCaveCandidate first, HydrologyCaveCandidate second) ->
                        compatibleSharedCandidates(first, second, coursesById)
        );
        plans = alignSharedTerminalPlanActions(plans, candidates, coursesById);
        ArrayList<HydrologyCavePlan> acceptedPlans = new ArrayList<>();
        for (HydrologyCavePlan plan : plans) {
            if (plan.accepted()) {
                acceptedPlans.add(plan);
                continue;
            }
            long courseId = plan.source().sourceId();
            if (rejectedCourseIds.add(courseId)) {
                addDiagnostic(coursesById.get(courseId), plan, diagnostics);
            }
        }
        if (rejectedCourseIds.isEmpty()) {
            CaveCourseGraph graph = acceptedGraph(nodes, edges, outlets, normalizedCourses);
            return new Result(
                    graph.nodes(),
                    graph.edges(),
                    graph.outlets(),
                    normalizedCourses,
                    List.copyOf(acceptedPlans)
            );
        }

        ArrayList<RiverCourse> acceptedCourses = new ArrayList<>();
        for (RiverCourse course : normalizedCourses) {
            if (!rejectedCourseIds.contains(course.id())) {
                acceptedCourses.add(course);
            }
        }
        CaveCourseGraph graph = acceptedGraph(nodes, edges, outlets, acceptedCourses);
        return new Result(
                graph.nodes(),
                graph.edges(),
                graph.outlets(),
                List.copyOf(acceptedCourses),
                List.copyOf(acceptedPlans)
        );
    }

    boolean compatibleSharedCandidates(
            HydrologyCaveCandidate first,
            HydrologyCaveCandidate second,
            Map<Long, RiverCourse> coursesById
    ) {
        if (!first.profileKey().equals(second.profileKey())) {
            return false;
        }
        RiverCourse firstCourse = coursesById.get(first.source().sourceId());
        RiverCourse secondCourse = coursesById.get(second.source().sourceId());
        return sharesDrainageOutlet(firstCourse, secondCourse)
                || sharesTerminalGrotto(firstCourse, secondCourse, first);
    }

    List<HydrologyCavePlan> alignSharedTerminalPlanActions(
            List<HydrologyCavePlan> plans,
            List<HydrologyCaveCandidate> candidates,
            Map<Long, RiverCourse> coursesById
    ) {
        HashMap<Long, HydrologyCaveCandidate> candidatesById = new HashMap<>(candidates.size());
        for (HydrologyCaveCandidate candidate : candidates) {
            candidatesById.put(candidate.source().sourceId(), candidate);
        }
        ArrayList<HydrologyCavePlan> aligned = new ArrayList<>(plans);
        for (int firstIndex = 0; firstIndex < aligned.size() - 1; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < aligned.size(); secondIndex++) {
                HydrologyCavePlan first = aligned.get(firstIndex);
                HydrologyCavePlan second = aligned.get(secondIndex);
                HydrologyCaveCandidate firstCandidate = candidatesById.get(first.source().sourceId());
                HydrologyCaveCandidate secondCandidate = candidatesById.get(second.source().sourceId());
                if (!first.accepted()
                        || !second.accepted()
                        || firstCandidate == null
                        || secondCandidate == null
                        || !compatibleSharedCandidates(firstCandidate, secondCandidate, coursesById)) {
                    continue;
                }
                LinkedHashMap<CavePosition, HydrologyCaveAction> firstActions = null;
                LinkedHashMap<CavePosition, HydrologyCaveAction> secondActions = null;
                for (Map.Entry<CavePosition, HydrologyCaveAction> entry : first.actions().entrySet()) {
                    HydrologyCaveAction secondAction = second.actions().get(entry.getKey());
                    if (secondAction == null || secondAction == entry.getValue()) {
                        continue;
                    }
                    HydrologyCaveAction sharedAction = sharedTerminalAction(entry.getValue(), secondAction);
                    if (firstActions == null) {
                        firstActions = new LinkedHashMap<>(first.actions());
                        secondActions = new LinkedHashMap<>(second.actions());
                    }
                    firstActions.put(entry.getKey(), sharedAction);
                    secondActions.put(entry.getKey(), sharedAction);
                }
                if (firstActions == null) {
                    continue;
                }
                aligned.set(firstIndex, withActions(first, firstActions));
                aligned.set(secondIndex, withActions(second, secondActions));
            }
        }
        return List.copyOf(aligned);
    }

    HydrologyCavePlan withActions(
            HydrologyCavePlan plan,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        return new HydrologyCavePlan(
                plan.source(),
                plan.rejection(),
                actions,
                plan.baselinePreconditions(),
                plan.arbitrationWinnerSourceId()
        );
    }

    void alignSharedTerminalCandidateActions(
            List<HydrologyCaveCandidate> candidates,
            Map<Long, RiverCourse> coursesById,
            Set<HydrologyCaveCandidate> exposureValidatedCandidates
    ) {
        for (int firstIndex = 0; firstIndex < candidates.size() - 1; firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < candidates.size(); secondIndex++) {
                HydrologyCaveCandidate first = candidates.get(firstIndex);
                HydrologyCaveCandidate second = candidates.get(secondIndex);
                RiverCourse firstCourse = coursesById.get(first.source().sourceId());
                RiverCourse secondCourse = coursesById.get(second.source().sourceId());
                if (!sharesDrainageOutlet(firstCourse, secondCourse)
                        && !sharesTerminalGrotto(firstCourse, secondCourse, first)) {
                    continue;
                }
                LinkedHashMap<CavePosition, HydrologyCaveAction> firstActions = null;
                LinkedHashMap<CavePosition, HydrologyCaveAction> secondActions = null;
                for (Map.Entry<CavePosition, HydrologyCaveAction> entry : first.actions().entrySet()) {
                    HydrologyCaveAction secondAction = second.actions().get(entry.getKey());
                    if (secondAction == null || secondAction == entry.getValue()) {
                        continue;
                    }
                    HydrologyCaveAction sharedAction = sharedTerminalAction(entry.getValue(), secondAction);
                    if (firstActions == null) {
                        firstActions = new LinkedHashMap<>(first.actions());
                        secondActions = new LinkedHashMap<>(second.actions());
                    }
                    firstActions.put(entry.getKey(), sharedAction);
                    secondActions.put(entry.getKey(), sharedAction);
                }
                if (firstActions == null) {
                    continue;
                }
                HydrologyCaveCandidate alignedFirst = withActions(first, firstActions);
                HydrologyCaveCandidate alignedSecond = withActions(second, secondActions);
                replaceExposureCandidate(exposureValidatedCandidates, first, alignedFirst);
                replaceExposureCandidate(exposureValidatedCandidates, second, alignedSecond);
                candidates.set(firstIndex, alignedFirst);
                candidates.set(secondIndex, alignedSecond);
            }
        }
    }

    HydrologyCaveAction sharedTerminalAction(
            HydrologyCaveAction first,
            HydrologyCaveAction second
    ) {
        return sharedTerminalActionPriority(first) <= sharedTerminalActionPriority(second)
                ? first
                : second;
    }

    int sharedTerminalActionPriority(HydrologyCaveAction action) {
        return switch (action) {
            case FALLING_FLUID -> 0;
            case WET_SOURCE -> 1;
            case DRY_AIR -> 2;
            case SEAL_GUARD -> 3;
        };
    }

    HydrologyCaveCandidate withActions(
            HydrologyCaveCandidate candidate,
            Map<CavePosition, HydrologyCaveAction> actions
    ) {
        return new HydrologyCaveCandidate(
                candidate.source(),
                candidate.profileKey(),
                candidate.settings(),
                candidate.allowDryCaveConnections(),
                actions,
                candidate.intentionalOpenings()
        );
    }

    void replaceExposureCandidate(
            Set<HydrologyCaveCandidate> exposureValidatedCandidates,
            HydrologyCaveCandidate existing,
            HydrologyCaveCandidate replacement
    ) {
        if (exposureValidatedCandidates.remove(existing)) {
            exposureValidatedCandidates.add(replacement);
        }
    }

    CaveSurfaceComposition composeSurfacePublication(
            HydrologyCaveCandidate candidate,
            RiverCourse course,
            HydrologyFootprintCompiler.ValidationRaster validation,
            Map<Long, RiverCourse> publishedCoursesById
    ) {
        LinkedHashMap<CavePosition, HydrologyCaveAction> composedActions = null;
        for (HydrologyColumnSample caveSample : validation.columnsForCourse(course.id())) {
            int maximumCaveY = maximumCaveY(caveSample, course.id());
            if (maximumCaveY == Integer.MIN_VALUE) {
                continue;
            }
            int plannedTerrainHeight = validation.plannedSurface().resolve(
                    caveSample.x(),
                    caveSample.z(),
                    caveSample.naturalHeight()
            );
            if (maximumCaveY <= plannedTerrainHeight) {
                continue;
            }
            HydrologyColumnSample surfaceSample = validation.surfaceColumnAt(
                    caveSample.x(),
                    caveSample.z(),
                    caveSample.naturalHeight()
            );
            if (surfaceSample == null) {
                continue;
            }
            for (int y = plannedTerrainHeight + 1; y <= maximumCaveY; y++) {
                HydrologyColumnSample.SurfacePublicationCell surfaceCell = surfaceSample
                        .surfacePublicationCellAt(y)
                        .orElse(null);
                if (surfaceCell == null) {
                    continue;
                }
                CavePosition position = new CavePosition(caveSample.x(), y, caveSample.z());
                HydrologyCaveAction caveAction = candidate.actions().get(position);
                if (caveAction == null) {
                    continue;
                }
                long surfaceCourseId = surfaceCell.layer().feature().courseId();
                RiverCourse surfaceCourse = publishedCoursesById.get(surfaceCourseId);
                boolean sharedTerminalGrotto = surfaceCourseId != course.id()
                        && sharesTerminalGrotto(course, surfaceCourse, candidate);
                if (!candidate.profileKey().equals(surfaceCell.layer().profileKey())
                        || (surfaceCourseId != course.id()
                        && !candidate.intentionalOpenings().contains(position)
                        && !sharedTerminalGrotto)) {
                    return CaveSurfaceComposition.rejected();
                }
                if (caveAction == surfaceCell.action()) {
                    continue;
                }
                if (surfaceCourseId != course.id()
                        && !sharesDrainageOutlet(course, surfaceCourse)
                        && !sharedTerminalGrotto) {
                    return CaveSurfaceComposition.rejected();
                }
                if (composedActions == null) {
                    composedActions = new LinkedHashMap<>(candidate.actions());
                }
                composedActions.put(position, surfaceCell.action());
            }
        }
        if (composedActions == null) {
            return CaveSurfaceComposition.accepted(candidate);
        }
        return CaveSurfaceComposition.accepted(new HydrologyCaveCandidate(
                candidate.source(),
                candidate.profileKey(),
                candidate.settings(),
                candidate.allowDryCaveConnections(),
                composedActions,
                candidate.intentionalOpenings()
        ));
    }

    boolean sharesTerminalGrotto(
            RiverCourse first,
            RiverCourse second,
            HydrologyCaveCandidate candidate
    ) {
        if (second == null || !first.profileKey().equals(second.profileKey())) {
            return false;
        }
        HydraulicSegment firstGrotto = terminalGrotto(first);
        HydraulicSegment secondGrotto = terminalGrotto(second);
        if (firstGrotto == null || secondGrotto == null) {
            return false;
        }
        HydrologyPoint firstPoint = firstGrotto.end();
        HydrologyPoint secondPoint = secondGrotto.end();
        int horizontalRadius = Math.addExact(
                candidate.settings().grottoHorizontalRadius(),
                Math.max(1, (int) StrictMath.ceil(secondGrotto.width() / 2D))
        );
        int verticalRadius = Math.addExact(
                candidate.settings().grottoVerticalRadius(),
                Math.max(1, secondGrotto.depth())
        );
        return firstPoint.distanceSquared2D(secondPoint) <= (long) horizontalRadius * horizontalRadius
                && Math.abs(firstPoint.y() - secondPoint.y()) <= verticalRadius;
    }

    HydraulicSegment terminalGrotto(RiverCourse course) {
        if (course == null || course.segments().isEmpty()) {
            return null;
        }
        HydraulicSegment segment = course.segments().getLast();
        return segment.type().isGrotto() ? segment : null;
    }

    boolean sharesDrainageOutlet(RiverCourse first, RiverCourse second) {
        return second != null
                && first.outletId().isPresent()
                && second.outletId().isPresent()
                && first.outletId().getAsLong() == second.outletId().getAsLong();
    }

    int maximumCaveY(HydrologyColumnSample sample, long courseId) {
        int maximumY = Integer.MIN_VALUE;
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (layer.feature().courseId() == courseId
                    && isCaveLayer(layer)
                    && !layer.oceanApron()
                    && layer.channel()
                    && layer.terrainOwned()) {
                maximumY = Math.max(maximumY, layer.ceilingY());
            }
        }
        return maximumY;
    }

    Set<Long> preflightRejectedCourseIds(
            List<RiverCourse> courses,
            Iterable<HydrologyColumnSample> columns,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, CaveCandidateSpanBuilder> builders = candidateSpanBuilders(courses, columns);
        return preflightRejectedCourseIds(builders, diagnostics);
    }

    Set<Long> preflightRejectedCourseIds(
            List<RiverCourse> courses,
            HydrologyFootprintCompiler.ValidationRaster validation,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, CaveCandidateSpanBuilder> builders = candidateSpanBuilders(courses, validation);
        return preflightRejectedCourseIds(builders, diagnostics);
    }

    Set<Long> preflightRejectedCourseIds(
            LinkedHashMap<Long, CaveCandidateSpanBuilder> builders,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        if (builders.isEmpty()) {
            return Set.of();
        }
        HashSet<Long> rejectedCourseIds = new HashSet<>();
        for (CaveCandidateSpanBuilder builder : builders.values()) {
            HydrologyFeatureType oversizedGrotto = builder.oversizedGrotto(options);
            boolean oversizedCourse = builder.positionCount()
                    > HydrologyCavePlannerSettings.MAXIMUM_PLANNED_MUTATIONS;
            if (oversizedGrotto == null && !oversizedCourse) {
                continue;
            }
            rejectedCourseIds.add(builder.course().id());
            addDiagnostic(
                    builder.course(),
                    builder.representative(),
                    oversizedGrotto == null ? builder.representative().type() : oversizedGrotto,
                    HydrologyCandidateRejection.VOLUME_LIMIT,
                    diagnostics,
                    HydrologyCandidateRejection.VOLUME_LIMIT.ordinal()
            );
        }
        for (CaveCandidateSpanBuilder builder : builders.values()) {
            if (rejectedCourseIds.contains(builder.course().id())
                    || !builder.exposed(view)
                    || builder.allowsIntentionalSurfaceExposure()) {
                continue;
            }
            rejectedCourseIds.add(builder.course().id());
            addDiagnostic(
                    builder.course(),
                    builder.representative(),
                    builder.representative().type(),
                    HydrologyCandidateRejection.CAVE_CONTAINMENT,
                    diagnostics,
                    HydrologyCaveRejection.OPEN_SURFACE.ordinal()
            );
        }
        return Set.copyOf(rejectedCourseIds);
    }

    List<RiverCourse> withoutZeroContributionEdges(List<RiverCourse> courses) {
        ArrayList<RiverCourse> normalized = new ArrayList<>(courses.size());
        boolean changed = false;
        for (RiverCourse course : courses) {
            ArrayList<DrainageEdge> contributingEdges = new ArrayList<>(course.drainageEdges().size());
            for (DrainageEdge edge : course.drainageEdges()) {
                if (edge.totalContributingSources() > 0) {
                    contributingEdges.add(edge);
                } else {
                    changed = true;
                }
            }
            if (contributingEdges.size() == course.drainageEdges().size()) {
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
                    contributingEdges,
                    course.segments()
            ));
        }
        return changed ? List.copyOf(normalized) : courses;
    }

    LinkedHashMap<Long, CaveCandidateSpanBuilder> candidateSpanBuilders(
            List<RiverCourse> courses,
            Iterable<HydrologyColumnSample> columns
    ) {
        LinkedHashMap<Long, CaveCandidateSpanBuilder> builders = new LinkedHashMap<>();
        for (RiverCourse course : courses) {
            HydraulicSegment representative = representativeCaveSegment(course);
            if (representative != null) {
                builders.put(course.id(), new CaveCandidateSpanBuilder(course, representative));
            }
        }
        if (builders.isEmpty()) {
            return builders;
        }

        for (HydrologyColumnSample sample : columns) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                CaveCandidateSpanBuilder builder = builders.get(layer.feature().courseId());
                if (builder == null || !isCaveLayer(layer) || layer.oceanApron()
                        || !layer.channel() || !layer.terrainOwned()) {
                    continue;
                }
                builder.addAction(sample.x(), sample.z(), layer);
            }
            for (HydrologyColumnLayer layer : sample.layers()) {
                CaveCandidateSpanBuilder builder = builders.get(layer.feature().courseId());
                if (builder != null && layer.oceanApron()) {
                    builder.addOceanOpening(sample.x(), sample.z(), sample.naturalHeight() + 1, layer.ceilingY());
                }
            }
        }
        builders.values().removeIf(CaveCandidateSpanBuilder::isEmpty);
        return builders;
    }

    LinkedHashMap<Long, CaveCandidateSpanBuilder> candidateSpanBuilders(
            List<RiverCourse> courses,
            HydrologyFootprintCompiler.ValidationRaster validation
    ) {
        LinkedHashMap<Long, CaveCandidateSpanBuilder> builders = new LinkedHashMap<>();
        for (RiverCourse course : courses) {
            HydraulicSegment representative = representativeCaveSegment(course);
            if (representative == null) {
                continue;
            }
            CaveCandidateSpanBuilder builder = new CaveCandidateSpanBuilder(course, representative);
            for (HydrologyColumnSample sample : validation.columnsForCourse(course.id())) {
                int maximumCaveY = Integer.MIN_VALUE;
                for (HydrologyColumnLayer layer : sample.layers()) {
                    if (layer.feature().courseId() != course.id()
                            || !isCaveLayer(layer)
                            || layer.oceanApron()
                            || !layer.channel()
                            || !layer.terrainOwned()) {
                        continue;
                    }
                    builder.addAction(sample.x(), sample.z(), layer);
                    maximumCaveY = Math.max(maximumCaveY, layer.ceilingY());
                }
                if (maximumCaveY != Integer.MIN_VALUE
                        && validation.ownsSurfaceChannelAt(
                        sample.x(),
                        sample.z(),
                        sample.naturalHeight(),
                        course.id()
                )) {
                    int plannedTerrainHeight = validation.plannedSurface().resolve(
                            sample.x(),
                            sample.z(),
                            sample.naturalHeight()
                    );
                    builder.addOpeningNeighborhood(
                            sample.x(),
                            sample.z(),
                            plannedTerrainHeight + 1,
                            maximumCaveY
                    );
                }
                for (HydrologyColumnLayer layer : sample.layers()) {
                    if (layer.feature().courseId() == course.id() && layer.oceanApron()) {
                        builder.addOceanOpening(
                                sample.x(),
                                sample.z(),
                                sample.naturalHeight() + 1,
                                layer.ceilingY()
                        );
                    }
                }
            }
            builder.addAdjacentSurfaceOpenings(validation);
            if (!builder.isEmpty()) {
                builders.put(course.id(), builder);
            }
        }
        return builders;
    }

    LinkedHashMap<Long, CaveCandidateBuilder> candidateBuilders(
            List<RiverCourse> courses,
            Iterable<HydrologyColumnSample> columns
    ) {
        LinkedHashMap<Long, CaveCandidateBuilder> builders = new LinkedHashMap<>();
        for (RiverCourse course : courses) {
            HydraulicSegment representative = representativeCaveSegment(course);
            if (representative != null) {
                builders.put(course.id(), new CaveCandidateBuilder(course, representative));
            }
        }
        if (builders.isEmpty()) {
            return builders;
        }

        for (HydrologyColumnSample sample : columns) {
            for (HydrologyColumnLayer layer : sample.layers()) {
                CaveCandidateBuilder builder = builders.get(layer.feature().courseId());
                if (builder == null || !isCaveLayer(layer) || layer.oceanApron()
                        || !layer.channel() || !layer.terrainOwned()) {
                    continue;
                }
                for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
                    CavePosition position = new CavePosition(sample.x(), y, sample.z());
                    builder.addAction(position, actionAt(layer, y));
                    builder.addSurfaceOpening(layer, position);
                }
            }
            for (HydrologyColumnLayer layer : sample.layers()) {
                CaveCandidateBuilder builder = builders.get(layer.feature().courseId());
                if (builder == null || !layer.oceanApron()) {
                    continue;
                }
                for (int y = sample.naturalHeight() + 1; y <= layer.ceilingY(); y++) {
                    builder.addOpening(new CavePosition(sample.x(), y, sample.z()));
                }
            }
        }
        builders.values().removeIf(CaveCandidateBuilder::isEmpty);
        return builders;
    }

    LinkedHashMap<Long, CaveCandidateBuilder> candidateBuilders(
            List<RiverCourse> courses,
            HydrologyFootprintCompiler.ValidationRaster validation
    ) {
        LinkedHashMap<Long, CaveCandidateBuilder> builders = new LinkedHashMap<>();
        for (RiverCourse course : courses) {
            HydraulicSegment representative = representativeCaveSegment(course);
            if (representative == null) {
                continue;
            }
            CaveCandidateBuilder builder = new CaveCandidateBuilder(course, representative);
            for (HydrologyColumnSample sample : validation.columnsForCourse(course.id())) {
                int maximumCaveY = Integer.MIN_VALUE;
                for (HydrologyColumnLayer layer : sample.layers()) {
                    if (layer.feature().courseId() != course.id()
                            || !isCaveLayer(layer)
                            || layer.oceanApron()
                            || !layer.channel()
                            || !layer.terrainOwned()) {
                        continue;
                    }
                    for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
                        CavePosition position = new CavePosition(sample.x(), y, sample.z());
                        builder.addAction(position, actionAt(layer, y));
                        builder.addSurfaceOpening(layer, position);
                    }
                    maximumCaveY = Math.max(maximumCaveY, layer.ceilingY());
                }
                if (maximumCaveY != Integer.MIN_VALUE
                        && validation.ownsSurfaceChannelAt(
                        sample.x(),
                        sample.z(),
                        sample.naturalHeight(),
                        course.id()
                )) {
                    int plannedTerrainHeight = validation.plannedSurface().resolve(
                            sample.x(),
                            sample.z(),
                            sample.naturalHeight()
                    );
                    for (int y = plannedTerrainHeight + 1; y <= maximumCaveY; y++) {
                        builder.addOpeningNeighborhood(new CavePosition(sample.x(), y, sample.z()));
                    }
                }
                for (HydrologyColumnLayer layer : sample.layers()) {
                    if (layer.feature().courseId() != course.id() || !layer.oceanApron()) {
                        continue;
                    }
                    for (int y = sample.naturalHeight() + 1; y <= layer.ceilingY(); y++) {
                        builder.addOpening(new CavePosition(sample.x(), y, sample.z()));
                    }
                }
            }
            builder.addAdjacentSurfaceOpenings(validation);
            if (!builder.isEmpty()) {
                builders.put(course.id(), builder);
            }
        }
        return builders;
    }

    static HydraulicSegment representativeCaveSegment(RiverCourse course) {
        HydraulicSegment selected = null;
        for (HydraulicSegment segment : course.segments()) {
            if (!segment.type().isUnderground() && !segment.type().isDeepFluid()) {
                continue;
            }
            if (selected == null || candidatePriority(segment.type()) < candidatePriority(selected.type())) {
                selected = segment;
            }
        }
        return selected;
    }

    static int candidatePriority(HydrologyFeatureType type) {
        if (type.isGrotto()) {
            return 0;
        }
        if (type.isDeepFluid()) {
            return 1;
        }
        return 2;
    }

    boolean isCaveLayer(HydrologyColumnLayer layer) {
        return layer.feature().type().isUnderground() || layer.feature().type().isDeepFluid();
    }

    HydrologyCaveAction actionAt(HydrologyColumnLayer layer, int y) {
        if (y > layer.fluidHeadY()) {
            return HydrologyCaveAction.DRY_AIR;
        }
        if (layer.fallingFluid() && y < layer.fluidHeadY()) {
            return HydrologyCaveAction.FALLING_FLUID;
        }
        return HydrologyCaveAction.WET_SOURCE;
    }

    void addDiagnostic(
            RiverCourse course,
            HydrologyCavePlan plan,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydraulicSegment representative = representativeCaveSegment(course);
        addDiagnostic(
                course,
                representative,
                representative.type(),
                HydrologyCandidateRejection.CAVE_CONTAINMENT,
                diagnostics,
                plan.rejection().ordinal()
        );
    }

    void addDiagnostic(
            RiverCourse course,
            HydraulicSegment representative,
            HydrologyFeatureType type,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics,
            int rejectionSalt
    ) {
        HydrologyCandidateKind kind = course.type() == RiverCourseType.DEEP_FLUID
                ? HydrologyCandidateKind.DEEP_FLUID
                : type.isGrotto() ? HydrologyCandidateKind.OUTLET : HydrologyCandidateKind.SOURCE;
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(
                        course.id(),
                        DIAGNOSTIC_SALT,
                        rejectionSalt
                ),
                kind,
                type,
                representative.start(),
                rejection, 0
        ));
    }

    static CaveCourseGraph acceptedGraph(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses
    ) {
        LinkedHashSet<Long> nodeIds = new LinkedHashSet<>();
        LinkedHashSet<Long> edgeIds = new LinkedHashSet<>();
        LinkedHashSet<Long> outletIds = new LinkedHashSet<>();
        for (RiverCourse course : courses) {
            if (course.sourceNodeId().isPresent()) {
                nodeIds.add(course.sourceNodeId().getAsLong());
            }
            if (course.outletId().isPresent()) {
                outletIds.add(course.outletId().getAsLong());
            }
            for (DrainageEdge edge : course.drainageEdges()) {
                if (edge.totalContributingSources() == 0) {
                    continue;
                }
                edgeIds.add(edge.id());
                nodeIds.add(edge.upstreamNodeId());
                nodeIds.add(edge.downstreamNodeId());
                outletIds.add(edge.outletId());
            }
        }
        ArrayList<DrainageNode> acceptedNodes = new ArrayList<>();
        for (DrainageNode node : nodes) {
            if (nodeIds.contains(node.id())) {
                acceptedNodes.add(node);
                outletIds.add(node.outletId());
            }
        }
        ArrayList<DrainageEdge> acceptedEdges = new ArrayList<>();
        for (DrainageEdge edge : edges) {
            if (edgeIds.contains(edge.id()) && edge.totalContributingSources() > 0) {
                acceptedEdges.add(edge);
            }
        }
        ArrayList<RiverOutlet> acceptedOutlets = new ArrayList<>();
        for (RiverOutlet outlet : outlets) {
            if (outletIds.contains(outlet.id())) {
                acceptedOutlets.add(outlet);
            }
        }
        return new CaveCourseGraph(
                List.copyOf(acceptedNodes),
                List.copyOf(acceptedEdges),
                List.copyOf(acceptedOutlets)
        );
    }

    static Result withoutCourses(Result result, Set<Long> rejectedCourseIds) {
        Objects.requireNonNull(result);
        Objects.requireNonNull(rejectedCourseIds);
        if (rejectedCourseIds.isEmpty()) {
            return result;
        }
        ArrayList<RiverCourse> acceptedCourses = new ArrayList<>(result.courses().size());
        for (RiverCourse course : result.courses()) {
            if (!rejectedCourseIds.contains(course.id())) {
                acceptedCourses.add(course);
            }
        }
        ArrayList<HydrologyCavePlan> acceptedPlans = new ArrayList<>(result.cavePlans().size());
        for (HydrologyCavePlan plan : result.cavePlans()) {
            if (!rejectedCourseIds.contains(plan.source().sourceId())) {
                acceptedPlans.add(plan);
            }
        }
        CaveCourseGraph graph = acceptedGraph(
                result.nodes(),
                result.edges(),
                result.outlets(),
                acceptedCourses
        );
        return new Result(
                graph.nodes(),
                graph.edges(),
                graph.outlets(),
                List.copyOf(acceptedCourses),
                List.copyOf(acceptedPlans)
        );
    }

    static HydrologyDiagnosticCandidate overlapDiagnostic(RiverCourse course, long winnerSourceId) {
        Objects.requireNonNull(course);
        HydraulicSegment representative = representativeCaveSegment(course);
        if (representative == null) {
            throw new IllegalArgumentException("Cross-tile cave arbitration requires a cave-bearing course.");
        }
        HydrologyFeatureType type = representative.type();
        HydrologyCandidateKind kind = course.type() == RiverCourseType.DEEP_FLUID
                ? HydrologyCandidateKind.DEEP_FLUID
                : type.isGrotto() ? HydrologyCandidateKind.OUTLET : HydrologyCandidateKind.SOURCE;
        return new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(
                        course.id(),
                        DIAGNOSTIC_SALT,
                        HydrologyCaveRejection.OVERLAPPING_SOURCE.ordinal(),
                        winnerSourceId
                ),
                kind,
                type,
                representative.start(),
                HydrologyCandidateRejection.CAVE_CONTAINMENT, 0
        );
    }

    record Result(
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses,
            List<HydrologyCavePlan> cavePlans
    ) {
    }

    record Options(
            boolean connectToExistingCaves,
            int coastalGrottoMaximumVolume,
            int inlandGrottoMaximumVolume
    ) {
        Options {
            if (coastalGrottoMaximumVolume < 1 || inlandGrottoMaximumVolume < 1) {
                throw new IllegalArgumentException("Grotto maximum volumes must be positive.");
            }
        }

        int maximumVolume(HydrologyFeatureType type) {
            return switch (type) {
                case COASTAL_GROTTO -> coastalGrottoMaximumVolume;
                case INLAND_GROTTO -> inlandGrottoMaximumVolume;
                default -> Integer.MAX_VALUE;
            };
        }
    }

    record CandidateKey(
            long courseId,
            RiverCourseType courseType,
            String profileKey,
            List<HydraulicSegment> segments,
            Options options
    ) {
        CandidateKey {
            Objects.requireNonNull(courseType, "courseType");
            if (profileKey == null || profileKey.isBlank()) {
                throw new IllegalArgumentException("profileKey must not be blank");
            }
            profileKey = profileKey.trim();
            segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
            Objects.requireNonNull(options, "options");
        }

        private static CandidateKey create(RiverCourse course, Options options) {
            return new CandidateKey(
                    course.id(),
                    course.type(),
                    course.profileKey(),
                    course.segments(),
                    options
            );
        }
    }

    static final class CandidateCache extends LinkedHashMap<CandidateKey, HydrologyCaveCandidate> {
        private static final int DEFAULT_MAXIMUM_ENTRIES = 256;
        private static final long DEFAULT_MAXIMUM_RETAINED_POSITIONS = 262_144L;

        private final int maximumEntries;
        private final long maximumRetainedPositions;
        private long retainedPositions;

        CandidateCache() {
            this(DEFAULT_MAXIMUM_ENTRIES, DEFAULT_MAXIMUM_RETAINED_POSITIONS);
        }

        CandidateCache(int maximumEntries, long maximumRetainedPositions) {
            super(16, 0.75F, true);
            if (maximumEntries < 1) {
                throw new IllegalArgumentException("Maximum candidate cache entries must be positive.");
            }
            if (maximumRetainedPositions < 1L) {
                throw new IllegalArgumentException("Maximum retained candidate positions must be positive.");
            }
            this.maximumEntries = maximumEntries;
            this.maximumRetainedPositions = maximumRetainedPositions;
        }

        @Override
        public HydrologyCaveCandidate put(CandidateKey key, HydrologyCaveCandidate candidate) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(candidate);
            HydrologyCaveCandidate previous = super.remove(key);
            if (previous != null) {
                retainedPositions -= weight(previous);
            }
            long weight = weight(candidate);
            if (weight > maximumRetainedPositions) {
                return previous;
            }
            super.put(key, candidate);
            retainedPositions += weight;
            trim();
            return previous;
        }

        @Override
        public HydrologyCaveCandidate remove(Object key) {
            HydrologyCaveCandidate removed = super.remove(key);
            if (removed != null) {
                retainedPositions -= weight(removed);
            }
            return removed;
        }

        @Override
        public void putAll(Map<? extends CandidateKey, ? extends HydrologyCaveCandidate> candidates) {
            for (Map.Entry<? extends CandidateKey, ? extends HydrologyCaveCandidate> entry
                    : candidates.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }

        @Override
        public void clear() {
            super.clear();
            retainedPositions = 0L;
        }

        long retainedPositions() {
            return retainedPositions;
        }

        private void trim() {
            Iterator<Map.Entry<CandidateKey, HydrologyCaveCandidate>> iterator = entrySet().iterator();
            while (iterator.hasNext()
                    && (size() > maximumEntries || retainedPositions > maximumRetainedPositions)) {
                Map.Entry<CandidateKey, HydrologyCaveCandidate> eldest = iterator.next();
                retainedPositions -= weight(eldest.getValue());
                iterator.remove();
            }
        }

        private static long weight(HydrologyCaveCandidate candidate) {
            return Math.max(1L, (long) candidate.actions().size() + candidate.intentionalOpenings().size());
        }
    }

    static int horizontalRadius(
            int entryX,
            int entryZ,
            int minimumX,
            int maximumX,
            int minimumZ,
            int maximumZ
    ) {
        long maximumDistanceSquared = 1L;
        long[][] corners = {
                {minimumX, minimumZ},
                {minimumX, maximumZ},
                {maximumX, minimumZ},
                {maximumX, maximumZ}
        };
        for (long[] corner : corners) {
            long deltaX = corner[0] - entryX;
            long deltaZ = corner[1] - entryZ;
            maximumDistanceSquared = Math.max(maximumDistanceSquared, deltaX * deltaX + deltaZ * deltaZ);
        }
        return Math.max(1, (int) StrictMath.ceil(StrictMath.sqrt(maximumDistanceSquared)) + 2);
    }

    static List<CaveSurfaceOpening> surfaceOpenings(RiverCourse course) {
        if (course.type() != RiverCourseType.SURFACE) {
            return List.of();
        }
        ArrayList<CaveSurfaceOpening> openings = new ArrayList<>();
        List<HydraulicSegment> segments = course.segments();
        for (int index = 0; index < segments.size(); index++) {
            HydraulicSegment segment = segments.get(index);
            if (!segment.type().isUnderground()) {
                continue;
            }
            int radius = Math.max(1, segment.width() / 2) + 1;
            if (segment.type() == HydrologyFeatureType.SINKHOLE) {
                HydrologyPoint start = segment.start();
                double maximumDistance = 0D;
                for (HydrologyPoint point : segment.centerline()) {
                    maximumDistance = Math.max(
                            maximumDistance,
                            StrictMath.hypot(point.x() - start.x(), point.z() - start.z())
                    );
                }
                radius += (int) StrictMath.ceil(maximumDistance);
            }
            long radiusSquared = (long) radius * radius;
            int minimumY = segment.type() == HydrologyFeatureType.SINKHOLE
                    ? segment.downstreamHeadY() + 1
                    : Integer.MIN_VALUE;
            boolean includeNeighborhood = segment.type() != HydrologyFeatureType.SINKHOLE;
            if (index == 0
                    || !segments.get(index - 1).type().isUnderground()) {
                openings.add(CaveSurfaceOpening.create(
                        segment,
                        true,
                        radiusSquared,
                        minimumY,
                        includeNeighborhood
                ));
            }
            boolean terminalCoastalOpening = index == segments.size() - 1
                    && segment.type() == HydrologyFeatureType.COASTAL_GROTTO;
            if (terminalCoastalOpening
                    || (index < segments.size() - 1
                    && !segments.get(index + 1).type().isUnderground())) {
                openings.add(CaveSurfaceOpening.create(
                        segment,
                        false,
                        radiusSquared,
                        minimumY,
                        includeNeighborhood
                ));
            }
        }
        return List.copyOf(openings);
    }
}
