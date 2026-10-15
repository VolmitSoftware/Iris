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
package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.object.ObjectBlock;

import java.io.File;
import java.util.List;

/**
 * Builds a module's objects as Iris objects of the pack. Every state it hands out comes from the platform through one
 * {@link PlatformBlockStates}, so setting a property on it or building it gives back the platform's own state: a content
 * plugin's block keeps its identity, as it did when the generators held platform states themselves.
 */
public final class IrisObjectBuilder implements ObjectBuilder {
    private static final File GOLDEN_DEBUG_SWITCH = new File("plugins/Iris/goldendebug.txt");

    private final IrisData data;
    private final PlatformBlockStates states = new PlatformBlockStates(B::getState);

    public IrisObjectBuilder(IrisData data) {
        this.data = data;
    }

    @Override
    public BlockState block(String key) {
        PlatformBlockState state = B.getStateOrNull(key, false);
        return state == null ? null : states.toApi(state);
    }

    @Override
    public BlockState pick(PaletteSpec palette, long seed, int x, int y, int z) {
        return states.toApi(B.getState(data.getPackResources().blocks().palette(palette, seed).at(x, y, z).key()));
    }

    @Override
    public BlockState withProperty(BlockState state, String name, String value) {
        return states.toApi(states.toPlatform(state).withProperty(name, value));
    }

    @Override
    public PlaceableObject build(String loadKey, int width, int height, int depth, List<ObjectBlock> blocks) {
        IrisObject object = new IrisObject(width, height, depth);
        for (ObjectBlock block : blocks) {
            object.getBlocks().put(new IrisBlockVector(block.position().x(), block.position().y(), block.position().z()),
                    states.toPlatform(block.state()));
        }
        object.setLoadKey(loadKey);
        object.setLoader(data);
        logGoldenDebugDigest(object);
        return object;
    }

    private static void logGoldenDebugDigest(IrisObject object) {
        if (!GOLDEN_DEBUG_SWITCH.isFile()) {
            return;
        }
        long[] digest = new long[]{0L};
        object.getBlocks().forEach((vector, state) -> {
            long position = ((long) vector.getBlockX() << 40) ^ ((long) vector.getBlockY() << 20) ^ vector.getBlockZ();
            digest[0] ^= Long.rotateLeft(position * 0x9E3779B97F4A7C15L ^ state.key().hashCode(), (int) (position & 63));
        });
        IrisLogging.debug("Goldendebug bake: " + object.getLoadKey() + " blocks=" + object.getBlocks().size() + " digest=" + Long.toHexString(digest[0]));
    }
}
