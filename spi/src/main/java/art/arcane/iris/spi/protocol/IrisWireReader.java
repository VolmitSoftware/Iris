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

/**
 * Sequential big-endian reader over one protocol frame.
 * <p>
 * Not thread-safe and not reusable: it carries a read position, so one instance serves one frame on one thread.
 * Every read is bounds-checked against the frame length and throws {@link ProtocolException} rather than
 * {@link ArrayIndexOutOfBoundsException}, so a hostile or truncated frame cannot read past its end. Length
 * prefixes are validated against the remaining bytes before any allocation, so a forged length cannot force a
 * large allocation.
 * <p>
 * Internal to Iris; not a published integration surface.
 */
public final class IrisWireReader {
    private final byte[] frame;
    private final int limit;
    private int position;

    /**
     * Wraps {@code frame} for reading from offset zero. The array is held by reference and must not be mutated
     * while the reader is in use.
     */
    public IrisWireReader(byte[] frame) {
        this.frame = frame;
        this.limit = frame.length;
        this.position = 0;
    }

    /**
     * Reads a 7-bit-continuation varint.
     *
     * @throws ProtocolException if the frame ends mid-varint or the encoding exceeds five bytes
     */
    public int readVarInt() throws ProtocolException {
        int result = 0;
        int shift = 0;
        for (int index = 0; index < 5; index++) {
            requireRemaining(1);
            int current = frame[position++] & 0xFF;
            result |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new ProtocolException("varint exceeds 5 bytes");
    }

    /**
     * Reads a fixed four-byte big-endian int.
     *
     * @throws ProtocolException if fewer than four bytes remain
     */
    public int readInt() throws ProtocolException {
        requireRemaining(4);
        int value = ((frame[position] & 0xFF) << 24)
                | ((frame[position + 1] & 0xFF) << 16)
                | ((frame[position + 2] & 0xFF) << 8)
                | (frame[position + 3] & 0xFF);
        position += 4;
        return value;
    }

    /**
     * Reads a fixed eight-byte big-endian long.
     *
     * @throws ProtocolException if fewer than eight bytes remain
     */
    public long readLong() throws ProtocolException {
        requireRemaining(8);
        long value = ((long) (frame[position] & 0xFF) << 56)
                | ((long) (frame[position + 1] & 0xFF) << 48)
                | ((long) (frame[position + 2] & 0xFF) << 40)
                | ((long) (frame[position + 3] & 0xFF) << 32)
                | ((long) (frame[position + 4] & 0xFF) << 24)
                | ((long) (frame[position + 5] & 0xFF) << 16)
                | ((long) (frame[position + 6] & 0xFF) << 8)
                | (frame[position + 7] & 0xFF);
        position += 8;
        return value;
    }

    /**
     * Reads a double from its IEEE 754 bit pattern.
     *
     * @throws ProtocolException if fewer than eight bytes remain
     */
    public double readDouble() throws ProtocolException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads one byte as a boolean; any non-zero value is true.
     *
     * @throws ProtocolException if no bytes remain
     */
    public boolean readBoolean() throws ProtocolException {
        requireRemaining(1);
        return frame[position++] != 0;
    }

    /**
     * Reads a varint-length-prefixed UTF-8 string. Never returns null; an empty string is legal.
     *
     * @throws ProtocolException if the length is negative or exceeds the remaining bytes
     */
    public String readString() throws ProtocolException {
        int declaredLength = readLength("negative string length", "string length exceeds remaining frame bytes");
        String value = new String(frame, position, declaredLength, StandardCharsets.UTF_8);
        position += declaredLength;
        return value;
    }

    /**
     * Reads a varint-length-prefixed byte array into a fresh copy. Never returns null.
     *
     * @throws ProtocolException if the length is negative or exceeds the remaining bytes
     */
    public byte[] readBytes() throws ProtocolException {
        int declaredLength = readLength("negative byte array length", "byte array length exceeds remaining frame bytes");
        byte[] value = new byte[declaredLength];
        System.arraycopy(frame, position, value, 0, declaredLength);
        position += declaredLength;
        return value;
    }

    private int readLength(String negativeMessage, String oversizedMessage) throws ProtocolException {
        int declaredLength = readVarInt();
        if (declaredLength < 0) {
            throw new ProtocolException(negativeMessage);
        }
        if (declaredLength > remaining()) {
            throw new ProtocolException(oversizedMessage);
        }
        return declaredLength;
    }

    private int remaining() {
        return limit - position;
    }

    private void requireRemaining(int count) throws ProtocolException {
        if (position + count > limit) {
            throw new ProtocolException("truncated frame: needed " + count + " bytes, had " + remaining());
        }
    }
}
