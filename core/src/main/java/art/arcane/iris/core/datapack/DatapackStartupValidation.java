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
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.volmlib.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

final class DatapackStartupValidation {
    static final String STARTUP_VALIDATION_CACHE = "startup-validation.json";

    static final int STARTUP_VALIDATION_SCHEMA = 1;

    static volatile StartupValidationCache activeStartupValidation;

    private DatapackStartupValidation() {
    }

    static StartupValidationCache cacheStartupValidation(
            File root,
            KList<File> worldFolders,
            Path cacheFile,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls
    ) {
        try {
            StartupValidationCache cache = createStartupValidationCache(
                    mcVersion,
                    irisVersion,
                    autoIngest,
                    stripOverrides,
                    urls,
                    startupValidationFingerprint(root, worldFolders, urls));
            writeStartupValidationCache(cacheFile, cache);
            return cache;
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Could not persist external datapack startup validation: "
                    + DatapackSupport.failureMessage(exception));
            return null;
        }
    }

    static void refreshStartupValidationAfterMaintenance(boolean maintenanceChanged) {
        if (!maintenanceChanged) {
            return;
        }
        StartupValidationCache validated = activeStartupValidation;
        if (validated == null || !IrisStartupValidation.isReady()) {
            return;
        }
        KList<String> configured = DatapackIngestService.collectConfiguredImports();
        List<String> urls = configured.stream().sorted().toList();
        boolean autoIngest = IrisSettings.get().getGeneral().autoIngestDatapacks;
        boolean stripOverrides = DatapackPackMetadata.resolveStripOverrides();
        String mcVersion = DatapackSupport.serverMcVersion();
        int irisVersion = IrisPlatforms.get().irisVersionNumber();
        if (!startupValidationContextMatches(
                validated, mcVersion, irisVersion, autoIngest, stripOverrides, urls)) {
            return;
        }
        File root = IrisPlatforms.get().dataFolder("datapacks");
        KList<File> worldFolders = ServerConfigurator.getDatapacksFolder();
        Path cacheFile = new File(root, STARTUP_VALIDATION_CACHE).toPath();
        DatapackTransactions.TRANSACTION_LOCK.lock();
        try {
            DatapackScratchRecovery.recoverTransactions(root, worldFolders);
            StartupValidationCache refreshed = refreshStartupValidationCache(
                    validated, root, worldFolders);
            writeStartupValidationCache(cacheFile, refreshed);
            activeStartupValidation = refreshed;
        } catch (IOException | RuntimeException exception) {
            IrisLogging.warn("Could not refresh external datapack validation after startup maintenance: "
                    + DatapackSupport.failureMessage(exception));
        } finally {
            DatapackTransactions.TRANSACTION_LOCK.unlock();
        }
    }

    static StartupValidationCache refreshStartupValidationCache(
            StartupValidationCache validated,
            File root,
            KList<File> worldFolders
    ) throws IOException {
        Objects.requireNonNull(validated, "Validated external datapack startup state");
        return createStartupValidationCache(
                validated.minecraftVersion,
                validated.irisVersion,
                validated.autoIngest,
                validated.stripOverrides,
                validated.urls,
                startupValidationFingerprint(root, worldFolders, validated.urls));
    }

    static StartupValidationCache createStartupValidationCache(
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls,
            String localFingerprint
    ) {
        StartupValidationCache cache = new StartupValidationCache();
        cache.schemaVersion = STARTUP_VALIDATION_SCHEMA;
        cache.minecraftVersion = Objects.requireNonNullElse(mcVersion, "");
        cache.irisVersion = irisVersion;
        cache.autoIngest = autoIngest;
        cache.stripOverrides = stripOverrides;
        cache.urls = List.copyOf(urls);
        cache.localFingerprint = localFingerprint;
        return cache;
    }

    static boolean startupValidationContextMatches(
            StartupValidationCache cache,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls
    ) {
        return cache != null
                && cache.schemaVersion == STARTUP_VALIDATION_SCHEMA
                && Objects.equals(cache.minecraftVersion, Objects.requireNonNullElse(mcVersion, ""))
                && cache.irisVersion == irisVersion
                && cache.autoIngest == autoIngest
                && cache.stripOverrides == stripOverrides
                && Objects.equals(cache.urls, urls);
    }

    static boolean startupValidationCacheMatches(
            StartupValidationCache cache,
            String mcVersion,
            int irisVersion,
            boolean autoIngest,
            boolean stripOverrides,
            List<String> urls,
            String localFingerprint
    ) {
        return startupValidationContextMatches(
                cache, mcVersion, irisVersion, autoIngest, stripOverrides, urls)
                && localFingerprint != null && !localFingerprint.isBlank()
                && Objects.equals(cache.localFingerprint, localFingerprint);
    }

    static StartupValidationCache readStartupValidationCache(Path cacheFile) {
        if (cacheFile == null
                || Files.isSymbolicLink(cacheFile)
                || !Files.isRegularFile(cacheFile, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            if (Files.size(cacheFile) > DatapackPackMetadata.MAX_METADATA_BYTES) {
                return null;
            }
            StartupValidationCache cache = DatapackSupport.GSON.fromJson(
                    DatapackSupport.readBoundedUtf8(cacheFile, DatapackPackMetadata.MAX_METADATA_BYTES, "External datapack startup validation"),
                    StartupValidationCache.class);
            if (cache == null || cache.urls == null) {
                return null;
            }
            List<String> sortedUrls = cache.urls.stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
            if (sortedUrls.size() != cache.urls.size()
                    || new HashSet<>(sortedUrls).size() != sortedUrls.size()
                    || !sortedUrls.equals(cache.urls)) {
                return null;
            }
            cache.urls = sortedUrls;
            return cache;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    static void writeStartupValidationCache(Path cacheFile, StartupValidationCache cache) throws IOException {
        Path absolute = cacheFile.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(absolute.getParent(), "External datapack startup validation parent");
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".startup-validation-", ".tmp");
        try {
            byte[] content = DatapackSupport.GSON.toJson(cache).getBytes(StandardCharsets.UTF_8);
            if (content.length > DatapackPackMetadata.MAX_METADATA_BYTES) {
                throw new IOException("External datapack startup validation exceeds " + DatapackPackMetadata.MAX_METADATA_BYTES + " bytes");
            }
            Files.write(staged, content, StandardOpenOption.TRUNCATE_EXISTING);
            DatapackSupport.forceFile(staged);
            try {
                Files.move(staged, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            DatapackSupport.forceDirectoryIfSupported(parent);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    static String startupValidationFingerprint(File root, KList<File> worldFolders) throws IOException {
        return startupValidationFingerprint(root, worldFolders, List.of());
    }

    static String startupValidationFingerprint(
            File root,
            KList<File> worldFolders,
            Iterable<String> sources
    ) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path manifestPath = new File(root, "manifest.json").toPath();
            updateFingerprintValue(digest, "manifest");
            if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(manifestPath)
                        || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Invalid datapack manifest path " + manifestPath);
                }
                byte[] manifest = DatapackSupport.readBoundedBytes(
                        manifestPath, DatapackManifestStore.MAX_MANIFEST_BYTES, "Datapack manifest fingerprint");
                DatapackSupport.updateDigestLong(digest, manifest.length);
                digest.update(manifest);
            } else if (Files.notExists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                DatapackSupport.updateDigestLong(digest, -1L);
            } else {
                throw new IOException("Cannot determine datapack manifest state at " + manifestPath);
            }

            Manifest manifest = DatapackManifestStore.readCommittedManifest(root);
            updateDirectoryFingerprint(digest, "staging", new File(root, "staging"));
            updateDirectoryFingerprint(digest, "transactions", new File(root, DatapackTransactions.TRANSACTION_DIRECTORY));
            updateDirectoryFingerprint(
                    digest,
                    "storage-install-scratch",
                    DatapackInstall.installScratchRoot(new File(root, "staging")));

            List<Entry> entries = new ArrayList<>(manifest.entries);
            entries.sort(Comparator.comparing(entry -> entry.id));
            List<File> targets = new ArrayList<>(worldFolders == null ? List.of() : worldFolders);
            targets.sort(Comparator.comparing(file -> file.toPath().toAbsolutePath().normalize().toString()));
            for (File worldFolder : targets) {
                String worldIdentity = worldFolder.toPath().toAbsolutePath().normalize().toString();
                updateFingerprintValue(digest, "world:" + worldIdentity);
                updateDirectoryFingerprint(
                        digest,
                        "world-install-scratch:" + worldIdentity,
                        DatapackInstall.installScratchRoot(worldFolder));
                for (Entry entry : entries) {
                    updateDirectoryFingerprint(
                            digest,
                            "world-pack:" + worldIdentity + ":" + entry.id,
                            new File(worldFolder, entry.id));
                }
            }
            updateLocalSourceFingerprint(digest, sources);
            return DatapackSupport.hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 algorithm unavailable", exception);
        }
    }

    static void updateLocalSourceFingerprint(
            MessageDigest digest,
            Iterable<String> sources
    ) throws IOException {
        if (sources == null) {
            return;
        }
        List<String> localSources = new ArrayList<>();
        for (String source : sources) {
            URI uri = parseSourceUri(source);
            if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
                localSources.add(uri.normalize().toASCIIString());
            }
        }
        localSources.sort(String::compareTo);
        byte[] buffer = new byte[DatapackOwnership.HASH_BUFFER_BYTES];
        for (String source : localSources) {
            Path path = DatapackArchive.requireLocalDatapackPath(parseSourceUri(source));
            updateFingerprintValue(digest, "local-source:" + source);
            try (InputStream input = Files.newInputStream(
                    path,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS)) {
                long bytes = 0;
                int length;
                while ((length = input.read(buffer)) > 0) {
                    bytes += length;
                    if (bytes > DatapackArchive.MAX_DOWNLOAD_BYTES) {
                        throw new IOException("Local datapack exceeds " + DatapackArchive.MAX_DOWNLOAD_BYTES + " bytes: " + path);
                    }
                    digest.update(buffer, 0, length);
                }
                DatapackSupport.updateDigestLong(digest, bytes);
            }
        }
    }

    static URI parseSourceUri(String source) throws IOException {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            return new URI(source.trim());
        } catch (URISyntaxException exception) {
            throw new IOException("Invalid datapack URL " + source, exception);
        }
    }

    static void updateDirectoryFingerprint(
            MessageDigest digest,
            String identity,
            File directory
    ) throws IOException {
        updateFingerprintValue(digest, identity);
        Path path = directory.toPath();
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            updateFingerprintValue(digest, "missing");
            return;
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid external datapack validation directory " + path);
        }
        updateFingerprintValue(digest, DatapackOwnership.directoryHash(directory));
        Path ownership = new File(directory, DatapackOwnership.OWNERSHIP_MARKER).toPath();
        if (Files.isRegularFile(ownership, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(ownership)) {
            byte[] marker = DatapackSupport.readBoundedBytes(
                    ownership, DatapackOwnership.MAX_OWNERSHIP_BYTES, "External datapack ownership fingerprint");
            DatapackSupport.updateDigestLong(digest, marker.length);
            digest.update(marker);
        } else {
            DatapackSupport.updateDigestLong(digest, -1L);
        }
    }

    static void updateFingerprintValue(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8);
        DatapackSupport.updateDigestInt(digest, bytes.length);
        digest.update(bytes);
    }
}
