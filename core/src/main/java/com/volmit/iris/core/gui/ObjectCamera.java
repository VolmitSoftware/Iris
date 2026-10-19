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

package com.volmit.iris.core.gui;

/**
 * Where the object viewer looks from: turned by yaw around the vertical axis, tilted down by pitch, both in radians.
 * Zoom 1 fits the object in the view at the starting angle. Pan moves the picture in fractions of the view's size.
 */
record ObjectCamera(double yaw, double pitch, double zoom, double panX, double panY) {
    static final ObjectCamera START = new ObjectCamera(0.76D, 0.38D, 1.72D, 0D, 0.025D);
    private static final double RADIANS_PER_DRAGGED_PIXEL = 0.008D;
    private static final double MAXIMUM_PITCH = 1.3D;
    private static final double MINIMUM_ZOOM = 0.4D;
    private static final double MAXIMUM_ZOOM = 12D;

    ObjectCamera orbitedBy(double draggedX, double draggedY) {
        double nextPitch = Math.max(-MAXIMUM_PITCH, Math.min(MAXIMUM_PITCH, pitch + draggedY * RADIANS_PER_DRAGGED_PIXEL));
        return new ObjectCamera(yaw + draggedX * RADIANS_PER_DRAGGED_PIXEL, nextPitch, zoom, panX, panY);
    }

    ObjectCamera zoomedBy(double factor) {
        return new ObjectCamera(yaw, pitch, Math.max(MINIMUM_ZOOM, Math.min(MAXIMUM_ZOOM, zoom * factor)), panX, panY);
    }

    ObjectCamera pannedBy(double fractionX, double fractionY) {
        return new ObjectCamera(yaw, pitch, zoom, panX + fractionX, panY + fractionY);
    }
}
