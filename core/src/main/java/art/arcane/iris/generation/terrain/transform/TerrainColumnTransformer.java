package art.arcane.iris.generation.terrain.transform;

@FunctionalInterface
public interface TerrainColumnTransformer {
    void transform(TerrainTransformContext context, int localX, int localZ);
}
