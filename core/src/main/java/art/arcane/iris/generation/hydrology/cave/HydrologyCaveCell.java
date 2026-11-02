package art.arcane.iris.generation.hydrology.cave;

import art.arcane.volmlib.util.matter.MatterCavern;

import java.util.Objects;

public final class HydrologyCaveCell {
    public static final String SEA_FLUID = "@sea";
    private static final byte LIQUID_FLUID = 1;
    private static final byte LIQUID_FORCED_AIR = 3;

    private final HydrologyCaveAction action;
    private final String fluidProfileKey;
    private final MatterCavern cavern;

    public HydrologyCaveCell(HydrologyCaveAction action, String fluidProfileKey) {
        this.action = Objects.requireNonNull(action);
        if (fluidProfileKey == null || fluidProfileKey.isBlank()) {
            throw new IllegalArgumentException("fluidProfileKey must not be blank");
        }
        this.fluidProfileKey = fluidProfileKey.trim();
        this.cavern = switch (action) {
            case WET_SOURCE, POOL_SOURCE -> new MatterCavern(true, "", LIQUID_FLUID);
            case DRY_AIR -> new MatterCavern(true, "", LIQUID_FORCED_AIR);
            case SEAL_GUARD -> null;
        };
    }

    public boolean carves() {
        return action != HydrologyCaveAction.SEAL_GUARD;
    }

    public boolean isWet() {
        return action == HydrologyCaveAction.WET_SOURCE || action == HydrologyCaveAction.POOL_SOURCE;
    }

    public MatterCavern asCavern() {
        return cavern;
    }

    public HydrologyCaveAction action() {
        return action;
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
        return action == hydrology.action && fluidProfileKey.equals(hydrology.fluidProfileKey);
    }

    @Override
    public int hashCode() {
        return (31 * action.hashCode()) + fluidProfileKey.hashCode();
    }

    @Override
    public String toString() {
        return "HydrologyCaveCell[action=" + action + ", fluidProfileKey=" + fluidProfileKey + "]";
    }
}
