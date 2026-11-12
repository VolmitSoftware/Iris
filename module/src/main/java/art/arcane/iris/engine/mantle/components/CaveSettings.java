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

import art.arcane.iris.builtin.anchor.PlaceTrait;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;

/** How a cave object placement looks for its anchor. A placement sets its own anchor mode; nothing sets one for all caves. */
final class CaveSettings {
    private static final int SCAN_STEP_BLOCKS = 1;
    private static final int MIN_DEPTH_BELOW_SURFACE_BLOCKS = 6;
    private static final int SEARCH_ATTEMPTS = 6;

    private CaveSettings() {
    }

    /** The placement's own anchor mode, else the floor; turned upside down, the ceiling. */
    static CaveAnchors.Search searchFor(ObjectPlacement placement) {
        CaveAnchorMode mode = anchorMode(placement);
        if (placement.mode().placeMode().has(PlaceTrait.UPSIDE_DOWN)) {
            mode = CaveAnchorMode.CEILING;
        }
        return new CaveAnchors.Search(mode, SCAN_STEP_BLOCKS, MIN_DEPTH_BELOW_SURFACE_BLOCKS, SEARCH_ATTEMPTS);
    }

    private static CaveAnchorMode anchorMode(ObjectPlacement placement) {
        CaveAnchorMode placementMode = placement.caveAnchorMode();
        if (placementMode == null || placementMode.equals(CaveAnchorMode.PROFILE_DEFAULT)) {
            return CaveAnchorMode.FLOOR;
        }
        return placementMode;
    }
}
