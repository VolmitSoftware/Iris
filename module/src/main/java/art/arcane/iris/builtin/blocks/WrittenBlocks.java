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

package art.arcane.iris.builtin.blocks;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The object's own blocks a placement wrote, in world positions, in the order they were written. Positions and states
 * are kept side by side, so recording a block allocates nothing beyond the position the placement already has.
 */
public final class WrittenBlocks {
    private final List<BlockPosition> positions = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();

    public void add(BlockPosition position, BlockState state) {
        positions.add(position);
        states.add(state);
    }

    public List<BlockPosition> positions() {
        return Collections.unmodifiableList(positions);
    }

    /** The written states, each at the index of its position in {@link #positions()}. */
    public List<BlockState> states() {
        return Collections.unmodifiableList(states);
    }

    /** Each written block's position and state, in the order they were written. */
    public void forEach(BiConsumer<BlockPosition, BlockState> action) {
        for (int index = 0; index < positions.size(); index++) {
            action.accept(positions.get(index), states.get(index));
        }
    }

    public boolean isEmpty() {
        return positions.isEmpty();
    }
}
