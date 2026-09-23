package art.arcane.iris.world.history;

import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

final class SavedTerrainStatusNbt {
    private final byte[] bytes;
    private final int limit;
    private int position;

    private SavedTerrainStatusNbt(byte[] bytes, int length) {
        this.bytes = bytes;
        this.limit = length;
    }

    static String read(byte[] bytes, int offset, int length, int chunkX, int chunkZ) throws IOException {
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IOException("Invalid saved chunk NBT payload bounds");
        }
        SavedTerrainStatusNbt input = new SavedTerrainStatusNbt(bytes, offset + length);
        input.position = offset;
        if (input.readUnsignedByte() != 10) {
            throw new IOException("Saved chunk root is not a compound");
        }
        input.readUtf();
        Integer savedX = null;
        Integer savedZ = null;
        String status = null;
        for (int type = input.readUnsignedByte(); type != 0; type = input.readUnsignedByte()) {
            int start = input.position + 2;
            int size = input.readUtf();
            boolean xName = input.matches(start, size, "xPos");
            boolean zName = input.matches(start, size, "zPos");
            boolean statusName = input.matches(start, size, "Status");
            if (xName || zName) {
                Integer value = null;
                if (type >= 1 && type <= 6) {
                    value = input.number(type);
                } else {
                    input.skip(type, Tag.DEFAULT_MAX_DEPTH - 1);
                }
                if (xName) {
                    savedX = value;
                } else {
                    savedZ = value;
                }
            } else if (statusName) {
                if (type == 8) {
                    int valueStart = input.position;
                    int valueLength = input.readUtf();
                    status = new DataInputStream(new ByteArrayInputStream(bytes, valueStart, valueLength + 2)).readUTF();
                } else {
                    input.skip(type, Tag.DEFAULT_MAX_DEPTH - 1);
                    status = null;
                }
            } else {
                input.skip(type, Tag.DEFAULT_MAX_DEPTH - 1);
            }
        }
        if (savedX == null || savedZ == null || savedX != chunkX || savedZ != chunkZ) {
            throw new IOException("Saved chunk coordinates do not match its region allocation");
        }
        if (status == null || status.isBlank()) {
            throw new IOException("Saved chunk field Status is not a nonempty string");
        }
        return status;
    }

    private boolean matches(int start, int size, String name) {
        if (size < name.length() || size > name.length() * 3) {
            return false;
        }
        if (size == name.length()) {
            for (int index = 0; index < name.length(); index++) {
                if (bytes[start + index] != name.charAt(index)) {
                    return false;
                }
            }
            return true;
        }
        int end = start + size;
        for (int index = 0; index < name.length(); index++) {
            if (start == end) {
                return false;
            }
            int value = bytes[start++] & 255;
            if ((value & 224) == 192) {
                value = (value & 31) << 6 | bytes[start++] & 63;
            } else if ((value & 240) == 224) {
                value = (value & 15) << 12 | (bytes[start++] & 63) << 6 | bytes[start++] & 63;
            }
            if (value != name.charAt(index)) {
                return false;
            }
        }
        return start == end;
    }

    private int number(int type) throws IOException {
        return switch (type) {
            case 1 -> (byte) readUnsignedByte();
            case 2 -> (short) readUnsignedShort();
            case 3 -> readInt();
            case 4 -> (int) readLong();
            case 5 -> (int) Float.intBitsToFloat(readInt());
            case 6 -> (int) Double.longBitsToDouble(readLong());
            default -> throw new IOException("Not numeric");
        };
    }

    private void require(long count) throws EOFException {
        if (count < 0 || count > limit - position) {
            throw new EOFException("Truncated saved chunk NBT payload");
        }
    }

    private void advance(long count) throws IOException {
        require(count);
        position += (int) count;
    }

    private int readUnsignedByte() throws IOException {
        require(1);
        return bytes[position++] & 255;
    }

    private int readUnsignedShort() throws IOException {
        require(2);
        int result = (bytes[position] & 255) << 8 | bytes[position + 1] & 255;
        position += 2;
        return result;
    }

    private int readInt() throws IOException {
        require(4);
        int result = (bytes[position] & 255) << 24 | (bytes[position + 1] & 255) << 16
                | (bytes[position + 2] & 255) << 8 | bytes[position + 3] & 255;
        position += 4;
        return result;
    }

    private long readLong() throws IOException {
        return (long) readInt() << 32 | Integer.toUnsignedLong(readInt());
    }

    private int readUtf() throws IOException {
        int length = readUnsignedShort();
        require(length);
        int end = position + length;
        while (position < end) {
            int first = bytes[position++] & 255;
            if (first < 128) {
                continue;
            }
            if ((first & 224) == 192) {
                if (position >= end || (bytes[position++] & 192) != 128) {
                    throw new IOException("Invalid saved chunk modified UTF-8");
                }
            } else if ((first & 240) == 224) {
                if (end - position < 2 || (bytes[position++] & 192) != 128 || (bytes[position++] & 192) != 128) {
                    throw new IOException("Invalid saved chunk modified UTF-8");
                }
            } else {
                throw new IOException("Invalid modified UTF-8");
            }
        }
        return length;
    }

    private void skip(int type, int depth) throws IOException {
        if (depth < 0) {
            throw new IOException("Saved chunk NBT nesting limit exceeded");
        }
        switch (type) {
            case 0 -> {
            }
            case 1 -> advance(1);
            case 2 -> advance(2);
            case 3, 5 -> advance(4);
            case 4, 6 -> advance(8);
            case 7, 11, 12 -> {
                int length = readInt();
                if (length < 0) {
                    throw new IOException("Negative saved chunk NBT array length");
                }
                advance((long) length * (type == 7 ? 1 : type == 11 ? 4 : 8));
            }
            case 8 -> readUtf();
            case 9 -> {
                int child = readUnsignedByte();
                int length = readInt();
                if (length > 0 && child > 12) {
                    throw new IOException("Invalid saved chunk NBT list type");
                }
                if (length > 0 && depth == 0) {
                    throw new IOException("Saved chunk NBT nesting limit exceeded");
                }
                if (child >= 1 && child <= 6) {
                    int width = switch (child) {
                        case 1 -> 1;
                        case 2 -> 2;
                        case 3, 5 -> 4;
                        default -> 8;
                    };
                    advance((long) Math.max(0, length) * width);
                } else if (child != 0) {
                    for (int index = 0; index < length; index++) {
                        skip(child, depth - 1);
                    }
                }
            }
            case 10 -> {
                for (int child = readUnsignedByte(); child != 0; child = readUnsignedByte()) {
                    readUtf();
                    skip(child, depth - 1);
                }
            }
            default -> throw new IOException("Invalid NBT tag type");
        }
    }
}
