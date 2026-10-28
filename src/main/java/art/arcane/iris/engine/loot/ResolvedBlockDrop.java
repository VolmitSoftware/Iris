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

package art.arcane.iris.engine.loot;

import art.arcane.iris.modules.loot.BlockDrop;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/** A {@link BlockDrop} with the blocks it names resolved to the platform's: which broken blocks it drops for. */
final class ResolvedBlockDrop {
    private static final char PROPERTIES_START = '[';

    private final BlockDrop drop;
    private final boolean exact;
    private final Set<String> blocks;

    private ResolvedBlockDrop(BlockDrop drop, Set<String> blocks) {
        this.drop = drop;
        this.exact = drop.exactBlocks();
        this.blocks = blocks;
    }

    /** @param blocks the pack's blocks, which the drop's block specs resolve against */
    static ResolvedBlockDrop of(BlockDrop drop, BlockRegistry blocks) {
        Set<String> keys = new LinkedHashSet<>();
        for (BlockSpec spec : drop.blocks()) {
            keys.add(comparable(blocks.resolve(spec).key(), drop.exactBlocks()));
        }
        return new ResolvedBlockDrop(drop, keys);
    }

    BlockDrop drop() {
        return drop;
    }

    /** Whether the drop applies to {@code brokenKey}, the platform's key for the broken block: its exact state, or just its kind of block. */
    boolean dropsFor(String brokenKey) {
        return blocks.contains(comparable(brokenKey, exact));
    }

    private static String comparable(String key, boolean exact) {
        if (exact) {
            return key;
        }
        int properties = key.indexOf(PROPERTIES_START);
        return properties < 0 ? key : key.substring(0, properties);
    }
}
