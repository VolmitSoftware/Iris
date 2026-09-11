package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.cave.CavePosition;
import art.arcane.iris.engine.hydrology.cave.CavePositionIndex;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCandidate;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveFluidPolicy;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveGrottoShape;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveMode;
import art.arcane.iris.engine.hydrology.cave.HydrologyCavePlannerSettings;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

final class CaveCandidateBuilder {
    private final RiverCourse course;
    private final HydraulicSegment representative;
    private final LinkedHashMap<CavePosition, HydrologyCaveAction> actions;
    private final LinkedHashSet<CavePosition> openings;
    private final CavePositionIndex openingIndex;
    private final List<CaveSurfaceOpening> surfaceOpenings;
    private int minimumX;
    private int maximumX;
    private int minimumY;
    private int maximumY;
    private int minimumZ;
    private int maximumZ;

    CaveCandidateBuilder(RiverCourse course, HydraulicSegment representative) {
        this.course = course;
        this.representative = representative;
        this.actions = new LinkedHashMap<>();
        this.openings = new LinkedHashSet<>();
        this.openingIndex = new CavePositionIndex();
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

    private HydraulicSegment representative() {
        return representative;
    }

    boolean isEmpty() {
        return actions.isEmpty();
    }

    void addAction(CavePosition position, HydrologyCaveAction action) {
        HydrologyCaveAction existing = actions.get(position);
        if (existing == null || actionPriority(action) < actionPriority(existing)) {
            actions.put(position, action);
        }
        include(position);
    }

    void addOpening(CavePosition position) {
        if (openingIndex.add(position.x(), position.y(), position.z())) {
            openings.add(position);
        }
        include(position);
    }

    void addOpening(int x, int y, int z) {
        if (openingIndex.add(x, y, z)) {
            openings.add(new CavePosition(x, y, z));
        }
        include(x, y, z);
    }

    void addOpeningNeighborhood(CavePosition position) {
        addOpening(position);
        for (int[] offset : HydrologyCaveCourseFilter.NEIGHBORS) {
            addOpening(
                    position.x() + offset[0],
                    position.y() + offset[1],
                    position.z() + offset[2]
            );
        }
    }

    void addAdjacentSurfaceOpenings(
            HydrologyFootprintCompiler.ValidationRaster validation
    ) {
        ArrayList<CavePosition> actionPositions = new ArrayList<>(actions.keySet());
        for (CavePosition position : actionPositions) {
            for (int[] offset : HydrologyCaveCourseFilter.HORIZONTAL_NEIGHBORS) {
                int neighborX = position.x() + offset[0];
                int neighborZ = position.z() + offset[1];
                if (actions.containsKey(new CavePosition(neighborX, position.y(), neighborZ))) {
                    continue;
                }
                HydrologyColumnSample neighbor = validation.surfaceColumnAt(neighborX, neighborZ);
                if (neighbor == null) {
                    continue;
                }
                for (HydrologyColumnLayer layer : neighbor.layers()) {
                    if (layer.feature().courseId() == course.id() && layer.channel() && layer.fluidOwned()
                            && layer.bedY() < position.y() && position.y() <= layer.fluidHeadY()) {
                        addOpening(neighborX, position.y(), neighborZ);
                        break;
                    }
                }
            }
        }
    }

    void addSurfaceOpening(HydrologyColumnLayer layer, CavePosition position) {
        for (CaveSurfaceOpening opening : surfaceOpenings) {
            if (!opening.matches(layer, position)) {
                continue;
            }
            if (opening.includeNeighborhood()) {
                addOpening(position);
                for (int[] offset : HydrologyCaveCourseFilter.NEIGHBORS) {
                    int y = position.y() + offset[1];
                    if (y >= opening.minimumY()) {
                        addOpening(position.x() + offset[0], y, position.z() + offset[2]);
                    }
                }
            } else {
                addOpening(position);
            }
        }
    }

    private void include(CavePosition position) {
        include(position.x(), position.y(), position.z());
    }

    private void include(int x, int y, int z) {
        minimumX = Math.min(minimumX, x);
        maximumX = Math.max(maximumX, x);
        minimumY = Math.min(minimumY, y);
        maximumY = Math.max(maximumY, y);
        minimumZ = Math.min(minimumZ, z);
        maximumZ = Math.max(maximumZ, z);
    }

    HydrologyCaveCandidate build(HydrologyCaveCourseFilter.Options options) {
        HydrologyPoint start = representative.start();
        int waterHead = representative.upstreamHeadY();
        int entryY = Math.max(waterHead, maximumY);
        CavePosition entry = new CavePosition(start.x(), entryY, start.z());
        CavePosition target = new CavePosition(start.x(), waterHead, start.z());
        int horizontalRadius = HydrologyCaveCourseFilter.horizontalRadius(
                entry.x(),
                entry.z(),
                minimumX,
                maximumX,
                minimumZ,
                maximumZ
        );
        int maximumDepth = Math.max(1, entryY - minimumY + 2);
        int dryHeadroom = Math.max(0, maximumY - waterHead);
        int volume = Math.max(1, actions.size());
        HydrologyCavePlannerSettings settings = new HydrologyCavePlannerSettings(
                horizontalRadius,
                maximumDepth,
                volume,
                1,
                1,
                1,
                1,
                dryHeadroom,
                HydrologyCaveFluidPolicy.REJECT_EXISTING,
                HydrologyCaveGrottoShape.ELLIPSOID,
                horizontalRadius,
                maximumDepth
        );
        return new HydrologyCaveCandidate(
                new HydrologyCaveSource(
                        course.id(),
                        entry,
                        target,
                        waterHead,
                        HydrologyCaveMode.GENERATED_GROTTO
                ),
                course.profileKey(),
                settings,
                options.connectToExistingCaves() && course.type() == RiverCourseType.UNDERGROUND,
                actions,
                openings
        );
    }

    private static int actionPriority(HydrologyCaveAction action) {
        return switch (action) {
            case WET_SOURCE -> 0;
            case FALLING_FLUID -> 1;
            case DRY_AIR -> 2;
            case SEAL_GUARD -> 3;
        };
    }

}
