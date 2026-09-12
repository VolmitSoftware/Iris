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
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

final class DatapackInstallPlanner {

    private DatapackInstallPlanner() {
    }

    static InstallPlan prepareInstall(
            File stagedDir,
            File worldFolder,
            Entry entry,
            String stagedHash,
            boolean stripOverrides,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        DatapackStagingGuard.ensureInstallTargetRoot(worldFolder);
        File target = new File(worldFolder, entry.id);
        InstallPlan unchanged = tryPrepareUnchangedManagedInstall(
                stagedDir,
                worldFolder,
                target,
                entry,
                stagedHash,
                stripOverrides,
                verifiedStagingInstall);
        if (unchanged != null) {
            return unchanged;
        }
        boolean canonicalStagingInstall = verifiedStagingInstall != null
                && verifiedStagingInstall.isCanonicalInstall(worldFolder, target);
        boolean legacyReplacementAuthorized = canonicalStagingInstall
                && verifiedStagingInstall.consume(stagedDir, worldFolder, target, entry, stagedHash);
        VerifiedStagingInstall legacyWorldAuthorization = null;
        File pendingRoot = DatapackInstall.installScratchRoot(worldFolder);
        File pending = new File(pendingRoot, entry.id + "-" + UUID.randomUUID());
        File backup = new File(pendingRoot, entry.id + "-backup-" + UUID.randomUUID());
        try {
            DatapackSupport.ensureScratchDirectory(pendingRoot, "datapack install staging");
            String targetRootIdentity = DatapackSupport.realDirectoryPath(worldFolder, "datapack target root");
            String scratchRootIdentity = DatapackSupport.realDirectoryPath(pendingRoot, "datapack install scratch root");
            String targetRootFileIdentity = DatapackSupport.directoryIdentity(worldFolder);
            String scratchRootFileIdentity = DatapackSupport.directoryIdentity(pendingRoot);
            IO.copyDirectory(stagedDir.toPath(), pending.toPath());
            Files.deleteIfExists(new File(pending, DatapackOwnership.OWNERSHIP_MARKER).toPath());
            String copiedHash = DatapackOwnership.directoryHash(pending);
            if (!Objects.equals(stagedHash, copiedHash)) {
                throw new IOException("Datapack staging changed or copied incompletely while preparing " + entry.id);
            }
            boolean removedOverrideMarker = Files.deleteIfExists(
                    new File(pending, DatapackPackMetadata.OVERRIDES_STRIPPED_MARKER).toPath());
            if (stripOverrides) {
                DatapackPackMetadata.stripVanillaStructureOverrides(pending);
                DatapackPackMetadata.writeMarker(new File(pending, DatapackPackMetadata.OVERRIDES_STRIPPED_MARKER));
            }
            DatapackPackMetadata.validatePackMetadata(pending);
            if (!stripOverrides && !removedOverrideMarker) {
                DatapackOwnership.writeOwnership(pending, entry, copiedHash);
            } else {
                DatapackOwnership.writeOwnership(pending, entry);
            }
            DatapackInstall.validateInstallTree(pending, worldFolder, "Prepared datapack install");
            Ownership desiredOwnership = DatapackOwnership.readOwnership(pending);
            String desiredHash = desiredOwnership.contentHash;
            String desiredMarkerHash = DatapackOwnership.ownershipMarkerFingerprint(pending);
            String desiredIdentity = DatapackSupport.directoryIdentity(pending);
            boolean hadTarget = DatapackSupport.pathExists(target.toPath(), "datapack install target");
            String originalHash = "";
            String originalMarkerHash = "absent";
            String originalIdentity = "";
            boolean contentChanged = !hadTarget;
            boolean publishRequired = !hadTarget;
            if (hadTarget) {
                if (Files.isSymbolicLink(target.toPath())
                        || !Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to replace non-directory or symbolic-link datapack " + target.getPath());
                }
                DatapackInstall.validateInstallTree(target, worldFolder, "Existing datapack install");
                Ownership ownership = DatapackOwnership.readOwnershipOrNull(target);
                if (ownership != null) {
                    DatapackOwnership.removeFinderMetadata(target);
                }
                String currentHash = DatapackOwnership.directoryHash(target);
                originalHash = currentHash;
                originalMarkerHash = DatapackOwnership.ownershipMarkerFingerprint(target);
                originalIdentity = DatapackSupport.directoryIdentity(target);
                if (ownership == null) {
                    boolean differingLegacyTarget = !Objects.equals(currentHash, desiredHash);
                    if (differingLegacyTarget && !legacyReplacementAuthorized
                            && verifiedStagingInstall != null
                            && verifiedStagingInstall.authorizeLegacyWorldReplacement(
                            stagedDir,
                            worldFolder,
                            target,
                            entry,
                            stagedHash,
                            currentHash,
                            originalMarkerHash)) {
                        legacyReplacementAuthorized = true;
                        legacyWorldAuthorization = verifiedStagingInstall;
                    }
                    if (differingLegacyTarget && !legacyReplacementAuthorized) {
                        throw new IOException("Refusing to replace unmanaged datapack at " + target.getPath());
                    }
                    if (differingLegacyTarget && (originalIdentity.isEmpty()
                            || targetRootFileIdentity.isEmpty()
                            || scratchRootFileIdentity.isEmpty()
                            || verifiedStagingInstall == null
                            || !verifiedStagingInstall.hasStablePathIdentities())) {
                        throw new IOException("Cannot safely identify legacy datapack staging at " + target.getPath());
                    }
                    publishRequired = true;
                } else {
                    if (!entry.id.equals(ownership.id)) {
                        throw new IOException("Datapack ownership mismatch at " + target.getPath());
                    }
                    publishRequired = !Objects.equals(ownership.contentHash, currentHash)
                            || !DatapackInstall.ownershipMetadataMatches(ownership, entry, desiredHash);
                }
                contentChanged = !Objects.equals(currentHash, desiredHash);
                publishRequired |= contentChanged;
                if (ownership != null && !Objects.equals(ownership.contentHash, currentHash)) {
                    IrisLogging.warn("Repairing modified or corrupt Iris-managed datapack at " + target.getPath());
                }
            }
            return new InstallPlan(
                    target,
                    pending,
                    backup,
                    pendingRoot,
                    hadTarget,
                    publishRequired,
                    contentChanged,
                    originalHash,
                    desiredHash,
                    originalMarkerHash,
                    desiredMarkerHash,
                    originalIdentity,
                    desiredIdentity,
                    targetRootIdentity,
                    scratchRootIdentity,
                    targetRootFileIdentity,
                    scratchRootFileIdentity,
                    entry.id,
                    entry.url,
                    legacyWorldAuthorization
            );
        } catch (UncheckedIOException e) {
            IOException cause = e.getCause();
            DatapackInstall.cleanupPreparedInstall(pending, pendingRoot, cause);
            throw cause;
        } catch (IOException e) {
            DatapackInstall.cleanupPreparedInstall(pending, pendingRoot, e);
            throw e;
        } catch (RuntimeException e) {
            DatapackInstall.cleanupPreparedInstall(pending, pendingRoot, e);
            throw e;
        }
    }

    static InstallPlan tryPrepareUnchangedManagedInstall(
            File stagedDir,
            File worldFolder,
            File target,
            Entry entry,
            String stagedHash,
            boolean stripOverrides,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        if (verifiedStagingInstall != null
                || stripOverrides
                || DatapackSupport.pathExists(new File(stagedDir, DatapackPackMetadata.OVERRIDES_STRIPPED_MARKER).toPath(),
                "staged datapack override marker")
                || !DatapackSupport.pathExists(target.toPath(), "datapack install target")) {
            return null;
        }
        if (Files.isSymbolicLink(target.toPath())
                || !Files.isDirectory(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to replace non-directory or symbolic-link datapack " + target.getPath());
        }
        Ownership ownership = DatapackOwnership.readOwnershipOrNull(target);
        if (ownership == null) {
            return null;
        }
        if (!entry.id.equals(ownership.id)) {
            throw new IOException("Datapack ownership mismatch at " + target.getPath());
        }
        if (!DatapackInstall.ownershipMetadataMatches(ownership, entry, stagedHash)) {
            return null;
        }
        DatapackInstall.validateInstallTree(target, worldFolder, "Existing datapack install");
        DatapackOwnership.removeFinderMetadata(target);
        String currentHash = DatapackOwnership.directoryHash(target);
        if (!Objects.equals(currentHash, stagedHash)) {
            return null;
        }
        String markerHash = DatapackOwnership.ownershipMarkerFingerprint(target);
        String identity = DatapackSupport.directoryIdentity(target);
        File pendingRoot = DatapackInstall.installScratchRoot(worldFolder);
        return new InstallPlan(
                target,
                new File(pendingRoot, entry.id + "-" + UUID.randomUUID()),
                new File(pendingRoot, entry.id + "-backup-" + UUID.randomUUID()),
                pendingRoot,
                true,
                false,
                false,
                currentHash,
                currentHash,
                markerHash,
                markerHash,
                identity,
                identity,
                DatapackSupport.realDirectoryPath(worldFolder, "datapack target root"),
                "",
                DatapackSupport.directoryIdentity(worldFolder),
                "",
                entry.id,
                entry.url,
                null
        );
    }

    static void publishInstallPlan(InstallPlan plan) throws IOException {
        verifyDirectoryContainerIdentity(
                plan.target().getParentFile(), plan.targetRootIdentity(),
                plan.targetRootFileIdentity(), "datapack target root");
        verifyDirectoryContainerIdentity(
                plan.pendingRoot(), plan.scratchRootIdentity(),
                plan.scratchRootFileIdentity(), "datapack install scratch root");
        verifyDesiredInstallSnapshot(plan.pending(), plan, "prepared datapack install");
        if (plan.hadTarget()) {
            verifyOriginalInstallSnapshot(plan.target(), plan, "original datapack target");
        } else if (DatapackSupport.pathExists(plan.target().toPath(), "new datapack target")) {
            throw new IOException("Datapack install target was concurrently created at " + plan.target().getPath());
        }
        if (plan.legacyWorldAuthorization() != null) {
            plan.legacyWorldAuthorization().verifyLegacyWorldSnapshot();
        }
        try {
            if (plan.hadTarget()) {
                DatapackSupport.moveNew(plan.target().toPath(), plan.backup().toPath());
                verifyOriginalInstallSnapshot(plan.backup(), plan, "datapack install backup");
                if (DatapackSupport.pathExists(plan.target().toPath(), "moved datapack target")) {
                    throw new IOException("Datapack install target reappeared after backup at " + plan.target().getPath());
                }
                forceInstallMoveDirectories(plan);
            }
            DatapackSupport.moveNew(plan.pending().toPath(), plan.target().toPath());
            verifyDesiredInstallSnapshot(plan.target(), plan, "installed datapack target");
            forceInstallMoveDirectories(plan);
        } catch (IOException publishFailure) {
            if (plan.hadTarget()
                    && Files.exists(plan.backup().toPath(), LinkOption.NOFOLLOW_LINKS)
                    && Files.exists(plan.pending().toPath(), LinkOption.NOFOLLOW_LINKS)
                    && Files.notExists(plan.target().toPath(), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    verifyOriginalInstallSnapshot(plan.backup(), plan, "datapack install backup");
                    DatapackSupport.moveNew(plan.backup().toPath(), plan.target().toPath());
                    forceInstallMoveDirectories(plan);
                } catch (IOException restoreFailure) {
                    publishFailure.addSuppressed(restoreFailure);
                }
            }
            throw publishFailure;
        }
    }

    static void forceInstallMoveDirectories(InstallPlan plan) throws IOException {
        DatapackSupport.forceDirectoryIfSupported(plan.target().getParentFile().toPath());
        DatapackSupport.forceDirectoryIfSupported(plan.pendingRoot().toPath());
    }

    static void verifyDirectoryContainerIdentity(
            File directory,
            String expectedRealPath,
            String expectedFileIdentity,
            String purpose
    ) throws IOException {
        Path normalized = directory.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Objects.equals(normalized.toRealPath(), Path.of(expectedRealPath))
                || (!expectedFileIdentity.isEmpty()
                && !Objects.equals(DatapackSupport.directoryIdentity(directory), expectedFileIdentity))) {
            throw new IOException("Changed or unsafe " + purpose + " " + normalized);
        }
    }

    static void verifyOriginalInstallSnapshot(
            File directory,
            InstallPlan plan,
            String purpose
    ) throws IOException {
        verifyDirectorySnapshot(
                directory,
                plan.target().getParentFile(),
                plan.originalHash(),
                plan.originalMarkerHash(),
                plan.originalIdentity(),
                purpose
        );
    }

    static void verifyDesiredInstallSnapshot(
            File directory,
            InstallPlan plan,
            String purpose
    ) throws IOException {
        verifyDirectorySnapshot(
                directory,
                plan.target().getParentFile(),
                plan.desiredHash(),
                plan.desiredMarkerHash(),
                plan.desiredIdentity(),
                purpose
        );
        Ownership ownership = DatapackOwnership.readOwnership(directory);
        if (!Objects.equals(ownership.id, plan.id())
                || !Objects.equals(ownership.url, plan.url())
                || !Objects.equals(ownership.contentHash, plan.desiredHash())) {
            throw new IOException("Datapack ownership changed in " + purpose + " at " + directory.getPath());
        }
    }

    static void verifyDirectorySnapshot(
            File directory,
            File storeAnchor,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity,
            String purpose
    ) throws IOException {
        if (!DatapackSupport.pathExists(directory.toPath(), purpose)
                || Files.isSymbolicLink(directory.toPath())
                || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or unsafe " + purpose + " at " + directory.getPath());
        }
        DatapackInstall.validateInstallTree(directory, storeAnchor, purpose);
        if (Files.exists(new File(directory, DatapackOwnership.OWNERSHIP_MARKER).toPath(), LinkOption.NOFOLLOW_LINKS)) {
            DatapackOwnership.removeFinderMetadata(directory);
        }
        if (!expectedIdentity.isEmpty()
                && !Objects.equals(DatapackSupport.directoryIdentity(directory), expectedIdentity)) {
            throw new IOException("Datapack directory identity changed in " + purpose + " at " + directory.getPath());
        }
        if (!Objects.equals(expectedHash, DatapackOwnership.directoryHash(directory))
                || !Objects.equals(expectedMarkerHash, DatapackOwnership.ownershipMarkerFingerprint(directory))) {
            throw new IOException("Datapack content changed in " + purpose + " at " + directory.getPath());
        }
    }

    static void cleanupInstallPlan(InstallPlan plan, boolean committed) throws IOException {
        DatapackInstall.deleteInstallScratch(plan.pending(), "datapack install pending directory");
        if (committed) {
            DatapackInstall.deleteInstallScratch(plan.backup(), "datapack install backup");
        }
        if (Files.exists(plan.backup().toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Preserving prior datapack backup after incomplete install: "
                    + plan.backup().getPath());
        }
        plan.pendingRoot().delete();
    }
}
