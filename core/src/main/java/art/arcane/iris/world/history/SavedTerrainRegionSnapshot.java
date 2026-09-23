package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

final class SavedTerrainRegionSnapshot {
    private static final int SECTOR_BYTES = 4_096;
    private static final int HEADER_BYTES = 2 * SECTOR_BYTES;
    private static final int MAX_CHUNK_BYTES = 64 * 1024 * 1024;

    private final Path file;
    private final BasicFileAttributes attributes;
    private final byte[] bytes;
    private final int byteLength;

    private SavedTerrainRegionSnapshot(SnapshotData data) {
        file = data.file();
        attributes = data.attributes();
        bytes = data.bytes();
        byteLength = Math.toIntExact(attributes.size());
    }

    static Optional<SavedTerrainRegionSnapshot> read(Path file, int maxBytes, byte[] reusable) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(reusable, "reusable");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Snapshot byte limit must not be negative");
        }
        Path normalized = file.toAbsolutePath().normalize();
        BasicFileAttributes attributes = readAttributes(normalized);
        if (attributes.size() < HEADER_BYTES) {
            throw new IOException("Truncated saved chunk region: " + normalized);
        }
        if (attributes.size() > maxBytes || attributes.fileKey() == null) {
            return Optional.empty();
        }
        int byteLength = (int) attributes.size();
        byte[] bytes = reusable.length >= byteLength ? reusable : new byte[byteLength];
        try (FileChannel input = FileChannel.open(normalized, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            readFully(input, ByteBuffer.wrap(bytes, 0, byteLength), normalized);
            if (input.size() != byteLength) {
                throw new IOException("Saved chunk region length changed during capture: " + normalized);
            }
        }
        SavedTerrainRegionSnapshot snapshot = new SavedTerrainRegionSnapshot(
                new SnapshotData(normalized, attributes, bytes));
        snapshot.validateAttributes();
        return Optional.of(snapshot);
    }

    byte[] bytes() {
        return bytes;
    }

    Payload payload(int index) throws IOException {
        if (index < 0 || index >= 1024) {
            throw new IOException("Saved chunk allocation index is invalid: " + index);
        }
        int allocation = readInt(bytes, index * Integer.BYTES);
        long offset = (long) (allocation >>> Byte.SIZE) * SECTOR_BYTES;
        int sectors = allocation & 255;
        if (offset < HEADER_BYTES || sectors == 0 || offset + 5 > byteLength) {
            throw new IOException("Saved chunk allocation is unavailable in " + file + " at " + index);
        }
        int start = (int) offset;
        int length = readInt(bytes, start);
        int compression = bytes[start + Integer.BYTES] & 255;
        if (length < 1 || length > MAX_CHUNK_BYTES || (long) length + Integer.BYTES > (long) sectors * SECTOR_BYTES
                || offset + Integer.BYTES + length > byteLength) {
            throw new IOException("Saved chunk payload is truncated or too large in " + file + " at " + index);
        }
        boolean external = (compression & 128) != 0;
        if (external && length != 1) {
            throw new IOException("External saved chunk has an invalid allocation length in " + file);
        }
        return new Payload(start + 5, length - 1, compression & 127, external);
    }

    void validateUnchanged() throws IOException {
        validateAttributes();
        byte[] header = new byte[HEADER_BYTES];
        try (FileChannel input = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            readFully(input, ByteBuffer.wrap(header), file);
            if (input.size() != byteLength) {
                throw new IOException("Saved chunk region length changed during capture: " + file);
            }
        }
        if (!Arrays.equals(bytes, 0, HEADER_BYTES, header, 0, HEADER_BYTES)) {
            throw new IOException("Saved chunk region header changed during capture: " + file);
        }
        validateAttributes();
    }

    private static BasicFileAttributes readAttributes(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) {
            throw new IOException("Saved chunk region is not a regular file: " + file);
        }
        return attributes;
    }

    private static void readFully(FileChannel input, ByteBuffer buffer, Path file) throws IOException {
        while (buffer.hasRemaining()) {
            if (input.read(buffer) < 0) {
                throw new IOException("Truncated saved chunk region: " + file);
            }
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
                | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private void validateAttributes() throws IOException {
        BasicFileAttributes current = readAttributes(file);
        if (!Objects.equals(attributes.fileKey(), current.fileKey()) || attributes.size() != current.size()
                || !attributes.lastModifiedTime().equals(current.lastModifiedTime())) {
            throw new IOException("Saved chunk region changed during capture: " + file);
        }
    }

    record Payload(int offset, int length, int compression, boolean external) {
    }

    private record SnapshotData(Path file, BasicFileAttributes attributes, byte[] bytes) {
    }
}
