/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

/**
 * Turns block positions into pixels with an orthographic orbit camera. Larger depth is nearer the camera.
 */
final class OrbitProjection {
    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double cosYaw;
    private final double sinYaw;
    private final double cosPitch;
    private final double sinPitch;
    private final double pixelsPerBlock;
    private final double screenCenterX;
    private final double screenCenterY;

    /**
     * @param center     the middle of the scene, which the camera's pivot is given from; the pivot is drawn in the middle
     *                   of the uncovered view
     * @param fittedSize the extent that fits the view at zoom 1, seen from {@code fittedFrom}'s angle
     */
    OrbitProjection(Vec3 center, Vec3 fittedSize, OrbitCamera camera, OrbitCamera fittedFrom, int width, int height, int coveredLeftWidth) {
        this(center, camera, fittedPixelsPerBlock(fittedSize, camera, fittedFrom, width - coveredLeftWidth, height), width, height, coveredLeftWidth);
    }

    private OrbitProjection(Vec3 center, OrbitCamera camera, double pixelsPerBlock, int width, int height, int coveredLeftWidth) {
        this.centerX = center.x() + camera.pivot().x();
        this.centerY = center.y() + camera.pivot().y();
        this.centerZ = center.z() + camera.pivot().z();
        this.cosYaw = Math.cos(camera.yaw());
        this.sinYaw = Math.sin(camera.yaw());
        this.cosPitch = Math.cos(camera.pitch());
        this.sinPitch = Math.sin(camera.pitch());
        this.pixelsPerBlock = pixelsPerBlock;
        this.screenCenterX = coveredLeftWidth + (width - coveredLeftWidth) / 2D;
        this.screenCenterY = height / 2D;
    }

    /**
     * For scenes without a size to fit, like endless terrain: zoom 1 draws {@code pixelsPerBlockAtZoomOne}.
     */
    static OrbitProjection atScale(Vec3 center, OrbitCamera camera, double pixelsPerBlockAtZoomOne, int width, int height, int coveredLeftWidth) {
        return new OrbitProjection(center, camera, camera.zoom() * pixelsPerBlockAtZoomOne, width, height, coveredLeftWidth);
    }

    private static double fittedPixelsPerBlock(Vec3 fittedSize, OrbitCamera camera, OrbitCamera fittedFrom, int visibleWidth, int height) {
        double largestSize = Math.max(fittedSize.x(), Math.max(fittedSize.y(), fittedSize.z()));
        double fit = fitAtAngle(fittedSize, largestSize, fittedFrom, visibleWidth / (double) height);
        return camera.zoom() * fit * height / 2D / largestSize;
    }

    // How far to scale the extent down so it fits the view at the given angle; orbiting keeps the scale.
    private static double fitAtAngle(Vec3 size, double largestSize, OrbitCamera angle, double aspect) {
        double width = size.x() / largestSize;
        double height = size.y() / largestSize;
        double depth = size.z() / largestSize;
        double projectedWidth = Math.abs(Math.cos(angle.yaw())) * width + Math.abs(Math.sin(angle.yaw())) * depth;
        double projectedDepth = Math.abs(Math.sin(angle.yaw())) * width + Math.abs(Math.cos(angle.yaw())) * depth;
        double projectedHeight = Math.abs(Math.cos(angle.pitch())) * height + Math.abs(Math.sin(angle.pitch())) * projectedDepth;
        return Math.min(1D, Math.min(aspect / projectedWidth, 1D / projectedHeight));
    }

    double pixelsPerBlock() {
        return pixelsPerBlock;
    }

    double screenCenterX() {
        return screenCenterX;
    }

    double screenCenterY() {
        return screenCenterY;
    }

    /**
     * The blocks a move across the picture covers, in the plane of the picture: moving by it keeps every point's depth.
     */
    Vec3 acrossPicture(double screenDeltaX, double screenDeltaY) {
        double across = screenDeltaX / pixelsPerBlock;
        double up = -screenDeltaY / pixelsPerBlock;
        return new Vec3(cosYaw * across - sinPitch * sinYaw * up, cosPitch * up, -sinYaw * across - sinPitch * cosYaw * up);
    }

    // A block's length along every pixel's line of sight, toward the camera; it moves nothing on the picture.
    Vec3 towardCamera() {
        return new Vec3(cosPitch * sinYaw, sinPitch, cosPitch * cosYaw);
    }

    double screenX(double x, double y, double z) {
        return screenCenterX + screenStepX(x - centerX, z - centerZ);
    }

    double screenY(double x, double y, double z) {
        return screenCenterY + screenStepY(x - centerX, y - centerY, z - centerZ);
    }

    double depth(double x, double y, double z) {
        return depthStep(x - centerX, y - centerY, z - centerZ);
    }

    double screenStepX(double x, double z) {
        return (cosYaw * x - sinYaw * z) * pixelsPerBlock;
    }

    double screenStepY(double x, double y, double z) {
        double towardCamera = sinYaw * x + cosYaw * z;
        return -(cosPitch * y - sinPitch * towardCamera) * pixelsPerBlock;
    }

    double depthStep(double x, double y, double z) {
        double towardCamera = sinYaw * x + cosYaw * z;
        return sinPitch * y + cosPitch * towardCamera;
    }

    /**
     * The point at height {@code y} drawn at a pixel. Looking level along the ground, every pixel's line of sight runs
     * parallel to it, so the point lies infinitely far off.
     */
    Vec3 pointAt(double screenX, double screenY, double y) {
        double across = (screenX - screenCenterX) / pixelsPerBlock;
        double up = (screenCenterY - screenY) / pixelsPerBlock;
        double towardCamera = (cosPitch * (y - centerY) - up) / sinPitch;
        double x = cosYaw * across + sinYaw * towardCamera;
        double z = cosYaw * towardCamera - sinYaw * across;
        return new Vec3(centerX + x, y, centerZ + z);
    }

    boolean facesCamera(double normalX, double normalY, double normalZ) {
        return depthStep(normalX, normalY, normalZ) > 0D;
    }

    // The point the pivot puts in the middle of the view; positions relative to it are what the clip matrix takes.
    Vec3 center() {
        return new Vec3(centerX, centerY, centerZ);
    }

    /**
     * This projection for a graphics card: a column-major matrix from a position relative to {@link #center()} to clip
     * space, where nearer is smaller and anything within {@code depthRange} blocks of the centre lies between the near
     * and far planes. Clip space runs bottom-up, so pictures read back from it are upside down.
     */
    float[] clipMatrix(int width, int height, double depthRange) {
        double perBlockX = 2D * pixelsPerBlock / width;
        double perBlockY = 2D * pixelsPerBlock / height;
        return new float[]{
                (float) (perBlockX * cosYaw), (float) (-perBlockY * sinPitch * sinYaw), (float) (-cosPitch * sinYaw / depthRange), 0F,
                0F, (float) (perBlockY * cosPitch), (float) (-sinPitch / depthRange), 0F,
                (float) (-perBlockX * sinYaw), (float) (-perBlockY * sinPitch * cosYaw), (float) (-cosPitch * cosYaw / depthRange), 0F,
                (float) (2D * screenCenterX / width - 1D), (float) (1D - 2D * screenCenterY / height), 0F, 1F
        };
    }
}
