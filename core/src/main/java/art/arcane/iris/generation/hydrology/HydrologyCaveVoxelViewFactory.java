package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.CaveVoxelView;

@FunctionalInterface
public interface HydrologyCaveVoxelViewFactory {
    CaveVoxelView create(PlannedSurface plannedSurface);

    interface PlannedSurface {
        int resolve(int x, int z, int naturalHeight);

        boolean ownsTerrain(int x, int z);
    }
}
