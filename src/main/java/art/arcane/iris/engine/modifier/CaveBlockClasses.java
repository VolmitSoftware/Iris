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

package art.arcane.iris.engine.modifier;

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What the cave passes of one chunk ask about blocks, answered from memory. A chunk holds few distinct states and a cave pass asks
 * about them per block, so each state instance (the hunk hands out one per distinct state) is classified once.
 */
final class CaveBlockClasses {
    private static final int SOLID = 1;
    private static final int FLUID = 2;
    private static final int AIR = 4;
    private static final int ORE = 8;
    private static final int DECORATION = 16;
    private static final int GRAVITY = 32;

    private final BlockRegistry blocks;
    private final Map<BlockState, Integer> classes = new IdentityHashMap<>();

    CaveBlockClasses(BlockRegistry blocks) {
        this.blocks = blocks;
    }

    boolean isSolid(BlockState state) {
        return (classesOf(state) & SOLID) != 0;
    }

    boolean isFluid(BlockState state) {
        return (classesOf(state) & FLUID) != 0;
    }

    boolean isAir(BlockState state) {
        return (classesOf(state) & AIR) != 0;
    }

    boolean isOre(BlockState state) {
        return (classesOf(state) & ORE) != 0;
    }

    boolean isDecorant(BlockState state) {
        return (classesOf(state) & DECORATION) != 0;
    }

    boolean hasGravity(BlockState state) {
        return state != null && (classesOf(state) & GRAVITY) != 0;
    }

    private int classesOf(BlockState state) {
        Integer known = classes.get(state);
        if (known != null) {
            return known;
        }
        int computed = classify(state);
        classes.put(state, computed);
        return computed;
    }

    private int classify(BlockState state) {
        return flag(state, BlockTrait.SOLID, SOLID) | flag(state, BlockTrait.FLUID, FLUID) | flag(state, BlockTrait.AIR, AIR)
                | flag(state, BlockTrait.ORE, ORE) | flag(state, BlockTrait.DECORATION, DECORATION) | flag(state, BlockTrait.GRAVITY, GRAVITY);
    }

    private int flag(BlockState state, BlockTrait trait, int bit) {
        return blocks.hasTrait(state, trait) ? bit : 0;
    }
}
