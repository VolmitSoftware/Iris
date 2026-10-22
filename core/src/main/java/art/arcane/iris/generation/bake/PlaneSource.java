package art.arcane.iris.generation.bake;

public interface PlaneSource {
    Object compute(BakePlane plane, int chunkX, int chunkZ);

    ColumnBake assemble(ChunkPlanes planes);

    boolean mainThread();

    boolean generationContext();
}
