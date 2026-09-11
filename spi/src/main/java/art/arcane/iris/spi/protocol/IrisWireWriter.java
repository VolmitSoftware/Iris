/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.spi.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Sequential big-endian writer that builds one protocol frame into a growing byte buffer.
 * <p>
 * Not thread-safe: it carries a write position, so one instance serves one frame on one thread. The buffer
 * doubles as needed and is hard-capped at {@link IrisProtocol#MAX_FRAME_BYTES} - exceeding it throws
 * {@link IllegalStateException} at the write that overflows rather than emitting an oversized frame the peer
 * would reject. Field order here is the wire format and must mirror {@link IrisWireReader}.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public final class IrisWireWriter {
    private byte[] buffer;
    private int length;

    /**
     * Creates an empty writer with a small buffer that grows on demand.
     */
    public IrisWireWriter() {
        this.buffer = new byte[64];
        this.length = 0;
    }

    /**
     * Writes a 7-bit-continuation varint. Negative values encode as five bytes.
     */
    public void writeVarInt(int value) {
        int remaining = value;
        while (true) {
            int chunk = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                writeByte(chunk | 0x80);
            } else {
                writeByte(chunk);
                return;
            }
        }
    }

    /**
     * Writes a fixed four-byte big-endian int.
     */
    public void writeInt(int value) {
        ensure(4);
        buffer[length++] = (byte) (value >>> 24);
        buffer[length++] = (byte) (value >>> 16);
        buffer[length++] = (byte) (value >>> 8);
        buffer[length++] = (byte) value;
    }

    /**
     * Writes a fixed eight-byte big-endian long.
     */
    public void writeLong(long value) {
        ensure(8);
        buffer[length++] = (byte) (value >>> 56);
        buffer[length++] = (byte) (value >>> 48);
        buffer[length++] = (byte) (value >>> 40);
        buffer[length++] = (byte) (value >>> 32);
        buffer[length++] = (byte) (value >>> 24);
        buffer[length++] = (byte) (value >>> 16);
        buffer[length++] = (byte) (value >>> 8);
        buffer[length++] = (byte) value;
    }

    /**
     * Writes a double as its IEEE 754 bit pattern.
     */
    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes a boolean as one byte, {@code 1} or {@code 0}.
     */
    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    /**
     * Writes a varint-length-prefixed UTF-8 string. {@code value} must not be null.
     */
    public void writeString(String value) {
        writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a varint-length-prefixed byte array, copying the contents. {@code value} must not be null.
     */
    public void writeBytes(byte[] value) {
        writeVarInt(value.length);
        ensure(value.length);
        System.arraycopy(value, 0, buffer, length, value.length);
        length += value.length;
    }

    /**
     * The bytes written so far, as a fresh copy trimmed to length. The writer stays usable afterwards. Never
     * returns null.
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, length);
    }

    private void writeByte(int value) {
        ensure(1);
        buffer[length++] = (byte) value;
    }

    private void ensure(int extra) {
        if ((long) length + extra > IrisProtocol.MAX_FRAME_BYTES) {
            throw new IllegalStateException("Iris protocol frame exceeds " + IrisProtocol.MAX_FRAME_BYTES + " byte cap");
        }
        int required = length + extra;
        if (required <= buffer.length) {
            return;
        }
        int grown = buffer.length;
        while (grown < required) {
            grown <<= 1;
        }
        if (grown > IrisProtocol.MAX_FRAME_BYTES) {
            grown = IrisProtocol.MAX_FRAME_BYTES;
        }
        buffer = Arrays.copyOf(buffer, grown);
    }
}
