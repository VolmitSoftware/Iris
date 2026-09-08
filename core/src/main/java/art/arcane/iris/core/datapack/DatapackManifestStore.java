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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;

final class DatapackManifestStore {
    static final long MAX_MANIFEST_BYTES = 16L * 1024L * 1024L;

    private DatapackManifestStore() {
    }

    static List<String> copyList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    static Entry copyEntry(Entry source) {
        Entry resolved = Objects.requireNonNull(source, "Datapack manifest entry must not be null");
        Entry copy = new Entry();
        copy.url = resolved.url;
        copy.id = resolved.id;
        copy.versionId = resolved.versionId;
        copy.versionNumber = resolved.versionNumber;
        copy.sha1 = resolved.sha1;
        copy.filename = resolved.filename;
        copy.etag = resolved.etag;
        copy.lastModified = resolved.lastModified;
        copy.installedEpoch = resolved.installedEpoch;
        copy.structuresImported = resolved.structuresImported;
        copy.stagingMetadata = resolved.stagingMetadata;
        copy.structureKeys = new ArrayList<>(copyList(resolved.structureKeys));
        copy.templateKeys = new ArrayList<>(copyList(resolved.templateKeys));
        copy.installMetadata = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.installMetadata, Map::of));
        copy.importedTargets = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.importedTargets, Map::of));
        copy.importAttempts = new HashMap<>(Objects.requireNonNullElseGet(
                resolved.importAttempts, Map::of));
        copy.importedBundles = new HashMap<>();
        if (resolved.importedBundles != null) {
            for (Map.Entry<String, Map<String, String>> bundle : resolved.importedBundles.entrySet()) {
                copy.importedBundles.put(bundle.getKey(), new HashMap<>(bundle.getValue()));
            }
        }
        return copy;
    }

    static Manifest readManifest(File root) {
        File file = new File(root, "manifest.json");
        Manifest manifest = null;
        boolean recoverFromStaging = false;
        Path path = file.toPath();
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            manifest = new Manifest();
        } else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            IrisLogging.error("Unreadable datapack manifest " + file.getPath()
                    + "; moving the non-regular path aside before recovering Iris-managed staging");
            recoverFromStaging = quarantine(path);
        } else {
            try {
                String json = DatapackSupport.readBoundedUtf8(path, MAX_MANIFEST_BYTES, "Datapack manifest");
                manifest = DatapackSupport.GSON.fromJson(json, Manifest.class);
                if (manifest == null) {
                    throw new IOException("Datapack manifest is empty");
                }
            } catch (Exception e) {
                IrisLogging.reportError("Unreadable datapack manifest " + file.getPath()
                        + "; moving it aside before recovering Iris-managed staging", e);
                recoverFromStaging = quarantine(file.toPath());
            }
        }
        if (manifest == null) {
            manifest = new Manifest();
        }
        normalizeManifest(manifest);
        if (recoverFromStaging) {
            recoverManifestFromStaging(root, manifest);
        }
        return manifest;
    }

    static boolean quarantine(Path file) {
        try {
            DatapackSupport.move(file, file.resolveSibling(file.getFileName().toString() + ".corrupt-" + System.currentTimeMillis()));
            return true;
        } catch (IOException e) {
            IrisLogging.reportError("Failed to move aside corrupt datapack manifest " + file, e);
            return false;
        }
    }

    static void normalizeManifest(Manifest manifest) {
        if (manifest.entries == null) {
            manifest.entries = new ArrayList<>();
            return;
        }
        List<Entry> normalized = new ArrayList<>();
        Set<String> urls = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Entry entry : manifest.entries) {
            if (entry == null || entry.url == null || entry.url.isBlank() || entry.id == null || entry.id.isBlank()) {
                continue;
            }
            entry.url = entry.url.trim();
            if (!DatapackArchive.isValidManagedId(entry.id)) {
                IrisLogging.warn("Ignoring datapack manifest entry with invalid id '" + entry.id + "'");
                continue;
            }
            entry.structureKeys = normalizeKeys(entry.structureKeys);
            entry.templateKeys = normalizeKeys(entry.templateKeys);
            entry.stagingMetadata = entry.stagingMetadata == null ? "" : entry.stagingMetadata.trim();
            entry.installMetadata = normalizeImportedTargets(entry.installMetadata);
            entry.importedTargets = normalizeImportedTargets(entry.importedTargets);
            entry.importAttempts = normalizeImportedTargets(entry.importAttempts);
            entry.importedBundles = normalizeImportedBundles(entry.importedBundles);
            if (!urls.add(entry.url) || !ids.add(entry.id)) {
                IrisLogging.warn("Ignoring duplicate datapack manifest entry for id '" + entry.id + "' and url " + entry.url);
                continue;
            }
            normalized.add(entry);
        }
        manifest.entries = normalized;
    }

    static void recoverManifestFromStaging(File root, Manifest manifest) {
        File staging = new File(root, "staging");
        File[] directories = staging.listFiles(File::isDirectory);
        if (directories == null) {
            return;
        }
        for (File directory : directories) {
            try {
                Ownership ownership = DatapackOwnership.readOwnershipOrNull(directory);
                if (ownership == null || !directory.getName().equals(ownership.id)
                        || manifest.find(ownership.url) != null || manifest.findById(ownership.id) != null) {
                    continue;
                }
                DatapackOwnership.validateManagedDirectory(directory, ownership.id);
                if (!Objects.equals(ownership.contentHash, DatapackOwnership.directoryHash(directory))) {
                    IrisLogging.warn("Ignoring corrupt Iris-managed datapack staging at " + directory.getPath());
                    continue;
                }
                Entry recovered = ownership.toEntry();
                recovered.installedEpoch = directory.lastModified();
                manifest.put(recovered);
                IrisLogging.warn("Recovered Iris-managed datapack manifest entry '" + recovered.id + "' from staging.");
            } catch (IOException e) {
                IrisLogging.warn("Ignoring orphan datapack staging at " + directory.getPath() + ": " + e.getMessage());
            }
        }
    }

    static List<String> normalizeKeys(List<String> keys) {
        TreeSet<String> normalized = new TreeSet<>();
        if (keys != null) {
            for (String key : keys) {
                if (key != null && !key.isBlank()) {
                    normalized.add(key.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    static Map<String, String> normalizeImportedTargets(Map<String, String> targets) {
        Map<String, String> normalized = new HashMap<>();
        if (targets == null) {
            return normalized;
        }
        for (Map.Entry<String, String> target : targets.entrySet()) {
            if (target.getKey() != null && !target.getKey().isBlank()
                    && target.getValue() != null && !target.getValue().isBlank()) {
                normalized.put(target.getKey(), target.getValue());
            }
        }
        return normalized;
    }

    static Map<String, Map<String, String>> normalizeImportedBundles(
            Map<String, Map<String, String>> targets
    ) {
        Map<String, Map<String, String>> normalized = new HashMap<>();
        if (targets == null) {
            return normalized;
        }
        for (Map.Entry<String, Map<String, String>> target : targets.entrySet()) {
            if (target.getKey() == null || target.getKey().isBlank() || target.getValue() == null) {
                continue;
            }
            Map<String, String> bundles = new TreeMap<>();
            for (Map.Entry<String, String> bundle : target.getValue().entrySet()) {
                if (bundle.getKey() != null && !bundle.getKey().isBlank()
                        && bundle.getValue() != null && !bundle.getValue().isBlank()) {
                    bundles.put(bundle.getKey(), bundle.getValue());
                }
            }
            normalized.put(target.getKey(), bundles);
        }
        return normalized;
    }

    static void writeManifest(File root, Manifest manifest) {
        try {
            writeManifestChecked(root, manifest);
        } catch (IOException e) {
            IrisLogging.reportError("Failed to write datapack manifest "
                    + new File(root, "manifest.json").toPath(), e);
        }
    }

    static void writeManifestChecked(File root, Manifest manifest) throws IOException {
        try (ManifestWrite write = prepareManifestWrite(root, manifest)) {
            write.publish();
        }
    }

    static ManifestWrite prepareManifestWrite(File root, Manifest manifest) throws IOException {
        Path file = new File(root, "manifest.json").toPath();
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "manifest", ".json.tmp");
        Path rollback = null;
        try {
            byte[] content = DatapackSupport.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
            if (content.length > MAX_MANIFEST_BYTES) {
                throw new IOException("Datapack manifest exceeds " + MAX_MANIFEST_BYTES + " bytes");
            }
            Files.write(temp, content, StandardOpenOption.TRUNCATE_EXISTING);
            DatapackSupport.forceFile(temp);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                    throw new IOException("Datapack manifest is not a regular file: " + file);
                }
                rollback = Files.createTempFile(parent, "manifest-rollback", ".json.tmp");
                Files.copy(file, rollback, StandardCopyOption.REPLACE_EXISTING);
                DatapackSupport.forceFile(rollback);
            }
            return new ManifestWrite(temp, file, rollback);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            if (rollback != null) {
                try {
                    Files.deleteIfExists(rollback);
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw e;
        }
    }

    static Manifest readCommittedManifest(File root) throws IOException {
        Path manifestPath = new File(root, "manifest.json").toPath();
        if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return new Manifest();
        }
        if (Files.isSymbolicLink(manifestPath)
                || !Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datapack manifest is not a regular file: " + manifestPath);
        }
        Manifest manifest;
        try {
            manifest = DatapackSupport.GSON.fromJson(
                    DatapackSupport.readBoundedUtf8(manifestPath, MAX_MANIFEST_BYTES, "Datapack manifest"),
                    Manifest.class
            );
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack manifest " + manifestPath, e);
        }
        if (manifest == null) {
            throw new IOException("Empty datapack manifest " + manifestPath);
        }
        normalizeManifest(manifest);
        return manifest;
    }
}
