package art.arcane.iris.generation.bake;

public interface BakeCache {
    ColumnBake get(int chunkX, int chunkZ);

    ColumnBake getIfPresent(int chunkX, int chunkZ);
}
