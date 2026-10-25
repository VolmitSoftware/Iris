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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Endless noise terrain as the orbit viewport shows it: the tiles in sight as block columns, drawn by
 * {@code workerCount} of {@code workers} at once on machines without a graphics card. The camera's pivot is given
 * from {@code anchor}, and zoom sets pixels per block directly, so tiles coming and going never move the view.
 *
 * @param floorY     where sides on the edge of the sampled ground end
 * @param waterLevel the height water fills up to, or NaN when the noise has no water
 */
record NoiseTerrainScene(List<HoledLayer> layers, Vec3 anchor, int floorY, int topY, double waterLevel,
                         ExecutorService workers, int workerCount) implements OrbitScene {
    // Looks further down than the object viewer, so a landscape's shape reads before its sides.
    static final OrbitCamera START = new OrbitCamera(0.76D, 0.62D, 1D, Vec3.ZERO);
    private static final double VIEW_HEIGHT_BLOCKS_AT_ZOOM_ONE = 384D;
    // Looking level would see endless ground, and looking up only its underside.
    private static final double LOWEST_PITCH = 0.1D;
    private static final double HIGHEST_PITCH = 1.3D;
    private static final double TALLEST_VIEW_BLOCKS = 100_000D;
    private static final double SHORTEST_VIEW_BLOCKS = 24D;
    private static final OrbitLimits LIMITS = new OrbitLimits(LOWEST_PITCH, HIGHEST_PITCH,
            VIEW_HEIGHT_BLOCKS_AT_ZOOM_ONE / TALLEST_VIEW_BLOCKS, VIEW_HEIGHT_BLOCKS_AT_ZOOM_ONE / SHORTEST_VIEW_BLOCKS);

    static OrbitProjection projectionFrom(Vec3 anchor, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        int covered = Math.max(0, Math.min(width - 1, coveredLeftWidth));
        return OrbitProjection.atScale(anchor, camera, height / VIEW_HEIGHT_BLOCKS_AT_ZOOM_ONE, width, height, covered);
    }

    @Override
    public boolean isEmpty() {
        return layers.stream().allMatch(layer -> layer.terrain().isEmpty());
    }

    @Override
    public OrbitCamera startingCamera() {
        return START;
    }

    @Override
    public OrbitLimits limits() {
        return LIMITS;
    }

    @Override
    public OrbitPicture render(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return renderOnCard(camera, width, height, coveredLeftWidth)
                .orElseGet(() -> NoiseTerrainRenderer.render(this, camera, width, height, coveredLeftWidth));
    }

    private Optional<OrbitPicture> renderOnCard(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        if (!GpuLibrary.isLoaded()) {
            return Optional.empty();
        }
        return GpuTerrainRenderer.render(this, camera, width, height, coveredLeftWidth);
    }

    @Override
    public boolean canRenderOnScreen() {
        return GpuLibrary.isLoaded() && GpuFrames.canDrawOnScreen();
    }

    @Override
    public Optional<OrbitPicture> renderOnScreen(ScreenTarget screen, OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return GpuTerrainRenderer.renderOnScreen(this, screen, camera, width, height, coveredLeftWidth);
    }

    @Override
    public OrbitProjection projection(OrbitCamera camera, int width, int height, int coveredLeftWidth) {
        return projectionFrom(anchor, camera, width, height, coveredLeftWidth);
    }

    boolean hasWater() {
        return Double.isFinite(waterLevel) && waterLevel > floorY;
    }

    /**
     * Where the layers lie, from the floor to the highest ground, so water can spread under all of them.
     */
    Extent extent() {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double lowest = floorY;
        double highest = topY;
        for (HoledLayer layer : layers) {
            NoiseTerrain terrain = layer.terrain();
            minX = Math.min(minX, terrain.originX());
            minZ = Math.min(minZ, terrain.originZ());
            maxX = Math.max(maxX, terrain.originX() + (double) terrain.sizeX());
            maxZ = Math.max(maxZ, terrain.originZ() + (double) terrain.sizeZ());
            if (!terrain.isEmpty()) {
                lowest = Math.min(lowest, terrain.baseY());
                highest = Math.max(highest, BlockFaces.blockTop(terrain.highest()));
            }
        }
        return new Extent(minX, lowest, minZ, maxX, highest, maxZ);
    }

    record Extent(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double sizeX() {
            return maxX - minX;
        }

        double sizeZ() {
            return maxZ - minZ;
        }

        // The farthest any of it lies from a point, which the depth buffer has to reach.
        double farthestFrom(Vec3 point) {
            double x = Math.max(Math.abs(minX - point.x()), Math.abs(maxX - point.x()));
            double y = Math.max(Math.abs(minY - point.y()), Math.abs(maxY - point.y()));
            double z = Math.max(Math.abs(minZ - point.z()), Math.abs(maxZ - point.z()));
            return Math.sqrt(x * x + y * y + z * z);
        }
    }
}
