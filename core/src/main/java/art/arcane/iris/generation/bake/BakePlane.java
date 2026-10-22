package art.arcane.iris.generation.bake;

public enum BakePlane {
    REGION,
    BASE_BIOME,
    BASE_HEIGHT,
    NATURAL_HEIGHT,
    NATURAL_TRUE_BIOME,
    HEIGHT,
    TRUE_BIOME,
    ROCK,
    FLUID,
    CAVE_BIOME;

    static final BakePlane[] PLANES = values();
}
