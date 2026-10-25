package art.arcane.iris.generation.hydrology.grid;

public interface HydrologyTerrainSource {
    void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly);

    int seaLevel();

    int minY();

    int maxY();
}
