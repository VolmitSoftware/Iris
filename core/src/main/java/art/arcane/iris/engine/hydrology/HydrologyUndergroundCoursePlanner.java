package art.arcane.iris.engine.hydrology;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

final class HydrologyUndergroundCoursePlanner {
    private final HydrologyPlanner planner;

    HydrologyUndergroundCoursePlanner(HydrologyPlanner planner) {
        this.planner = planner;
    }

    void compileUndergroundCourses(
            HydrologyTileKey key,
            HydrologySampledGrid grid,
            HydrologyRoutingPlan routing,
            CompiledGraph graph,
            List<Integer> sources,
            List<RiverCourse> courses,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        LinkedHashMap<Long, ArrayList<UndergroundCourseDraft>> draftsByOutlet = new LinkedHashMap<>();
        for (int sourceIndex : sources) {
            HydrologyGridNode source = grid.node(sourceIndex);
            long courseId = HydrologyHash.mix(planner.worldSeed, HydrologySurfaceCoursePlanner.COURSE_SALT, HydrologySourcePlanner.UNDERGROUND_SOURCE_SALT, source.id());
            HydrologyCoursePath path = planner.surfaceCourses.coursePath(null, routing, graph, sourceIndex, false);
            if (path == null) {
                addCompiledSourceDiagnostic(
                        source,
                        false,
                        courseId,
                        HydrologyCandidateRejection.NO_DRAINAGE_PATH,
                        diagnostics
                );
                continue;
            }
            draftsByOutlet.computeIfAbsent(path.outlet().id(), (Long ignored) -> new ArrayList<>())
                    .add(new UndergroundCourseDraft(source, courseId, path));
        }
        for (ArrayList<UndergroundCourseDraft> outletDrafts : draftsByOutlet.values()) {
            outletDrafts.sort(Comparator
                    .comparingDouble((UndergroundCourseDraft draft) -> planner.tributaries.edgeLength(
                            draft.path().edges(),
                            draft.path().edges().size()
                    ))
                    .reversed()
                    .thenComparingLong(UndergroundCourseDraft::courseId));
            RiverCourse mainCourse = null;
            int tributaries = 0;
            for (UndergroundCourseDraft draft : outletDrafts) {
                if (mainCourse != null && tributaries >= planner.settings.underground().tributaries()) {
                    break;
                }
                if (mainCourse == null) {
                    RiverCourse course = buildUndergroundCourse(draft, draft.path(), planner.segments.outletHead(draft.path().outlet()), diagnostics);
                    if (course != null) {
                        mainCourse = course;
                        courses.add(course);
                    }
                    continue;
                }
                RiverCourse tributary = planner.tributaries.buildUndergroundTributary(draft, mainCourse, diagnostics);
                if (tributary != null) {
                    courses.add(tributary);
                    tributaries++;
                }
            }
        }
    }

    RiverCourse buildUndergroundCourse(
            UndergroundCourseDraft draft,
            HydrologyCoursePath path,
            int outletHead,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        HydrologyGridNode source = draft.source();
        long courseId = draft.courseId();
        HydrologyPoint sourcePoint = path.points().getFirst();
        HydrologyPoint trunkPoint = path.points().getLast();
        HydrologyTerrainSample trunkTerrain = Objects.requireNonNull(
                planner.sampleDetailed(trunkPoint.x(), trunkPoint.z()),
                "Hydrology underground trunk left sampled terrain"
        );
        String profileKey = planner.segments.chooseProfile(trunkTerrain, path.outlet().id());
        int styledHead = planner.sampleGeometry(
                HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                profileKey,
                sourcePoint.x(),
                sourcePoint.z(),
                0L,
                planner.settings.underground().minimumFluidY(),
                planner.settings.underground().maximumFluidY()
        );
        if (outletHead > planner.settings.underground().maximumFluidY()) {
            addCompiledSourceDiagnostic(
                    source,
                    false,
                    courseId,
                    HydrologyCandidateRejection.OUTLET_LEVEL,
                    diagnostics
            );
            return null;
        }
        int initialHead = Math.max(styledHead, outletHead);
        RiverCourse course = buildUndergroundCourse(courseId, profileKey, source, path, initialHead, outletHead);
        if (course == null) {
            addCompiledSourceDiagnostic(
                    source,
                    false,
                    courseId,
                    HydrologyCandidateRejection.CAVE_CONTAINMENT,
                    diagnostics
            );
        }
        return course;
    }

    void addCompiledSourceDiagnostic(
            HydrologyGridNode source,
            boolean surface,
            long courseId,
            HydrologyCandidateRejection rejection,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        addCompiledSourceDiagnostic(source, surface, courseId, rejection, 0, diagnostics);
    }

    void addCompiledSourceDiagnostic(
            HydrologyGridNode source,
            boolean surface,
            long courseId,
            HydrologyCandidateRejection rejection,
            int detail,
            List<HydrologyDiagnosticCandidate> diagnostics
    ) {
        int y = surface ? source.terrain().naturalHeight() : source.terrain().caveFluidY();
        diagnostics.add(new HydrologyDiagnosticCandidate(
                HydrologyHash.mix(courseId, HydrologySourcePlanner.DIAGNOSTIC_SALT, rejection.ordinal()),
                HydrologyCandidateKind.SOURCE,
                surface ? HydrologyFeatureType.SURFACE_POOL : HydrologyFeatureType.UNDERGROUND_POOL,
                new HydrologyPoint(source.x(), y, source.z()),
                rejection,
                detail
        ));
    }

    RiverCourse buildUndergroundCourse(
            long courseId,
            String profileKey,
            HydrologyGridNode source,
            HydrologyCoursePath path,
            int initialHead,
            int outletHead
    ) {
        int pairCount = path.points().size() - 1;
        if (pairCount < 1) {
            return null;
        }
        int[] heads = new int[path.points().size()];
        int[] preferredHeads = new int[path.points().size()];
        int[] widths = new int[pairCount];
        int[] depths = new int[pairCount];
        preferredHeads[0] = initialHead;
        for (int index = 0; index < pairCount; index++) {
            HydrologyPoint point = path.points().get(index);
            HydrologyTerrainSample terrain = planner.sampleBasisWithoutSlope(point.x(), point.z());
            int discharge = Math.max(1, path.pairEdges().get(index).contributingUndergroundSources());
            widths[index] = planner.segments.scaledDimension(
                    planner.sampleGeometry(
                            HydrologyGeometrySampler.Field.UNDERGROUND_WIDTH,
                            profileKey,
                            point.x(),
                            point.z(),
                            0L,
                            planner.settings.underground().minimumWidth(),
                            planner.settings.underground().maximumWidth()
                    ),
                    planner.settings.underground().minimumWidth(),
                    planner.settings.underground().maximumWidth(),
                    discharge,
                    terrain.widthMultiplier()
            );
            depths[index] = planner.segments.scaledDimension(
                    planner.sampleGeometry(
                            HydrologyGeometrySampler.Field.UNDERGROUND_DEPTH,
                            profileKey,
                            point.x(),
                            point.z(),
                            0L,
                            planner.settings.underground().minimumDepth(),
                            planner.settings.underground().maximumDepth()
                    ),
                    planner.settings.underground().minimumDepth(),
                    planner.settings.underground().maximumDepth(),
                    discharge,
                    terrain.depthMultiplier()
            );
            HydrologyPoint downstreamPoint = path.points().get(index + 1);
            int caveHead = planner.sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_FLUID_LEVEL,
                    profileKey,
                    downstreamPoint.x(),
                    downstreamPoint.z(),
                    0L,
                    planner.settings.underground().minimumFluidY(),
                    planner.settings.underground().maximumFluidY()
            );
            preferredHeads[index + 1] = caveHead;
        }
        int[] minimumHeads = undergroundMinimumHeads(path, depths, outletHead);
        int[] optimisticMaximumHeads = undergroundOptimisticMaximumHeads(path);
        if (!undergroundHeadsFeasible(minimumHeads, optimisticMaximumHeads, outletHead)) {
            return null;
        }
        int[] maximumHeads = undergroundMaximumHeads(courseId, profileKey, path, widths);
        if (!solveUndergroundHeads(preferredHeads, minimumHeads, maximumHeads, outletHead, heads)) {
            return null;
        }
        levelUndergroundMouth(path, heads, outletHead);
        ArrayList<HydraulicSegment> segments = new ArrayList<>(pairCount + 1);
        for (int index = 0; index < pairCount; index++) {
            int drop = heads[index] - heads[index + 1];
            HydrologyFeatureType type = drop > 0
                    ? HydrologyFeatureType.UNDERGROUND_DROP
                    : HydrologyFeatureType.UNDERGROUND_POOL;
            if (!planner.segments.addHydraulicSegments(
                    courseId,
                    index,
                    type,
                    heads[index],
                    heads[index + 1],
                    widths[index],
                    depths[index],
                    List.of(
                            HydrologyPlanner.withY(path.points().get(index), heads[index]),
                            HydrologyPlanner.withY(path.points().get(index + 1), heads[index + 1])
                    ),
                    false,
                    segments
            )) {
                return null;
            }
        }
        planner.segments.appendOutletSegments(
                RiverCourseType.UNDERGROUND,
                courseId,
                path,
                heads[heads.length - 1],
                widths,
                depths,
                segments
        );
        int discharge = planner.segments.maximumUndergroundDischarge(path.edges());
        return new RiverCourse(
                courseId,
                RiverCourseType.UNDERGROUND,
                OptionalLong.of(source.id()),
                OptionalLong.of(path.outlet().id()),
                profileKey,
                discharge,
                path.edges(),
                segments
        );
    }

    int[] undergroundMaximumHeads(
            long courseId,
            String profileKey,
            HydrologyCoursePath path,
            int[] widths
    ) {
        int[] segmentCaps = new int[widths.length];
        for (int index = 0; index < widths.length; index++) {
            int radius = Math.max(1, widths[index] / 2);
            segmentCaps[index] = undergroundSegmentCap(
                    courseId,
                    profileKey,
                    path.points().get(index),
                    path.points().get(index + 1),
                    radius
            );
        }
        return nodeMaximumHeads(segmentCaps);
    }

    int[] undergroundOptimisticMaximumHeads(HydrologyCoursePath path) {
        int segmentCount = path.points().size() - 1;
        int[] segmentCaps = new int[segmentCount];
        Arrays.fill(segmentCaps, Integer.MAX_VALUE);
        int minimumHeadroom = planner.settings.underground().minimumHeadroom();
        for (int index = 0; index < segmentCount; index++) {
            HydrologyPoint start = path.points().get(index);
            HydrologyPoint end = path.points().get(index + 1);
            segmentCaps[index] = Math.min(
                    optimisticUndergroundSegmentCap(start, minimumHeadroom),
                    optimisticUndergroundSegmentCap(end, minimumHeadroom)
            );
        }
        return nodeMaximumHeads(segmentCaps);
    }

    int optimisticUndergroundSegmentCap(HydrologyPoint point, int minimumHeadroom) {
        HydrologyTerrainSample terrain = planner.sampleLandBasis(point.x(), point.z());
        return terrain == null || terrain.ocean()
                ? Integer.MAX_VALUE
                : terrain.naturalHeight() - minimumHeadroom - planner.settings.underground().minimumRockCover();
    }

    int undergroundSegmentCap(
            long courseId,
            String profileKey,
            HydrologyPoint start,
            HydrologyPoint end,
            int radius
    ) {
        int minimumHeadroom = planner.settings.underground().minimumHeadroom();
        int maximumHeadroom = planner.settings.underground().maximumHeadroom();
        UndergroundSegmentCapKey key = new UndergroundSegmentCapKey(
                courseId,
                profileKey,
                start.x(),
                start.z(),
                end.x(),
                end.z(),
                radius,
                minimumHeadroom,
                maximumHeadroom
        );
        HydrologyPlanner.PlanningSamples samples = planner.planningSamples.get();
        if (samples != null) {
            Integer cached = samples.undergroundSegmentCaps.get(key);
            if (cached != null) {
                return cached;
            }
        }
        int segmentCap = Integer.MAX_VALUE;
        List<HydrologyPoint> centerline = planner.segments.line(start, end, 1);
        for (HydrologyPoint point : centerline) {
            int headroom = planner.sampleGeometry(
                    HydrologyGeometrySampler.Field.UNDERGROUND_HEADROOM,
                    profileKey,
                    point.x(),
                    point.z(),
                    courseId,
                    minimumHeadroom,
                    maximumHeadroom
            );
            int minimumLandHeight = minimumLandHeightWithinRadius(point.x(), point.z(), radius);
            if (minimumLandHeight != Integer.MAX_VALUE) {
                segmentCap = Math.min(segmentCap, minimumLandHeight - headroom - planner.settings.underground().minimumRockCover());
            }
        }
        if (samples != null) {
            samples.undergroundSegmentCaps.put(key, segmentCap);
        }
        return segmentCap;
    }

    int[] nodeMaximumHeads(int[] segmentCaps) {
        int[] maximumHeads = new int[segmentCaps.length + 1];
        maximumHeads[0] = segmentCaps[0];
        for (int index = 1; index < maximumHeads.length - 1; index++) {
            maximumHeads[index] = Math.min(segmentCaps[index - 1], segmentCaps[index]);
        }
        maximumHeads[maximumHeads.length - 1] = segmentCaps[segmentCaps.length - 1];
        return maximumHeads;
    }

    int minimumLandHeightWithinRadius(int x, int z, int radius) {
        HydrologyPlanner.PlanningSamples samples = planner.planningSamples.get();
        MinimumLandHeightKey key = new MinimumLandHeightKey(x, z, radius);
        if (samples != null) {
            Integer cached = samples.minimumLandHeights.get(key);
            if (cached != null) {
                return cached;
            }
        }

        List<HydrologyGridOffset> offsets = samples == null
                ? radialOffsets(radius)
                : samples.radialOffsets.computeIfAbsent(radius, this::radialOffsets);
        int minimumHeight = Integer.MAX_VALUE;
        for (HydrologyGridOffset offset : offsets) {
            HydrologyTerrainSample terrain = planner.sampleLandBasis(x + offset.x(), z + offset.z());
            if (terrain == null || terrain.ocean()) {
                continue;
            }
            minimumHeight = Math.min(minimumHeight, terrain.naturalHeight());
        }
        if (samples != null) {
            samples.minimumLandHeights.put(key, minimumHeight);
        }
        return minimumHeight;
    }

    List<HydrologyGridOffset> radialOffsets(int radius) {
        int blockRadius = (int) StrictMath.ceil(radius + 0.25D);
        ArrayList<HydrologyGridOffset> offsets = new ArrayList<>();
        for (int deltaZ = -blockRadius; deltaZ <= blockRadius; deltaZ++) {
            for (int deltaX = -blockRadius; deltaX <= blockRadius; deltaX++) {
                if (StrictMath.hypot(deltaX, deltaZ) <= radius + 0.25D) {
                    offsets.add(new HydrologyGridOffset(deltaX, deltaZ));
                }
            }
        }
        return List.copyOf(offsets);
    }

    int[] undergroundMinimumHeads(HydrologyCoursePath path, int[] depths, int outletHead) {
        int[] minimumHeads = new int[path.points().size()];
        for (int index = 0; index < minimumHeads.length; index++) {
            int depth = index == 0
                    ? depths[0]
                    : index == depths.length ? depths[depths.length - 1] : Math.max(depths[index - 1], depths[index]);
            depth = Math.max(depth, planner.settings.geometry().drops().maximumBasinDepth());
            long worldFloorHead = (long) planner.minimumY + depth + planner.settings.underground().minimumFloorCover();
            int boundedWorldFloor = worldFloorHead < Integer.MIN_VALUE
                    ? Integer.MIN_VALUE
                    : worldFloorHead > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) worldFloorHead;
            int configuredMinimum = index == minimumHeads.length - 1
                    ? outletHead
                    : planner.settings.underground().minimumFluidY();
            minimumHeads[index] = Math.max(outletHead, Math.max(configuredMinimum, boundedWorldFloor));
        }
        return minimumHeads;
    }

    static boolean undergroundHeadsFeasible(
            int[] minimumHeads,
            int[] maximumHeads,
            int outletHead
    ) {
        if (minimumHeads.length == 0 || minimumHeads.length != maximumHeads.length) {
            throw new IllegalArgumentException("Underground head arrays must have the same positive length.");
        }
        int index = minimumHeads.length - 1;
        int requiredHead = Math.max(minimumHeads[index], outletHead);
        if (requiredHead > maximumHeads[index]) {
            return false;
        }
        for (index--; index >= 0; index--) {
            requiredHead = Math.max(minimumHeads[index], requiredHead);
            if (requiredHead > maximumHeads[index]) {
                return false;
            }
        }
        return true;
    }

    static boolean solveUndergroundHeads(
            int[] preferredHeads,
            int[] minimumHeads,
            int[] maximumHeads,
            int outletHead,
            int[] solvedHeads
    ) {
        int length = preferredHeads.length;
        if (length == 0
                || minimumHeads.length != length
                || maximumHeads.length != length
                || solvedHeads.length != length) {
            throw new IllegalArgumentException("Underground head arrays must have the same positive length.");
        }
        int[] requiredHeads = new int[length];
        requiredHeads[length - 1] = Math.max(minimumHeads[length - 1], outletHead);
        if (requiredHeads[length - 1] > maximumHeads[length - 1]) {
            return false;
        }
        for (int index = length - 2; index >= 0; index--) {
            requiredHeads[index] = Math.max(minimumHeads[index], requiredHeads[index + 1]);
            if (requiredHeads[index] > maximumHeads[index]) {
                return false;
            }
        }
        solvedHeads[0] = HydrologyPlanner.clamp(preferredHeads[0], requiredHeads[0], maximumHeads[0]);
        for (int index = 1; index < length; index++) {
            int maximum = Math.min(maximumHeads[index], solvedHeads[index - 1]);
            if (requiredHeads[index] > maximum) {
                return false;
            }
            solvedHeads[index] = HydrologyPlanner.clamp(preferredHeads[index], requiredHeads[index], maximum);
        }
        solvedHeads[length - 1] = outletHead;
        return outletHead >= minimumHeads[length - 1]
                && outletHead <= maximumHeads[length - 1]
                && (length == 1 || solvedHeads[length - 2] >= outletHead);
    }

    HydrologyCoursePath undergroundCoursePath(List<DrainageEdge> edges, RiverOutlet outlet) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        ArrayList<DrainageEdge> pairEdges = new ArrayList<>();
        for (DrainageEdge edge : edges) {
            for (int pointIndex = 0; pointIndex < edge.centerline().size(); pointIndex++) {
                HydrologyPoint point = edge.centerline().get(pointIndex);
                if (points.isEmpty()) {
                    points.add(point);
                } else if (pointIndex > 0) {
                    points.add(point);
                    pairEdges.add(edge);
                }
            }
        }
        return points.size() >= 2 && pairEdges.size() == points.size() - 1
                ? new HydrologyCoursePath(
                List.copyOf(points),
                List.copyOf(pairEdges),
                List.copyOf(edges),
                outlet,
                true,
                false,
                null
        )
                : null;
    }

    void levelUndergroundMouth(HydrologyCoursePath path, int[] heads, int outletHead) {
        heads[heads.length - 1] = outletHead;
        int levelingDistance = planner.settings.outlets().mouthLevelingDistance();
        if (levelingDistance == 0) {
            return;
        }
        double remaining = 0D;
        int initialHead = heads[0];
        for (int index = path.points().size() - 2; index >= 0; index--) {
            HydrologyPoint current = path.points().get(index);
            HydrologyPoint downstream = path.points().get(index + 1);
            remaining += StrictMath.hypot(downstream.x() - current.x(), downstream.z() - current.z());
            if (remaining > levelingDistance) {
                break;
            }
            double ratio = remaining / levelingDistance;
            int cap = outletHead + (int) StrictMath.ceil((initialHead - outletHead) * ratio);
            heads[index] = Math.min(heads[index], cap);
        }
        for (int index = 1; index < heads.length; index++) {
            heads[index] = Math.min(heads[index - 1], heads[index]);
        }
        heads[heads.length - 1] = outletHead;
    }
}
