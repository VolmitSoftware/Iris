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

import art.arcane.iris.engine.object.prefab.TileData;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Iris's tile data as IrisApi block entity payloads, in the binary form Iris objects store them in. Iris
 * reads no other format, so a payload of another format or version fails rather than being dropped.
 */
public final class BlockEntities {
    public static final ResourceId TILE_FORMAT = ResourceId.parse("iris:tile");
    public static final int TILE_FORMAT_VERSION = 1;

    private BlockEntities() {
    }

    public static BlockEntityData of(TileData tile) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            tile.toBinary(out);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot store the tile data of " + tile.getMaterialKey(), failure);
        }
        return new BlockEntityData(TILE_FORMAT, TILE_FORMAT_VERSION, bytes.toByteArray());
    }

    public static TileData tileOf(BlockEntityData entity) {
        if (!entity.format().equals(TILE_FORMAT) || entity.version() != TILE_FORMAT_VERSION) {
            throw new IllegalArgumentException("Iris stores block entities of format " + TILE_FORMAT + " version "
                    + TILE_FORMAT_VERSION + " only, not " + entity.format() + " version " + entity.version());
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(entity.payload()))) {
            return TileData.read(in);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read a block entity of format " + TILE_FORMAT, failure);
        }
    }
}
