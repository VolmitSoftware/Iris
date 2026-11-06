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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.modules.placement.BlockTraits;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacementAccess;
import com.volmit.geometry.BlockPosition;

import java.util.Optional;

/** A placement stage's buffered world: reads see the placement's earlier writes, writes wait for the placement to commit. */
final class PlacementWorld {
    private final PlacementAccess access;
    private final BlockTraits traits;

    PlacementWorld(PlacementAccess access, BlockTraits traits) {
        this.access = access;
        this.traits = traits;
    }

    BlockState get(int x, int y, int z) {
        return access.get(x, y, z);
    }

    boolean has(BlockState state, BlockTrait trait) {
        return traits.has(state, trait);
    }

    boolean isSolid(int x, int y, int z) {
        return has(get(x, y, z), BlockTrait.SOLID);
    }

    void set(int x, int y, int z, BlockState state) {
        access.set(x, y, z, state);
    }

    /** A block entity needs the record; a plain block takes the write that allocates nothing. */
    void set(int x, int y, int z, BlockState state, Optional<BlockEntityData> blockEntity) {
        if (blockEntity.isEmpty()) {
            access.set(x, y, z, state);
            return;
        }
        access.set(new PlacedBlock(new BlockPosition(x, y, z), state, blockEntity));
    }
}
