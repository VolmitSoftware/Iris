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

package art.arcane.iris.studio.bridge;

import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A square of the map as the Iris Studio app reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i16 subjects[grid²] | padding to 4 | i16 regions[grid²] | padding to 4 | f32 heights[grid²], when the header says so
 * </pre>
 * The grids hold {@code (size + 2)²} cells row by row: the tile's pixels inside a ring of the neighbouring tiles' edge
 * pixels, so edges and slopes continue across tiles. Subjects and regions index the header's labels.
 *
 * @param subjectKind BIOME, REGION or WATER: what the subjects are
 * @param heights     null when the tile carries none
 */
record StudioTile(int size, double blocksPerPixel, String subjectKind, List<Label> labels, short[] subjects, short[] regions,
                  float[] heights) {
    static final short NO_LABEL = -1;
    private static final int ALIGNMENT_BYTES = 4;

    /** @param color 0xRRGGBB */
    record Label(String kind, String key, String name, int color) {
    }

    byte[] encode(Gson gson) {
        byte[] header = gson.toJson(new Header(size, blocksPerPixel, subjectKind, labels, heights != null, false))
                .getBytes(StandardCharsets.UTF_8);
        int cells = (size + 2) * (size + 2);
        int length = aligned(Integer.BYTES + header.length) + aligned(cells * Short.BYTES) * 2
                + (heights == null ? 0 : cells * Float.BYTES);
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        buffer.position(aligned(buffer.position()));
        putShorts(buffer, subjects);
        putShorts(buffer, regions);
        if (heights != null) {
            buffer.asFloatBuffer().put(heights);
        }
        return buffer.array();
    }

    private static void putShorts(ByteBuffer buffer, short[] grid) {
        buffer.asShortBuffer().put(grid);
        buffer.position(aligned(buffer.position() + grid.length * Short.BYTES));
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private record Header(int size, double blocksPerPixel, String subjectKind, List<Label> labels, boolean heights,
                          boolean pickNoise) {
    }
}
