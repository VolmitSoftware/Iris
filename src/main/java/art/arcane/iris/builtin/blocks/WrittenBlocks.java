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

import com.volmit.placement.PlacedBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The object's own blocks a placement wrote, in world positions, in the order they were written. */
public final class WrittenBlocks {
    private final List<PlacedBlock> blocks = new ArrayList<>();

    public void add(PlacedBlock block) {
        blocks.add(block);
    }

    public List<PlacedBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
