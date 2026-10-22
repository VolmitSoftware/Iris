package art.arcane.iris.generation.bake;

public enum BakePlane {
    REGION(false),
    BASE_BIOME(false),
    BASE_HEIGHT(true),
    NATURAL_HEIGHT(true),
    NATURAL_TRUE_BIOME(false),
    HEIGHT(true),
    TRUE_BIOME(false),
    ROCK(false),
    FLUID(false),
    CAVE_BIOME(false);

    static final BakePlane[] PLANES = values();

    private final boolean doubles;

    BakePlane(boolean doubles) {
        this.doubles = doubles;
    }

    public boolean doubles() {
        return doubles;
    }
}
