package art.arcane.iris.generation.hydrology.underground;

public interface CarvedTerrain {
    CaveVoxel voxel(int x, int y, int z);

    int surface(int x, int z);

    String caveFluid(int x, int y, int z);
}
