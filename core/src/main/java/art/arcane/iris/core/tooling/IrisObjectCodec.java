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

package art.arcane.iris.core.tooling;

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.core.modules.ObjectGeometry;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.geometry.BlockBox;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.tooling.ObjectCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** The {@code .iob} format of Iris's {@link IrisObject}: the format the host reads below a pack's {@code objects} folder. */
public final class IrisObjectCodec implements ObjectCodec {
    private static final String EXTENSION = ".iob";

    @Override
    public String extension() {
        return EXTENSION;
    }

    @Override
    public byte[] encode(GeneratedObject object) throws IOException {
        if (!object.markers().isEmpty()) {
            throw new IOException("The " + EXTENSION + " format holds no markers, and the object has " + object.markers().size());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        irisObjectOf(object).write(bytes);
        return bytes.toByteArray();
    }

    @Override
    public GeneratedObject decode(byte[] bytes) throws IOException {
        IrisObject object = new IrisObject();
        try {
            object.read(new ByteArrayInputStream(bytes));
        } catch (IOException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IOException("Cannot read an " + EXTENSION + " object: " + failure.getMessage(), failure);
        }
        return ObjectGeometry.of(object);
    }

    /** Iris keys an object's blocks by their offset from its center, so the local positions are the keys. */
    private static IrisObject irisObjectOf(GeneratedObject object) {
        BlockBox bounds = object.bounds();
        IrisObject irisObject = new IrisObject(
                bounds.maxX() - bounds.minX(), bounds.maxY() - bounds.minY(), bounds.maxZ() - bounds.minZ());
        PlatformBlockStates states = new PlatformBlockStates(B::getState);
        for (ObjectBlock block : object.blocks()) {
            IrisBlockVector offset = new IrisBlockVector(block.position().x(), block.position().y(), block.position().z());
            irisObject.blocks.put(offset, states.toPlatform(block.state()));
            block.blockEntity().ifPresent(entity -> irisObject.states.put(offset.clone(), BlockEntities.tileOf(entity)));
        }
        return irisObject;
    }
}
