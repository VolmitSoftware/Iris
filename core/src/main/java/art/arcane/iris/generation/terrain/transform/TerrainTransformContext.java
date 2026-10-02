package art.arcane.iris.generation.terrain.transform;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

public interface TerrainTransformContext {
    int blockX();

    int blockZ();

    int minY();

    int height();

    NativeBlockState original(int worldX, int worldY, int worldZ);

    void set(int localX, int localY, int localZ, NativeBlockState state);
}
