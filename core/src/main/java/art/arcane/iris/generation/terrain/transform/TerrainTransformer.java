package art.arcane.iris.generation.terrain.transform;

public interface TerrainTransformer {
    int radius();

    void transform(TerrainTransformContext context);

    default TerrainColumnTransformer columnTransformer() {
        return null;
    }
}
