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
 * How far a scene lets the orbit camera tilt and zoom. Pitch is in radians, positive looking down.
 */
record OrbitLimits(double lowestPitch, double highestPitch, double leastZoom, double mostZoom) {
    static final OrbitLimits DEFAULT = new OrbitLimits(-1.3D, 1.3D, 0.4D, 12D);

    double pitchWithin(double pitch) {
        return Math.max(lowestPitch, Math.min(highestPitch, pitch));
    }

    double zoomWithin(double zoom) {
        return Math.max(leastZoom, Math.min(mostZoom, zoom));
    }
}
