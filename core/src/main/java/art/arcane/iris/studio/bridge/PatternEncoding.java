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

import art.arcane.iris.studio.map.PalettePatterns;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * A palette's pattern as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes | i16 picks[columns²]
 * </pre>
 * The header holds the columns across, the blocks between columns, the block states the picks index, and the pick
 * ({@code noBlock}) of a column the palette places nothing at.
 */
final class PatternEncoding {
    private static final int ALIGNMENT_BYTES = 4;

    private PatternEncoding() {
    }

    static byte[] encode(PalettePatterns.Pattern pattern, int blocksPerColumn, Gson gson) {
        byte[] header = gson.toJson(new Header(pattern.columns(), blocksPerColumn, pattern.blocks(), pattern.blocks(), PalettePatterns.NO_BLOCK))
                .getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(aligned(Integer.BYTES + header.length) + pattern.picks().length * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        buffer.position(aligned(buffer.position()));
        buffer.asShortBuffer().put(pattern.picks());
        return buffer.array();
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private record Header(int columns, int blocksPerColumn, java.util.List<String> blocks, java.util.List<String> drawnAs, int noBlock) {
    }
}
