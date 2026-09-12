package art.arcane.iris.generation.hydrology.cave;

import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.Objects;

public final class HydrologyCaveCell {
    private static final String DEFAULT_FLUID_PROFILE_KEY = "default";
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_FORCED_AIR = 3;

    private final HydrologyCaveAction action;
    private final String fluidProfileKey;
    private final String floodedBiomeKey;
    private final MatterCavern cavern;

    public HydrologyCaveCell(
            HydrologyCaveAction action,
            String fluidProfileKey,
            String floodedBiomeKey
    ) {
        this.action = Objects.requireNonNull(action);
        if (fluidProfileKey == null || fluidProfileKey.isBlank()) {
            throw new IllegalArgumentException("fluidProfileKey must not be blank");
        }
        this.fluidProfileKey = fluidProfileKey.trim();
        this.floodedBiomeKey = floodedBiomeKey == null ? "" : floodedBiomeKey.trim();
        this.cavern = switch (action) {
            case WET_SOURCE, FALLING_FLUID -> new MatterCavern(true, this.floodedBiomeKey, LIQUID_FLUID);
            case DRY_AIR -> new MatterCavern(true, this.floodedBiomeKey, LIQUID_FORCED_AIR);
            case SEAL_GUARD -> null;
        };
    }

    public static HydrologyCaveCell of(HydrologyCaveAction action) {
        return new HydrologyCaveCell(action, DEFAULT_FLUID_PROFILE_KEY, "");
    }

    public static HydrologyCaveCell of(HydrologyCaveAction action, String fluidProfileKey) {
        return new HydrologyCaveCell(action, fluidProfileKey, "");
    }

    public boolean carves() {
        return action != HydrologyCaveAction.SEAL_GUARD;
    }

    public boolean isWet() {
        return action == HydrologyCaveAction.WET_SOURCE || action == HydrologyCaveAction.FALLING_FLUID;
    }

    public boolean isFalling() {
        return action == HydrologyCaveAction.FALLING_FLUID;
    }

    public boolean protectsPlacement() {
        return true;
    }

    public MatterCavern asCavern() {
        return cavern;
    }

    public HydrologyCaveAction action() {
        return action;
    }

    public String floodedBiomeKey() {
        return floodedBiomeKey;
    }

    public String fluidProfileKey() {
        return fluidProfileKey;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HydrologyCaveCell hydrology)) {
            return false;
        }
        return action == hydrology.action
                && fluidProfileKey.equals(hydrology.fluidProfileKey)
                && floodedBiomeKey.equals(hydrology.floodedBiomeKey);
    }

    @Override
    public int hashCode() {
        return (31 * ((31 * action.hashCode()) + fluidProfileKey.hashCode())) + floodedBiomeKey.hashCode();
    }

    @Override
    public String toString() {
        return "HydrologyCaveCell[action=" + action + ", fluidProfileKey=" + fluidProfileKey
                + ", floodedBiomeKey=" + floodedBiomeKey + "]";
    }
}
