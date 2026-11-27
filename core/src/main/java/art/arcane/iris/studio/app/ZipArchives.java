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

package art.arcane.iris.studio.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unpacks archives into a folder that only appears once it is complete: everything lands in a staging folder first,
 * which is then moved into place, so a crash halfway never leaves a folder that looks installed.
 */
final class ZipArchives {
    private static final long DITTO_TIMEOUT_MINUTES = 5;
    private static final int FILE_TYPE_MASK = 0170000;
    private static final int SYMLINK_TYPE = 0120000;
    private static final int PERMISSION_MASK = 0777;
    private static final List<PosixFilePermission> PERMISSION_BITS = List.of(
            PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ,
            PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ,
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ);

    private ZipArchives() {
    }

    /** Keeps each entry's Unix mode and symlinks, so executables stay executable. */
    static void extract(Path archive, Path folder) throws IOException {
        Path staging = staging(folder);
        boolean posix = staging.getFileSystem().supportedFileAttributeViews().contains("posix");
        Map<String, Integer> modes = posix ? unixModes(archive) : Map.of();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                extractEntry(zip, entry, staging, modes.getOrDefault(entry.getName(), 0), posix);
            }
        }
        moveIntoPlace(staging, folder);
    }

    /** Unpacks with macOS's ditto, which keeps everything an app bundle relies on. */
    static void extractWithDitto(Path archive, Path folder) throws IOException {
        Path staging = staging(folder);
        Process ditto = new ProcessBuilder("ditto", "-x", "-k", archive.toString(), staging.toString())
                .redirectErrorStream(true)
                .start();
        try {
            if (!ditto.waitFor(DITTO_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                ditto.destroyForcibly();
                throw new IOException("ditto did not finish unpacking " + archive);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("unpacking " + archive + " was interrupted", interrupted);
        }
        if (ditto.exitValue() != 0) {
            throw new IOException("ditto could not unpack " + archive + ": "
                    + new String(ditto.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
        }
        moveIntoPlace(staging, folder);
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path entry : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        }
    }

    private static void extractEntry(ZipFile zip, ZipEntry entry, Path staging, int mode, boolean posix) throws IOException {
        Path target = insideStaging(staging, staging.resolve(entry.getName()));
        if (entry.isDirectory()) {
            Files.createDirectories(target);
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = zip.getInputStream(entry)) {
            if (posix && (mode & FILE_TYPE_MASK) == SYMLINK_TYPE) {
                Path linkTarget = Path.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                insideStaging(staging, target.getParent().resolve(linkTarget));
                Files.createSymbolicLink(target, linkTarget);
                return;
            }
            Files.copy(input, target);
        }
        if (posix && mode != 0) {
            Files.setPosixFilePermissions(target, permissions(mode));
        }
    }

    private static Path insideStaging(Path staging, Path path) throws IOException {
        Path normalized = path.normalize();
        if (!normalized.startsWith(staging)) {
            throw new IOException("archive entry " + path + " points outside its folder");
        }
        return normalized;
    }

    private static Set<PosixFilePermission> permissions(int mode) {
        Set<PosixFilePermission> permissions = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        int bits = mode & PERMISSION_MASK;
        for (int bit = 0; bit < PERMISSION_BITS.size(); bit++) {
            if ((bits & (1 << bit)) != 0) {
                permissions.add(PERMISSION_BITS.get(bit));
            }
        }
        return permissions;
    }

    /**
     * Each entry's Unix mode, read from the archive's central directory, where zip tools made on Unix keep it in the
     * high half of the external attributes. {@link ZipFile} reads the entries but not this field.
     */
    static Map<String, Integer> unixModes(Path archive) throws IOException {
        try (FileChannel channel = FileChannel.open(archive)) {
            CentralDirectory directory = CentralDirectory.locate(channel);
            ByteBuffer records = read(channel, directory.offset(), directory.size());
            Map<String, Integer> modes = new HashMap<>();
            int position = 0;
            for (long index = 0; index < directory.entries(); index++) {
                if (records.getInt(position) != CentralDirectory.ENTRY_SIGNATURE) {
                    throw new IOException(archive + " has a damaged central directory");
                }
                int madeBySystem = (records.getShort(position + 4) >> 8) & 0xFF;
                int nameLength = records.getShort(position + 28) & 0xFFFF;
                int extraLength = records.getShort(position + 30) & 0xFFFF;
                int commentLength = records.getShort(position + 32) & 0xFFFF;
                int externalAttributes = records.getInt(position + 38);
                byte[] name = new byte[nameLength];
                records.get(position + 46, name);
                if (madeBySystem == CentralDirectory.MADE_ON_UNIX) {
                    modes.put(new String(name, StandardCharsets.UTF_8), externalAttributes >>> 16);
                }
                position += 46 + nameLength + extraLength + commentLength;
            }
            return modes;
        }
    }

    private static ByteBuffer read(FileChannel channel, long offset, long length) throws IOException {
        if (length > Integer.MAX_VALUE - 8 || offset < 0 || offset + length > channel.size()) {
            throw new IOException("zip structure points outside the archive");
        }
        ByteBuffer buffer = ByteBuffer.allocate((int) length).order(ByteOrder.LITTLE_ENDIAN);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, offset + buffer.position()) < 0) {
                throw new IOException("archive ended early");
            }
        }
        return buffer.flip();
    }

    private record CentralDirectory(long offset, long size, long entries) {
        static final int ENTRY_SIGNATURE = 0x02014b50;
        static final int MADE_ON_UNIX = 3;
        private static final int END_SIGNATURE = 0x06054b50;
        private static final int ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
        private static final int ZIP64_END_SIGNATURE = 0x06064b50;
        private static final int END_RECORD_LENGTH = 22;
        private static final int ZIP64_LOCATOR_LENGTH = 20;
        private static final int MAX_COMMENT_LENGTH = 0xFFFF;

        static CentralDirectory locate(FileChannel channel) throws IOException {
            long size = channel.size();
            int tailLength = (int) Math.min(size, END_RECORD_LENGTH + MAX_COMMENT_LENGTH);
            long tailOffset = size - tailLength;
            ByteBuffer tail = read(channel, tailOffset, tailLength);
            for (int end = tailLength - END_RECORD_LENGTH; end >= 0; end--) {
                if (tail.getInt(end) == END_SIGNATURE) {
                    return fromEndRecord(channel, tail, end, tailOffset + end);
                }
            }
            throw new IOException("not a zip archive");
        }

        private static CentralDirectory fromEndRecord(FileChannel channel, ByteBuffer tail, int end, long endOffset) throws IOException {
            long entries = tail.getShort(end + 10) & 0xFFFFL;
            long size = tail.getInt(end + 12) & 0xFFFFFFFFL;
            long offset = tail.getInt(end + 16) & 0xFFFFFFFFL;
            if (entries != 0xFFFFL && size != 0xFFFFFFFFL && offset != 0xFFFFFFFFL) {
                return new CentralDirectory(offset, size, entries);
            }
            ByteBuffer locator = read(channel, endOffset - ZIP64_LOCATOR_LENGTH, ZIP64_LOCATOR_LENGTH);
            if (locator.getInt(0) != ZIP64_LOCATOR_SIGNATURE) {
                throw new IOException("zip64 archive without its locator");
            }
            ByteBuffer zip64End = read(channel, locator.getLong(8), 56);
            if (zip64End.getInt(0) != ZIP64_END_SIGNATURE) {
                throw new IOException("zip64 archive without its end record");
            }
            return new CentralDirectory(zip64End.getLong(48), zip64End.getLong(40), zip64End.getLong(32));
        }
    }

    private static Path staging(Path folder) throws IOException {
        Path staging = folder.resolveSibling(folder.getFileName() + ".part");
        deleteRecursively(staging);
        Files.createDirectories(staging);
        return staging;
    }

    private static void moveIntoPlace(Path staging, Path folder) throws IOException {
        deleteRecursively(folder);
        try {
            Files.move(staging, folder, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staging, folder);
        }
    }
}
