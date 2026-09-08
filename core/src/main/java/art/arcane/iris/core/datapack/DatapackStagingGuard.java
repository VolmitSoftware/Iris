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

import art.arcane.iris.core.datapack.DatapackIngestService.Report;
import art.arcane.iris.core.datapack.DatapackIngestService.Entry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DatapackStagingGuard {

    private DatapackStagingGuard() {
    }

    static VerifiedStagingInstall authorizeVerifiedStagingInstall(
            File root,
            File stagingRoot,
            File verifiedSource,
            Entry entry
    ) throws IOException {
        Path normalizedRoot = DatapackSupport.requireDirectoryIdentity(root, "datapack storage root");
        Path normalizedStagingRoot = DatapackSupport.requireDirectoryIdentity(stagingRoot, "datapack staging root");
        DatapackSupport.requireNoSymbolicLinkComponents(normalizedRoot, "datapack storage root");
        DatapackSupport.requireNoSymbolicLinkComponents(normalizedStagingRoot, "datapack staging root");
        Path expectedStagingRoot = normalizedRoot.resolve("staging").normalize();
        if (!normalizedStagingRoot.equals(expectedStagingRoot)
                || !Files.isSameFile(normalizedStagingRoot, expectedStagingRoot)) {
            throw new IOException("Verified datapack staging root is not Iris's canonical staging directory");
        }
        Path normalizedSource = DatapackSupport.requireDirectoryIdentity(verifiedSource, "verified datapack extraction");
        DatapackSupport.requireNoSymbolicLinkComponents(normalizedSource, "verified datapack extraction");
        if (!Objects.equals(normalizedSource.getParent(), normalizedStagingRoot)
                || !Files.isSameFile(normalizedSource.getParent(), normalizedStagingRoot)) {
            throw new IOException("Verified datapack extraction is outside Iris's canonical staging directory");
        }
        verifyPendingExtractionName(normalizedSource.getFileName().toString(), entry.id);
        DatapackOwnership.validateManagedDirectory(verifiedSource, entry.id);
        DatapackScratchRecovery.validateScratchTree(normalizedSource);
        if (!DatapackScratchRecovery.sameScratchVolume(normalizedSource, normalizedStagingRoot)) {
            throw new IOException("Verified datapack extraction crosses a filesystem boundary");
        }
        String desiredHash = DatapackOwnership.directoryHash(verifiedSource);
        Ownership ownership = DatapackOwnership.readOwnership(verifiedSource);
        if (!DatapackInstall.ownershipMetadataMatches(ownership, entry, desiredHash)) {
            throw new IOException("Verified datapack extraction does not match the resolved archive metadata");
        }
        Manifest committedManifest = DatapackManifestStore.readCommittedManifest(root);
        Entry committed = committedManifest.findById(entry.id);
        boolean legacyReplacementAuthorized = committed != null
                && Objects.equals(committed.id, entry.id)
                && Objects.equals(committed.url, entry.url);
        LegacyStagingSnapshot legacyStagingSnapshot = captureLegacyStagingSnapshot(
                normalizedStagingRoot, entry.id, legacyReplacementAuthorized);
        return new VerifiedStagingInstall(
                normalizedRoot,
                normalizedStagingRoot,
                normalizedSource,
                entry,
                desiredHash,
                committed,
                legacyReplacementAuthorized,
                legacyStagingSnapshot
        );
    }

    static LegacyStagingSnapshot captureLegacyStagingSnapshot(
            Path normalizedStagingRoot,
            String id,
            boolean legacyReplacementAuthorized
    ) throws IOException {
        if (!legacyReplacementAuthorized) {
            return null;
        }
        Path target = normalizedStagingRoot.resolve(id).normalize();
        if (!DatapackSupport.pathExists(target, "legacy datapack staging target")) {
            return null;
        }
        if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid legacy datapack staging target " + target);
        }
        DatapackInstall.validateInstallTree(target.toFile(), normalizedStagingRoot.toFile(), "Legacy datapack staging");
        String markerHash = DatapackOwnership.ownershipMarkerFingerprint(target.toFile());
        if (!"absent".equals(markerHash)) {
            return null;
        }
        return new LegacyStagingSnapshot(
                target,
                target.toRealPath(),
                DatapackSupport.directoryIdentity(target.toFile()),
                DatapackOwnership.directoryHash(target.toFile()),
                markerHash
        );
    }

    static void verifyPendingExtractionName(String name, String id) throws IOException {
        String prefix = ".pending-" + id + "-";
        if (!name.startsWith(prefix)) {
            throw new IOException("Verified datapack extraction has an invalid staging name " + name);
        }
        try {
            UUID.fromString(name.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            throw new IOException("Verified datapack extraction has an invalid staging identity " + name, e);
        }
    }

    static void validateInstallParticipants(
            List<File> worldFolders,
            VerifiedStagingInstall verifiedStagingInstall,
            Entry entry
    ) throws IOException {
        List<File> roots = new ArrayList<>(worldFolders);
        if (verifiedStagingInstall != null) {
            roots.add(verifiedStagingInstall.stagingRoot().toFile());
        }
        Set<Path> normalizedTargets = new HashSet<>();
        Set<Path> realRoots = new HashSet<>();
        Set<Path> targetIdentities = new HashSet<>();
        List<Path> existingTargets = new ArrayList<>();
        for (File folder : roots) {
            ensureInstallTargetRoot(folder);
            Path normalizedRoot = folder.toPath().toAbsolutePath().normalize();
            Path realRoot = normalizedRoot.toRealPath();
            Path normalizedTarget = normalizedRoot.resolve(entry.id).normalize();
            if (!realRoots.add(realRoot) || !normalizedTargets.add(normalizedTarget)) {
                throw new IOException("Duplicate datapack install target " + normalizedTarget);
            }
            Path targetIdentity = realRoot.resolve(entry.id).normalize();
            if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(normalizedTarget)
                        || !Files.isDirectory(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing invalid datapack install target " + normalizedTarget);
                }
                targetIdentity = normalizedTarget.toRealPath();
                for (Path existingTarget : existingTargets) {
                    if (Files.isSameFile(normalizedTarget, existingTarget)) {
                        throw new IOException("Aliased datapack install target " + normalizedTarget);
                    }
                }
                existingTargets.add(normalizedTarget);
            }
            if (!targetIdentities.add(targetIdentity)) {
                throw new IOException("Aliased datapack install target " + normalizedTarget);
            }
        }
        if (verifiedStagingInstall != null) {
            verifiedStagingInstall.verifyStagingRoot();
        }
    }

    static void ensureInstallTargetRoot(File directory) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(directory.toPath());
        }
        DatapackSupport.requireDirectoryIdentity(directory, "datapack install root");
    }

    static void recordInstallResult(
            VolmitSender sender,
            Report report,
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            InstallResult result,
            String versionNumber
    ) {
        DatapackStagingReapply.recordInstallMetadata(stagedDir, worldFolders, entry);
        if (result.changed()) {
            report.updated.add(entry.id + " (" + DatapackSupport.safe(versionNumber) + ")");
            report.requiresRestart = true;
            DatapackSupport.message(sender, C.GREEN + "  Repaired " + C.WHITE + entry.id + C.GREEN + " " + DatapackSupport.safe(versionNumber));
            return;
        }
        report.upToDate.add(entry.id + " (" + DatapackSupport.safe(versionNumber) + ")");
        DatapackSupport.message(sender, C.GRAY + "  Up to date: " + C.WHITE + entry.id + C.GRAY + " " + DatapackSupport.safe(versionNumber));
    }

    static boolean isUsableStaging(File stagedDir, Entry entry) {
        return inspectUsableStaging(stagedDir, entry).usable();
    }

    static StagingInspection inspectUsableStaging(File stagedDir, Entry entry) {
        try {
            DatapackOwnership.validateManagedDirectory(stagedDir, entry.id);
            Ownership ownership = DatapackOwnership.readOwnership(stagedDir);
            String contentHash = DatapackOwnership.directoryHash(stagedDir);
            if (!DatapackInstall.ownershipSourceMatches(ownership, entry)
                    || !Objects.equals(ownership.versionId, entry.versionId)
                    || !Objects.equals(ownership.versionNumber, entry.versionNumber)
                    || !Objects.equals(ownership.sha1, entry.sha1)
                    || !Objects.equals(ownership.contentHash, contentHash)) {
                return new StagingInspection(false, false, false);
            }
            PackResources resources = DatapackOwnership.scanPackResources(stagedDir);
            List<String> previousStructureKeys = DatapackManifestStore.copyList(entry.structureKeys);
            List<String> previousTemplateKeys = DatapackManifestStore.copyList(entry.templateKeys);
            boolean ownershipCorrected = false;
            if (!DatapackManifestStore.copyList(resources.structureKeys()).equals(DatapackManifestStore.copyList(ownership.structureKeys))
                    || !DatapackManifestStore.copyList(resources.templateKeys()).equals(DatapackManifestStore.copyList(ownership.templateKeys))) {
                Entry corrected = DatapackManifestStore.copyEntry(entry);
                corrected.structureKeys = resources.structureKeys();
                corrected.templateKeys = resources.templateKeys();
                DatapackOwnership.writeOwnership(stagedDir, corrected);
                ownershipCorrected = true;
            }
            entry.structureKeys = resources.structureKeys();
            entry.templateKeys = resources.templateKeys();
            boolean manifestChanged = !previousStructureKeys.equals(DatapackManifestStore.copyList(entry.structureKeys))
                    || !previousTemplateKeys.equals(DatapackManifestStore.copyList(entry.templateKeys));
            return new StagingInspection(true, ownershipCorrected, manifestChanged);
        } catch (IOException e) {
            IrisLogging.warn("Ignoring unusable Iris datapack staging at " + stagedDir.getPath() + ": " + e.getMessage());
            return new StagingInspection(false, false, false);
        }
    }

    record LegacyStagingSnapshot(
            Path normalizedTarget,
            Path realTarget,
            String targetIdentity,
            String contentHash,
            String markerHash
    ) {
    }
}
