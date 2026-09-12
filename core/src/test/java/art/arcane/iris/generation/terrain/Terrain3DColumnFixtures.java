package art.arcane.iris.generation.terrain;

public final class Terrain3DColumnFixtures {
    private Terrain3DColumnFixtures() {
    }

    public static Terrain3DColumn spans(double baseHeight, int... boundaries) {
        return new Terrain3DColumn(baseHeight, boundaries[1] + 1, true, boundaries);
    }
}
