package art.arcane.iris.generation.bake;

public interface PlaneSource {
    void fill(BakePlane plane, int chunkX, int chunkZ, long[] missing, Object values);

    boolean mainThread();
}
