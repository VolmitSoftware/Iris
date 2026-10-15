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
import com.volmit.config.Desc;
import com.volmit.config.MinItems;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.ResourceRef;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("object-loot")
@Desc("Represents loot within this object")
public final class ObjectLoot implements ObjectLootEntry {
    @MinItems(1)
    @Desc("The list of blocks this loot table should apply to")
    private List<BlockSpec> filter = List.of();
    @Desc("Exactly match the block data or not")
    private boolean exact = false;
    @Desc("The loot table name")
    @Required
    @ResourceRef(LootTable.class)
    private String name;
    @Desc("The weight of this loot table being chosen")
    @MinNumber(1)
    private int weight = 1;

    public ObjectLoot() {
    }

    /** A table for every container of the placement. */
    public ObjectLoot(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int weight() {
        return weight;
    }

    @Override
    public boolean exact() {
        return exact;
    }

    @Override
    public List<BlockSpec> filter() {
        return filter == null ? List.of() : Collections.unmodifiableList(filter);
    }
}
