package art.arcane.iris.generation.hydrology.cave;

public interface CaveVoxelView {
    boolean isInWorld(CavePosition position);

    CaveVoxel voxelAt(CavePosition position);

    boolean isOpenToSurface(CavePosition position);

    boolean isAboveTerrainSurface(CavePosition position);

    default boolean hasAboveTerrainSurface(int x, int z, int minimumY, int maximumY) {
        if (minimumY > maximumY) {
            return false;
        }
        for (int y = minimumY; ; y++) {
            CavePosition position = new CavePosition(x, y, z);
            if (isInWorld(position) && isAboveTerrainSurface(position)) {
                return true;
            }
            if (y == maximumY) {
                return false;
            }
        }
    }
}
