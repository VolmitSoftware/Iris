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
 * Where the 3D viewers look from: turned by yaw around the vertical axis, tilted down by pitch, both in radians, around
 * a pivot drawn in the middle of the view. The pivot is given in blocks from the middle of the scene. Zoom 1 fits the
 * scene in the view at the starting angle.
 */
record OrbitCamera(double yaw, double pitch, double zoom, Vec3 pivot) {
    static final OrbitCamera START = new OrbitCamera(0.76D, 0.38D, 1.72D, Vec3.ZERO);
    private static final double RADIANS_PER_DRAGGED_PIXEL = 0.008D;

    OrbitCamera orbitedBy(double draggedX, double draggedY, OrbitLimits limits) {
        double nextPitch = limits.pitchWithin(pitch + draggedY * RADIANS_PER_DRAGGED_PIXEL);
        return new OrbitCamera(yaw + draggedX * RADIANS_PER_DRAGGED_PIXEL, nextPitch, zoom, pivot);
    }

    /**
     * @param towardFixedPoint how far from the pivot, across the view, the point that stays in place lies, like the one
     *                         under the cursor
     */
    OrbitCamera zoomedBy(double factor, OrbitLimits limits, Vec3 towardFixedPoint) {
        double nextZoom = limits.zoomWithin(zoom * factor);
        double appliedFactor = nextZoom / zoom;
        return new OrbitCamera(yaw, pitch, nextZoom, pivot.plus(towardFixedPoint.times(1D - 1D / appliedFactor)));
    }

    OrbitCamera pannedBy(Vec3 blocks) {
        return new OrbitCamera(yaw, pitch, zoom, pivot.plus(blocks));
    }
}
