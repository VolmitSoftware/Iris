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

package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

/** Builds objects block by block for the procedural objects, with blocks as the platform has them. */
public final class ObjectBuilder {
    private final IrisData data;
    private final PackRuntime runtime;
    private final Map<Long, RNG> paletteRngs = new HashMap<>();

    public ObjectBuilder(IrisData data) {
        this.data = data;
        this.runtime = PackRuntime.forPack(data);
    }

    /** The platform's state for {@code key}; null when the platform has no such block. */
    public NativeBlockState block(String key) {
        return B.getStateOrNull(key, false);
    }

    /** The block {@code palette} gives at the position when its noise is seeded with {@code seed}. */
    public NativeBlockState pick(IrisMaterialPalette palette, long seed, int x, int y, int z) {
        return palette.get(paletteRngs.computeIfAbsent(seed, RNG::new), x, y, z, runtime);
    }

    public boolean isAir(NativeBlockState state) {
        return B.isAir(state);
    }

    /** Whether {@code state} is wood: a block of a tree that is not a leaf. */
    public boolean isLog(NativeBlockState state) {
        return state.isTreeBlock() && !ProceduralBlocks.hasProperty(state, "distance");
    }

    /** {@code state} with the property set, as the platform sets it; {@code state} itself when the block does not accept it. */
    public NativeBlockState withPropertyIfAccepted(NativeBlockState state, String name, String value) {
        try {
            return state.withProperty(name, value);
        } catch (IllegalArgumentException notAccepted) {
            return state;
        }
    }

    /** The object of {@code blocks}, put in their order, {@code width} by {@code height} by {@code depth}. */
    public IrisObject build(String name, ProceduralShape shape) {
        IrisObject object = new IrisObject(shape.width(), shape.height(), shape.depth());
        object.setLoadKey(name);
        object.setLoader(data);
        for (ProceduralBlock block : shape.blocks()) {
            object.getBlocks().put(new IrisBlockVector(block.x(), block.y(), block.z()), block.state());
        }
        return object;
    }
}
