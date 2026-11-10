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

package art.arcane.iris.engine.framework.structure.assembly;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;

import java.util.List;
import java.util.Objects;

/**
 * How the pieces of an assembled structure are stamped into the world.
 *
 * @param placeMode     the place mode each piece is stamped with
 * @param edits         the find-and-replace block edits applied to every piece
 * @param loot          the loot tables the containers of the pieces draw from, blank names left out
 * @param vanillaSource the platform structure this structure stands in for, blank for a hand-authored one
 */
public record StructureStamp(ObjectPlaceMode placeMode, List<ObjectReplace> edits, List<String> loot, String vanillaSource) {
    public StructureStamp {
        Objects.requireNonNull(placeMode, "placeMode");
        edits = List.copyOf(edits);
        loot = List.copyOf(loot);
        Objects.requireNonNull(vanillaSource, "vanillaSource");
    }
}
