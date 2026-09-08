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

import art.arcane.iris.core.datapack.DatapackIngestService.ReapplyOutcome;
import art.arcane.iris.core.datapack.DatapackIngestService.Entry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class DatapackStagingReapply {

    private DatapackStagingReapply() {
    }

    static ReapplyOutcome reapplyFromStagingLocked(KList<File> worldFolders) {
        File root = IrisPlatforms.get().dataFolder("datapacks");
        ReapplyOutcome recovery = recoverBeforeReapplyOutcome(root, worldFolders);
        if (!recovery.succeeded()) {
            return reportReapplyFailure(recovery);
        }
        File stagingDir = IrisPlatforms.get().dataFolderNoCreate("datapacks", "staging");
        ReapplyOutcome repair = reapplyStagingRootOutcome(
                root,
                stagingDir,
                worldFolders,
                DatapackPackMetadata.resolveStripOverrides());
        if (!repair.succeeded()) {
            return reportReapplyFailure(repair);
        }
        return ReapplyOutcome.success(recovery.recovered(), repair.repaired());
    }

    static boolean reapplyStagingRoot(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        return reportReapplyFailure(reapplyStagingRootOutcome(
                root,
                stagingDir,
                worldFolders,
                stripOverrides)).succeeded();
    }

    static ReapplyOutcome reapplyStagingRootOutcome(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        Manifest manifest = DatapackManifestStore.readManifest(root);
        if (stagingDir == null
                || !Files.exists(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            if (manifest.entries.isEmpty()) {
                return ReapplyOutcome.success(false, false);
            }
            File missing = stagingDir == null ? new File(root, "staging") : stagingDir;
            return ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is missing at " + missing.getPath()));
        }
        if (Files.isSymbolicLink(stagingDir.toPath())
                || !Files.isDirectory(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is not a safe directory at " + stagingDir.getPath()));
        }
        return reapplyStagedDirectoriesOutcome(
                root, stagingDir, worldFolders, stripOverrides, manifest);
    }

    static boolean reapplyStagedDirectories(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides
    ) {
        if (Files.isSymbolicLink(stagingDir.toPath())
                || !Files.isDirectory(stagingDir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return reportReapplyFailure(ReapplyOutcome.failed(new IOException(
                    "Managed datapack staging is not a safe directory at "
                            + stagingDir.getPath()))).succeeded();
        }
        return reportReapplyFailure(reapplyStagedDirectoriesOutcome(
                root,
                stagingDir,
                worldFolders,
                stripOverrides,
                DatapackManifestStore.readManifest(root))).succeeded();
    }

    static ReapplyOutcome reapplyStagedDirectoriesOutcome(
            File root,
            File stagingDir,
            KList<File> worldFolders,
            boolean stripOverrides,
            Manifest manifest
    ) {
        File[] staged = stagingDir.listFiles(File::isDirectory);
        if (staged == null) {
            return ReapplyOutcome.failed(new IOException(
                    "Unable to enumerate managed datapack staging at " + stagingDir.getPath()));
        }
        boolean repaired = false;
        IOException failure = null;
        for (Entry entry : manifest.entries) {
            File stagedDir = new File(stagingDir, entry.id);
            if (isRecordedUnchangedInstall(stagedDir, worldFolders, entry, stripOverrides)) {
                continue;
            }
            if (!DatapackStagingGuard.isUsableStaging(stagedDir, entry)) {
                forgetInstallMetadata(entry);
                failure = DatapackSupport.appendFailure(failure, new IOException(
                        "Managed datapack staging is unusable for '" + entry.id
                                + "' at " + stagedDir.getPath()));
                continue;
            }
            try {
                InstallResult result = DatapackInstall.install(stagedDir, worldFolders, entry, stripOverrides);
                if (result.changed()) {
                    repaired = true;
                    IrisLogging.warn("Repaired installed datapack '" + entry.id
                            + "' from Iris staging before datapack compilation.");
                }
                recordInstallMetadata(stagedDir, worldFolders, entry);
            } catch (IOException e) {
                forgetInstallMetadata(entry);
                failure = DatapackSupport.appendFailure(failure, e);
            }
        }
        DatapackManifestStore.writeManifest(root, manifest);
        return failure == null
                ? ReapplyOutcome.success(false, repaired)
                : ReapplyOutcome.failed(failure);
    }

    static ReapplyOutcome reportReapplyFailure(ReapplyOutcome outcome) {
        if (!outcome.succeeded()) {
            IrisLogging.reportError(
                    "External datapack recovery or staging repair failed.",
                    outcome.failure().orElseThrow());
        }
        return outcome;
    }

    static boolean isRecordedUnchangedInstall(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides
    ) {
        if (entry.stagingMetadata == null || entry.stagingMetadata.isBlank()
                || entry.installMetadata == null || entry.installMetadata.size() != worldFolders.size()) {
            return false;
        }
        try {
            if (!isRecordedManagedDirectory(stagedDir, entry)
                    || !entry.stagingMetadata.equals(metadataDigest(stagedDir))) {
                return false;
            }
            for (File worldFolder : worldFolders) {
                File target = new File(worldFolder, entry.id);
                if (!isRecordedManagedDirectory(target, entry)
                        || new File(target, DatapackPackMetadata.OVERRIDES_STRIPPED_MARKER).isFile() != stripOverrides) {
                    return false;
                }
                String recorded = entry.installMetadata.get(installMetadataKey(target));
                if (recorded == null || !recorded.equals(metadataDigest(target))) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            IrisLogging.reportError("Managed datapack '" + entry.id
                    + "' requires full verification", e);
            return false;
        }
    }

    static boolean isRecordedManagedDirectory(File directory, Entry entry) throws IOException {
        Path path = directory.toPath();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            return false;
        }
        if (!new File(directory, "pack.mcmeta").isFile()) {
            return false;
        }
        Ownership ownership = DatapackOwnership.readOwnershipOrNull(directory);
        return ownership != null
                && DatapackInstall.ownershipSourceMatches(ownership, entry)
                && Objects.equals(ownership.versionId, entry.versionId)
                && Objects.equals(ownership.versionNumber, entry.versionNumber)
                && Objects.equals(ownership.sha1, entry.sha1);
    }

    static void recordInstallMetadata(File stagedDir, KList<File> worldFolders, Entry entry) {
        try {
            Map<String, String> recorded = new HashMap<>();
            for (File worldFolder : worldFolders) {
                File target = new File(worldFolder, entry.id);
                recorded.put(installMetadataKey(target), metadataDigest(target));
            }
            entry.stagingMetadata = metadataDigest(stagedDir);
            entry.installMetadata = recorded;
        } catch (IOException e) {
            IrisLogging.reportError("Unable to record managed datapack metadata for '" + entry.id + "'", e);
            forgetInstallMetadata(entry);
        }
    }

    static void forgetInstallMetadata(Entry entry) {
        entry.stagingMetadata = "";
        entry.installMetadata = new HashMap<>();
    }

    static String installMetadataKey(File target) {
        return target.toPath().toAbsolutePath().normalize().toString();
    }

    static String metadataDigest(File root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            List<MetadataEntry> entries = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(rootPath)) {
                Iterator<Path> iterator = paths.iterator();
                int pathCount = 0;
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (path.equals(rootPath) || DatapackOwnership.isFinderMetadata(path)) {
                        continue;
                    }
                    pathCount++;
                    if (pathCount > DatapackOwnership.MAX_MANAGED_PATHS) {
                        throw new IOException("Datapack contains more than " + DatapackOwnership.MAX_MANAGED_PATHS + " paths");
                    }
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("Datapack contains a symbolic link: " + path);
                    }
                    String relativePath = rootPath.relativize(path).toString();
                    entries.add(new MetadataEntry(path, relativePath));
                }
            }
            entries.sort(Comparator.comparing(MetadataEntry::relativePath));
            for (MetadataEntry entry : entries) {
                String relative = entry.relativePath().replace(File.separatorChar, '/');
                byte[] relativeBytes = relative.getBytes(StandardCharsets.UTF_8);
                BasicFileAttributes attributes = Files.readAttributes(
                        entry.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isDirectory() && !attributes.isRegularFile()) {
                    throw new IOException("Datapack contains an unsupported filesystem entry: " + entry.path());
                }
                digest.update((byte) (attributes.isDirectory() ? 1 : 2));
                DatapackSupport.updateDigestInt(digest, relativeBytes.length);
                digest.update(relativeBytes);
                if (!attributes.isDirectory()) {
                    DatapackSupport.updateDigestLong(digest, attributes.size());
                    DatapackSupport.updateDigestLong(digest, attributes.lastModifiedTime().toMillis());
                }
            }
            return DatapackSupport.hex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm unavailable", e);
        }
    }

    static boolean recoverBeforeReapply(File root, List<File> worldFolders) {
        return reportReapplyFailure(recoverBeforeReapplyOutcome(root, worldFolders)).succeeded();
    }

    static ReapplyOutcome recoverBeforeReapplyOutcome(File root, List<File> worldFolders) {
        try {
            return ReapplyOutcome.success(DatapackScratchRecovery.recoverTransactions(root, worldFolders), false);
        } catch (IOException e) {
            return ReapplyOutcome.failed(e);
        }
    }
}
