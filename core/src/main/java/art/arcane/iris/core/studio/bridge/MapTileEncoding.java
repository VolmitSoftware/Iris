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

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.studio.map.MapLabel;
import art.arcane.iris.core.studio.map.MapTile;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A {@link MapTile} as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i16 subjects[grid²] | padding to 4 | i16 regions[grid²] | padding to 4
 * f32 heights[grid²], when the header says so | f32 pickNoise[grid²], when the header says so
 * </pre>
 * The header holds the size, the scale, the subject kind and the labels the grids index. Every array starts on a
 * multiple of 4 bytes, so the studio views them in place.
 */
final class MapTileEncoding {
    private static final int ALIGNMENT_BYTES = 4;

    private MapTileEncoding() {
    }

    static byte[] encode(MapTile tile, Gson gson) {
        boolean hasHeights = tile.heights() != null;
        boolean hasPickNoise = tile.pickNoise() != null;
        byte[] header = gson.toJson(new Header(tile.size(), tile.blocksPerPixel(), tile.subjectKind(), tile.labels(),
                        hasHeights, hasPickNoise))
                .getBytes(StandardCharsets.UTF_8);
        int cells = tile.gridSize() * tile.gridSize();
        int length = aligned(Integer.BYTES + header.length)
                + aligned(cells * Short.BYTES) * 2
                + (hasHeights ? cells * Float.BYTES : 0)
                + (hasPickNoise ? cells * Float.BYTES : 0);
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        align(buffer);
        buffer.asShortBuffer().put(tile.subjects());
        buffer.position(buffer.position() + cells * Short.BYTES);
        align(buffer);
        buffer.asShortBuffer().put(tile.regions());
        buffer.position(buffer.position() + cells * Short.BYTES);
        align(buffer);
        if (hasHeights) {
            buffer.asFloatBuffer().put(tile.heights());
            buffer.position(buffer.position() + cells * Float.BYTES);
        }
        if (hasPickNoise) {
            buffer.asFloatBuffer().put(tile.pickNoise());
        }
        return buffer.array();
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private static void align(ByteBuffer buffer) {
        buffer.position(aligned(buffer.position()));
    }

    private record Header(int size, double blocksPerPixel, MapLabel.Kind subjectKind, List<MapLabel> labels,
                          boolean heights, boolean pickNoise) {
    }
}
