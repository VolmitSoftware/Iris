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

package art.arcane.iris.pack.datapack;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.storage.Durability;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;

final class DatapackSupport {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DatapackSupport() {
    }

    static String failureMessage(Throwable exception) {
        if (exception == null) {
            return "unknown failure";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    static IOException appendFailure(IOException current, IOException additional) {
        if (current == null) {
            return additional;
        }
        current.addSuppressed(additional);
        return current;
    }

    static IOException appendIOException(IOException current, Throwable failure) {
        IOException next = failure instanceof IOException ioFailure
                ? ioFailure : new IOException("Datapack transaction participant failed", failure);
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    static Path requireDirectoryIdentity(File directory, String purpose) throws IOException {
        Path normalized = directory.toPath().toAbsolutePath().normalize();
        BasicFileAttributes attributes = Files.readAttributes(
                normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!DatapackScratchRecovery.isSupportedScratchDirectory(attributes)) {
            throw new IOException("Invalid " + purpose + " " + normalized);
        }
        normalized.toRealPath();
        return normalized;
    }

    static void requireNoSymbolicLinkComponents(Path normalized, String purpose) throws IOException {
        Path current = normalized.getRoot();
        for (Path component : normalized) {
            current = current == null ? component : current.resolve(component);
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink()) {
                throw new IOException("Refusing symbolic-link component in " + purpose + " " + normalized);
            }
            if (attributes.isOther()) {
                throw new IOException("Refusing special filesystem component in " + purpose + " " + normalized);
            }
            if (!DatapackScratchRecovery.isSupportedScratchDirectory(attributes)) {
                throw new IOException("Refusing unsupported component in " + purpose + " " + normalized);
            }
        }
    }

    static String directoryIdentity(File directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Invalid datapack directory identity at " + directory.getPath());
        }
        Object fileKey = attributes.fileKey();
        if (fileKey != null) {
            return "key:" + fileKey;
        }
        // Windows exposes no inode-equivalent for a directory, and returning nothing here fails
        // the identity gate closed, which disables legacy staging replacement on that platform
        // outright. Creation time is the one stable discriminator the filesystem still offers: a
        // directory swapped in at the same path brings its own, so a swap is still caught. It is
        // a weaker guarantee than an inode, so it is only ever the fallback.
        return "created:" + attributes.creationTime();
    }

    static boolean pathExists(Path path, String purpose) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        throw new IOException("Cannot determine " + purpose + " state at " + path);
    }

    static String safe(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    static String safeFile(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    static String hex(byte[] hash) {
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    static void updateDigestInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    static void updateDigestLong(MessageDigest digest, long value) {
        digest.update((byte) (value >>> 56));
        digest.update((byte) (value >>> 48));
        digest.update((byte) (value >>> 40));
        digest.update((byte) (value >>> 32));
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    static void message(VolmitSender sender, String text) {
        if (sender != null) {
            sender.sendMessage(text);
            return;
        }
        IrisLogging.info(text);
    }

    static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void moveNew(Path source, Path target) throws IOException {
        if (pathExists(target, "move target")) {
            throw new IOException("Refusing to replace concurrently-created path " + target);
        }
        Files.move(source, target);
    }

    static void ensureScratchDirectory(File directory, String purpose) throws IOException {
        Path path = directory.toPath();
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing invalid " + purpose + " directory " + directory.getPath());
            }
            return;
        }
        Files.createDirectories(path);
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not create a safe " + purpose + " directory " + directory.getPath());
        }
    }

    static void verifyDirectoryContainerIfPresent(File directory, String purpose) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing invalid " + purpose + " directory " + directory.getPath());
        }
    }

    static String normalizedPath(File file) {
        return file.toPath().toAbsolutePath().normalize().toString();
    }

    static String realDirectoryPath(File directory, String purpose) throws IOException {
        Path path = directory.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid " + purpose + " " + path);
        }
        return path.toRealPath().toString();
    }

    static String readBoundedUtf8(Path path, long maxBytes, String purpose) throws IOException {
        return new String(readBoundedBytes(path, maxBytes, purpose), StandardCharsets.UTF_8);
    }

    static byte[] readBoundedBytes(Path path, long maxBytes, String purpose) throws IOException {
        int readLimit = Math.toIntExact(maxBytes + 1);
        byte[] content;
        try (InputStream input = Files.newInputStream(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            content = input.readNBytes(readLimit);
        }
        if (content.length > maxBytes) {
            throw new IOException(purpose + " exceeds " + maxBytes + " bytes");
        }
        return content;
    }

    static void forceFile(Path file) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            Durability.force(channel);
        }
    }

    static void forceDirectoryIfSupported(Path directory) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            Durability.force(channel);
        }
    }
}
