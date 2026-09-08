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

package art.arcane.iris.core.datapack;

import art.arcane.iris.core.datapack.DatapackIngestService.Entry;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

final class DatapackOwnership {
    static final String FINDER_METADATA = ".DS_Store";

    static final String OWNERSHIP_MARKER = ".iris-managed.json";

    static final int OWNERSHIP_SCHEMA = 1;

    static final int MAX_MANAGED_PATHS = DatapackArchive.MAX_ARCHIVE_ENTRIES + 16;

    static final long MAX_OWNERSHIP_BYTES = 1024L * 1024L;

    static final int HASH_BUFFER_BYTES = 64 * 1024;

    private DatapackOwnership() {
    }

    static void writeOwnership(File directory, Entry entry) throws IOException {
        writeOwnership(directory, entry, directoryHash(directory));
    }

    static void writeOwnership(File directory, Entry entry, String contentHash) throws IOException {
        if (!DatapackArchive.isValidManagedId(entry.id) || entry.url == null || entry.url.isBlank()) {
            throw new IOException("Invalid Iris datapack ownership identity for " + directory.getPath());
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IOException("Missing Iris datapack ownership content hash for " + directory.getPath());
        }
        Ownership ownership = new Ownership(
                OWNERSHIP_SCHEMA,
                entry.id,
                entry.url,
                entry.versionId,
                entry.versionNumber,
                entry.sha1,
                contentHash,
                DatapackManifestStore.copyList(entry.structureKeys),
                DatapackManifestStore.copyList(entry.templateKeys)
        );
        Path marker = new File(directory, OWNERSHIP_MARKER).toPath();
        Path temporary = Files.createTempFile(directory.toPath(), OWNERSHIP_MARKER, ".tmp");
        try {
            Files.writeString(
                    temporary,
                    DatapackSupport.GSON.toJson(ownership),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            DatapackSupport.move(temporary, marker);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Ownership readOwnership(File directory) throws IOException {
        Ownership ownership = readOwnershipOrNull(directory);
        if (ownership == null) {
            throw new IOException("Missing Iris datapack ownership marker in " + directory.getPath());
        }
        return ownership;
    }

    static Ownership readOwnershipOrNull(File directory) throws IOException {
        File marker = new File(directory, OWNERSHIP_MARKER);
        Path markerPath = marker.toPath();
        if (Files.notExists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.exists(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot determine Iris datapack ownership marker state in " + directory.getPath());
        }
        if (Files.isSymbolicLink(markerPath)
                || !Files.isRegularFile(markerPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
        }
        if (marker.length() > MAX_OWNERSHIP_BYTES) {
            throw new IOException("Oversized Iris datapack ownership marker in " + directory.getPath());
        }
        try {
            Ownership ownership = DatapackSupport.GSON.fromJson(DatapackSupport.readBoundedUtf8(
                    marker.toPath(), MAX_OWNERSHIP_BYTES, "Iris datapack ownership marker"), Ownership.class);
            if (ownership == null || ownership.schemaVersion != OWNERSHIP_SCHEMA
                    || !DatapackArchive.isValidManagedId(ownership.id) || ownership.url == null || ownership.url.isBlank()
                    || ownership.contentHash == null || ownership.contentHash.isBlank()) {
                throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
            }
            return ownership;
        } catch (RuntimeException e) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath(), e);
        }
    }

    static String ownershipMarkerFingerprint(File directory) throws IOException {
        Path marker = new File(directory, OWNERSHIP_MARKER).toPath();
        if (Files.notExists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return "absent";
        }
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot determine Iris datapack ownership marker state in " + directory.getPath());
        }
        if (Files.isSymbolicLink(marker)
                || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid Iris datapack ownership marker in " + directory.getPath());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + DatapackSupport.hex(digest.digest(DatapackSupport.readBoundedBytes(
                    marker, MAX_OWNERSHIP_BYTES, "Iris datapack ownership marker")));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    static void validateManagedDirectory(File directory, String id) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing datapack directory " + directory.getPath());
        }
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Refusing symbolic-link datapack directory " + directory.getPath());
        }
        DatapackPackMetadata.validatePackMetadata(directory);
        rejectSymbolicLinks(directory);
        Ownership ownership = readOwnershipOrNull(directory);
        if (ownership == null) {
            throw new IOException("Datapack is not Iris-managed: " + directory.getPath());
        }
        if (!id.equals(ownership.id)) {
            throw new IOException("Datapack ownership mismatch at " + directory.getPath());
        }
        removeFinderMetadata(directory);
    }

    static void rejectSymbolicLinks(File root) throws IOException {
        try (Stream<Path> paths = Files.walk(root.toPath())) {
            Path symbolicLink = paths.filter(Files::isSymbolicLink).findFirst().orElse(null);
            if (symbolicLink != null) {
                throw new IOException("Datapack contains a symbolic link: " + symbolicLink);
            }
        }
    }

    static boolean sameDatapackVolume(
            Path root,
            FileStore rootStore,
            Path entry
    ) throws IOException {
        return DatapackScratchRecovery.sameScratchVolume(root, rootStore, entry, Files.getFileStore(entry));
    }

    static String directoryHash(File root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            Path rootMarker = rootPath.resolve(OWNERSHIP_MARKER);
            FileStore rootStore = Files.getFileStore(rootPath);
            List<Path> entries = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(rootPath)) {
                Iterator<Path> iterator = paths.iterator();
                int pathCount = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (path.equals(rootPath) || path.equals(rootMarker)) {
                        continue;
                    }
                    if (isFinderMetadata(path)) {
                        if (Files.isSymbolicLink(path)
                                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                            throw new IOException("Suspicious Finder metadata in datapack: " + path);
                        }
                        continue;
                    }
                    pathCount++;
                    if (pathCount > MAX_MANAGED_PATHS) {
                        throw new IOException("Datapack contains more than " + MAX_MANAGED_PATHS + " paths");
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Datapack contains a symbolic link: " + path);
                    }
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Datapack contains an unsupported filesystem entry: " + path);
                    }
                    entries.add(path);
                }
            }
            entries.sort(Comparator.comparing(path -> rootPath.relativize(path).toString()));
            byte[] buffer = new byte[HASH_BUFFER_BYTES];
            long totalBytes = 0;
            for (Path entry : entries) {
                String relative = rootPath.relativize(entry).toString().replace(File.separatorChar, '/');
                byte[] relativeBytes = relative.getBytes(StandardCharsets.UTF_8);
                BasicFileAttributes attributes = Files.readAttributes(
                        entry,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );
                if (attributes.isSymbolicLink()
                        || attributes.isOther()
                        || !attributes.isDirectory() && !attributes.isRegularFile()) {
                    throw new IOException("Datapack entry changed while hashing: " + relative);
                }
                if (!DatapackScratchRecovery.sameScratchVolume(rootPath, rootStore, entry, Files.getFileStore(entry))) {
                    throw new IOException("Datapack entry crosses a filesystem boundary: " + entry);
                }
                boolean directory = attributes.isDirectory();
                digest.update((byte) (directory ? 1 : 2));
                DatapackSupport.updateDigestInt(digest, relativeBytes.length);
                digest.update(relativeBytes);
                if (!directory) {
                    long expectedBytes = attributes.size();
                    if (expectedBytes > DatapackArchive.MAX_ENTRY_BYTES) {
                        throw new IOException("Datapack file exceeds " + DatapackArchive.MAX_ENTRY_BYTES + " bytes: " + relative);
                    }
                    totalBytes += expectedBytes;
                    if (totalBytes > DatapackArchive.MAX_EXPANDED_BYTES) {
                        throw new IOException("Datapack contents exceed " + DatapackArchive.MAX_EXPANDED_BYTES + " bytes");
                    }
                    DatapackSupport.updateDigestLong(digest, expectedBytes);
                    long entryBytes = 0;
                    try (InputStream input = Files.newInputStream(
                            entry,
                            StandardOpenOption.READ,
                            LinkOption.NOFOLLOW_LINKS
                    )) {
                        int length;
                        while ((length = input.read(buffer)) > 0) {
                            entryBytes += length;
                            if (entryBytes > expectedBytes) {
                                throw new IOException("Datapack file changed while hashing: " + relative);
                            }
                            digest.update(buffer, 0, length);
                        }
                    }
                    if (entryBytes != expectedBytes) {
                        throw new IOException("Datapack file changed while hashing: " + relative);
                    }
                }
            }
            return DatapackSupport.hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    static void removeFinderMetadata(File root) throws IOException {
        List<Path> entries;
        try (Stream<Path> paths = Files.walk(root.toPath())) {
            entries = paths.limit(MAX_MANAGED_PATHS + 1L).toList();
        }
        if (entries.size() > MAX_MANAGED_PATHS) {
            throw new IOException("Datapack contains more than " + MAX_MANAGED_PATHS + " paths");
        }
        for (Path path : entries) {
            if (!isFinderMetadata(path)) {
                continue;
            }
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Suspicious Finder metadata in datapack: " + path);
            }
            Files.delete(path);
        }
    }

    static boolean isFinderMetadata(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && FINDER_METADATA.equals(fileName.toString());
    }

    static PackResources scanPackResources(File root) throws IOException {
        TreeSet<String> structureKeys = new TreeSet<>();
        TreeSet<String> structureSetKeys = new TreeSet<>();
        TreeSet<String> templateKeys = new TreeSet<>();
        Path dataRoot = new File(root, "data").toPath();
        if (!Files.isDirectory(dataRoot, LinkOption.NOFOLLOW_LINKS)) {
            return new PackResources(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        try (Stream<Path> paths = Files.walk(dataRoot)) {
            for (Path path : paths.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).toList()) {
                Path relative = dataRoot.relativize(path);
                if (relative.getNameCount() < 3) {
                    continue;
                }
                String namespace = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                String normalized = relative.subpath(1, relative.getNameCount()).toString().replace(File.separatorChar, '/');
                addResourceKey(structureKeys, namespace, normalized, "worldgen/structure/", ".json");
                addResourceKey(structureKeys, namespace, normalized, "worldgen/structures/", ".json");
                addResourceKey(structureSetKeys, namespace, normalized, "worldgen/structure_set/", ".json");
                addResourceKey(structureSetKeys, namespace, normalized, "worldgen/structure_sets/", ".json");
                addResourceKey(templateKeys, namespace, normalized, "structure/", ".nbt");
                addResourceKey(templateKeys, namespace, normalized, "structures/", ".nbt");
            }
        }
        return new PackResources(
                new ArrayList<>(structureKeys),
                new ArrayList<>(structureSetKeys),
                new ArrayList<>(templateKeys));
    }

    static void addResourceKey(Set<String> keys, String namespace, String path, String prefix, String suffix) {
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            return;
        }
        String resourcePath = path.substring(prefix.length(), path.length() - suffix.length());
        if (!resourcePath.isBlank()) {
            keys.add(namespace + ":" + resourcePath);
        }
    }

    static boolean deleteOwnedDirectory(File directory, String id) throws IOException {
        Path path = directory.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing non-directory or symbolic-link target " + directory.getPath());
        }
        Ownership ownership = readOwnership(directory);
        if (!id.equals(ownership.id)) {
            throw new IOException("Ownership marker belongs to '" + ownership.id + "'");
        }
        removeFinderMetadata(directory);
        if (!Objects.equals(ownership.contentHash, directoryHash(directory))) {
            throw new IOException("Refusing to delete modified or corrupt Iris-managed datapack " + directory.getPath());
        }
        File parent = Objects.requireNonNull(directory.getParentFile(), "managed datapack parent");
        DatapackInstall.validateInstallTree(directory, parent, "Managed datapack deletion");
        IO.delete(directory);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not delete " + directory.getPath());
        }
        return true;
    }
}
