/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
package com.volmit.iris.engine.object.procedural;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisMaterialPalette;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.Vector3i;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.List;

/**
 * Turns the blocks a procedural generator asks for into the pack's block data, and its shapes into objects. One builder
 * serves one bake on one thread.
 */
public final class ObjectBuilder {
    private final IrisData data;
    private final KMap<String, BlockData> withPropertyCache = new KMap<>();

    public ObjectBuilder(IrisData data) {
        this.data = data;
    }

    /** The block {@code key} names; null when there is no such block. */
    public BlockData block(String key) {
        return B.getOrNull(key, false);
    }

    /** The block {@code palette} gives at the position when its noise is seeded with {@code seed}. */
    public BlockData pick(IrisMaterialPalette palette, long seed, int x, int y, int z) {
        return palette.get(new RNG(seed), x, y, z, data);
    }

    /** {@code state} with the property set; {@code state} itself when the block has no such property or value. */
    public BlockData withProperty(BlockData state, String name, String value) {
        String key = state.getAsString() + "|" + name + "=" + value;
        return withPropertyCache.computeIfAbsent(key, ignored -> merged(state, name, value)).clone();
    }

    private static BlockData merged(BlockData state, String name, String value) {
        try {
            return state.merge(Bukkit.createBlockData(state.getMaterial(), "[" + name + "=" + value + "]"));
        } catch (IllegalArgumentException unknownProperty) {
            return state;
        }
    }

    /** The object of {@code blocks}, {@code width} by {@code height} by {@code depth}, loaded under {@code loadKey}. */
    public IrisObject build(String loadKey, int width, int height, int depth, List<ObjectBlock> blocks) {
        IrisObject object = new IrisObject(width, height, depth);
        for (ObjectBlock block : blocks) {
            BlockPosition position = block.position();
            object.getBlocks().put(new Vector3i(position.x(), position.y(), position.z()), block.state());
        }
        object.setLoadKey(loadKey);
        object.setLoader(data);
        return object;
    }
}
