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

import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.tooling.BlockPattern;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A {@link BlockPattern} as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes | i16 picks[columns²]
 * </pre>
 * The header holds the columns across, the blocks between columns, the block keys the picks index, and the pick
 * ({@code noBlock}) of a column the pattern places nothing at.
 */
final class PatternEncoding {
    private static final int ALIGNMENT_BYTES = 4;
    private static final short NO_BLOCK = -1;

    private PatternEncoding() {
    }

    static byte[] encode(BlockPattern pattern, int blocksPerColumn, Gson gson) {
        List<String> blocks = pattern.blocks().stream().map(BlockState::key).toList();
        byte[] header = gson.toJson(new Header(pattern.columns(), blocksPerColumn, blocks, NO_BLOCK))
                .getBytes(StandardCharsets.UTF_8);
        int columns = pattern.columns();
        ByteBuffer buffer = ByteBuffer.allocate(aligned(Integer.BYTES + header.length) + columns * columns * Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header);
        buffer.position(aligned(buffer.position()));
        for (int row = 0; row < columns; row++) {
            for (int column = 0; column < columns; column++) {
                buffer.putShort((short) pattern.pickAt(column, row).orElse(NO_BLOCK));
            }
        }
        return buffer.array();
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private record Header(int columns, int blocksPerColumn, List<String> blocks, int noBlock) {
    }
}
