package art.arcane.iris.structure.object;


import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** One resolved plan per mode. Plans are immutable, so every placement shares the default set. */
final class PlacementLifecycles {
    private final Map<ObjectPlaceMode, PlacementLifecycle> plans;

    private PlacementLifecycles(Map<ObjectPlaceMode, PlacementLifecycle> plans) {
        this.plans = Map.copyOf(plans);
    }

    static PlacementLifecycles resolve() {
        Map<ObjectPlaceMode, PlacementLifecycle> plans = new EnumMap<>(ObjectPlaceMode.class);
        for (ObjectPlaceMode mode : ObjectPlaceMode.values()) {
            try {
                plans.put(mode, mode.createLifecycle());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid placement lifecycle for " + mode + ": " + e.getMessage(), e);
            }
        }
        return new PlacementLifecycles(plans);
    }

    static PlacementLifecycles defaults() {
        return Defaults.INSTANCE;
    }

    PlacementLifecycle get(ObjectPlaceMode mode) {
        return plans.get(Objects.requireNonNull(mode, "Placement mode"));
    }

    private static final class Defaults {
        private static final PlacementLifecycles INSTANCE = resolve();
    }
}
