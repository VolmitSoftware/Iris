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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.prefab.ScaledObjects;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.geometry.BlockBox;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.object.ScaleMethod;
import com.volmit.resource.PackResources;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * The pack's objects as Iris loads, scales and bores them: each a cached {@link IrisObject}. Scaled variants live in the
 * scale cache of the world generating, so scaling needs the pack's engine. Built objects are Iris objects of the pack.
 */
final class IrisPackObjects implements PackObjects {
    private static final File GOLDEN_DEBUG_SWITCH = new File("plugins/Iris/goldendebug.txt");

    private final IrisData data;
    private final IrisPackResources resources;

    IrisPackObjects(IrisData data, IrisPackResources resources) {
        this.data = data;
        this.resources = resources;
    }

    @Override
    public Optional<PlaceableObject> object(com.volmit.resource.ResourceId id) {
        return Optional.ofNullable(data.getObjectLoader().load(keyOf(id)));
    }

    @Override
    public Optional<BlockBox> sizeOf(com.volmit.resource.ResourceId id) {
        File file = data.getObjectLoader().findFile(keyOf(id));
        if (file == null) {
            return Optional.empty();
        }
        try {
            var size = IrisObject.sampleSize(file);
            return Optional.of(new BlockBox(0, 0, 0, size.getBlockX(), size.getBlockY(), size.getBlockZ()));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    @Override
    public PlaceableObject scaled(PlaceableObject object, double factor, ScaleMethod method) {
        Engine engine = data.getEngine();
        if (engine == null) {
            throw new IllegalStateException("Scaling " + object.name() + " needs the world the pack generates, and it has none");
        }
        return ScaledObjects.scaled(irisObject(object), factor, method, engine);
    }

    @Override
    public PlaceableObject smartBored(PlaceableObject object) {
        IrisObject bored = irisObject(object);
        IrisObjectShaping.ensureSmartBored(bored);
        return bored;
    }

    @Override
    public PlaceableObject build(String name, int width, int height, int depth, List<ObjectBlock> blocks) {
        PlatformBlockStates states = new PlatformBlockStates(B::getState);
        IrisObject object = new IrisObject(width, height, depth);
        for (ObjectBlock block : blocks) {
            object.getBlocks().put(new IrisBlockVector(block.position().x(), block.position().y(), block.position().z()),
                    states.toPlatform(block.state()));
        }
        object.setLoadKey(name);
        object.setLoader(data);
        logGoldenDebugDigest(object);
        return object;
    }

    private String keyOf(com.volmit.resource.ResourceId id) {
        return resources.keyOf(PackResources.OBJECTS, id);
    }

    private static IrisObject irisObject(PlaceableObject object) {
        if (object instanceof IrisObject iris) {
            return iris;
        }
        throw new IllegalArgumentException("Iris scales and bores the objects of its packs and of its builder only, not " + object.name());
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
