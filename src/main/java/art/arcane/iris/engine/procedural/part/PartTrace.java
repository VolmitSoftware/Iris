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
package art.arcane.iris.engine.procedural.part;

import java.util.Map;

/**
 * Which part placed each block of one baked variant, by the block's position in the variant's object. Decorations
 * belong to no part.
 */
public final class PartTrace {
    public static final PartTrace EMPTY = new PartTrace(Map.of());

    private final Map<Long, Integer> partsByBlock;

    PartTrace(Map<Long, Integer> partsByBlock) {
        this.partsByBlock = partsByBlock;
    }

    /** The {@link PartOutline} id of the part that placed the block, or {@link PartOutline#NO_PART}. */
    public int partAt(int x, int y, int z) {
        return partsByBlock.getOrDefault(PartCanvas.key(x, y, z), PartOutline.NO_PART);
    }
}
