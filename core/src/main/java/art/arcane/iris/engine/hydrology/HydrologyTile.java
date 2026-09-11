package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class HydrologyTile {
    private final HydrologyTileKey key;
    private final long worldSeed;
    private final long settingsFingerprint;
    private final int tileSize;
    private final List<DrainageNode> nodes;
    private final List<DrainageEdge> edges;
    private final List<RiverOutlet> outlets;
    private final List<RiverCourse> courses;
    private final List<HydrologyCavePlan> cavePlans;
    private final List<HydrologyDiagnosticCandidate> diagnosticCandidates;
    private final RiverFootprint footprint;
    private final Map<Long, DrainageNode> nodesById;
    private final Map<Long, RiverOutlet> outletsById;
    private final Map<Long, HydrologyCavePlan> cavePlansByCourseId;
    private final List<HydrologyFeatureRef> features;

    public HydrologyTile(
            HydrologyTileKey key,
            long worldSeed,
            long settingsFingerprint,
            int tileSize,
            List<DrainageNode> nodes,
            List<DrainageEdge> edges,
            List<RiverOutlet> outlets,
            List<RiverCourse> courses,
            List<HydrologyCavePlan> cavePlans,
            List<HydrologyDiagnosticCandidate> diagnosticCandidates,
            RiverFootprint footprint
    ) {
        this.key = Objects.requireNonNull(key, "key");
        if (tileSize <= 0) {
            throw new IllegalArgumentException("tileSize must be positive.");
        }
        this.worldSeed = worldSeed;
        this.settingsFingerprint = settingsFingerprint;
        this.tileSize = tileSize;
        this.nodes = sortedCopy(nodes, Comparator.comparingLong(DrainageNode::id));
        this.edges = sortedCopy(edges, Comparator.comparingLong(DrainageEdge::id));
        this.outlets = sortedCopy(outlets, Comparator.comparingLong(RiverOutlet::id));
        this.courses = sortedCopy(courses, Comparator.comparingLong(RiverCourse::id));
        this.cavePlans = sortedCopy(
                cavePlans,
                Comparator.comparingLong((HydrologyCavePlan plan) -> plan.source().sourceId())
        );
        this.diagnosticCandidates = sortedCopy(
                diagnosticCandidates,
                Comparator.comparingLong(HydrologyDiagnosticCandidate::id)
        );
        this.footprint = Objects.requireNonNull(footprint, "footprint");
        this.nodesById = indexNodes(this.nodes);
        this.outletsById = indexOutlets(this.outlets);
        this.cavePlansByCourseId = indexCavePlans(this.cavePlans);
        validateGraph();
        validateCourses();
        this.features = collectFeatures(footprint);
    }

    public HydrologyTileKey key() {
        return key;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public long settingsFingerprint() {
        return settingsFingerprint;
    }

    public int tileSize() {
        return tileSize;
    }

    public List<DrainageNode> nodes() {
        return nodes;
    }

    public List<DrainageEdge> edges() {
        return edges;
    }

    public List<RiverOutlet> outlets() {
        return outlets;
    }

    public List<RiverCourse> courses() {
        return courses;
    }

    public List<HydrologyCavePlan> cavePlans() {
        return cavePlans;
    }

    public List<HydrologyDiagnosticCandidate> diagnosticCandidates() {
        return diagnosticCandidates;
    }

    public RiverFootprint footprint() {
        return footprint;
    }

    public Optional<DrainageNode> node(long id) {
        return Optional.ofNullable(nodesById.get(id));
    }

    public Optional<RiverOutlet> outlet(long id) {
        return Optional.ofNullable(outletsById.get(id));
    }

    public Optional<HydrologyCavePlan> cavePlan(long courseId) {
        return Optional.ofNullable(cavePlansByCourseId.get(courseId));
    }

    public Optional<HydrologyColumnSample> columnAt(int x, int z) {
        return footprint.sample(x, z);
    }

    public HydrologyRenderSample renderAt(int x, int z) {
        return footprint.renderSample(x, z);
    }

    public HydrologyDiagnosticRenderSample diagnosticRenderAt(int x, int z, int maximumDistance) {
        if (maximumDistance < 0) {
            throw new IllegalArgumentException("maximumDistance cannot be negative.");
        }
        long maximumDistanceSquared = (long) maximumDistance * maximumDistance;
        ArrayList<HydrologyDiagnosticCandidate> selected = new ArrayList<>();
        for (HydrologyDiagnosticCandidate candidate : diagnosticCandidates) {
            long distance = candidate.point().distanceSquared2D(new HydrologyPoint(x, candidate.point().y(), z));
            if (distance <= maximumDistanceSquared) {
                selected.add(candidate);
            }
        }
        return new HydrologyDiagnosticRenderSample(x, z, selected);
    }

    public List<HydrologyFeatureRef> features() {
        return features;
    }

    public Optional<HydrologyFeatureRef> nearestFeature(
            HydrologyFeatureType type,
            int x,
            int z,
            int maximumDistance
    ) {
        return nearestFeature(Set.of(Objects.requireNonNull(type, "type")), null, x, z, maximumDistance);
    }

    public Optional<HydrologyFeatureRef> nearestFeature(
            Set<HydrologyFeatureType> types,
            String profileKey,
            int x,
            int z,
            int maximumDistance
    ) {
        return nearestFeature(types, profileKey, x, z, maximumDistance, feature -> true);
    }

    public Optional<HydrologyFeatureRef> nearestFeature(
            Set<HydrologyFeatureType> types,
            String profileKey,
            int x,
            int z,
            int maximumDistance,
            Predicate<HydrologyFeatureRef> eligibility
    ) {
        Objects.requireNonNull(types, "types");
        Predicate<HydrologyFeatureRef> requiredEligibility = Objects.requireNonNull(
                eligibility,
                "eligibility"
        );
        if (maximumDistance < 0) {
            throw new IllegalArgumentException("maximumDistance cannot be negative.");
        }
        long maximumDistanceSquared = (long) maximumDistance * maximumDistance;
        HydrologyFeatureRef nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (RiverCourse course : courses) {
            if (profileKey != null && !profileKey.isBlank() && !profileKey.equals(course.profileKey())) {
                continue;
            }
            for (HydraulicSegment segment : course.segments()) {
                if (!types.contains(segment.type())) {
                    continue;
                }
                for (HydrologyPoint point : segment.centerline()) {
                    long deltaX = (long) point.x() - x;
                    long deltaZ = (long) point.z() - z;
                    long distance = deltaX * deltaX + deltaZ * deltaZ;
                    if (distance > maximumDistanceSquared || distance > nearestDistance) {
                        continue;
                    }
                    HydrologyFeatureRef feature = publishedCenterlineFeature(segment, point);
                    if (feature == null || !requiredEligibility.test(feature)) {
                        continue;
                    }
                    if (distance < nearestDistance || nearest == null || feature.id() < nearest.id()) {
                        nearest = feature;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    public boolean acyclic() {
        for (DrainageEdge edge : edges) {
            DrainageNode upstream = nodesById.get(edge.upstreamNodeId());
            DrainageNode downstream = nodesById.get(edge.downstreamNodeId());
            if (upstream == null || downstream == null || downstream.potential() >= upstream.potential()) {
                return false;
            }
        }
        return true;
    }

    private void validateGraph() {
        if (!acyclic()) {
            throw new IllegalArgumentException("Drainage edges must strictly lower potential.");
        }
        for (DrainageNode node : nodes) {
            if (!outletsById.containsKey(node.outletId())) {
                throw new IllegalArgumentException("Every drainage node must resolve to an accepted outlet.");
            }
        }
    }

    private void validateCourses() {
        LinkedHashMap<Long, RiverCourse> coursesById = new LinkedHashMap<>();
        for (RiverCourse course : courses) {
            if (coursesById.put(course.id(), course) != null) {
                throw new IllegalArgumentException("Duplicate river course id: " + course.id());
            }
            if (!course.hydraulicallyNonRising()) {
                throw new IllegalArgumentException("Course heads must be non-rising downstream.");
            }
            if (course.sourceNodeId().isPresent() && !nodesById.containsKey(course.sourceNodeId().getAsLong())) {
                throw new IllegalArgumentException("Course source is not part of the accepted drainage graph.");
            }
            if (course.outletId().isPresent() && !outletsById.containsKey(course.outletId().getAsLong())) {
                throw new IllegalArgumentException("Course outlet is not accepted by this tile.");
            }
            if (isCaveBearing(course) != cavePlansByCourseId.containsKey(course.id())) {
                throw new IllegalArgumentException("Every cave-bearing course must have one accepted containment plan.");
            }
        }
        validateFootprintCourses(coursesById);
    }

    private void validateFootprintCourses(Map<Long, RiverCourse> coursesById) {
        LinkedHashMap<Long, Map<CavePosition, HydrologyCaveAction>> expectedActions = new LinkedHashMap<>();
        for (HydrologyColumnSample sample : footprint.columns().values()) {
            ArrayList<Long> caveCourseIds = new ArrayList<>();
            for (HydrologyColumnLayer layer : sample.layers()) {
                long courseId = layer.feature().courseId();
                if (!coursesById.containsKey(courseId)) {
                    throw new IllegalArgumentException("Footprint references an unaccepted river course: " + courseId);
                }
                if (layer.oceanApron() || !layer.channel()
                        || !(layer.terrainOwned() || layer.fallingFluid() && layer.fluidOwned())
                        || !isCaveLayer(layer, coursesById.get(courseId))) {
                    continue;
                }
                Map<CavePosition, HydrologyCaveAction> courseActions = expectedActions.computeIfAbsent(
                        courseId,
                        (Long ignored) -> new HashMap<>()
                );
                if (!caveCourseIds.contains(courseId)) {
                    caveCourseIds.add(courseId);
                }
                for (int y = layer.bedY() + 1; y <= layer.ceilingY(); y++) {
                    CavePosition position = new CavePosition(sample.x(), y, sample.z());
                    HydrologyCaveAction action = actionAt(layer, y);
                    HydrologyCaveAction existing = courseActions.get(position);
                    if (existing == null || actionPriority(action) < actionPriority(existing)) {
                        courseActions.put(position, action);
                    }
                }
            }
            composeSurfaceActions(sample, caveCourseIds, coursesById, expectedActions);
        }
        HashSet<Long> validatedCourseIds = new HashSet<>();
        for (RiverCourse course : courses) {
            if (!isCaveBearing(course)) {
                continue;
            }
            if (!validatedCourseIds.add(course.id())) {
                continue;
            }
            List<RiverCourse> sharedCourses = sharedOutletCaveCourses(course);
            HashMap<CavePosition, HydrologyCaveAction> expected = new HashMap<>();
            HashMap<CavePosition, HydrologyCaveAction> actual = new HashMap<>();
            for (RiverCourse sharedCourse : sharedCourses) {
                validatedCourseIds.add(sharedCourse.id());
                mergeCaveActions(
                        expected,
                        expectedActions.getOrDefault(sharedCourse.id(), Map.of())
                );
                HydrologyCavePlan plan = cavePlansByCourseId.get(sharedCourse.id());
                plan.forEachAction((CavePosition position, HydrologyCaveAction action) -> {
                    if (action != HydrologyCaveAction.SEAL_GUARD) {
                        mergeCaveAction(actual, position, action);
                    }
                });
            }
            if (!actual.equals(expected)) {
                throw caveFootprintMismatch(course.id(), expected, actual);
            }
        }
    }

    private List<RiverCourse> sharedOutletCaveCourses(RiverCourse course) {
        if (course.outletId().isEmpty()) {
            return List.of(course);
        }
        long outletId = course.outletId().getAsLong();
        ArrayList<RiverCourse> shared = new ArrayList<>();
        for (RiverCourse candidate : courses) {
            if (isCaveBearing(candidate)
                    && candidate.outletId().isPresent()
                    && candidate.outletId().getAsLong() == outletId
                    && candidate.profileKey().equals(course.profileKey())) {
                shared.add(candidate);
            }
        }
        return List.copyOf(shared);
    }

    private void mergeCaveActions(
            Map<CavePosition, HydrologyCaveAction> target,
            Map<CavePosition, HydrologyCaveAction> source
    ) {
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : source.entrySet()) {
            mergeCaveAction(target, entry.getKey(), entry.getValue());
        }
    }

    private void mergeCaveAction(
            Map<CavePosition, HydrologyCaveAction> actions,
            CavePosition position,
            HydrologyCaveAction action
    ) {
        HydrologyCaveAction existing = actions.get(position);
        if (existing == null || actionPriority(action) < actionPriority(existing)) {
            actions.put(position, action);
        }
    }

    private IllegalArgumentException caveFootprintMismatch(
            long courseId,
            Map<CavePosition, HydrologyCaveAction> expected,
            Map<CavePosition, HydrologyCaveAction> actual
    ) {
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : expected.entrySet()) {
            HydrologyCaveAction actualAction = actual.get(entry.getKey());
            if (actualAction != entry.getValue()) {
                return new IllegalArgumentException("Cave footprint differs from its accepted containment plan: "
                        + courseId + " at " + entry.getKey() + ", footprint=" + entry.getValue()
                        + ", plan=" + actualAction + ".");
            }
        }
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : actual.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                return new IllegalArgumentException("Cave footprint differs from its accepted containment plan: "
                        + courseId + " has extra plan action " + entry.getValue() + " at " + entry.getKey() + ".");
            }
        }
        return new IllegalArgumentException("Cave footprint differs from its accepted containment plan: "
                + courseId + ".");
    }

    private void composeSurfaceActions(
            HydrologyColumnSample sample,
            List<Long> caveCourseIds,
            Map<Long, RiverCourse> coursesById,
            Map<Long, Map<CavePosition, HydrologyCaveAction>> expectedActions
    ) {
        if (caveCourseIds.isEmpty()) {
            return;
        }
        int minimumY = Integer.MAX_VALUE;
        int maximumY = Integer.MIN_VALUE;
        for (HydrologyColumnLayer layer : sample.layers()) {
            if (!layer.publishesSurfaceFluid()) {
                continue;
            }
            minimumY = Math.min(minimumY, layer.bedY() + 1);
            maximumY = Math.max(maximumY, layer.fluidHeadY());
        }
        for (int y = minimumY; y <= maximumY; y++) {
            HydrologyColumnSample.SurfacePublicationCell surfaceCell = sample
                    .surfacePublicationCellAt(y)
                    .orElse(null);
            if (surfaceCell == null) {
                continue;
            }
            CavePosition position = new CavePosition(sample.x(), y, sample.z());
            for (long courseId : caveCourseIds) {
                Map<CavePosition, HydrologyCaveAction> courseActions = expectedActions.get(courseId);
                if (!courseActions.containsKey(position)) {
                    continue;
                }
                RiverCourse course = coursesById.get(courseId);
                if (!course.profileKey().equals(surfaceCell.layer().profileKey())) {
                    throw new IllegalArgumentException("Surface and cave hydrology profiles conflict at "
                            + position + ".");
                }
                courseActions.put(position, surfaceCell.action());
            }
        }
    }

    private boolean isCaveBearing(RiverCourse course) {
        for (HydraulicSegment segment : course.segments()) {
            if (segment.type().isUnderground() || segment.type().isDeepFluid()
                    || segment.type().isSurface() && segment.fallingFluid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isCaveLayer(HydrologyColumnLayer layer, RiverCourse course) {
        if (layer.feature().type().isUnderground() || layer.feature().type().isDeepFluid()) {
            return true;
        }
        for (HydraulicSegment segment : course.segments()) {
            if (segment.id() == layer.feature().segmentId()) {
                return segment.type().isSurface() && segment.fallingFluid();
            }
        }
        return false;
    }

    private HydrologyCaveAction actionAt(HydrologyColumnLayer layer, int y) {
        if (y > layer.fluidHeadY()) {
            return HydrologyCaveAction.DRY_AIR;
        }
        if (layer.fallingFluid() && y < layer.fluidHeadY()) {
            return HydrologyCaveAction.FALLING_FLUID;
        }
        return HydrologyCaveAction.WET_SOURCE;
    }

    private int actionPriority(HydrologyCaveAction action) {
        return switch (action) {
            case WET_SOURCE -> 0;
            case FALLING_FLUID -> 1;
            case DRY_AIR -> 2;
            case SEAL_GUARD -> 3;
        };
    }

    private static Map<Long, DrainageNode> indexNodes(List<DrainageNode> values) {
        LinkedHashMap<Long, DrainageNode> indexed = new LinkedHashMap<>();
        for (DrainageNode value : values) {
            if (indexed.put(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate drainage node id: " + value.id());
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static Map<Long, RiverOutlet> indexOutlets(List<RiverOutlet> values) {
        LinkedHashMap<Long, RiverOutlet> indexed = new LinkedHashMap<>();
        for (RiverOutlet value : values) {
            if (indexed.put(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate river outlet id: " + value.id());
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static Map<Long, HydrologyCavePlan> indexCavePlans(List<HydrologyCavePlan> values) {
        LinkedHashMap<Long, HydrologyCavePlan> indexed = new LinkedHashMap<>();
        for (HydrologyCavePlan value : values) {
            if (!value.accepted()) {
                throw new IllegalArgumentException("Hydrology tiles cannot contain rejected cave plans.");
            }
            long courseId = value.source().sourceId();
            if (indexed.put(courseId, value) != null) {
                throw new IllegalArgumentException("Duplicate cave plan for course: " + courseId);
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private HydrologyFeatureRef publishedCenterlineFeature(
            HydraulicSegment segment,
            HydrologyPoint point
    ) {
        HydrologyColumnSample column = footprint.columns().get(RiverFootprint.pack(point.x(), point.z()));
        if (column == null) {
            return null;
        }
        for (HydrologyColumnLayer layer : column.layers()) {
            HydrologyFeatureRef feature = layer.feature();
            if (layer.channel()
                    && feature.segmentId() == segment.id()
                    && feature.type() == segment.type()) {
                if (feature.x() == point.x() && feature.z() == point.z()) {
                    return feature;
                }
                return new HydrologyFeatureRef(
                        feature.id(),
                        feature.type(),
                        feature.courseId(),
                        feature.segmentId(),
                        point.x(),
                        layer.fluidHeadY(),
                        point.z(),
                        feature.flowDeltaX(),
                        feature.flowDeltaZ(),
                        feature.source()
                );
            }
        }
        return null;
    }

    static List<HydrologyFeatureRef> collectFeatures(RiverFootprint footprint) {
        HashMap<Long, HydrologyColumnLayer> representatives = new HashMap<>();
        for (HydrologyColumnSample column : footprint.columns().values()) {
            for (HydrologyColumnLayer layer : column.layers()) {
                long segmentId = layer.feature().segmentId();
                HydrologyColumnLayer existing = representatives.get(segmentId);
                if (existing == null || featureOrder(layer, existing) < 0) {
                    representatives.put(segmentId, layer);
                }
            }
        }
        ArrayList<HydrologyFeatureRef> ordered = new ArrayList<>(representatives.size());
        for (HydrologyColumnLayer layer : representatives.values()) {
            ordered.add(layer.feature());
        }
        ordered.sort(Comparator.comparingInt((HydrologyFeatureRef feature) -> feature.type().renderPriority())
                .thenComparingLong(HydrologyFeatureRef::id));
        return List.copyOf(ordered);
    }

    private static int featureOrder(HydrologyColumnLayer first, HydrologyColumnLayer second) {
        int role = Integer.compare(featureRole(first), featureRole(second));
        if (role != 0) {
            return role;
        }
        HydrologyFeatureRef firstFeature = first.feature();
        HydrologyFeatureRef secondFeature = second.feature();
        int y = Integer.compare(firstFeature.y(), secondFeature.y());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(firstFeature.x(), secondFeature.x());
        return x != 0 ? x : Integer.compare(firstFeature.z(), secondFeature.z());
    }

    private static int featureRole(HydrologyColumnLayer layer) {
        if (layer.feature().source()) {
            return 0;
        }
        if (layer.fallingFluid()) {
            return 1;
        }
        if (layer.receivingPool()) {
            return 2;
        }
        if (layer.channel() && !layer.oceanApron()) {
            return 3;
        }
        if (layer.channel()) {
            return 4;
        }
        return 5;
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<T> comparator) {
        Objects.requireNonNull(values, "values");
        ArrayList<T> ordered = new ArrayList<>(values);
        ordered.sort(comparator);
        return List.copyOf(ordered);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof HydrologyTile tile)) {
            return false;
        }
        return worldSeed == tile.worldSeed
                && settingsFingerprint == tile.settingsFingerprint
                && tileSize == tile.tileSize
                && key.equals(tile.key)
                && nodes.equals(tile.nodes)
                && edges.equals(tile.edges)
                && outlets.equals(tile.outlets)
                && courses.equals(tile.courses)
                && cavePlans.equals(tile.cavePlans)
                && diagnosticCandidates.equals(tile.diagnosticCandidates)
                && footprint.equals(tile.footprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                key,
                worldSeed,
                settingsFingerprint,
                tileSize,
                nodes,
                edges,
                outlets,
                courses,
                cavePlans,
                diagnosticCandidates,
                footprint
        );
    }

    @Override
    public String toString() {
        return "HydrologyTile[key=" + key + ", courses=" + courses.size() + ", candidates="
                + diagnosticCandidates.size() + ", columns=" + footprint.size() + "]";
    }
}
