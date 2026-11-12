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

import art.arcane.iris.core.link.CustomBlockLook;
import art.arcane.iris.core.studio.map.MapLabel;
import art.arcane.iris.core.studio.map.MapTile;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link MapTile} as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i16 subjects[grid²] | padding to 4 | i16 regions[grid²] | padding to 4
 * f32 heights[grid²], when the header says so | f32 pickNoise[grid²], when the header says so
 * i16 ground[grid²] | padding to 4 | i16 cover[grid²] | padding to 4 | i16 raisedCounts[grid²] | padding to 4
 * | i16 raisedY[raised] | padding to 4 | i16 raised[raised] | padding to 4, when the header names blocks
 * u8 voxelX[voxels] | padding to 4 | u8 voxelZ[voxels] | padding to 4 | i16 voxelY[voxels] | padding to 4
 * | i16 voxelBlock[voxels] | padding to 4 | u8 voxelFaces[voxels] | padding to 4, when the header counts voxels
 * </pre>
 * {@code raised} is the sum of the raised counts, which the header also holds, as it holds {@code voxels}, the count of
 * blocks seen from the open air, or -1 for a tile without them.
 * The header holds the size, the scale, the subject kind, the labels the grids index, and the blocks the ground and
 * cover grids index when the tile carries the tops of its columns, each as the state players see. Every array starts on a
 * multiple of 4 bytes, so the studio views them in place.
 */
final class MapTileEncoding {
    private static final int ALIGNMENT_BYTES = 4;
    private static final int NO_VOXELS = -1;

    private MapTileEncoding() {
    }

    /**
     * @param pluginLooks the plugin block a server state is with the vanilla state players see in its place; empty for
     *                    every state that is no plugin's block
     */
    static byte[] encode(MapTile tile, Gson gson, Function<String, Optional<CustomBlockLook>> pluginLooks) {
        boolean hasHeights = tile.heights() != null;
        boolean hasPickNoise = tile.pickNoise() != null;
        MapTile.Blocks blocks = tile.blocks();
        int raised = blocks == null ? 0 : blocks.raised().length;
        MapTile.Voxels voxels = blocks == null ? null : blocks.voxels();
        int voxelCount = voxels == null ? NO_VOXELS : voxels.count();
        byte[] header = gson.toJson(new Header(tile.size(), tile.blocksPerPixel(), tile.subjectKind(), tile.labels(),
                        hasHeights, hasPickNoise, blocks == null ? null : statesPlayersSee(blocks.names(), pluginLooks), raised, voxelCount))
                .getBytes(StandardCharsets.UTF_8);
        int cells = tile.gridSize() * tile.gridSize();
        int length = aligned(Integer.BYTES + header.length)
                + aligned(cells * Short.BYTES) * 2
                + (hasHeights ? cells * Float.BYTES : 0)
                + (hasPickNoise ? cells * Float.BYTES : 0)
                + (blocks == null ? 0 : aligned(cells * Short.BYTES) * 3 + aligned(raised * Short.BYTES) * 2)
                + (voxels == null ? 0 : aligned(voxelCount) * 3 + aligned(voxelCount * Short.BYTES) * 2);
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        align(buffer);
        putShorts(buffer, tile.subjects());
        putShorts(buffer, tile.regions());
        if (hasHeights) {
            buffer.asFloatBuffer().put(tile.heights());
            buffer.position(buffer.position() + cells * Float.BYTES);
        }
        if (hasPickNoise) {
            buffer.asFloatBuffer().put(tile.pickNoise());
            buffer.position(buffer.position() + cells * Float.BYTES);
        }
        if (blocks != null) {
            putShorts(buffer, blocks.ground());
            putShorts(buffer, blocks.cover());
            putShorts(buffer, blocks.raisedCounts());
            putShorts(buffer, blocks.raisedY());
            putShorts(buffer, blocks.raised());
        }
        if (voxels != null) {
            putBytes(buffer, voxels.x());
            putBytes(buffer, voxels.z());
            putShorts(buffer, voxels.y());
            putShorts(buffer, voxels.blocks());
            putBytes(buffer, voxels.faces());
        }
        return buffer.array();
    }

    private static void putBytes(ByteBuffer buffer, byte[] values) {
        buffer.put(values);
        align(buffer);
    }

    private static void putShorts(ByteBuffer buffer, short[] grid) {
        buffer.asShortBuffer().put(grid);
        buffer.position(aligned(buffer.position() + grid.length * Short.BYTES));
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private static void align(ByteBuffer buffer) {
        buffer.position(aligned(buffer.position()));
    }

    private static List<String> statesPlayersSee(List<String> serverStates, Function<String, Optional<CustomBlockLook>> pluginLooks) {
        return serverStates.stream()
                .map(state -> pluginLooks.apply(state).map(CustomBlockLook::shownAs).orElse(state))
                .toList();
    }

    private record Header(int size, double blocksPerPixel, MapLabel.Kind subjectKind, List<MapLabel> labels,
                          boolean heights, boolean pickNoise, List<String> blocks, int raised, int voxels) {
    }
}
