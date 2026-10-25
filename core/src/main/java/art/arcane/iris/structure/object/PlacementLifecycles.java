package art.arcane.iris.structure.object;


import java.util.EnumMap;
import java.util.Map;

/** One resolved plan per mode in an engine runtime. Engine-less tools share a separate default set. */
public final class PlacementLifecycles {
    private final Map<ObjectPlaceMode, PlacementLifecycle> plans;

    private PlacementLifecycles(Map<ObjectPlaceMode, PlacementLifecycle> plans) {
        this.plans = Map.copyOf(plans);
    }

    public static PlacementLifecycles resolve() {
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

    public static PlacementLifecycles defaults() {
        return Defaults.INSTANCE;
    }

    static PlacementLifecycles forPlacer(IObjectPlacer placer) {
        var engine = placer.getEngine();
        PlacementLifecycles plans = engine == null ? null : engine.getPlacementLifecycles();
        return plans == null ? defaults() : plans;
    }

    public PlacementLifecycle get(ObjectPlaceMode mode) {
        return plans.get(java.util.Objects.requireNonNull(mode, "Placement mode"));
    }

    private static final class Defaults {
        private static final PlacementLifecycles INSTANCE = resolve();
    }
}
