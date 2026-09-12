package art.arcane.iris.generation.hydrology.cave;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public final class HydrologyCaveConflictPolicy {
    private static final Comparator<HydrologyCaveSource> SOURCE_PRIORITY = Comparator
            .comparingInt(HydrologyCaveSource::waterHeadY)
            .reversed()
            .thenComparingLong(HydrologyCaveSource::sourceId)
            .thenComparingInt(source -> source.entry().x())
            .thenComparingInt(source -> source.entry().y())
            .thenComparingInt(source -> source.entry().z())
            .thenComparingInt(source -> source.target().x())
            .thenComparingInt(source -> source.target().y())
            .thenComparingInt(source -> source.target().z())
            .thenComparing(HydrologyCaveSource::mode);

    private HydrologyCaveConflictPolicy() {
    }

    public static Comparator<HydrologyCaveSource> sourcePriority() {
        return SOURCE_PRIORITY;
    }

    public static int compareSources(HydrologyCaveSource left, HydrologyCaveSource right) {
        return SOURCE_PRIORITY.compare(
                Objects.requireNonNull(left, "left"),
                Objects.requireNonNull(right, "right")
        );
    }

    public static boolean hasIncompatibleOverlap(
            String leftProfileKey,
            Map<CavePosition, HydrologyCaveAction> leftActions,
            String rightProfileKey,
            Map<CavePosition, HydrologyCaveAction> rightActions
    ) {
        String normalizedLeftProfileKey = requireProfileKey(leftProfileKey, "leftProfileKey");
        String normalizedRightProfileKey = requireProfileKey(rightProfileKey, "rightProfileKey");
        Map<CavePosition, HydrologyCaveAction> normalizedLeftActions = Objects.requireNonNull(
                leftActions,
                "leftActions"
        );
        Map<CavePosition, HydrologyCaveAction> normalizedRightActions = Objects.requireNonNull(
                rightActions,
                "rightActions"
        );
        if (normalizedLeftActions.size() > normalizedRightActions.size()) {
            return hasIncompatibleOverlap(
                    normalizedRightProfileKey,
                    normalizedRightActions,
                    normalizedLeftProfileKey,
                    normalizedLeftActions
            );
        }
        boolean sameProfile = normalizedLeftProfileKey.equals(normalizedRightProfileKey);
        for (Map.Entry<CavePosition, HydrologyCaveAction> entry : normalizedLeftActions.entrySet()) {
            CavePosition position = Objects.requireNonNull(entry.getKey(), "left action position");
            if (!normalizedRightActions.containsKey(position)) {
                continue;
            }
            HydrologyCaveAction leftAction = Objects.requireNonNull(entry.getValue(), "left action");
            HydrologyCaveAction rightAction = Objects.requireNonNull(
                    normalizedRightActions.get(position),
                    "right action"
            );
            if (!sameProfile || leftAction != rightAction) {
                return true;
            }
        }
        return false;
    }

    private static String requireProfileKey(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
