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

import art.arcane.iris.pack.datapack.DatapackIngestService.Entry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.pack.datapack.ModrinthResolver.ResolvedDatapack;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.io.ZipUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DatapackArchive {
    static final String USER_AGENT = "VolmitSoftware/Iris (datapack-ingest)";

    static final int MAX_REDIRECTS = 5;

    static final int MAX_ARCHIVE_ENTRIES = 100_000;

    static final int MAX_CACHE_FILES = 32;

    static final long MAX_DOWNLOAD_BYTES = 256L * 1024L * 1024L;

    static final long MAX_EXPANDED_BYTES = 1024L * 1024L * 1024L;

    static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;

    static final long MAX_CACHE_BYTES = 1024L * 1024L * 1024L;

    static final Set<String> RESERVED_IDS = Set.of("iris");

    private DatapackArchive() {
    }

    static File extractArchive(File zip, File stagingRoot, Entry entry) throws IOException {
        DatapackSupport.ensureScratchDirectory(stagingRoot, "datapack staging");
        File pending = new File(stagingRoot, ".pending-" + entry.id + "-" + UUID.randomUUID());
        try {
            if (!pending.mkdirs() && !pending.isDirectory()) {
                throw new IOException("Couldn't create datapack extraction directory " + pending.getPath());
            }
            ZipUtils.unzipFile(zip, pending, MAX_ARCHIVE_ENTRIES, MAX_EXPANDED_BYTES, MAX_ENTRY_BYTES);
            flattenIfWrapped(pending);
            DatapackPackMetadata.validatePackMetadata(pending);
            PackResources resources = DatapackOwnership.scanPackResources(pending);
            entry.structureKeys = resources.structureKeys();
            entry.templateKeys = resources.templateKeys();
            DatapackOwnership.writeOwnership(pending, entry);
            return pending;
        } catch (UncheckedIOException e) {
            IOException failure = e.getCause();
            cleanupFailedExtraction(pending, failure);
            throw failure;
        } catch (IOException | RuntimeException e) {
            cleanupFailedExtraction(pending, e);
            throw e;
        }
    }

    static void cleanupFailedExtraction(File pending, Throwable failure) {
        try {
            DatapackInstall.deleteInstallScratch(pending, "failed datapack extraction");
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    static void cleanupExtractedStaging(File extractedDir) {
        try {
            DatapackInstall.deleteInstallScratch(extractedDir, "extracted datapack staging");
        } catch (IOException e) {
            IrisLogging.warn("Preserving extracted datapack staging for restart cleanup: "
                    + extractedDir.getPath() + " (" + e.getMessage() + ")");
        }
    }

    static void flattenIfWrapped(File dir) throws IOException {
        if (new File(dir, "pack.mcmeta").isFile()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        File singleDir = null;
        int dirCount = 0;
        int fileCount = 0;
        for (File child : children) {
            if (child.isDirectory()) {
                dirCount++;
                singleDir = child;
            } else {
                fileCount++;
            }
        }
        if (dirCount != 1 || fileCount != 0 || singleDir == null || !new File(singleDir, "pack.mcmeta").isFile()) {
            return;
        }
        File[] inner = singleDir.listFiles();
        if (inner != null) {
            for (File item : inner) {
                File moved = new File(dir, item.getName());
                if (item.renameTo(moved)) {
                    continue;
                }
                if (item.isDirectory()) {
                    IO.copyDirectory(item.toPath(), moved.toPath());
                } else {
                    Files.copy(item.toPath(), moved.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        DatapackInstall.deleteInstallScratch(singleDir, "wrapped datapack extraction");
    }

    static DownloadResult download(String url, File dest, String etag, String lastModified) throws IOException {
        URI current = DatapackStartupValidation.parseSourceUri(url);
        if (current == null) {
            throw new IOException("Empty datapack URL");
        }
        if ("file".equalsIgnoreCase(current.getScheme())) {
            return copyLocalDatapack(current, dest);
        }
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            if (!"http".equalsIgnoreCase(current.getScheme()) && !"https".equalsIgnoreCase(current.getScheme())) {
                throw new IOException("Datapack URL must use HTTP or HTTPS: " + current);
            }
            URL target = current.toURL();
            HttpURLConnection connection = (HttpURLConnection) target.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (etag != null && !etag.isBlank()) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            if (lastModified != null && !lastModified.isBlank()) {
                connection.setRequestProperty("If-Modified-Since", lastModified);
            }
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(false);
            try {
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    String responseEtag = headerOrFallback(connection, "ETag", etag);
                    String responseLastModified = headerOrFallback(connection, "Last-Modified", lastModified);
                    return new DownloadResult(true, responseEtag, responseLastModified);
                }
                if (code / 100 == 3) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw new IOException("Redirect without a location header from " + current);
                    }
                    try {
                        current = current.resolve(new URI(location));
                    } catch (URISyntaxException e) {
                        throw new IOException("Invalid redirect location from " + current + ": " + location, e);
                    }
                    continue;
                }
                if (code != 200) {
                    throw new IOException("HTTP " + code + " downloading " + current);
                }
                long declaredLength = connection.getContentLengthLong();
                if (declaredLength > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Datapack download exceeds " + MAX_DOWNLOAD_BYTES + " bytes");
                }

                File parent = dest.getParentFile();
                Path parentPath = parent == null ? Path.of(".").toAbsolutePath().normalize() : parent.toPath();
                DatapackSupport.ensureScratchDirectory(parentPath.toFile(), "datapack download cache");
                Path temporary = Files.createTempFile(parentPath, dest.getName() + "-", ".part");
                try {
                    long downloaded = 0;
                    String responseEtag = connection.getHeaderField("ETag");
                    String responseLastModified = connection.getHeaderField("Last-Modified");
                    try (InputStream in = connection.getInputStream();
                         OutputStream out = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            downloaded += length;
                            if (downloaded > MAX_DOWNLOAD_BYTES) {
                                throw new IOException("Datapack download exceeds " + MAX_DOWNLOAD_BYTES + " bytes");
                            }
                            out.write(buffer, 0, length);
                        }
                    }
                    DatapackSupport.move(temporary, dest.toPath());
                    return new DownloadResult(false, responseEtag, responseLastModified);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many redirects downloading " + url);
    }

    static DownloadResult copyLocalDatapack(URI source, File destination) throws IOException {
        Path sourcePath = requireLocalDatapackPath(source);
        Path destinationPath = destination.toPath().toAbsolutePath().normalize();
        if (Files.exists(destinationPath, LinkOption.NOFOLLOW_LINKS)
                && Files.isSameFile(sourcePath, destinationPath)) {
            throw new IOException("Local datapack source and cache destination are the same file: " + sourcePath);
        }
        Path parent = destinationPath.getParent();
        if (parent == null) {
            throw new IOException("Local datapack cache destination has no parent: " + destinationPath);
        }
        DatapackSupport.ensureScratchDirectory(parent.toFile(), "datapack download cache");
        BasicFileAttributes before = Files.readAttributes(
                sourcePath,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        Path temporary = Files.createTempFile(parent, destination.getName() + "-", ".part");
        try {
            long copied = 0;
            try (InputStream input = Files.newInputStream(
                    sourcePath,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS);
                 OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[DatapackOwnership.HASH_BUFFER_BYTES];
                int length;
                while ((length = input.read(buffer)) > 0) {
                    copied += length;
                    if (copied > MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Local datapack exceeds " + MAX_DOWNLOAD_BYTES + " bytes: " + sourcePath);
                    }
                    output.write(buffer, 0, length);
                }
            }
            BasicFileAttributes after = Files.readAttributes(
                    sourcePath,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || copied != after.size()) {
                throw new IOException("Local datapack changed while Iris was copying it: " + sourcePath);
            }
            DatapackSupport.move(temporary, destinationPath);
            return new DownloadResult(false, null, null);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static Path requireLocalDatapackPath(URI source) throws IOException {
        if (source == null
                || !"file".equalsIgnoreCase(source.getScheme())
                || source.isOpaque()
                || source.getRawAuthority() != null
                || source.getRawQuery() != null
                || source.getRawFragment() != null) {
            throw new IOException("Datapack file URL must be an absolute local file URI without authority, query, or fragment: " + source);
        }
        Path path;
        try {
            path = Path.of(source).toAbsolutePath().normalize();
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid local datapack file URL: " + source, exception);
        }
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local datapack is not a regular non-symbolic-link file: " + path);
        }
        long size = Files.size(path);
        if (size > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Local datapack exceeds " + MAX_DOWNLOAD_BYTES + " bytes: " + path);
        }
        return path;
    }

    static String headerOrFallback(HttpURLConnection connection, String name, String fallback) {
        String value = connection.getHeaderField(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static String sha1(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, length);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm unavailable", e);
        }
    }

    static String deriveId(ResolvedDatapack resolved) {
        String base = resolved.getProjectSlug();
        if (base == null || base.isBlank()) {
            base = resolved.getFileName();
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
        }
        String id = sanitizeId(base);
        if (resolved.isDirect()) {
            return id + "-" + ModrinthResolver.directIdentity(resolved.getDownloadUrl());
        }
        return id;
    }

    static String sanitizeId(String value) {
        if (value == null) {
            return "datapack";
        }
        String lower = value.toLowerCase(Locale.ROOT).trim();
        StringBuilder builder = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                builder.append(c);
            } else if (c == ' ' || c == '/' || c == '\\') {
                builder.append('-');
            }
        }
        String cleaned = builder.toString().replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^[-_.]+", "").replaceAll("[-_.]+$", "");
        return cleaned.isBlank() ? "datapack" : cleaned;
    }

    static boolean isValidManagedId(String value) {
        return value != null && !value.isBlank() && value.equals(sanitizeId(value))
                && !RESERVED_IDS.contains(value);
    }

    static void pruneCache(File cacheDir) {
        File[] files = cacheDir.listFiles(File::isFile);
        if (files == null) {
            return;
        }
        List<File> archives = new ArrayList<>();
        long totalBytes = 0;
        for (File file : files) {
            if (file.getName().endsWith(".part")) {
                IO.delete(file);
                continue;
            }
            if (file.getName().endsWith(".zip")) {
                archives.add(file);
                totalBytes += Math.max(0, file.length());
            }
        }
        archives.sort(Comparator.comparingLong(File::lastModified));
        int remaining = archives.size();
        for (File archive : archives) {
            if (remaining <= MAX_CACHE_FILES && totalBytes <= MAX_CACHE_BYTES) {
                break;
            }
            long size = Math.max(0, archive.length());
            IO.delete(archive);
            if (!archive.exists()) {
                remaining--;
                totalBytes -= size;
            }
        }
    }
}
