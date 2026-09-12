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
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class DatapackInstall {
    static final int MAX_SCRATCH_DELETE_ATTEMPTS = 3;

    private DatapackInstall() {
    }

    static InstallResult install(File stagedDir, KList<File> worldFolders, Entry entry, boolean stripOverrides) throws IOException {
        File root = inferDatapackRoot(stagedDir);
        DatapackScratchRecovery.recoverTransactions(root, worldFolders);
        InstallExecution execution = prepareInstallExecution(
                stagedDir,
                worldFolders,
                entry,
                stripOverrides,
                root
        );
        try {
            verifyInstallExecution(execution);
        } catch (IOException failure) {
            rollbackInstallExecutions(List.of(execution), failure);
            throw failure;
        }
        finishInstallExecution(execution);
        return execution.result();
    }

    static InstallExecution prepareInstallExecution(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides,
            File root
    ) throws IOException {
        return prepareInstallExecution(
                stagedDir, worldFolders, entry, stripOverrides, root, null);
    }

    static InstallExecution prepareInstallExecution(
            File stagedDir,
            KList<File> worldFolders,
            Entry entry,
            boolean stripOverrides,
            File root,
            VerifiedStagingInstall verifiedStagingInstall
    ) throws IOException {
        DatapackOwnership.validateManagedDirectory(stagedDir, entry.id);
        Ownership stagedOwnership = DatapackOwnership.readOwnership(stagedDir);
        String stagedHash = DatapackOwnership.directoryHash(stagedDir);
        if (!ownershipMetadataMatches(stagedOwnership, entry, stagedHash)) {
            throw new IOException("Iris datapack staging does not match the committed manifest entry for " + entry.id);
        }
        DatapackStagingGuard.validateInstallParticipants(worldFolders, verifiedStagingInstall, entry);
        List<InstallPlan> plans = new ArrayList<>();
        try {
            for (File worldFolder : worldFolders) {
                plans.add(DatapackInstallPlanner.prepareInstall(
                        stagedDir, worldFolder, entry, stagedHash, stripOverrides, verifiedStagingInstall));
            }
            if (verifiedStagingInstall != null) {
                plans.add(DatapackInstallPlanner.prepareInstall(
                        stagedDir,
                        verifiedStagingInstall.stagingRoot().toFile(),
                        entry,
                        stagedHash,
                        false,
                        verifiedStagingInstall));
            }
        } catch (IOException | RuntimeException preparationFailure) {
            for (InstallPlan plan : plans) {
                try {
                    DatapackInstallPlanner.cleanupInstallPlan(plan, false);
                } catch (IOException cleanupFailure) {
                    preparationFailure.addSuppressed(cleanupFailure);
                }
            }
            if (preparationFailure instanceof UncheckedIOException unchecked) {
                throw unchecked.getCause();
            }
            if (preparationFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw preparationFailure;
        }

        boolean changed = false;
        List<InstallPlan> publishPlans = new ArrayList<>();
        List<InstallPlan> unchangedPlans = new ArrayList<>();
        try {
            for (InstallPlan plan : plans) {
                changed |= plan.contentChanged();
                if (plan.publishRequired()) {
                    publishPlans.add(plan);
                } else {
                    unchangedPlans.add(plan);
                    DatapackInstallPlanner.cleanupInstallPlan(plan, true);
                }
            }
        } catch (IOException cleanupFailure) {
            for (InstallPlan plan : plans) {
                try {
                    DatapackInstallPlanner.cleanupInstallPlan(plan, false);
                } catch (IOException additionalFailure) {
                    cleanupFailure.addSuppressed(additionalFailure);
                }
            }
            throw cleanupFailure;
        }
        if (publishPlans.isEmpty()) {
            return new InstallExecution(
                    new InstallResult(changed),
                    null,
                    stagedDir,
                    entry,
                    stagedHash,
                    verifiedStagingInstall == null,
                    publishPlans,
                    unchangedPlans);
        }

        Manifest committedManifest = DatapackManifestStore.readCommittedManifest(root);
        boolean manifestAlreadyMatched = manifestEntryMatches(committedManifest.findById(entry.id), entry);
        DatapackCoordinator coordinator;
        try {
            coordinator = DatapackTransactions.createInstallCoordinator(root, entry, publishPlans, manifestAlreadyMatched);
        } catch (IOException | RuntimeException coordinatorFailure) {
            for (InstallPlan plan : publishPlans) {
                try {
                    DatapackInstallPlanner.cleanupInstallPlan(plan, false);
                } catch (IOException cleanupFailure) {
                    coordinatorFailure.addSuppressed(cleanupFailure);
                }
            }
            if (coordinatorFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw coordinatorFailure;
        }

        try {
            coordinator.phase(CoordinatorPhase.PUBLISHING);
            for (InstallPlan plan : publishPlans) {
                DatapackInstallPlanner.publishInstallPlan(plan);
            }
            coordinator.phase(CoordinatorPhase.PUBLISHED);
        } catch (IOException | RuntimeException publishFailure) {
            try {
                DatapackCoordinatorResolution.resolveCoordinatorDirectories(coordinator.journal, false);
                coordinator.finish();
            } catch (IOException rollbackFailure) {
                publishFailure.addSuppressed(rollbackFailure);
            }
            if (publishFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw publishFailure;
        }
        return new InstallExecution(
                new InstallResult(changed),
                coordinator,
                stagedDir,
                entry,
                stagedHash,
                verifiedStagingInstall == null,
                publishPlans,
                unchangedPlans);
    }

    static void verifyInstallExecution(InstallExecution execution) throws IOException {
        if (execution.verified()) {
            return;
        }
        if (execution.verifyStagedSource()) {
            DatapackOwnership.validateManagedDirectory(execution.stagedDir(), execution.entry().id);
            Ownership stagedOwnership = DatapackOwnership.readOwnership(execution.stagedDir());
            String stagedHash = DatapackOwnership.directoryHash(execution.stagedDir());
            if (!Objects.equals(stagedHash, execution.stagedHash())
                    || !ownershipMetadataMatches(stagedOwnership, execution.entry(), stagedHash)) {
                throw new IOException("Iris datapack staging changed before installation commit for "
                        + execution.entry().id);
            }
        }
        for (InstallPlan plan : execution.publishedPlans()) {
            DatapackInstallPlanner.verifyDesiredInstallSnapshot(plan.target(), plan, "published datapack target");
        }
        for (InstallPlan plan : execution.unchangedPlans()) {
            DatapackInstallPlanner.verifyOriginalInstallSnapshot(plan.target(), plan, "unchanged datapack target");
        }
        execution.markVerified();
    }

    static void finishInstallExecution(InstallExecution execution) throws IOException {
        if (!execution.verified()) {
            throw new IOException("Datapack install cannot commit before final verification");
        }
        if (execution.coordinator() == null) {
            return;
        }
        execution.coordinator().phase(CoordinatorPhase.COMMITTED);
        DatapackCoordinatorResolution.resolveCoordinatorDirectories(execution.coordinator().journal, true);
        execution.coordinator().finish();
    }

    static void rollbackInstallExecutions(List<InstallExecution> executions, Throwable failure) {
        List<InstallExecution> reversed = new ArrayList<>(executions);
        Collections.reverse(reversed);
        for (InstallExecution execution : reversed) {
            if (execution.coordinator() == null) {
                continue;
            }
            try {
                DatapackCoordinatorResolution.resolveCoordinatorDirectories(execution.coordinator().journal, false);
                execution.coordinator().finish();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    static File inferDatapackRoot(File stagedDir) {
        File parent = stagedDir.getParentFile();
        if (parent != null && "staging".equals(parent.getName()) && parent.getParentFile() != null) {
            return parent.getParentFile();
        }
        return parent == null ? stagedDir : parent;
    }

    static boolean manifestEntryMatches(Entry committed, Entry desired) {
        return committed != null
                && Objects.equals(committed.id, desired.id)
                && Objects.equals(committed.url, desired.url)
                && Objects.equals(committed.versionId, desired.versionId)
                && Objects.equals(committed.versionNumber, desired.versionNumber)
                && Objects.equals(committed.sha1, desired.sha1);
    }

    static void validateInstallTree(File directory, File storeAnchor, String purpose) throws IOException {
        DatapackScratchRecovery.validateScratchTree(directory.toPath());
        if (!DatapackScratchRecovery.sameScratchVolume(directory.toPath(), storeAnchor.toPath())) {
            throw new IOException(purpose + " crosses a filesystem boundary at " + directory.getPath());
        }
    }

    static File installScratchRoot(File targetFolder) {
        File parent = targetFolder.getParentFile();
        return new File(parent == null ? targetFolder : parent, ".iris-datapack-install");
    }

    static void cleanupPreparedInstall(File pending, File pendingRoot, Throwable failure) {
        try {
            deleteInstallScratch(pending, "prepared datapack install");
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        pendingRoot.delete();
    }

    static boolean ownershipMetadataMatches(Ownership ownership, Entry entry, String contentHash) {
        return ownership.schemaVersion == DatapackOwnership.OWNERSHIP_SCHEMA
                && Objects.equals(ownership.id, entry.id)
                && Objects.equals(ownership.url, entry.url)
                && Objects.equals(ownership.versionId, entry.versionId)
                && Objects.equals(ownership.versionNumber, entry.versionNumber)
                && Objects.equals(ownership.sha1, entry.sha1)
                && Objects.equals(ownership.contentHash, contentHash)
                && DatapackManifestStore.copyList(ownership.structureKeys).equals(DatapackManifestStore.copyList(entry.structureKeys))
                && DatapackManifestStore.copyList(ownership.templateKeys).equals(DatapackManifestStore.copyList(entry.templateKeys));
    }

    static boolean ownershipSourceMatches(Ownership ownership, Entry entry) {
        return ownership.schemaVersion == DatapackOwnership.OWNERSHIP_SCHEMA
                && Objects.equals(ownership.id, entry.id)
                && Objects.equals(ownership.url, entry.url);
    }

    static void deleteInstallScratch(File scratch, String purpose) throws IOException {
        Path scratchPath = scratch.toPath();
        File parent = Objects.requireNonNull(scratch.getParentFile(), "datapack scratch parent");
        for (int attempt = 0; attempt < MAX_SCRATCH_DELETE_ATTEMPTS; attempt++) {
            if (Files.notExists(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (!Files.exists(scratchPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(scratchPath)
                    || !Files.isDirectory(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to remove unsafe " + purpose + " " + scratch.getPath());
            }
            validateInstallTree(scratch, parent, purpose);
            DatapackOwnership.removeFinderMetadata(scratch);
            IO.delete(scratch);
            if (Files.notExists(scratchPath, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
        }
        throw new IOException("Could not remove " + purpose + " " + scratch.getPath());
    }
}
