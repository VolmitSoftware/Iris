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
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

final class DatapackTransactions {
    static final String TRANSACTION_DIRECTORY = ".iris-datapack-transactions";

    static final String TRANSACTION_JOURNAL = "journal.json";

    static final String TRANSACTION_JOURNAL_NEXT = "journal.next.json";

    static final int TRANSACTION_SCHEMA = 2;

    static final long MAX_TRANSACTION_JOURNAL_BYTES = 4L * 1024L * 1024L;

    static final int MAX_TRANSACTION_COUNT = 1_024;

    static final ReentrantLock TRANSACTION_LOCK = new ReentrantLock();

    private DatapackTransactions() {
    }

    static DatapackCoordinator createInstallCoordinator(
            File root,
            Entry entry,
            List<InstallPlan> plans,
            boolean manifestAlreadyMatched
    ) throws IOException {
        CoordinatorJournal journal = newCoordinatorJournal(
                CoordinatorOperation.INSTALL,
                entry,
                manifestAlreadyMatched
        );
        for (InstallPlan plan : plans) {
            if (!plan.publishRequired()) {
                continue;
            }
            CoordinatorDirectory directory = new CoordinatorDirectory();
            directory.target = DatapackSupport.normalizedPath(plan.target());
            directory.pending = DatapackSupport.normalizedPath(plan.pending());
            directory.backup = DatapackSupport.normalizedPath(plan.backup());
            directory.hadTarget = plan.hadTarget();
            directory.originalHash = plan.originalHash();
            directory.desiredHash = plan.desiredHash();
            directory.originalMarkerHash = plan.originalMarkerHash();
            directory.desiredMarkerHash = plan.desiredMarkerHash();
            directory.originalIdentity = plan.originalIdentity();
            directory.desiredIdentity = plan.desiredIdentity();
            directory.targetRoot = plan.targetRootIdentity();
            directory.scratchRoot = plan.scratchRootIdentity();
            directory.targetRootIdentity = plan.targetRootFileIdentity();
            directory.scratchRootIdentity = plan.scratchRootFileIdentity();
            journal.directories.add(directory);
        }
        return createCoordinator(root, journal);
    }

    static DatapackCoordinator createRemovalCoordinator(
            File root,
            Entry entry,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval
    ) throws IOException {
        CoordinatorJournal journal = newCoordinatorJournal(CoordinatorOperation.REMOVE, entry, true);
        for (DirectoryMove move : directoryRemoval.moves()) {
            CoordinatorDirectory directory = new CoordinatorDirectory();
            directory.target = DatapackSupport.normalizedPath(move.target());
            directory.pending = "";
            directory.backup = DatapackSupport.normalizedPath(move.backup());
            directory.hadTarget = true;
            directory.originalHash = move.originalHash();
            directory.desiredHash = "";
            directory.originalMarkerHash = move.originalMarkerHash();
            directory.desiredMarkerHash = "";
            directory.originalIdentity = move.originalIdentity();
            directory.desiredIdentity = "";
            directory.targetRoot = move.targetRootIdentity();
            directory.scratchRoot = move.scratchRootIdentity();
            directory.targetRootIdentity = move.targetRootFileIdentity();
            directory.scratchRootIdentity = move.scratchRootFileIdentity();
            journal.directories.add(directory);
        }
        for (StructureTransactionWriter.PreparedRemovalToken token : editableRemoval.recoveryTokens()) {
            CoordinatorEditable editable = new CoordinatorEditable();
            editable.packRoot = token.packRoot().toString();
            editable.transactionId = token.transactionId().toString();
            editable.claimId = UUID.randomUUID().toString();
            journal.editables.add(editable);
        }
        Path transactionRoot = coordinatorTransactionRoot(root, journal);
        try {
            editableRemoval.claimRecoveryOwners(transactionRoot, journal);
            writeCoordinatorJournal(transactionRoot, journal);
            return new DatapackCoordinator(transactionRoot, journal);
        } catch (IOException | RuntimeException creationFailure) {
            try {
                DatapackInstall.deleteInstallScratch(transactionRoot.toFile(), "incomplete datapack transaction");
            } catch (IOException cleanupFailure) {
                creationFailure.addSuppressed(cleanupFailure);
            }
            if (creationFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw creationFailure;
        }
    }

    static CoordinatorJournal newCoordinatorJournal(
            CoordinatorOperation operation,
            Entry entry,
            boolean manifestAlreadyMatched
    ) {
        CoordinatorJournal journal = new CoordinatorJournal();
        journal.schemaVersion = TRANSACTION_SCHEMA;
        journal.transactionId = UUID.randomUUID().toString();
        journal.operation = operation;
        journal.phase = CoordinatorPhase.PREPARED;
        journal.id = entry.id;
        journal.url = entry.url;
        journal.versionId = entry.versionId;
        journal.versionNumber = entry.versionNumber;
        journal.sha1 = entry.sha1;
        journal.manifestAlreadyMatched = manifestAlreadyMatched;
        return journal;
    }

    static DatapackCoordinator createCoordinator(File root, CoordinatorJournal journal) throws IOException {
        Path transactionRoot = coordinatorTransactionRoot(root, journal);
        writeCoordinatorJournal(transactionRoot, journal);
        return new DatapackCoordinator(transactionRoot, journal);
    }

    static Path coordinatorTransactionRoot(File root, CoordinatorJournal journal) throws IOException {
        File transactionDirectory = new File(root, TRANSACTION_DIRECTORY);
        DatapackSupport.ensureScratchDirectory(transactionDirectory, "datapack transaction");
        Path transactionRoot = new File(transactionDirectory, journal.transactionId).toPath().toAbsolutePath().normalize();
        if (Files.exists(transactionRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datapack transaction id already exists: " + journal.transactionId);
        }
        return transactionRoot;
    }

    static CoordinatorJournal readCoordinatorJournal(Path transactionRoot) throws IOException {
        Path committed = transactionRoot.resolve(TRANSACTION_JOURNAL);
        Path next = transactionRoot.resolve(TRANSACTION_JOURNAL_NEXT);
        if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
            return parseCoordinatorJournal(committed);
        }
        if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> entries = Files.list(transactionRoot)) {
                if (entries.findAny().isEmpty()) {
                    return null;
                }
            }
            throw new IOException("Missing datapack transaction journal " + committed);
        }
        try {
            return parseCoordinatorJournal(next);
        } catch (IOException firstWriteFailure) {
            try (Stream<Path> entries = Files.list(transactionRoot)) {
                List<Path> contents = entries.limit(2).toList();
                if (contents.size() == 1 && Objects.equals(contents.getFirst(), next)
                        && !Files.isSymbolicLink(next)) {
                    return null;
                }
            }
            throw firstWriteFailure;
        }
    }

    static CoordinatorJournal parseCoordinatorJournal(Path journalPath) throws IOException {
        if (Files.isSymbolicLink(journalPath)
                || !Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid datapack transaction journal " + journalPath);
        }
        try {
            CoordinatorJournal journal = DatapackSupport.GSON.fromJson(
                    DatapackSupport.readBoundedUtf8(
                            journalPath,
                            MAX_TRANSACTION_JOURNAL_BYTES,
                            "Datapack transaction journal"
                    ),
                    CoordinatorJournal.class
            );
            if (journal == null) {
                throw new IOException("Empty datapack transaction journal " + journalPath);
            }
            return journal;
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack transaction journal " + journalPath, e);
        }
    }

    static void validateCoordinatorJournal(
            File root,
            List<File> worldFolders,
            Manifest committedManifest,
            Path transactionRoot,
            CoordinatorJournal journal
    ) throws IOException {
        if (journal.schemaVersion != TRANSACTION_SCHEMA || journal.transactionId == null
                || journal.operation == null || journal.phase == null || journal.id == null
                || journal.url == null || journal.directories == null || journal.editables == null) {
            throw new IOException("Incomplete datapack transaction journal at " + transactionRoot);
        }
        UUID transactionId;
        try {
            transactionId = UUID.fromString(journal.transactionId);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid datapack transaction id " + journal.transactionId, e);
        }
        if (!transactionId.toString().equals(transactionRoot.getFileName().toString())) {
            throw new IOException("Datapack transaction id does not match its directory");
        }
        if (!journal.id.equals(DatapackArchive.sanitizeId(journal.id)) || DatapackArchive.RESERVED_IDS.contains(journal.id)) {
            throw new IOException("Invalid datapack transaction entry id " + journal.id);
        }
        if (journal.directories.size() > worldFolders.size() + 1 || journal.editables.size() > 10_000) {
            throw new IOException("Datapack transaction journal contains too many participants");
        }
        if (journal.operation == CoordinatorOperation.INSTALL && !journal.editables.isEmpty()) {
            throw new IOException("Datapack install transaction unexpectedly contains editable participants");
        }

        Set<Path> allowedTargets = new HashSet<>();
        allowedTargets.add(new File(new File(root, "staging"), journal.id).toPath().toAbsolutePath().normalize());
        for (File worldFolder : worldFolders) {
            allowedTargets.add(new File(worldFolder, journal.id).toPath().toAbsolutePath().normalize());
        }
        Set<Path> seenTargets = new HashSet<>();
        Set<Path> seenTargetIdentities = new HashSet<>();
        for (CoordinatorDirectory directory : journal.directories) {
            DatapackCoordinatorResolution.validateCoordinatorDirectory(
                    directory, journal.operation, allowedTargets, seenTargets, seenTargetIdentities);
        }
        Set<String> seenEditables = new HashSet<>();
        Set<Path> allowedEditableRoots = journal.editables.isEmpty()
                ? Set.of() : DatapackCoordinatorResolution.authoritativeEditableRoots(committedManifest, journal);
        for (CoordinatorEditable editable : journal.editables) {
            if (editable == null || editable.packRoot == null || editable.transactionId == null
                    || editable.claimId == null
                    || !seenEditables.add(editable.packRoot)) {
                throw new IOException("Invalid duplicate editable participant in datapack transaction");
            }
            Path packRoot = coordinatorPath(editable.packRoot, "editable pack root");
            Path realPackRoot = Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)
                    ? packRoot.toRealPath() : packRoot;
            if (Files.isSymbolicLink(packRoot) || !Files.isDirectory(packRoot, LinkOption.NOFOLLOW_LINKS)
                    || !packRoot.equals(realPackRoot) || !allowedEditableRoots.contains(realPackRoot)) {
                throw new IOException("Invalid editable pack root in datapack transaction: " + packRoot);
            }
            try {
                UUID.fromString(editable.transactionId);
                UUID.fromString(editable.claimId);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid editable transaction recovery identity", e);
            }
            StructureTransactionWriter writer = new StructureTransactionWriter(packRoot);
            boolean recoveryDataPresent = writer.verifyRecoveryOwner(
                    new StructureTransactionWriter.PreparedRemovalToken(
                            packRoot,
                            UUID.fromString(editable.transactionId)
                    ),
                    new StructureTransactionWriter.RecoveryOwner(
                            transactionRoot,
                            UUID.fromString(journal.transactionId),
                            UUID.fromString(editable.claimId)
                    ),
                    committedManifest.findById(journal.id) == null
                            && journal.phase == CoordinatorPhase.PUBLISHED
            );
            if (!recoveryDataPresent && journal.phase != CoordinatorPhase.COMMITTED) {
                throw new IOException("Editable structure recovery data disappeared before commit");
            }
        }
    }

    static void deleteCoordinatorTransaction(Path transactionRoot) throws IOException {
        DatapackInstall.deleteInstallScratch(transactionRoot.toFile(), "completed datapack transaction");
        DatapackSupport.forceDirectoryIfSupported(Objects.requireNonNull(transactionRoot.getParent(), "transaction parent"));
    }

    static void writeCoordinatorJournal(Path transactionRoot, CoordinatorJournal journal) throws IOException {
        byte[] content = DatapackSupport.GSON.toJson(journal).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_TRANSACTION_JOURNAL_BYTES) {
            throw new IOException("Datapack transaction journal exceeds " + MAX_TRANSACTION_JOURNAL_BYTES + " bytes");
        }
        Files.createDirectories(transactionRoot);
        Path next = transactionRoot.resolve(TRANSACTION_JOURNAL_NEXT);
        if (Files.isSymbolicLink(next)) {
            throw new IOException("Datapack transaction journal cannot be a symbolic link: " + next);
        }
        Files.deleteIfExists(next);
        Files.write(next, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        DatapackSupport.forceFile(next);
        DatapackSupport.move(next, transactionRoot.resolve(TRANSACTION_JOURNAL));
        DatapackSupport.forceDirectoryIfSupported(transactionRoot);
        DatapackSupport.forceDirectoryIfSupported(Objects.requireNonNull(transactionRoot.getParent(), "transaction parent"));
    }

    static Path coordinatorPath(String value, String purpose) throws IOException {
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Invalid datapack transaction " + purpose + " path", e);
        }
    }
}
