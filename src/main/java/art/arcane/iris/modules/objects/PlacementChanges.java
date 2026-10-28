/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.objects;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;

import java.util.List;

/**
 * How a placement differs from the settings it was read from, when {@code iris:objects} places it: a field that is
 * null is left as the settings have it.
 *
 * @param place the objects the placement names, replacing the settings'
 * @param mode the place mode
 * @param upsideDown whether the object is turned over, for the objects hung from the terrain stacked above
 * @param carvingSupport where the placement may place
 * @param requireSurfaceSupport whether a surface placement needs solid ground under it
 * @param surfaceSupportBuffer the buffer of solid ground around the object's base
 * @param forcePlace whether the placement ignores the placement restrictions
 */
public record PlacementChanges(List<String> place, ObjectPlaceMode mode, Boolean upsideDown, CarvingMode carvingSupport,
                               Boolean requireSurfaceSupport, Integer surfaceSupportBuffer, Boolean forcePlace) {
    public static final PlacementChanges NONE = new PlacementChanges(null, null, null, null, null, null, null);

    public boolean isNone() {
        return this.equals(NONE);
    }

    public PlacementChanges withPlace(List<String> place) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, requireSurfaceSupport, surfaceSupportBuffer, forcePlace);
    }

    public PlacementChanges withMode(ObjectPlaceMode mode) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, requireSurfaceSupport, surfaceSupportBuffer, forcePlace);
    }

    public PlacementChanges withUpsideDown(boolean upsideDown) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, requireSurfaceSupport, surfaceSupportBuffer, forcePlace);
    }

    public PlacementChanges withCarvingSupport(CarvingMode carvingSupport) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, requireSurfaceSupport, surfaceSupportBuffer, forcePlace);
    }

    public PlacementChanges withSurfaceSupport(boolean required, int buffer) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, required, buffer, forcePlace);
    }

    public PlacementChanges withForcePlace(boolean forcePlace) {
        return new PlacementChanges(place, mode, upsideDown, carvingSupport, requireSurfaceSupport, surfaceSupportBuffer, forcePlace);
    }
}
