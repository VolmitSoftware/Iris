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
package art.arcane.iris.builtin.objects;

import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.object.ObjectBlock;

import java.util.List;

/**
 * Builds objects block by block for a module, with blocks as the platform has them: a state the builder handed out
 * keeps what the platform knows about it when a property is set or the object is built, so a content plugin's block
 * stays itself. One builder serves one bake on one thread.
 */
public interface ObjectBuilder {
    /** The platform's state for {@code key}; null when the platform has no such block. */
    BlockState block(String key);

    /** The block {@code palette} gives at the position when its noise is seeded with {@code seed}. */
    BlockState pick(PaletteSpec palette, long seed, int x, int y, int z);

    /** {@code state} with the property set, as the platform sets it. */
    BlockState withProperty(BlockState state, String name, String value);

    /** The object of {@code blocks}, put in their order, {@code width} by {@code height} by {@code depth}, placeable like the pack's objects. */
    PlaceableObject build(String loadKey, int width, int height, int depth, List<ObjectBlock> blocks);
}
