package art.arcane.iris.generation.hydrology;

public enum HydrologyFeatureType {
    SURFACE_POOL,
    RIFFLE,
    CASCADE,
    WATERFALL,
    // No planner produces a ridge bore any more. The constant stays because its ordinal seeds the
    // segment, outlet and feature ids of every constant after it on the cave and deep-fluid paths.
    RIDGE_BORE,
    UNDERGROUND_POOL,
    UNDERGROUND_DROP,
    SINKHOLE,
    COASTAL_GROTTO,
    INLAND_GROTTO,
    MOUTH,
    DEEP_POOL,
    DEEP_CHANNEL,
    // A standing surface pool (lava or any fluid) with no course: a bowl cut into the ground with a lip.
    STANDING_POOL;

    public boolean isSurface() {
        return switch (this) {
            case SURFACE_POOL, RIFFLE, CASCADE, WATERFALL, MOUTH, STANDING_POOL -> true;
            default -> false;
        };
    }

    public boolean isUnderground() {
        return switch (this) {
            case RIDGE_BORE, UNDERGROUND_POOL, UNDERGROUND_DROP, SINKHOLE, COASTAL_GROTTO, INLAND_GROTTO -> true;
            default -> false;
        };
    }

    public boolean isGrotto() {
        return this == COASTAL_GROTTO || this == INLAND_GROTTO;
    }

    public boolean isDeepFluid() {
        return this == DEEP_POOL || this == DEEP_CHANNEL;
    }

    public boolean isDrop() {
        return this == RIFFLE || this == CASCADE || this == WATERFALL
                || this == UNDERGROUND_DROP || this == SINKHOLE;
    }

    int renderPriority() {
        return switch (this) {
            case WATERFALL -> 0;
            case SINKHOLE -> 1;
            case CASCADE -> 2;
            case RIFFLE -> 3;
            case COASTAL_GROTTO -> 4;
            case INLAND_GROTTO -> 5;
            case MOUTH -> 6;
            case RIDGE_BORE -> 7;
            case UNDERGROUND_DROP -> 8;
            case UNDERGROUND_POOL -> 9;
            case DEEP_CHANNEL -> 10;
            case DEEP_POOL -> 11;
            case SURFACE_POOL -> 12;
            case STANDING_POOL -> 13;
        };
    }
}
