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

package art.arcane.iris.modules.objects;

import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;

import java.util.List;

/**
 * Builds objects block by block for the procedural objects, with blocks as the platform has them: the registry keeps a
 * content plugin's block itself when a property is set on it or the object is built ({@link BlockRegistry}).
 */
public final class ObjectBuilder {
    private final BlockRegistry blocks;
    private final PackObjects objects;

    public ObjectBuilder(PackResources resources) {
        this.blocks = resources.blocks();
        this.objects = resources.objects();
    }

    /** The platform's state for {@code key}; null when the platform has no such block. */
    public BlockState block(String key) {
        return blocks.find(key).orElse(null);
    }

    /** The block {@code palette} gives at the position when its noise is seeded with {@code seed}. */
    public BlockState pick(PaletteSpec palette, long seed, int x, int y, int z) {
        return blocks.palette(palette, seed).at(x, y, z);
    }

    /** {@code state} with the property set, as the platform sets it. */
    public BlockState withProperty(BlockState state, String name, String value) {
        return blocks.withProperty(state, name, value);
    }

    /** The object of {@code blocks}, put in their order, {@code width} by {@code height} by {@code depth}, placeable like the pack's objects. */
    public PlaceableObject build(String name, int width, int height, int depth, List<ObjectBlock> blocks) {
        return objects.build(name, width, height, depth, blocks);
    }
}
