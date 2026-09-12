package art.arcane.iris.command;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.generation.geometry.IrisBlockVector;

import java.util.Objects;

record ObjectPasteBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static ObjectPasteBounds resolve(
            IrisObject object,
            IrisObjectRotation rotation,
            int anchorX,
            int anchorY,
            int anchorZ
    ) {
        IrisObject placedObject = Objects.requireNonNull(object, "object");
        IrisObjectRotation placementRotation = Objects.requireNonNull(rotation, "rotation");
        int[] xOffsets = {
                -placedObject.getCenter().getBlockX(),
                placedObject.getW() - 1 - placedObject.getCenter().getBlockX()
        };
        int[] yOffsets = {
                -placedObject.getCenter().getBlockY(),
                placedObject.getH() - 1 - placedObject.getCenter().getBlockY()
        };
        int[] zOffsets = {
                -placedObject.getCenter().getBlockZ(),
                placedObject.getD() - 1 - placedObject.getCenter().getBlockZ()
        };
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;

        for (int xOffset : xOffsets) {
            for (int yOffset : yOffsets) {
                for (int zOffset : zOffsets) {
                    IrisBlockVector transformed = placementRotation.rotate(new IrisBlockVector(xOffset, yOffset, zOffset));
                    int worldX = anchorX + (int) Math.round(transformed.getX());
                    int worldY = anchorY + (int) Math.round(transformed.getY());
                    int worldZ = anchorZ + (int) Math.round(transformed.getZ());
                    minimumX = Math.min(minimumX, worldX);
                    minimumY = Math.min(minimumY, worldY);
                    minimumZ = Math.min(minimumZ, worldZ);
                    maximumX = Math.max(maximumX, worldX);
                    maximumY = Math.max(maximumY, worldY);
                    maximumZ = Math.max(maximumZ, worldZ);
                }
            }
        }

        return new ObjectPasteBounds(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
    }
}
