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

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacementAccess;
import com.volmit.world.BlockPosition;

import java.util.Optional;

/**
 * A placement stage's buffered world in Iris's block states: reads see the placement's earlier writes,
 * writes wait for the placement to commit.
 */
final class PlacementWorld {
    private final PlacementAccess access;
    private final PlatformBlockStates states;

    PlacementWorld(PlacementAccess access, PlatformBlockStates states) {
        this.access = access;
        this.states = states;
    }

    PlatformBlockState get(int x, int y, int z) {
        return states.toPlatform(access.get(x, y, z));
    }

    boolean isSolid(int x, int y, int z) {
        return B.isSolid(get(x, y, z));
    }

    PlacedBlock set(int x, int y, int z, PlatformBlockState state) {
        return set(x, y, z, state, null);
    }

    /** {@code tile} may be null for a block without tile data. */
    PlacedBlock set(int x, int y, int z, PlatformBlockState state, TileData tile) {
        PlacedBlock block = new PlacedBlock(new BlockPosition(x, y, z), states.toApi(state),
                Optional.ofNullable(tile).map(BlockEntities::of));
        access.set(block);
        return block;
    }
}
