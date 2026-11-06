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
import art.arcane.iris.core.studio.saved.SavedTile;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A {@link SavedTile} as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i16 groundY[size²] | padding to 4 | i16 waterSurfaceY[size²] | padding to 4
 * i16 blocks[size²] | padding to 4 | i16 biomes[size²]
 * </pre>
 * The header holds the size, the scale, the block names and the biome labels the grids index, and the values that mean
 * no ground and no water.
 */
final class SavedTileEncoding {
    private static final int ALIGNMENT_BYTES = 4;

    private SavedTileEncoding() {
    }

    static byte[] encode(SavedTile tile, List<MapLabel> biomes, Gson gson) {
        byte[] header = gson.toJson(new Header(tile.size(), tile.blocksPerPixel(), tile.blockNames(), biomes,
                SavedTile.NO_GROUND, SavedTile.NO_WATER)).getBytes(StandardCharsets.UTF_8);
        List<short[]> grids = List.of(tile.groundY(), tile.waterSurfaceY(), tile.blocks(), tile.biomes());
        int gridBytes = aligned(tile.size() * tile.size() * Short.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(aligned(Integer.BYTES + header.length) + gridBytes * grids.size())
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        buffer.position(aligned(buffer.position()));
        for (short[] grid : grids) {
            buffer.asShortBuffer().put(grid);
            buffer.position(buffer.position() + gridBytes);
        }
        return buffer.array();
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private record Header(int size, int blocksPerPixel, List<String> blocks, List<MapLabel> biomes, int noGround, int noWater) {
    }
}
