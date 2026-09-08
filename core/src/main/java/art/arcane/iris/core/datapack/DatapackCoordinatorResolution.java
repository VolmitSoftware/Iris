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
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.volmlib.util.io.IO;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DatapackCoordinatorResolution {

    private DatapackCoordinatorResolution() {
    }

    static Set<Path> authoritativeEditableRoots(
            Manifest committedManifest,
            CoordinatorJournal journal
    ) throws IOException {
        Set<Path> roots = new HashSet<>();
        Entry committed = committedManifest.findById(journal.id);
        if (committed != null) {
            if (!Objects.equals(committed.url, journal.url)) {
                throw new IOException("Datapack transaction conflicts with the committed editable pack owner");
            }
            addExistingPackRoots(roots, committed.importedTargets.keySet());
            addExistingPackRoots(roots, committed.importAttempts.keySet());
            addExistingPackRoots(roots, committed.importedBundles.keySet());
            return roots;
        }
        if (journal.operation != CoordinatorOperation.REMOVE || !journal.phase.published()) {
            throw new IOException("Datapack transaction has no committed editable pack authority");
        }
        for (CoordinatorEditable editable : journal.editables) {
            Path packRoot = DatapackTransactions.coordinatorPath(editable.packRoot, "editable pack root");
            if (Files.isSymbolicLink(packRoot) || !Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid editable pack root in datapack transaction: " + packRoot);
            }
            Path realPackRoot = packRoot.toRealPath();
            if (!packRoot.equals(realPackRoot)) {
                throw new IOException("Editable pack root changed before datapack recovery: " + packRoot);
            }
            roots.add(realPackRoot);
        }
        return roots;
    }

    static void addExistingPackRoots(Set<Path> roots, Set<String> paths) throws IOException {
        for (String path : paths) {
            try {
                Path root = Path.of(path).toAbsolutePath().normalize();
                if (Files.isDirectory(root)) {
                    roots.add(root.toRealPath());
                }
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable pack root in the datapack manifest: " + path, e);
            }
        }
    }

    static void validateCoordinatorDirectory(
            CoordinatorDirectory directory,
            CoordinatorOperation operation,
            Set<Path> allowedTargets,
            Set<Path> seenTargets,
            Set<Path> seenTargetIdentities
    ) throws IOException {
        if (directory == null || directory.target == null || directory.backup == null
                || directory.originalHash == null || directory.desiredHash == null
                || directory.originalMarkerHash == null || directory.desiredMarkerHash == null
                || directory.originalIdentity == null || directory.desiredIdentity == null
                || directory.targetRoot == null || directory.scratchRoot == null
                || directory.targetRootIdentity == null || directory.scratchRootIdentity == null) {
            throw new IOException("Incomplete directory participant in datapack transaction");
        }
        Path target = DatapackTransactions.coordinatorPath(directory.target, "target");
        if (!allowedTargets.contains(target) || !seenTargets.add(target)) {
            throw new IOException("Datapack transaction target is outside its configured roots: " + target);
        }
        File targetFile = target.toFile();
        File targetParent = targetFile.getParentFile();
        Path targetRootIdentity = DatapackTransactions.coordinatorPath(directory.targetRoot, "target root");
        if (!seenTargetIdentities.add(targetRootIdentity.resolve(target.getFileName()).normalize())) {
            throw new IOException("Aliased datapack transaction target " + target);
        }
        validateRecoveryContainer(
                targetParent.toPath(),
                targetRootIdentity,
                true,
                "datapack target root"
        );
        DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                targetParent, directory.targetRoot, directory.targetRootIdentity, "datapack target root");
        File scratchRoot = new File(
                targetParent.getParentFile() == null ? targetParent : targetParent.getParentFile(),
                operation == CoordinatorOperation.INSTALL ? ".iris-datapack-install" : ".iris-datapack-remove"
        );
        Path expectedScratch = scratchRoot.toPath().toAbsolutePath().normalize();
        Path backup = DatapackTransactions.coordinatorPath(directory.backup, "backup");
        if (!Objects.equals(backup.getParent(), expectedScratch) || Files.isSymbolicLink(backup)) {
            throw new IOException("Invalid datapack transaction backup path " + backup);
        }
        Path pending = null;
        if (operation == CoordinatorOperation.INSTALL) {
            if (directory.pending == null || directory.pending.isBlank()) {
                throw new IOException("Install transaction is missing its pending directory");
            }
            pending = DatapackTransactions.coordinatorPath(directory.pending, "pending directory");
            if (!Objects.equals(pending.getParent(), expectedScratch) || Files.isSymbolicLink(pending)) {
                throw new IOException("Invalid datapack transaction pending path " + pending);
            }
        } else if (directory.pending != null && !directory.pending.isBlank()) {
            throw new IOException("Removal transaction unexpectedly contains a pending directory");
        }
        boolean scratchRequired = Files.exists(backup, LinkOption.NOFOLLOW_LINKS)
                || pending != null && Files.exists(pending, LinkOption.NOFOLLOW_LINKS);
        validateRecoveryContainer(
                expectedScratch,
                DatapackTransactions.coordinatorPath(directory.scratchRoot, "scratch root"),
                scratchRequired,
                "datapack transaction scratch root"
        );
        if (scratchRequired) {
            DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                    expectedScratch.toFile(), directory.scratchRoot,
                    directory.scratchRootIdentity, "datapack transaction scratch root");
        }
    }

    static void validateRecoveryContainer(
            Path container,
            Path expectedRealPath,
            boolean required,
            String purpose
    ) throws IOException {
        Path normalized = container.toAbsolutePath().normalize();
        Path normalizedExpected = expectedRealPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                throw new IOException("Missing " + purpose + " " + normalized);
            }
            return;
        }
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)
                || !Objects.equals(normalized.toRealPath(), normalizedExpected)) {
            throw new IOException("Changed or unsafe " + purpose + " " + normalized);
        }
    }

    static boolean coordinatorCommitDecision(Manifest manifest, CoordinatorJournal journal) throws IOException {
        Entry committed = manifest.findById(journal.id);
        if (journal.operation == CoordinatorOperation.REMOVE) {
            if (committed == null) {
                if (!journal.phase.published()) {
                    throw new IOException("Datapack removal manifest changed before every participant was published");
                }
                return true;
            }
            if (!Objects.equals(committed.url, journal.url) || journal.phase == CoordinatorPhase.COMMITTED) {
                throw new IOException("Datapack removal journal conflicts with the committed manifest");
            }
            return false;
        }

        boolean matches = committed != null
                && Objects.equals(committed.url, journal.url)
                && Objects.equals(committed.versionId, journal.versionId)
                && Objects.equals(committed.versionNumber, journal.versionNumber)
                && Objects.equals(committed.sha1, journal.sha1);
        if (journal.manifestAlreadyMatched) {
            if (!matches) {
                throw new IOException("Committed datapack changed while an install transaction was incomplete");
            }
            return journal.phase.published();
        }
        if (matches) {
            if (!journal.phase.published()) {
                throw new IOException("Datapack install manifest changed before every target was published");
            }
            return true;
        }
        if (journal.phase == CoordinatorPhase.COMMITTED) {
            throw new IOException("Committed datapack install journal conflicts with the manifest");
        }
        return false;
    }

    static void resolveCoordinatorDirectories(CoordinatorJournal journal, boolean commit) throws IOException {
        List<CoordinatorDirectory> directories = new ArrayList<>(journal.directories);
        if (!commit) {
            Collections.reverse(directories);
        }
        IOException failure = null;
        for (CoordinatorDirectory directory : directories) {
            try {
                if (journal.operation == CoordinatorOperation.INSTALL) {
                    resolveInstallDirectory(journal, directory, commit);
                } else {
                    resolveRemovalDirectory(directory, commit);
                }
            } catch (IOException | RuntimeException e) {
                IOException participantFailure = e instanceof IOException ioFailure
                        ? ioFailure : new IOException("Failed resolving datapack directory participant", e);
                if (failure == null) {
                    failure = participantFailure;
                } else {
                    failure.addSuppressed(participantFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static void resolveInstallDirectory(
            CoordinatorJournal journal,
            CoordinatorDirectory directory,
            boolean commit
    ) throws IOException {
        File target = new File(directory.target);
        File pending = new File(directory.pending);
        File backup = new File(directory.backup);
        File targetRoot = new File(directory.targetRoot);
        if (commit) {
            verifyDesiredDirectory(
                    target, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
            deleteOriginalBackupIfPresent(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity);
            deleteDesiredDirectoryIfPresent(
                    pending, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
            cleanupScratchParent(backup);
            return;
        }

        if (directory.hadTarget) {
            if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                DatapackInstallPlanner.verifyDirectorySnapshot(
                        backup, targetRoot, directory.originalHash,
                        directory.originalMarkerHash, directory.originalIdentity,
                        "datapack install backup");
                deleteDesiredDirectoryIfPresent(
                        target, targetRoot, journal, directory.desiredHash,
                        directory.desiredMarkerHash, directory.desiredIdentity);
                DatapackSupport.moveNew(backup.toPath(), target.toPath());
                DatapackSupport.forceDirectoryIfSupported(target.getParentFile().toPath());
                DatapackSupport.forceDirectoryIfSupported(backup.getParentFile().toPath());
            } else {
                DatapackInstallPlanner.verifyDirectorySnapshot(
                        target, targetRoot, directory.originalHash,
                        directory.originalMarkerHash, directory.originalIdentity,
                        "original datapack target");
            }
        } else {
            if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Unexpected backup for newly-installed datapack " + backup.getPath());
            }
            deleteDesiredDirectoryIfPresent(
                    target, targetRoot, journal, directory.desiredHash,
                    directory.desiredMarkerHash, directory.desiredIdentity);
        }
        deleteDesiredDirectoryIfPresent(
                pending, targetRoot, journal, directory.desiredHash,
                directory.desiredMarkerHash, directory.desiredIdentity);
        cleanupScratchParent(backup);
    }

    static void resolveRemovalDirectory(CoordinatorDirectory directory, boolean commit) throws IOException {
        File target = new File(directory.target);
        File backup = new File(directory.backup);
        File targetRoot = new File(directory.targetRoot);
        if (commit) {
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Removed datapack target reappeared before transaction cleanup: " + target.getPath());
            }
            deleteOriginalBackupIfPresent(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity);
            cleanupScratchParent(backup);
            return;
        }
        if (Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            DatapackInstallPlanner.verifyDirectorySnapshot(
                    backup, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity,
                    "datapack removal backup");
            if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Datapack removal target was concurrently recreated: " + target.getPath());
            }
            DatapackSupport.moveNew(backup.toPath(), target.toPath());
            DatapackSupport.forceDirectoryIfSupported(target.getParentFile().toPath());
            DatapackSupport.forceDirectoryIfSupported(backup.getParentFile().toPath());
        } else {
            DatapackInstallPlanner.verifyDirectorySnapshot(
                    target, targetRoot, directory.originalHash,
                    directory.originalMarkerHash, directory.originalIdentity,
                    "original datapack target");
        }
        cleanupScratchParent(backup);
    }

    static void resolveCoordinatorEditables(
            CoordinatorJournal journal,
            Path transactionRoot,
            boolean commit
    ) throws IOException {
        IOException failure = null;
        for (CoordinatorEditable editable : journal.editables) {
            try {
                Path packRoot = DatapackTransactions.coordinatorPath(editable.packRoot, "editable pack root");
                StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
                writer.resolvePreparedRemoval(
                        new StructureTransactionWriter.PreparedRemovalToken(
                                packRoot,
                                UUID.fromString(editable.transactionId)
                        ),
                        new StructureTransactionWriter.RecoveryOwner(
                                transactionRoot,
                                UUID.fromString(journal.transactionId),
                                UUID.fromString(editable.claimId)
                        ),
                        commit
                );
                IrisData.invalidateLoadedStructureResources(packRoot.toFile());
            } catch (IOException | RuntimeException e) {
                IOException participantFailure = e instanceof IOException ioFailure
                        ? ioFailure : new IOException("Failed resolving editable structure participant", e);
                if (failure == null) {
                    failure = participantFailure;
                } else {
                    failure.addSuppressed(participantFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    static void verifyDesiredDirectory(
            File directory,
            File targetRoot,
            CoordinatorJournal journal,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        DatapackInstallPlanner.verifyDirectorySnapshot(
                directory, targetRoot, expectedHash, expectedMarkerHash,
                expectedIdentity, "installed datapack target");
        Ownership ownership = DatapackOwnership.readOwnership(directory);
        if (!Objects.equals(ownership.id, journal.id) || !Objects.equals(ownership.url, journal.url)
                || !Objects.equals(ownership.contentHash, expectedHash)) {
            throw new IOException("Installed datapack ownership does not match its transaction at " + directory.getPath());
        }
    }

    static void deleteDesiredDirectoryIfPresent(
            File directory,
            File targetRoot,
            CoordinatorJournal journal,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        verifyDesiredDirectory(
                directory, targetRoot, journal, expectedHash,
                expectedMarkerHash, expectedIdentity);
        deleteVerifiedDirectory(directory);
    }

    static void deleteOriginalBackupIfPresent(
            File backup,
            File targetRoot,
            String expectedHash,
            String expectedMarkerHash,
            String expectedIdentity
    ) throws IOException {
        if (!Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        DatapackInstallPlanner.verifyDirectorySnapshot(
                backup, targetRoot, expectedHash, expectedMarkerHash,
                expectedIdentity, "datapack transaction backup");
        deleteVerifiedDirectory(backup);
    }

    static void deleteVerifiedDirectory(File directory) throws IOException {
        File parent = Objects.requireNonNull(directory.getParentFile(), "datapack transaction parent");
        DatapackInstall.validateInstallTree(directory, parent, "Datapack transaction directory");
        IO.delete(directory);
        if (Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not delete datapack transaction directory " + directory.getPath());
        }
    }

    static void cleanupScratchParent(File participant) {
        File parent = participant.getParentFile();
        if (parent != null) {
            parent.delete();
        }
    }
}
