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

package art.arcane.iris.builtin.loot;

import com.volmit.block.BlockSpec;

import java.util.List;

/**
 * One loot table an object placement gives its containers: which table, how often it is picked among the placement's
 * tables, and which of the object's containers it is for.
 */
public interface ObjectLootEntry {
    /** The table: a key of the pack's loot tables, or a key of the platform's. */
    String name();

    /** How often the table is picked among equals; a table with no weight is never picked. */
    int weight();

    /** Whether the table applies to the exact state of the blocks in {@link #filter}, not to every state of their kind of block. */
    boolean exact();

    /** The blocks the table is for; none means every container the placement holds. */
    List<BlockSpec> filter();
}
