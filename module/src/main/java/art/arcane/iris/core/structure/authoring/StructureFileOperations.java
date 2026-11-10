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

package art.arcane.iris.core.structure.authoring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

interface StructureFileOperations {
    boolean exists(Path path);

    boolean isRegularFile(Path path);

    default boolean isDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    byte[] readAllBytes(Path path) throws IOException;

    String sha256(Path path) throws IOException;

    void createDirectories(Path path) throws IOException;

    void writeNew(Path path, byte[] content) throws IOException;

    void move(Path source, Path target) throws IOException;

    default void moveNew(Path source, Path target) throws IOException {
        move(source, target);
    }

    void deleteIfExists(Path path) throws IOException;

    void deleteTree(Path root) throws IOException;

    default List<Path> list(Path root) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.sorted().toList();
        }
    }

    default List<Path> list(Path root, int maxEntries) throws IOException {
        List<Path> entries = list(root);
        return entries.size() <= maxEntries ? entries : entries.subList(0, maxEntries + 1);
    }

    default void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    default void forceDirectory(Path path) throws IOException {
        if (!Files.getFileStore(path).supportsFileAttributeView("posix")) {
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}

final class NioStructureFileOperations implements StructureFileOperations {
    @Override
    public boolean exists(Path path) {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public boolean isRegularFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return StructureHash.sha256(input);
        }
    }

    @Override
    public void createDirectories(Path path) throws IOException {
        Files.createDirectories(path);
    }

    @Override
    public void writeNew(Path path, byte[] content) throws IOException {
        Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    @Override
    public void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void moveNew(Path source, Path target) throws IOException {
        Files.move(source, target);
    }

    @Override
    public void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    @Override
    public void deleteTree(Path root) throws IOException {
        if (!exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> stream = Files.walk(root)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    @Override
    public List<Path> list(Path root, int maxEntries) throws IOException {
        try (Stream<Path> stream = Files.list(root)) {
            return stream.limit(maxEntries + 1L).sorted().toList();
        }
    }
}
