package art.arcane.iris.generation.hydrology;

import java.util.List;

final class HydrologySampledGrid {
    private final int minimumX;
    private final int minimumZ;
    private final int ownerMinimumX;
    private final int ownerMinimumZ;
    private final int ownerSize;
    private final int width;
    private final int spacing;
    private final List<HydrologyGridNode> nodes;
    private volatile HydrologyDrainageGraph drainage;

    HydrologySampledGrid(int minimumX, int minimumZ, int ownerMinimumX, int ownerMinimumZ,
                        int ownerSize, int width, int spacing, List<HydrologyGridNode> nodes) {
        this.minimumX = minimumX;
        this.minimumZ = minimumZ;
        this.ownerMinimumX = ownerMinimumX;
        this.ownerMinimumZ = ownerMinimumZ;
        this.ownerSize = ownerSize;
        this.width = width;
        this.spacing = spacing;
        this.nodes = List.copyOf(nodes);
    }

    int minimumX() {
        return minimumX;
    }

    int minimumZ() {
        return minimumZ;
    }

    int ownerMinimumX() {
        return ownerMinimumX;
    }

    int ownerMinimumZ() {
        return ownerMinimumZ;
    }

    int ownerSize() {
        return ownerSize;
    }

    int width() {
        return width;
    }

    int spacing() {
        return spacing;
    }

    List<HydrologyGridNode> nodes() {
        return nodes;
    }


    HydrologyDrainageGraph drainage(HydrologySourcePlanner sourcePlanner) {
        HydrologyDrainageGraph current = drainage;
        if (current != null && current.settings().equals(sourcePlanner.settings())) {
            return current;
        }
        synchronized (this) {
            current = drainage;
            if (current == null || !current.settings().equals(sourcePlanner.settings())) {
                current = new HydrologyDrainageGraph(this, sourcePlanner);
                drainage = current;
            }
            return current;
        }
    }

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
