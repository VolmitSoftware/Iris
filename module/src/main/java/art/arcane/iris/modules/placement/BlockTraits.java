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

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The traits of the block states one placement handles, asked of the block registry once per state instance. A placement
 * reads the same few states over and over and the platform looks every state up by its value, so the answers are kept by
 * identity. For one placement on one thread.
 */
public final class BlockTraits {
    private final BlockRegistry blocks;
    private final Map<BlockState, TraitAnswers> answers = new IdentityHashMap<>();

    public BlockTraits(BlockRegistry blocks) {
        this.blocks = blocks;
    }

    public boolean has(BlockState state, BlockTrait trait) {
        return answers.computeIfAbsent(state, ignored -> new TraitAnswers()).has(blocks, state, trait);
    }

    private static final class TraitAnswers {
        private int asked;
        private int held;

        boolean has(BlockRegistry blocks, BlockState state, BlockTrait trait) {
            int flag = 1 << trait.ordinal();
            if ((asked & flag) == 0) {
                if (blocks.hasTrait(state, trait)) {
                    held |= flag;
                }
                asked |= flag;
            }
            return (held & flag) != 0;
        }
    }
}
