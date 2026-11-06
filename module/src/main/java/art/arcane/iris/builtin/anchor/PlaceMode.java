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
package art.arcane.iris.builtin.anchor;

import java.util.Set;

/**
 * What an object place mode does, as data the placement stages ask instead of comparing modes: how it finds its
 * height, the stilt it grows, how it bends the terrain around the object, and its traits.
 */
public record PlaceMode(AnchorSampling anchor, StiltShape stilt, VacuumProfile vacuum, Set<PlaceTrait> traits) {
    public PlaceMode {
        traits = Set.copyOf(traits);
    }

    public static PlaceMode of(AnchorSampling anchor, StiltShape stilt, PlaceTrait... traits) {
        return new PlaceMode(anchor, stilt, VacuumProfile.NONE, Set.of(traits));
    }

    public static PlaceMode vacuum(VacuumProfile vacuum) {
        return new PlaceMode(AnchorSampling.CENTER, StiltShape.NONE, vacuum, Set.of());
    }

    public boolean has(PlaceTrait trait) {
        return traits.contains(trait);
    }

    /** Whether the mode bends the terrain to meet the object's base. */
    public boolean bendsTerrain() {
        return vacuum != VacuumProfile.NONE;
    }
}
