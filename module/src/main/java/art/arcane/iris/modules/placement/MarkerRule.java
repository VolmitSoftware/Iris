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

package art.arcane.iris.modules.placement;

import com.volmit.block.BlockState;

import java.util.List;

/**
 * One marker of a placement, an entry of its {@code markers}, with its blocks resolved in the pack.
 *
 * @param marker  the pack's marker to add
 * @param targets the blocks to mark
 * @param exact   whether a block must match a target exactly; otherwise its material is enough
 * @param maximum how many blocks to mark at most
 */
public record MarkerRule(String marker, List<BlockState> targets, boolean exact, int maximum) {
    public MarkerRule {
        targets = List.copyOf(targets);
    }
}
