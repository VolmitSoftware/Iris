package art.arcane.iris.engine.hydrology;

import java.util.List;

record HydrologySampledGrid(
        int minimumX,
        int minimumZ,
        int ownerMinimumX,
        int ownerMinimumZ,
        int ownerSize,
        int width,
        int spacing,
        List<HydrologyGridNode> nodes
) {
    HydrologyGridNode node(int index) {
        return nodes.get(index);
    }

    HydrologyGridNode nodeAt(int gridX, int gridZ) {
        if (gridX < 0 || gridZ < 0 || gridX >= width || gridZ >= width) {
            return null;
        }
        return nodes.get(gridZ * width + gridX);
    }

    HydrologyGridNode nodeAtWorld(int x, int z) {
        long deltaX = (long) x - minimumX;
        long deltaZ = (long) z - minimumZ;
        if (deltaX < 0L || deltaZ < 0L || deltaX % spacing != 0L || deltaZ % spacing != 0L) {
            return null;
        }
        long gridX = deltaX / spacing;
        long gridZ = deltaZ / spacing;
        if (gridX >= width || gridZ >= width) {
            return null;
        }
        return nodeAt((int) gridX, (int) gridZ);
    }

    boolean owns(int x, int z) {
        return x >= ownerMinimumX && x < ownerMinimumX + ownerSize
                && z >= ownerMinimumZ && z < ownerMinimumZ + ownerSize;
    }
}
