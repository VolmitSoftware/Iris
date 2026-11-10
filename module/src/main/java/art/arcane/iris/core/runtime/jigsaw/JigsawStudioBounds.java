package art.arcane.iris.core.runtime.jigsaw;

import java.util.Objects;

public record JigsawStudioBounds(
        int originX,
        int originY,
        int originZ,
        JigsawStudioCellDimensions dimensions
) {
    public JigsawStudioBounds {
        dimensions = Objects.requireNonNull(dimensions, "Jigsaw Studio bounds dimensions");
    }

    public int maxX() {
        return originX + dimensions.width() - 1;
    }

    public int maxY() {
        return originY + dimensions.height() - 1;
    }

    public int maxZ() {
        return originZ + dimensions.depth() - 1;
    }

    public boolean contains(int worldX, int worldY, int worldZ) {
        return worldX >= originX && worldX <= maxX()
                && worldY >= originY && worldY <= maxY()
                && worldZ >= originZ && worldZ <= maxZ();
    }

    public boolean intersectsHorizontal(int minX, int minZ, int maxX, int maxZ) {
        return this.maxX() >= minX && originX <= maxX
                && this.maxZ() >= minZ && originZ <= maxZ;
    }
}
