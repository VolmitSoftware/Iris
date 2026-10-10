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
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

@Snippet("block-drops")
@Desc("Represents a block drop list")
public final class BlockDrop {
    @Required
    @MinItems(1)
    @Desc("The blocks that drop loot")
    private List<BlockSpec> blocks = List.of();

    @Desc("If exact blocks is set to true, minecraft:barrel[axis=x] will only drop for that axis. When exact is false (default) any barrel will drop the defined drops.")
    private boolean exactBlocks = false;

    @MinItems(1)
    @Desc("Add in specific items to drop")
    private List<LootEntry> drops = List.of();

    @Desc("If this is in a biome, setting skipParents to true will ignore the drops in the region and dimension for this block type. The default (false) will allow all three nodes to fire and add to a list of drops.")
    private boolean skipParents = false;

    @Desc("Removes the default vanilla block drops and only drops the given items & any parent loot tables specified for this block type.")
    private boolean replaceVanillaDrops = false;

    public BlockDrop() {
    }

    public List<BlockSpec> blocks() {
        return blocks == null ? List.of() : Collections.unmodifiableList(blocks);
    }

    public boolean exactBlocks() {
        return exactBlocks;
    }

    public List<LootEntry> drops() {
        return drops == null ? List.of() : Collections.unmodifiableList(drops);
    }

    public boolean skipParents() {
        return skipParents;
    }

    public boolean replaceVanillaDrops() {
        return replaceVanillaDrops;
    }
}
