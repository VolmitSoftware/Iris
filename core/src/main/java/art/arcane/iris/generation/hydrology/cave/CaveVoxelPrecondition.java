package art.arcane.iris.generation.hydrology.cave;

import java.util.Objects;

public record CaveVoxelPrecondition(CaveVoxel voxel, boolean openToSurface) {
    public CaveVoxelPrecondition {
        Objects.requireNonNull(voxel);
    }
}
