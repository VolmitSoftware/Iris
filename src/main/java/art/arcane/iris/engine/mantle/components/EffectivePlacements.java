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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.PlacementChanges;
import art.arcane.iris.builtin.objects.PlacementSource;

import java.util.List;
import java.util.Locale;

/** How a dimension and a cave profile change the placement an object is placed with. */
final class EffectivePlacements {
    private static final String IMPORTS_FOLDER = "imports/";

    private final boolean dimensionRequiresSurfaceSupport;
    private final int dimensionSurfaceSupportBuffer;

    EffectivePlacements(boolean dimensionRequiresSurfaceSupport, int dimensionSurfaceSupportBuffer) {
        this.dimensionRequiresSurfaceSupport = dimensionRequiresSurfaceSupport;
        this.dimensionSurfaceSupportBuffer = dimensionSurfaceSupportBuffer;
    }

    /** The changes to make to {@code placement} to place {@code object} on the surface. */
    PlacementChanges forSurface(PlacementSource placement, PlaceableObject object) {
        return withDimensionSurfaceSupport(placement, withImportedMode(placement, object, PlacementChanges.NONE));
    }

    /** The changes to make to {@code placement} to place {@code object} in a cave with the cave profile {@code caveProfile}. */
    PlacementChanges forCave(PlacementSource placement, PlaceableObject object, CaveProfile caveProfile) {
        PlacementChanges changes = forSurface(placement, object);
        ObjectPlaceMode mode = changes.mode() != null ? changes.mode() : placement.mode();
        if (mode != ObjectPlaceMode.CENTER_HEIGHT || caveProfile == null) {
            return changes;
        }
        ObjectPlaceMode profileMode = caveProfile.defaultObjectPlaceMode().orElse(null);
        if (profileMode == null) {
            return changes;
        }
        String key = object.key();
        List<String> place = key == null || key.isBlank() ? List.of() : List.of(key);
        return changes.withPlace(place).withMode(profileMode);
    }

    /** Imported objects are placed with the fast minimal stilt unless their mode finds its own height. */
    private static PlacementChanges withImportedMode(PlacementSource placement, PlaceableObject object, PlacementChanges changes) {
        String key = object.key();
        if (key == null || key.isBlank() || !isImported(key)) {
            return changes;
        }
        ObjectPlaceMode mode = placement.mode();
        if (mode.placeMode().has(PlaceTrait.FREE_HEIGHT) || mode == ObjectPlaceMode.FAST_MIN_STILT) {
            return changes;
        }
        return changes.withPlace(List.of(key)).withMode(ObjectPlaceMode.FAST_MIN_STILT);
    }

    private static boolean isImported(String key) {
        return key.toLowerCase(Locale.ROOT).contains(IMPORTS_FOLDER);
    }

    /** The dimension acts as the floor: it can force support on and widen the buffer, never weaken either. */
    private PlacementChanges withDimensionSurfaceSupport(PlacementSource placement, PlacementChanges changes) {
        boolean require = placement.requireSurfaceSupport() && dimensionRequiresSurfaceSupport;
        int buffer = Math.max(placement.surfaceSupportBuffer(), dimensionSurfaceSupportBuffer);
        if (require == placement.requireSurfaceSupport() && buffer == placement.surfaceSupportBuffer()) {
            return changes;
        }
        return changes.withSurfaceSupport(require, buffer);
    }
}
