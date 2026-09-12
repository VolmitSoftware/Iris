package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CavePosition;
import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CaveCandidateSpanBuilder {
    private final RiverCourse course;
    private final HydraulicSegment representative;
    private final LinkedHashMap<Long, CaveSpanSet> actions;
    private final LinkedHashMap<Long, CaveSpanSet> openings;
    private final LinkedHashMap<Long, CaveSpanSet> coastalGrottoActions;
    private final LinkedHashMap<Long, CaveSpanSet> inlandGrottoActions;
    private final List<CaveSurfaceOpening> surfaceOpenings;
    private int minimumX;
    private int maximumX;
    private int minimumY;
    private int maximumY;
    private int minimumZ;
    private int maximumZ;

    CaveCandidateSpanBuilder(RiverCourse course, HydraulicSegment representative) {
        this.course = course;
        this.representative = representative;
        this.actions = new LinkedHashMap<>();
        this.openings = new LinkedHashMap<>();
        this.coastalGrottoActions = new LinkedHashMap<>();
        this.inlandGrottoActions = new LinkedHashMap<>();
        this.surfaceOpenings = HydrologyCaveCourseFilter.surfaceOpenings(course);
        this.minimumX = Integer.MAX_VALUE;
        this.maximumX = Integer.MIN_VALUE;
        this.minimumY = Integer.MAX_VALUE;
        this.maximumY = Integer.MIN_VALUE;
        this.minimumZ = Integer.MAX_VALUE;
        this.maximumZ = Integer.MIN_VALUE;
    }

    RiverCourse course() {
        return course;
    }

    HydraulicSegment representative() {
        return representative;
    }

    boolean isEmpty() {
        return actions.isEmpty();
    }

    boolean allowsIntentionalSurfaceExposure() {
        return course.surfaceSinkholeContinuation();
    }

    void addAction(int x, int z, HydrologyColumnLayer layer) {
        int minimumActionY = layer.bedY() + 1;
        int maximumActionY = layer.ceilingY();
        if (minimumActionY > maximumActionY) {
            return;
        }
        addSpan(actions, x, z, minimumActionY, maximumActionY);
        if (layer.feature().type() == HydrologyFeatureType.COASTAL_GROTTO) {
            addSpan(coastalGrottoActions, x, z, minimumActionY, maximumActionY);
        } else if (layer.feature().type() == HydrologyFeatureType.INLAND_GROTTO) {
            addSpan(inlandGrottoActions, x, z, minimumActionY, maximumActionY);
        }
        include(x, minimumActionY, z);
        include(x, maximumActionY, z);
        for (CaveSurfaceOpening opening : surfaceOpenings) {
            if (!opening.matchesColumn(layer, x, z)) {
                continue;
            }
            int minimumOpeningY = Math.max(minimumActionY, opening.minimumY());
            if (minimumOpeningY > maximumActionY) {
                continue;
            }
            if (!opening.includeNeighborhood()) {
                addOpening(x, z, minimumOpeningY, maximumActionY);
                continue;
            }
            addOpening(x, z, Math.max(minimumOpeningY - 1, opening.minimumY()), maximumActionY + 1);
            for (int[] offset : HydrologyCaveCourseFilter.HORIZONTAL_NEIGHBORS) {
                addOpening(x + offset[0], z + offset[1], minimumOpeningY, maximumActionY);
            }
        }
    }

    void addOceanOpening(int x, int z, int minimumOpeningY, int maximumOpeningY) {
        if (minimumOpeningY <= maximumOpeningY) {
            addOpening(x, z, minimumOpeningY, maximumOpeningY);
        }
    }

    void addOpeningNeighborhood(
            int x,
            int z,
            int minimumOpeningY,
            int maximumOpeningY
    ) {
        if (minimumOpeningY > maximumOpeningY) {
            return;
        }
        addOpening(x, z, minimumOpeningY - 1, maximumOpeningY + 1);
        for (int[] offset : HydrologyCaveCourseFilter.HORIZONTAL_NEIGHBORS) {
            addOpening(x + offset[0], z + offset[1], minimumOpeningY, maximumOpeningY);
        }
    }

    void addAdjacentSurfaceOpenings(
            HydrologyFootprintCompiler.ValidationRaster validation
    ) {
        for (Map.Entry<Long, CaveSpanSet> entry : actions.entrySet()) {
            int x = RiverFootprint.unpackX(entry.getKey());
            int z = RiverFootprint.unpackZ(entry.getKey());
            for (int[] offset : HydrologyCaveCourseFilter.HORIZONTAL_NEIGHBORS) {
                int neighborX = x + offset[0];
                int neighborZ = z + offset[1];
                HydrologyColumnSample neighbor = validation.surfaceColumnAt(neighborX, neighborZ);
                if (neighbor == null) {
                    continue;
                }
                for (HydrologyColumnLayer layer : neighbor.layers()) {
                    if (layer.feature().courseId() != course.id() || !layer.channel() || !layer.fluidOwned()) {
                        continue;
                    }
                    for (CaveYSpan span : entry.getValue().spans()) {
                        int minimumY = Math.max(span.minimumY(), layer.bedY() + 1);
                        int maximumY = Math.min(span.maximumY(), layer.fluidHeadY());
                        if (minimumY <= maximumY) {
                            addAdjacentFluidOpening(neighborX, neighborZ, minimumY, maximumY);
                        }
                    }
                }
            }
        }
    }

    private void addAdjacentFluidOpening(int x, int z, int minimumY, int maximumY) {
        CaveSpanSet owned = actions.get(RiverFootprint.pack(x, z));
        if (owned == null) {
            addOpening(x, z, minimumY, maximumY);
            return;
        }
        int cursor = minimumY;
        for (CaveYSpan span : owned.spans()) {
            if (span.maximumY() < cursor) {
                continue;
            }
            if (span.minimumY() > maximumY) {
                break;
            }
            if (cursor < span.minimumY()) {
                addOpening(x, z, cursor, Math.min(maximumY, span.minimumY() - 1));
            }
            cursor = Math.max(cursor, span.maximumY() + 1);
            if (cursor > maximumY) {
                return;
            }
        }
        addOpening(x, z, cursor, maximumY);
    }

    HydrologyFeatureType oversizedGrotto(HydrologyCaveCourseFilter.Options options) {
        if (positionCount(coastalGrottoActions)
                > options.maximumVolume(HydrologyFeatureType.COASTAL_GROTTO)) {
            return HydrologyFeatureType.COASTAL_GROTTO;
        }
        if (positionCount(inlandGrottoActions)
                > options.maximumVolume(HydrologyFeatureType.INLAND_GROTTO)) {
            return HydrologyFeatureType.INLAND_GROTTO;
        }
        return null;
    }

    long positionCount() {
        return positionCount(actions);
    }

    boolean exposed(CaveVoxelView view) {
        CaveCandidateBounds bounds = bounds();
        for (Map.Entry<Long, CaveSpanSet> entry : actions.entrySet()) {
            int x = RiverFootprint.unpackX(entry.getKey());
            int z = RiverFootprint.unpackZ(entry.getKey());
            CaveSpanSet actionSpans = entry.getValue();
            CaveSpanSet openingSpans = openings.get(entry.getKey());
            if (exposedDifference(actionSpans, null, openingSpans, x, z, bounds, view)) {
                return true;
            }
            for (CaveYSpan span : actionSpans.spans()) {
                int lowerBoundary = span.minimumY() - 1;
                if (!actionSpans.contains(lowerBoundary)
                        && !contains(openingSpans, lowerBoundary)
                        && exposed(x, lowerBoundary, z, bounds, view)) {
                    return true;
                }
                int upperBoundary = span.maximumY() + 1;
                if (!actionSpans.contains(upperBoundary)
                        && !contains(openingSpans, upperBoundary)
                        && exposed(x, upperBoundary, z, bounds, view)) {
                    return true;
                }
            }
            for (int[] offset : HydrologyCaveCourseFilter.HORIZONTAL_NEIGHBORS) {
                int neighborX = x + offset[0];
                int neighborZ = z + offset[1];
                long neighborKey = RiverFootprint.pack(neighborX, neighborZ);
                if (exposedDifference(
                        actionSpans,
                        actions.get(neighborKey),
                        openings.get(neighborKey),
                        neighborX,
                        neighborZ,
                        bounds,
                        view
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean exposedDifference(
            CaveSpanSet source,
            CaveSpanSet excludedActions,
            CaveSpanSet excludedOpenings,
            int x,
            int z,
            CaveCandidateBounds bounds,
            CaveVoxelView view
    ) {
        for (CaveYSpan span : source.spans()) {
            if (exposedDifference(
                    span,
                    excludedActions,
                    excludedOpenings,
                    x,
                    z,
                    bounds,
                    view
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean exposedDifference(
            CaveYSpan source,
            CaveSpanSet excludedActions,
            CaveSpanSet excludedOpenings,
            int x,
            int z,
            CaveCandidateBounds bounds,
            CaveVoxelView view
    ) {
        List<CaveYSpan> actionSpans = excludedActions == null ? List.of() : excludedActions.spans();
        List<CaveYSpan> openingSpans = excludedOpenings == null ? List.of() : excludedOpenings.spans();
        int actionIndex = 0;
        int openingIndex = 0;
        long cursor = source.minimumY();
        while (cursor <= source.maximumY()) {
            while (actionIndex < actionSpans.size()
                    && actionSpans.get(actionIndex).maximumY() < cursor) {
                actionIndex++;
            }
            while (openingIndex < openingSpans.size()
                    && openingSpans.get(openingIndex).maximumY() < cursor) {
                openingIndex++;
            }
            CaveYSpan action = actionIndex < actionSpans.size() ? actionSpans.get(actionIndex) : null;
            CaveYSpan opening = openingIndex < openingSpans.size() ? openingSpans.get(openingIndex) : null;
            CaveYSpan excluded = first(action, opening);
            if (excluded == null || excluded.minimumY() > source.maximumY()) {
                return exposed(x, (int) cursor, source.maximumY(), z, bounds, view);
            }
            if (cursor < excluded.minimumY()
                    && exposed(x, (int) cursor, excluded.minimumY() - 1, z, bounds, view)) {
                return true;
            }
            cursor = Math.max(cursor, (long) excluded.maximumY() + 1L);
            if (excluded == action) {
                actionIndex++;
            } else {
                openingIndex++;
            }
        }
        return false;
    }

    private CaveYSpan first(CaveYSpan first, CaveYSpan second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.minimumY() <= second.minimumY() ? first : second;
    }

    boolean exposed(
            int x,
            int minimumY,
            int maximumY,
            int z,
            CaveCandidateBounds bounds,
            CaveVoxelView view
    ) {
        if (!bounds.containsColumn(x, z)) {
            return false;
        }
        int boundedMinimumY = Math.max(minimumY, bounds.minimumY());
        int boundedMaximumY = Math.min(maximumY, bounds.maximumY());
        return boundedMinimumY <= boundedMaximumY
                && view.hasAboveTerrainSurface(x, z, boundedMinimumY, boundedMaximumY);
    }

    boolean exposed(
            int x,
            int y,
            int z,
            CaveCandidateBounds bounds,
            CaveVoxelView view
    ) {
        CavePosition position = new CavePosition(x, y, z);
        return bounds.contains(position)
                && view.isInWorld(position)
                && view.isAboveTerrainSurface(position);
    }

    private CaveCandidateBounds bounds() {
        HydrologyPoint start = representative.start();
        int waterHead = representative.upstreamHeadY();
        int entryY = Math.max(waterHead, maximumY);
        int horizontalRadius = HydrologyCaveCourseFilter.horizontalRadius(
                start.x(),
                start.z(),
                minimumX,
                maximumX,
                minimumZ,
                maximumZ
        );
        int maximumDepth = Math.max(1, entryY - minimumY + 2);
        int dryHeadroom = Math.max(0, maximumY - waterHead);
        return new CaveCandidateBounds(
                start.x(),
                entryY,
                start.z(),
                waterHead,
                horizontalRadius,
                maximumDepth,
                dryHeadroom
        );
    }

    private void addOpening(int x, int z, int minimumOpeningY, int maximumOpeningY) {
        addSpan(openings, x, z, minimumOpeningY, maximumOpeningY);
        include(x, minimumOpeningY, z);
        include(x, maximumOpeningY, z);
    }

    private void include(int x, int y, int z) {
        minimumX = Math.min(minimumX, x);
        maximumX = Math.max(maximumX, x);
        minimumY = Math.min(minimumY, y);
        maximumY = Math.max(maximumY, y);
        minimumZ = Math.min(minimumZ, z);
        maximumZ = Math.max(maximumZ, z);
    }

    private static void addSpan(
            Map<Long, CaveSpanSet> spans,
            int x,
            int z,
            int minimumY,
            int maximumY
    ) {
        spans.computeIfAbsent(RiverFootprint.pack(x, z), (Long ignored) -> new CaveSpanSet())
                .add(minimumY, maximumY);
    }

    private static boolean contains(CaveSpanSet spans, int y) {
        return spans != null && spans.contains(y);
    }

    static long positionCount(Map<Long, CaveSpanSet> spans) {
        long count = 0L;
        for (CaveSpanSet spanSet : spans.values()) {
            count += spanSet.positionCount();
        }
        return count;
    }
}
