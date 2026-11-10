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

package art.arcane.iris.core.structure.authoring;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public final class StructureTransactionWriter {
    private static final String STAGING_RELATIVE_PATH = ".iris/structure-staging";
    private static final String PROCESS_LOCK_RELATIVE_PATH = ".iris/structure-authoring.lock";
    private static final String RECOVERY_CLAIM_FILE = "external-coordinator.json";
    private static final int RECOVERY_CLAIM_SCHEMA = 1;
    private static final int MAX_RECOVERY_CLAIM_BYTES = 64 * 1024;
    private static final int MAX_COORDINATOR_JOURNAL_BYTES = 4 * 1024 * 1024;
    private static final int MAX_STRUCTURE_STATE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_VERIFIED_READ_FILE_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_RECOVERY_TRANSACTIONS = 1_024;
    private static final LockedRemovalValidator NO_REMOVAL_VALIDATION = () -> {
    };
    private static final ConcurrentMap<Path, ReentrantLock> ROOT_LOCKS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    private final Path packRoot;
    private final StructureFileOperations files;
    private final ReentrantLock rootLock;

    public StructureTransactionWriter(Path packRoot) {
        this(packRoot, new NioStructureFileOperations());
    }

    StructureTransactionWriter(Path packRoot, StructureFileOperations files) {
        this.packRoot = canonicalPackRoot(Objects.requireNonNull(packRoot, "packRoot"));
        this.files = Objects.requireNonNull(files, "files");
        rootLock = ROOT_LOCKS.computeIfAbsent(this.packRoot, ignored -> new ReentrantLock());
    }

    public Path packRoot() {
        return packRoot;
    }

    public Path ownershipManifestPath(StructureKey key) {
        Objects.requireNonNull(key, "key");
        return resolveTarget(StructureOwnershipManifest.relativePath(key));
    }

    public StructureRecoveryResult recoverIncompleteTransactions() {
        rootLock.lock();
        try (ProcessLock ignored = acquireProcessLock()) {
            return recoverIncompleteTransactionsLocked();
        } catch (IOException | RuntimeException e) {
            return new StructureRecoveryResult(
                    0,
                    0,
                    0,
                    List.of(new StructureRecoveryResult.Failure(packRoot, e))
            );
        } finally {
            rootLock.unlock();
        }
    }

    public StructureWriteResult write(StructureResourceBundle bundle, StructureWriteMode mode) {
        return write(bundle, new StructureWriteOptions(mode, false));
    }

    public Optional<StructureSource> ownedSource(StructureKey key) throws IOException {
        Objects.requireNonNull(key, "key");
        rootLock.lock();
        try (ProcessLock ignored = acquireProcessLock()) {
            StructureRecoveryResult recovery = recoverIncompleteTransactionsLocked();
            if (!recovery.successful()) {
                throw recoveryFailure(recovery);
            }
            Path manifestPath = ownershipManifestPath(key);
            if (!files.exists(manifestPath)) {
                return Optional.empty();
            }
            if (!files.isRegularFile(manifestPath)) {
                throw new IOException("Structure ownership manifest is not a regular file: " + manifestPath);
            }
            StructureOwnershipManifest manifest;
            try {
                manifest = StructureOwnershipManifest.fromJson(readBoundedBytes(
                        manifestPath,
                        MAX_STRUCTURE_STATE_BYTES,
                        "Structure ownership manifest"
                ));
            } catch (RuntimeException e) {
                throw new IOException("Invalid structure ownership manifest at " + manifestPath, e);
            }
            if (!manifest.structure().equals(key)) {
                throw new IOException("Structure ownership manifest belongs to " + manifest.structure());
            }
            return Optional.of(manifest.source());
        } finally {
            rootLock.unlock();
        }
    }

    public boolean removeOwned(StructureKey key, StructureSource.Kind sourceKind, StructureKey sourceKey) throws IOException {
        return removeOwned(new OwnedRemoval(
                key,
                sourceKind,
                sourceKey,
                Optional.empty(),
                Optional.empty()));
    }

    public boolean removeManagedDatapackOwned(
            StructureKey key,
            StructureSource.Kind sourceKind,
            StructureKey sourceKey
    ) throws IOException {
        return removeOwned(OwnedRemoval.managedDatapack(key, sourceKind, sourceKey));
    }

    private boolean removeOwned(OwnedRemoval request) throws IOException {
        try (PreparedRemoval removal = prepareOwnedRemovals(List.of(request))) {
            boolean changed = removal.changed();
            removal.markCommitted();
            removal.finishCommit();
            return changed;
        }
    }

    public PreparedRemoval prepareOwnedRemovals(List<OwnedRemoval> removals) throws IOException {
        return prepareOwnedRemovals(removals, NO_REMOVAL_VALIDATION);
    }

    public PreparedRemoval prepareOwnedRemovals(
            List<OwnedRemoval> removals,
            LockedRemovalValidator validator
    ) throws IOException {
        return prepareOwnedRemovals(removals, false, validator);
    }

    public PreparedRemoval prepareMatchingOwnedRemovals(List<OwnedRemoval> removals) throws IOException {
        return prepareOwnedRemovals(removals, true, NO_REMOVAL_VALIDATION);
    }

    private PreparedRemoval prepareOwnedRemovals(
            List<OwnedRemoval> removals,
            boolean skipOwnershipMismatches,
            LockedRemovalValidator validator
    ) throws IOException {
        Objects.requireNonNull(removals, "removals");
        List<OwnedRemoval> requests = List.copyOf(removals);
        LockedRemovalValidator requiredValidator = Objects.requireNonNull(
                validator,
                "locked removal validator");
        rootLock.lock();
        ProcessLock processLock = null;
        Path transactionRoot = null;
        LinkedHashMap<Path, Path> backups = new LinkedHashMap<>();
        try {
            processLock = acquireProcessLock();
            StructureRecoveryResult recovery = recoverIncompleteTransactionsLocked();
            if (!recovery.successful()) {
                throw recoveryFailure(recovery);
            }
            requiredValidator.validate();
            RemovalPlan plan = buildRemovalPlan(requests, skipOwnershipMismatches);
            if (plan.targets().isEmpty()) {
                return new PreparedRemoval(null, null, backups, processLock, false);
            }

            UUID transactionId = UUID.randomUUID();
            transactionRoot = stagingRoot().resolve(transactionId.toString()).normalize();
            Path backupRoot = transactionRoot.resolve("backup");
            StructureTransactionJournal journal = StructureTransactionJournal.prepared(
                    transactionId,
                    plan.targets()
            );
            files.createDirectories(backupRoot);
            writeJournal(transactionRoot, journal);
            files.forceDirectory(transactionRoot);
            files.forceDirectory(stagingRoot());
            verifyTargetSnapshot(journal);
            backupTargets(journal, backupRoot, backups);
            return new PreparedRemoval(transactionRoot, journal, backups, processLock, true);
        } catch (IOException | RuntimeException preparationFailure) {
            Optional<Throwable> rollbackFailure = rollback(backups, List.of());
            if (rollbackFailure.isPresent()) {
                preparationFailure.addSuppressed(rollbackFailure.get());
            } else if (transactionRoot != null) {
                cleanupAfterFailure(transactionRoot, preparationFailure);
            }
            if (processLock != null) {
                try {
                    processLock.close();
                } catch (IOException closeFailure) {
                    preparationFailure.addSuppressed(closeFailure);
                }
            }
            rootLock.unlock();
            if (preparationFailure instanceof IOException ioFailure) {
                throw ioFailure;
            }
            throw new IOException("Failed preparing owned structure removal", preparationFailure);
        }
    }

    public record OwnedRemoval(
            StructureKey key,
            StructureSource.Kind sourceKind,
            StructureKey sourceKey,
            Optional<StructureOwnershipManifest.Origin> requiredOrigin,
            Optional<String> expectedManifestHash
    ) {
        public OwnedRemoval {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(sourceKey, "sourceKey");
            requiredOrigin = Objects.requireNonNull(requiredOrigin, "requiredOrigin");
            expectedManifestHash = Objects.requireNonNull(expectedManifestHash, "expectedManifestHash");
            if (expectedManifestHash.isPresent()
                    && !StructureHash.isSha256(expectedManifestHash.get())) {
                throw new IllegalArgumentException("Expected ownership manifest hash must be SHA-256");
            }
        }

        public static OwnedRemoval managedDatapack(
                StructureKey key,
                StructureSource.Kind sourceKind,
                StructureKey sourceKey
        ) {
            return new OwnedRemoval(
                    key,
                    sourceKind,
                    sourceKey,
                    Optional.of(StructureOwnershipManifest.Origin.MANAGED_DATAPACK),
                    Optional.empty()
            );
        }
    }

    @FunctionalInterface
    public interface LockedRemovalValidator {
        void validate() throws IOException;
    }

    public record PreparedRemovalToken(Path packRoot, UUID transactionId) {
        public PreparedRemovalToken {
            packRoot = canonicalPackRoot(Objects.requireNonNull(packRoot, "packRoot"));
            Objects.requireNonNull(transactionId, "transactionId");
        }
    }

    public record RecoveryOwner(Path transactionRoot, UUID transactionId, UUID claimId) {
        public RecoveryOwner {
            transactionRoot = validateRecoveryOwnerRoot(Objects.requireNonNull(transactionRoot, "transactionRoot"));
            Objects.requireNonNull(transactionId, "transactionId");
            Objects.requireNonNull(claimId, "claimId");
            if (!transactionId.toString().equals(transactionRoot.getFileName().toString())) {
                throw new IllegalArgumentException("Recovery owner transaction id does not match its directory");
            }
        }
    }

    public boolean verifyRecoveryOwner(
            PreparedRemovalToken token,
            RecoveryOwner owner,
            boolean verifyPreparedState
    ) throws IOException {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(owner, "owner");
        if (!packRoot.equals(token.packRoot())) {
            throw new IOException("Prepared removal token belongs to a different pack root");
        }
        Path transactionRoot = stagingRoot().resolve(token.transactionId().toString()).normalize();
        if (!files.exists(transactionRoot)) {
            return false;
        }
        verifyRecoveryClaim(transactionRoot, token.transactionId(), owner);
        if (verifyPreparedState) {
            verifyPreparedRemovalAuthority(transactionRoot, token.transactionId());
        }
        return true;
    }

    public void resolvePreparedRemoval(PreparedRemovalToken token, boolean commit) throws IOException {
        resolvePreparedRemoval(token, null, commit);
    }

    public void resolvePreparedRemoval(
            PreparedRemovalToken token,
            RecoveryOwner owner,
            boolean commit
    ) throws IOException {
        Objects.requireNonNull(token, "token");
        if (!packRoot.equals(token.packRoot())) {
            throw new IOException("Prepared removal token belongs to a different pack root");
        }
        rootLock.lock();
        try (ProcessLock ignored = acquireProcessLock()) {
            Path transactionRoot = stagingRoot().resolve(token.transactionId().toString()).normalize();
            if (!files.exists(transactionRoot)) {
                return;
            }
            if (owner != null) {
                verifyRecoveryClaim(transactionRoot, token.transactionId(), owner);
            }
            Path journalPath = recoveryJournalPath(transactionRoot);
            if (journalPath == null || !files.isRegularFile(journalPath)) {
                throw new IOException("Missing prepared removal journal at " + transactionRoot);
            }
            StructureTransactionJournal journal;
            try {
                journal = StructureTransactionJournal.fromJson(readBoundedBytes(
                        journalPath,
                        MAX_STRUCTURE_STATE_BYTES,
                        "Prepared removal journal"
                ));
            } catch (RuntimeException e) {
                throw new IOException("Invalid prepared removal journal at " + journalPath, e);
            }
            if (!journal.transactionId().equals(token.transactionId())) {
                throw new IOException("Prepared removal journal id does not match " + token.transactionId());
            }
            if (commit) {
                verifyCommittedTransaction(journal);
            } else {
                restorePreparedTransaction(transactionRoot, journal);
            }
            cleanupTransaction(transactionRoot);
        } finally {
            rootLock.unlock();
        }
    }

    public final class PreparedRemoval implements AutoCloseable {
        private final Path transactionRoot;
        private final StructureTransactionJournal journal;
        private final LinkedHashMap<Path, Path> backups;
        private final ProcessLock processLock;
        private final boolean changed;
        private boolean committed;
        private boolean closed;

        private PreparedRemoval(
                Path transactionRoot,
                StructureTransactionJournal journal,
                LinkedHashMap<Path, Path> backups,
                ProcessLock processLock,
                boolean changed
        ) {
            this.transactionRoot = transactionRoot;
            this.journal = journal;
            this.backups = backups;
            this.processLock = processLock;
            this.changed = changed;
        }

        public boolean changed() {
            return changed;
        }

        public Optional<PreparedRemovalToken> recoveryToken() {
            if (transactionRoot == null) {
                return Optional.empty();
            }
            UUID transactionId = UUID.fromString(Objects.requireNonNull(
                    transactionRoot.getFileName(),
                    "prepared removal transaction directory"
            ).toString());
            return Optional.of(new PreparedRemovalToken(packRoot, transactionId));
        }

        public void claimRecoveryOwner(RecoveryOwner owner) throws IOException {
            requireOpen();
            Objects.requireNonNull(owner, "owner");
            if (transactionRoot == null) {
                return;
            }
            RecoveryClaim claim = new RecoveryClaim(
                    RECOVERY_CLAIM_SCHEMA,
                    owner.transactionRoot().toString(),
                    owner.transactionId(),
                    owner.claimId()
            );
            byte[] claimContent = GSON.toJson(claim).getBytes(StandardCharsets.UTF_8);
            if (claimContent.length > MAX_RECOVERY_CLAIM_BYTES) {
                throw new IOException("External recovery claim exceeds " + MAX_RECOVERY_CLAIM_BYTES + " bytes");
            }
            Path claimPath = transactionRoot.resolve(RECOVERY_CLAIM_FILE);
            files.writeNew(claimPath, claimContent);
            files.forceFile(claimPath);
            files.forceDirectory(transactionRoot);
        }

        public void markCommitted() throws IOException {
            requireOpen();
            if (transactionRoot == null) {
                committed = true;
                return;
            }
            boolean committedJournalWritten = false;
            try {
                writeJournal(transactionRoot, journal.committed());
                committedJournalWritten = true;
                files.forceDirectory(transactionRoot);
                committed = true;
            } catch (IOException | RuntimeException commitFailure) {
                if (committedJournalWritten || isCommittedJournal(transactionRoot, commitFailure)) {
                    committed = true;
                }
                if (commitFailure instanceof IOException ioFailure) {
                    throw ioFailure;
                }
                throw new IOException("Failed marking owned structure removal committed", commitFailure);
            }
        }

        public void finishCommit() throws IOException {
            requireOpen();
            if (!committed) {
                throw new IllegalStateException("Owned structure removal has not been marked committed");
            }
            IOException failure = null;
            if (transactionRoot != null) {
                try {
                    cleanupTransaction(transactionRoot);
                } catch (IOException | RuntimeException cleanupFailure) {
                    failure = new IOException("Owned structure removal committed but cleanup remains at "
                            + transactionRoot, cleanupFailure);
                }
            }
            IOException releaseFailure = release();
            if (failure == null) {
                failure = releaseFailure;
            } else if (releaseFailure != null) {
                failure.addSuppressed(releaseFailure);
            }
            if (failure != null) {
                throw failure;
            }
        }

        public void rollback() throws IOException {
            if (closed) {
                return;
            }
            IOException failure = null;
            if (transactionRoot != null) {
                Optional<Throwable> rollbackFailure = StructureTransactionWriter.this.rollback(backups, List.of());
                if (rollbackFailure.isPresent()) {
                    failure = new IOException("Failed restoring prepared owned structure removal at "
                            + transactionRoot, rollbackFailure.get());
                } else {
                    try {
                        cleanupTransaction(transactionRoot);
                    } catch (IOException | RuntimeException cleanupFailure) {
                        failure = new IOException("Restored owned structure removal but cleanup remains at "
                                + transactionRoot, cleanupFailure);
                    }
                }
            }
            IOException releaseFailure = release();
            if (failure == null) {
                failure = releaseFailure;
            } else if (releaseFailure != null) {
                failure.addSuppressed(releaseFailure);
            }
            if (failure != null) {
                throw failure;
            }
        }

        public void leaveForRecovery() throws IOException {
            if (closed) {
                return;
            }
            IOException releaseFailure = release();
            if (releaseFailure != null) {
                throw releaseFailure;
            }
        }

        @Override
        public void close() throws IOException {
            rollback();
        }

        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("Owned structure removal transaction is closed");
            }
        }

        private IOException release() {
            IOException failure = null;
            try {
                processLock.close();
            } catch (IOException e) {
                failure = e;
            } finally {
                closed = true;
                rootLock.unlock();
            }
            return failure;
        }
    }

    public StructureWriteResult preview(StructureResourceBundle bundle, StructureWriteMode mode) {
        return write(bundle, StructureWriteOptions.preview(mode));
    }

    public StructureWriteResult claimExisting(
            StructureOwnershipManifest manifest,
            StructureTransactionReadSet readSet
    ) {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(readSet, "readSet");
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            String expectedHash = readSet.fileHashes().get(resource.getKey());
            if (!resource.getValue().equals(expectedHash)) {
                throw new IllegalArgumentException(
                        "Claim read set does not pin owned resource " + resource.getKey());
            }
        }
        rootLock.lock();
        try (ProcessLock ignored = acquireProcessLock()) {
            StructureRecoveryResult recovery = recoverIncompleteTransactionsLocked();
            if (!recovery.successful()) {
                return claimResult(
                        StructureWriteResult.Status.FAILED,
                        manifest,
                        List.of(),
                        Optional.of(recoveryFailure(recovery))
                );
            }
            List<StructureWriteResult.Conflict> conflicts = verifyReadSet(readSet);
            Path manifestPath = ownershipManifestPath(manifest.structure());
            if (files.exists(manifestPath)) {
                ArrayList<StructureWriteResult.Conflict> updated = new ArrayList<>(conflicts);
                updated.add(StructureWriteResult.Conflict.at(
                        manifest.relativePath(),
                        StructureWriteResult.ConflictReason.MANIFEST_EXISTS));
                conflicts = orderedConflicts(updated);
            }
            if (!conflicts.isEmpty()) {
                StructureWriteResult.Status status = conflicts.stream().anyMatch(conflict ->
                        conflict.reason() == StructureWriteResult.ConflictReason.MANIFEST_EXISTS)
                        ? StructureWriteResult.Status.ADD_ONLY_CONFLICT
                        : StructureWriteResult.Status.OWNERSHIP_CONFLICT;
                return claimResult(status, manifest, conflicts, Optional.empty());
            }
            return commitClaim(manifest, readSet);
        } catch (IOException | RuntimeException exception) {
            return claimResult(
                    StructureWriteResult.Status.FAILED,
                    manifest,
                    List.of(),
                    Optional.of(exception)
            );
        } finally {
            rootLock.unlock();
        }
    }

    public StructureWriteResult write(StructureResourceBundle bundle, StructureWriteOptions options) {
        return writeVerified(
                bundle,
                options,
                StructureTransactionReadSet.empty(),
                StructureOwnershipManifest.Provenance.created()
        );
    }

    public StructureWriteResult writeManagedDatapack(
            StructureResourceBundle bundle,
            StructureWriteMode mode
    ) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(mode, "mode");
        if (bundle.source().kind() != StructureSource.Kind.DATAPACK
                && bundle.source().kind() != StructureSource.Kind.VANILLA) {
            throw new IllegalArgumentException("Managed datapack writes require a datapack or vanilla source");
        }
        return writeVerified(
                bundle,
                new StructureWriteOptions(mode, false),
                StructureTransactionReadSet.empty(),
                managedDatapackProvenance(bundle),
                ExistingProvenancePolicy.INSTALL_MANAGED_DATAPACK
        );
    }

    public StructureWriteResult writeVerified(
            StructureResourceBundle bundle,
            StructureWriteOptions options,
            StructureTransactionReadSet readSet,
            StructureOwnershipManifest.Provenance provenance
    ) {
        return writeVerified(
                bundle,
                options,
                readSet,
                provenance,
                ExistingProvenancePolicy.PRESERVE
        );
    }

    private StructureWriteResult writeVerified(
            StructureResourceBundle bundle,
            StructureWriteOptions options,
            StructureTransactionReadSet readSet,
            StructureOwnershipManifest.Provenance provenance,
            ExistingProvenancePolicy provenancePolicy
    ) {
        Objects.requireNonNull(bundle, "bundle");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(readSet, "readSet");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(provenancePolicy, "provenancePolicy");
        rootLock.lock();
        try {
            if (options.dryRun()) {
                return writeLocked(bundle, options, readSet, provenance, provenancePolicy);
            }
            try (ProcessLock ignored = acquireProcessLock()) {
                return writeLocked(bundle, options, readSet, provenance, provenancePolicy);
            }
        } catch (IOException | RuntimeException e) {
            return failedResult(bundle, e);
        } finally {
            rootLock.unlock();
        }
    }

    private StructureWriteResult writeLocked(
            StructureResourceBundle bundle,
            StructureWriteOptions options,
            StructureTransactionReadSet readSet,
            StructureOwnershipManifest.Provenance provenance,
            ExistingProvenancePolicy provenancePolicy
    )
            throws IOException {
        if (!options.dryRun()) {
            StructureRecoveryResult recovery = recoverIncompleteTransactionsLocked();
            if (!recovery.successful()) {
                return failedResult(bundle, recoveryFailure(recovery));
            }
        }
        List<StructureWriteResult.Conflict> readSetConflicts = verifyReadSet(readSet);
        if (!readSetConflicts.isEmpty()) {
            return readSetConflictResult(bundle, readSetConflicts);
        }
        WritePlan plan = buildPlan(bundle, options, provenance, provenancePolicy);
        if (!plan.conflicts().isEmpty()) {
            StructureWriteResult.Status status = options.mode() == StructureWriteMode.ADD_ONLY
                    ? StructureWriteResult.Status.ADD_ONLY_CONFLICT
                    : StructureWriteResult.Status.OWNERSHIP_CONFLICT;
            return result(status, plan, Optional.empty());
        }
        if (options.dryRun()) {
            return result(StructureWriteResult.Status.DRY_RUN, plan, Optional.empty());
        }
        if (plan.action() == StructureWriteResult.Action.NONE) {
            return result(StructureWriteResult.Status.UNCHANGED, plan, Optional.empty());
        }
        return commit(plan, readSet);
    }

    private RemovalPlan buildRemovalPlan(
            List<OwnedRemoval> removals,
            boolean skipOwnershipMismatches
    ) throws IOException {
        TreeMap<String, StructureTransactionJournal.Target> targets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (OwnedRemoval removal : removals) {
            Path manifestPath = ownershipManifestPath(removal.key());
            if (!files.exists(manifestPath)) {
                continue;
            }
            if (!files.isRegularFile(manifestPath)) {
                throw new IOException("Structure ownership manifest is not a regular file: " + manifestPath);
            }
            byte[] manifestContent = readBoundedBytes(
                    manifestPath,
                    MAX_STRUCTURE_STATE_BYTES,
                    "Structure ownership manifest"
            );
            String manifestHash = StructureHash.sha256(manifestContent);
            if (removal.expectedManifestHash().isPresent()
                    && !removal.expectedManifestHash().get().equals(manifestHash)) {
                throw new IOException("Structure ownership manifest changed after removal was planned: "
                        + removal.key());
            }
            StructureOwnershipManifest manifest;
            try {
                manifest = StructureOwnershipManifest.fromJson(manifestContent);
            } catch (RuntimeException e) {
                throw new IOException("Invalid structure ownership manifest at " + manifestPath, e);
            }
            if (!manifest.structure().equals(removal.key())) {
                throw new IOException("Structure ownership manifest belongs to " + manifest.structure());
            }
            boolean sourceMismatch = manifest.source().kind() != removal.sourceKind()
                    || !manifest.source().key().equals(removal.sourceKey());
            boolean originMismatch = removal.requiredOrigin().isPresent()
                    && manifest.provenance().origin() != removal.requiredOrigin().get();
            if (sourceMismatch || originMismatch) {
                if (skipOwnershipMismatches) {
                    continue;
                }
                if (originMismatch) {
                    throw new IOException("Structure '" + removal.key()
                            + "' is not owned by managed datapack ingest");
                }
                throw new IOException("Structure '" + removal.key() + "' is owned by source "
                        + manifest.source().key() + " (" + manifest.source().kind() + "), not "
                        + removal.sourceKey() + " (" + removal.sourceKind() + ")");
            }

            for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
                Path target = resolveTarget(resource.getKey());
                verifyRemovalTarget(target, resource.getKey(), resource.getValue());
                addRemovalTarget(targets, resource.getKey(), resource.getValue());
            }
            addRemovalTarget(targets, manifest.relativePath(), StructureHash.sha256(manifestContent));
        }
        return new RemovalPlan(List.copyOf(targets.values()));
    }

    private void addRemovalTarget(
            Map<String, StructureTransactionJournal.Target> targets,
            String relativePath,
            String contentHash
    ) throws IOException {
        StructureTransactionJournal.Target target = new StructureTransactionJournal.Target(
                relativePath,
                true,
                contentHash,
                ""
        );
        StructureTransactionJournal.Target existing = targets.putIfAbsent(relativePath, target);
        if (existing != null && (!existing.relativePath().equals(relativePath)
                || !existing.originalHash().equals(contentHash))) {
            throw new IOException("Owned structure removals overlap at incompatible resource path " + relativePath);
        }
    }

    private void verifyRemovalTarget(Path target, String relativePath, String expectedHash) throws IOException {
        if (!files.isRegularFile(target)) {
            throw new IOException("Owned structure resource is missing or not a regular file: " + relativePath);
        }
        String actualHash = files.sha256(target);
        if (!expectedHash.equals(actualHash)) {
            throw new IOException("Owned structure resource was modified and will not be removed: " + relativePath);
        }
    }

    private StructureRecoveryResult recoverIncompleteTransactionsLocked() {
        Path stagingRoot = stagingRoot();
        ArrayList<StructureRecoveryResult.Failure> failures = new ArrayList<>();
        int restoredPrepared = 0;
        int cleanedCommitted = 0;
        int cleanedOrphans = 0;
        if (!files.exists(stagingRoot)) {
            return new StructureRecoveryResult(0, 0, 0, List.of());
        }
        if (!files.isDirectory(stagingRoot)) {
            IOException failure = new IOException("Structure staging root is not a directory: " + stagingRoot);
            return new StructureRecoveryResult(
                    0,
                    0,
                    0,
                    List.of(new StructureRecoveryResult.Failure(stagingRoot, failure))
            );
        }

        List<Path> transactionRoots;
        try {
            transactionRoots = files.list(stagingRoot, MAX_RECOVERY_TRANSACTIONS);
        } catch (IOException | RuntimeException e) {
            return new StructureRecoveryResult(
                    0,
                    0,
                    0,
                    List.of(new StructureRecoveryResult.Failure(stagingRoot, e))
            );
        }
        if (transactionRoots.size() > MAX_RECOVERY_TRANSACTIONS) {
            IOException failure = new IOException("Structure recovery transaction count exceeds "
                    + MAX_RECOVERY_TRANSACTIONS);
            return new StructureRecoveryResult(
                    0,
                    0,
                    0,
                    List.of(new StructureRecoveryResult.Failure(stagingRoot, failure))
            );
        }

        for (Path transactionRoot : transactionRoots) {
            try {
                RecoveryOutcome outcome = recoverTransaction(transactionRoot);
                switch (outcome) {
                    case RESTORED_PREPARED -> restoredPrepared++;
                    case CLEANED_COMMITTED -> cleanedCommitted++;
                    case CLEANED_ORPHAN -> cleanedOrphans++;
                }
            } catch (IOException | RuntimeException e) {
                failures.add(new StructureRecoveryResult.Failure(transactionRoot, e));
            }
        }
        return new StructureRecoveryResult(
                restoredPrepared,
                cleanedCommitted,
                cleanedOrphans,
                failures
        );
    }

    private RecoveryOutcome recoverTransaction(Path transactionRoot) throws IOException {
        Path normalizedRoot = transactionRoot.toAbsolutePath().normalize();
        Path stagingRoot = stagingRoot();
        if (!Objects.equals(normalizedRoot.getParent(), stagingRoot)) {
            throw new IOException("Structure transaction is not a direct child of " + stagingRoot);
        }
        rejectSymbolicLinks(stagingRoot, normalizedRoot);
        if (!files.isDirectory(normalizedRoot)) {
            files.deleteTree(normalizedRoot);
            files.forceDirectory(stagingRoot);
            return RecoveryOutcome.CLEANED_ORPHAN;
        }

        Path journalPath = recoveryJournalPath(normalizedRoot);
        if (journalPath == null) {
            if (hasRecoveryData(normalizedRoot.resolve("backup"))) {
                throw new IOException("Structure transaction has backups but no recovery journal: "
                        + normalizedRoot);
            }
            cleanupTransaction(normalizedRoot);
            return RecoveryOutcome.CLEANED_ORPHAN;
        }
        if (!files.isRegularFile(journalPath)) {
            throw new IOException("Structure transaction journal is not a regular file: " + journalPath);
        }

        StructureTransactionJournal journal;
        try {
            journal = StructureTransactionJournal.fromJson(readBoundedBytes(
                    journalPath,
                    MAX_STRUCTURE_STATE_BYTES,
                    "Structure transaction journal"
            ));
        } catch (RuntimeException e) {
            throw new IOException("Invalid structure transaction journal at " + journalPath, e);
        }
        String directoryName = Objects.requireNonNull(normalizedRoot.getFileName(), "transaction directory name")
                .toString();
        if (!journal.transactionId().toString().equals(directoryName)) {
            throw new IOException("Structure transaction journal id " + journal.transactionId()
                    + " does not match directory " + directoryName);
        }
        if (hasActiveRecoveryOwner(normalizedRoot, journal.transactionId())) {
            throw new IOException("Structure transaction recovery is owned by an active datapack coordinator: "
                    + normalizedRoot);
        }

        return switch (journal.phase()) {
            case PREPARED -> {
                restorePreparedTransaction(normalizedRoot, journal);
                cleanupTransaction(normalizedRoot);
                yield RecoveryOutcome.RESTORED_PREPARED;
            }
            case COMMITTED -> {
                verifyCommittedTransaction(journal);
                cleanupTransaction(normalizedRoot);
                yield RecoveryOutcome.CLEANED_COMMITTED;
            }
        };
    }

    private Path recoveryJournalPath(Path transactionRoot) throws IOException {
        Path journalPath = transactionRoot.resolve(StructureTransactionJournal.FILE_NAME);
        if (files.exists(journalPath)) {
            return journalPath;
        }
        Path nextJournalPath = transactionRoot.resolve(StructureTransactionJournal.NEXT_FILE_NAME);
        if (!files.exists(nextJournalPath)) {
            return null;
        }
        if (!files.isRegularFile(nextJournalPath)) {
            throw new IOException("Structure transaction next journal is not a regular file: "
                    + nextJournalPath);
        }
        return nextJournalPath;
    }

    private boolean hasActiveRecoveryOwner(Path transactionRoot, UUID transactionId) throws IOException {
        Path claimPath = transactionRoot.resolve(RECOVERY_CLAIM_FILE);
        if (!files.exists(claimPath)) {
            return false;
        }
        RecoveryClaim claim = readRecoveryClaim(claimPath);
        RecoveryOwner owner;
        try {
            owner = new RecoveryOwner(
                    Path.of(claim.coordinatorTransactionRoot()),
                    claim.coordinatorTransactionId(),
                    claim.claimId()
            );
        } catch (RuntimeException e) {
            throw new IOException("Invalid external recovery claim at " + claimPath, e);
        }
        return coordinatorReferencesClaim(
                new PreparedRemovalToken(packRoot, transactionId),
                owner
        );
    }

    private void verifyRecoveryClaim(
            Path transactionRoot,
            UUID transactionId,
            RecoveryOwner owner
    ) throws IOException {
        Path claimPath = transactionRoot.resolve(RECOVERY_CLAIM_FILE);
        RecoveryClaim claim = readRecoveryClaim(claimPath);
        if (!Objects.equals(claim.coordinatorTransactionRoot(), owner.transactionRoot().toString())
                || !Objects.equals(claim.coordinatorTransactionId(), owner.transactionId())
                || !Objects.equals(claim.claimId(), owner.claimId())) {
            throw new IOException("Prepared removal recovery claim does not match its datapack coordinator");
        }
        if (!coordinatorReferencesClaim(new PreparedRemovalToken(packRoot, transactionId), owner)) {
            throw new IOException("Prepared removal recovery coordinator was not durably published");
        }
    }

    private void verifyPreparedRemovalAuthority(Path transactionRoot, UUID transactionId) throws IOException {
        Path journalPath = recoveryJournalPath(transactionRoot);
        if (journalPath == null || !files.isRegularFile(journalPath)) {
            throw new IOException("Missing prepared removal journal at " + transactionRoot);
        }
        StructureTransactionJournal journal;
        try {
            journal = StructureTransactionJournal.fromJson(readBoundedBytes(
                    journalPath,
                    MAX_STRUCTURE_STATE_BYTES,
                    "Prepared removal journal"
            ));
        } catch (RuntimeException e) {
            throw new IOException("Invalid prepared removal journal at " + journalPath, e);
        }
        if (!journal.transactionId().equals(transactionId)
                || journal.phase() != StructureTransactionJournal.Phase.PREPARED) {
            throw new IOException("Prepared removal journal does not match its datapack coordinator");
        }
        boolean ownershipManifestPresent = false;
        Path backupRoot = transactionRoot.resolve("backup").normalize();
        for (StructureTransactionJournal.Target state : journal.targets()) {
            if (!state.hadOriginal() || !state.replacementHash().isEmpty()) {
                throw new IOException("External coordinator claimed a non-removal structure transaction");
            }
            ownershipManifestPresent |= state.relativePath().startsWith(".iris/structure-manifests/");
            Path target = resolveTarget(state.relativePath());
            if (files.exists(target)) {
                throw new IOException("Prepared removal target reappeared before coordinator recovery: "
                        + state.relativePath());
            }
            Path backup = resolveTransactionPath(backupRoot, state.relativePath());
            if (!files.isRegularFile(backup)) {
                throw new IOException("Prepared removal backup is missing or not a regular file: "
                        + state.relativePath());
            }
            verifyOriginalContent(backup, state);
        }
        if (!ownershipManifestPresent) {
            throw new IOException("External coordinator removal has no structure ownership manifest");
        }
    }

    private RecoveryClaim readRecoveryClaim(Path claimPath) throws IOException {
        if (!files.isRegularFile(claimPath)) {
            throw new IOException("Invalid external recovery claim " + claimPath);
        }
        byte[] content = readBoundedBytes(
                claimPath,
                MAX_RECOVERY_CLAIM_BYTES,
                "External recovery claim"
        );
        try {
            RecoveryClaim claim = GSON.fromJson(
                    new String(content, StandardCharsets.UTF_8),
                    RecoveryClaim.class
            );
            if (claim == null || claim.schemaVersion() != RECOVERY_CLAIM_SCHEMA
                    || claim.coordinatorTransactionRoot() == null
                    || claim.coordinatorTransactionId() == null || claim.claimId() == null) {
                throw new IOException("Incomplete external recovery claim " + claimPath);
            }
            return claim;
        } catch (RuntimeException e) {
            throw new IOException("Invalid external recovery claim " + claimPath, e);
        }
    }

    private boolean coordinatorReferencesClaim(
            PreparedRemovalToken token,
            RecoveryOwner owner
    ) throws IOException {
        Path ownerRoot = owner.transactionRoot();
        Path ownerParent = Objects.requireNonNull(ownerRoot.getParent(), "recovery owner parent");
        if (Files.isSymbolicLink(ownerParent) || Files.isSymbolicLink(ownerRoot)) {
            throw new IOException("External recovery owner path contains a symbolic link: " + ownerRoot);
        }
        if (!Files.exists(ownerRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isDirectory(ownerRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("External recovery owner is not a directory: " + ownerRoot);
        }
        Path committed = ownerRoot.resolve("journal.json");
        Path next = ownerRoot.resolve("journal.next.json");
        Path journalPath;
        if (Files.exists(committed, LinkOption.NOFOLLOW_LINKS)) {
            journalPath = committed;
        } else if (Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
            journalPath = next;
        } else {
            try (Stream<Path> contents = Files.list(ownerRoot)) {
                if (contents.findAny().isEmpty()) {
                    return false;
                }
            }
            throw new IOException("External recovery owner has no transaction journal: " + ownerRoot);
        }
        if (Files.isSymbolicLink(journalPath)
                || !Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid external recovery owner journal " + journalPath);
        }
        JsonObject journal;
        try {
            journal = JsonParser.parseString(new String(
                    readBoundedBytes(
                            journalPath,
                            MAX_COORDINATOR_JOURNAL_BYTES,
                            "External recovery owner journal"
                    ),
                    StandardCharsets.UTF_8
            )).getAsJsonObject();
        } catch (RuntimeException e) {
            if (journalPath.equals(next) && isOnlyOwnerArtifact(ownerRoot, next)) {
                return false;
            }
            throw new IOException("Invalid external recovery owner journal " + journalPath, e);
        }
        if (!journal.has("schemaVersion") || journal.get("schemaVersion").getAsInt() != 2
                || !journal.has("transactionId")
                || !owner.transactionId().toString().equals(journal.get("transactionId").getAsString())
                || !journal.has("operation") || !"REMOVE".equals(journal.get("operation").getAsString())
                || !journal.has("editables") || !journal.get("editables").isJsonArray()) {
            throw new IOException("External recovery owner journal does not match its claim");
        }
        JsonArray editables = journal.getAsJsonArray("editables");
        for (JsonElement element : editables) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject editable = element.getAsJsonObject();
            if (editable.has("packRoot") && editable.has("transactionId") && editable.has("claimId")
                    && packRoot.toString().equals(editable.get("packRoot").getAsString())
                    && token.transactionId().toString().equals(editable.get("transactionId").getAsString())
                    && owner.claimId().toString().equals(editable.get("claimId").getAsString())) {
                return true;
            }
        }
        throw new IOException("External recovery owner journal does not contain its claimed structure transaction");
    }

    private boolean isOnlyOwnerArtifact(Path ownerRoot, Path artifact) throws IOException {
        try (Stream<Path> contents = Files.list(ownerRoot)) {
            List<Path> entries = contents.limit(2).toList();
            return entries.size() == 1 && Objects.equals(entries.getFirst(), artifact);
        }
    }

    private void verifyCommittedTransaction(StructureTransactionJournal journal) throws IOException {
        for (StructureTransactionJournal.Target state : journal.targets()) {
            Path target = resolveTarget(state.relativePath());
            if (state.replacementHash().isEmpty()) {
                if (files.exists(target)) {
                    throw new IOException("Committed structure transaction retained a removed target: "
                            + state.relativePath());
                }
                continue;
            }
            if (!files.isRegularFile(target)) {
                throw new IOException("Committed structure transaction target is missing or is not a regular file: "
                        + state.relativePath());
            }
            String actualHash = files.sha256(target);
            if (!state.replacementHash().equals(actualHash)) {
                throw new IOException("Committed structure transaction target hash mismatch for "
                        + state.relativePath());
            }
        }
    }

    private void restorePreparedTransaction(
            Path transactionRoot,
            StructureTransactionJournal journal
    ) throws IOException {
        Throwable recoveryFailure = null;
        ArrayList<StructureTransactionJournal.Target> targets = new ArrayList<>(journal.targets());
        Collections.reverse(targets);
        for (StructureTransactionJournal.Target state : targets) {
            try {
                restorePreparedTarget(transactionRoot, state);
            } catch (IOException | RuntimeException e) {
                recoveryFailure = appendFailure(recoveryFailure, e);
            }
        }
        if (recoveryFailure != null) {
            throw new IOException("Unable to restore prepared structure transaction " + transactionRoot,
                    recoveryFailure);
        }
    }

    private void restorePreparedTarget(
            Path transactionRoot,
            StructureTransactionJournal.Target state
    ) throws IOException {
        Path target = resolveTarget(state.relativePath());
        Path backupRoot = transactionRoot.resolve("backup").normalize();
        Path backup = resolveTransactionPath(backupRoot, state.relativePath());
        Path targetParent = Objects.requireNonNull(target.getParent(), "recovery target parent");
        if (!state.hadOriginal()) {
            removeReplacementIfPresent(target, state);
            return;
        }
        if (!files.exists(backup)) {
            verifyOriginalTarget(target, state);
            files.forceFile(target);
            files.forceDirectory(targetParent);
            return;
        }
        if (!files.isRegularFile(backup)) {
            throw new IOException("Structure transaction backup is not a regular file: " + backup);
        }
        verifyOriginalContent(backup, state);
        if (files.isRegularFile(target) && state.originalHash().equals(files.sha256(target))) {
            files.deleteIfExists(backup);
            files.forceFile(target);
            files.forceDirectory(targetParent);
            files.forceDirectory(Objects.requireNonNull(backup.getParent(), "recovery backup parent"));
            return;
        }
        removeReplacementIfPresent(target, state);
        files.createDirectories(targetParent);
        files.moveNew(backup, target);
        files.forceFile(target);
        files.forceDirectory(targetParent);
        files.forceDirectory(Objects.requireNonNull(backup.getParent(), "recovery backup parent"));
    }

    private boolean hasRecoveryData(Path backupRoot) throws IOException {
        if (!files.exists(backupRoot)) {
            return false;
        }
        if (!files.isDirectory(backupRoot)) {
            return true;
        }
        for (Path child : files.list(backupRoot)) {
            if (!files.isDirectory(child) || hasRecoveryData(child)) {
                return true;
            }
        }
        return false;
    }

    private IOException recoveryFailure(StructureRecoveryResult recovery) {
        IOException failure = new IOException("Unable to recover " + recovery.failures().size()
                + " incomplete structure transaction(s) under " + stagingRoot());
        for (StructureRecoveryResult.Failure recoveryFailure : recovery.failures()) {
            failure.addSuppressed(recoveryFailure.cause());
        }
        return failure;
    }

    private StructureOwnershipManifest.Provenance managedDatapackProvenance(
            StructureResourceBundle bundle
    ) {
        TreeMap<String, String> hashes = new TreeMap<>();
        TreeMap<String, String> mappings = new TreeMap<>();
        StringBuilder closure = new StringBuilder();
        for (StructureResourceBundle.Resource resource : bundle.resources().values()) {
            hashes.put(resource.relativePath(), resource.contentHash());
            mappings.put(resource.relativePath(), resource.relativePath());
            closure.append(resource.relativePath())
                    .append('=')
                    .append(resource.contentHash())
                    .append('\n');
        }
        String closureHash = StructureHash.sha256(closure.toString().getBytes(StandardCharsets.UTF_8));
        StructureSource source = bundle.source();
        String planInput = "managed-datapack\n"
                + source.kind() + '\n'
                + source.key().value() + '\n'
                + source.version() + '\n'
                + source.contentHash() + '\n'
                + closureHash;
        return new StructureOwnershipManifest.Provenance(
                StructureOwnershipManifest.Origin.MANAGED_DATAPACK,
                UUID.randomUUID().toString(),
                StructureHash.sha256(planInput.getBytes(StandardCharsets.UTF_8)),
                closureHash,
                Math.max(1L, System.currentTimeMillis()),
                hashes,
                mappings,
                StructureOwnershipManifest.RollbackDisposition.NONE
        );
    }

    private boolean managedDatapackUpgradeAllowed(
            StructureOwnershipManifest previousManifest,
            StructureSource nextSource
    ) {
        StructureOwnershipManifest.Origin origin = previousManifest.provenance().origin();
        if (origin != StructureOwnershipManifest.Origin.CREATED
                && origin != StructureOwnershipManifest.Origin.MANAGED_DATAPACK) {
            return false;
        }
        return previousManifest.source().kind() == nextSource.kind()
                && previousManifest.source().key().equals(nextSource.key());
    }

    private WritePlan buildPlan(
            StructureResourceBundle bundle,
            StructureWriteOptions options,
            StructureOwnershipManifest.Provenance provenance,
            ExistingProvenancePolicy provenancePolicy
    ) throws IOException {
        StructureWriteMode mode = options.mode();
        StructureOwnershipManifest nextManifest = StructureOwnershipManifest.from(bundle, provenance);
        String manifestRelativePath = nextManifest.relativePath();
        Path manifestPath = resolveTarget(manifestRelativePath);
        ArrayList<StructureWriteResult.Conflict> conflicts = new ArrayList<>();
        TreeMap<String, String> previousResourceHashes = new TreeMap<>();

        if (mode == StructureWriteMode.ADD_ONLY) {
            if (files.exists(manifestPath)) {
                conflicts.add(StructureWriteResult.Conflict.at(
                        manifestRelativePath,
                        StructureWriteResult.ConflictReason.MANIFEST_EXISTS
                ));
            }
            findAddOnlyConflicts(bundle, conflicts);
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.ADD,
                    conflicts
            );
        }

        if (!files.exists(manifestPath)) {
            if (!options.expectedManifestHash().isEmpty()) {
                conflicts.add(StructureWriteResult.Conflict.staleManifest(
                        manifestRelativePath,
                        options.expectedManifestHash(),
                        ""));
            }
            findUnownedResources(bundle, previousResourceHashes, conflicts);
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.ADD,
                    conflicts
            );
        }

        if (!files.isRegularFile(manifestPath)) {
            conflicts.add(StructureWriteResult.Conflict.invalidManifest(
                    manifestRelativePath,
                    "Ownership manifest is not a regular file"
            ));
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.OVERWRITE,
                    conflicts
            );
        }

        byte[] manifestContent;
        try {
            manifestContent = readBoundedBytes(
                    manifestPath,
                    MAX_STRUCTURE_STATE_BYTES,
                    "Structure ownership manifest"
            );
        } catch (IOException exception) {
            conflicts.add(StructureWriteResult.Conflict.invalidManifest(
                    manifestRelativePath,
                    exception.toString()));
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.OVERWRITE,
                    conflicts
            );
        }
        if (!options.expectedManifestHash().isEmpty()) {
            String actualManifestHash = StructureHash.sha256(manifestContent);
            if (!options.expectedManifestHash().equals(actualManifestHash)) {
                conflicts.add(StructureWriteResult.Conflict.staleManifest(
                        manifestRelativePath,
                        options.expectedManifestHash(),
                        actualManifestHash));
                return createPlan(
                        bundle,
                        nextManifest,
                        previousResourceHashes,
                        StructureWriteResult.Action.OVERWRITE,
                        conflicts
                );
            }
        }

        StructureOwnershipManifest previousManifest;
        try {
            previousManifest = StructureOwnershipManifest.fromJson(manifestContent);
        } catch (RuntimeException e) {
            conflicts.add(StructureWriteResult.Conflict.invalidManifest(manifestRelativePath, e.toString()));
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.OVERWRITE,
                    conflicts
            );
        }

        if (!previousManifest.structure().equals(bundle.key())) {
            conflicts.add(StructureWriteResult.Conflict.invalidManifest(
                    manifestRelativePath,
                    "Ownership manifest belongs to " + previousManifest.structure()
            ));
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.OVERWRITE,
                    conflicts
            );
        }

        if (provenancePolicy == ExistingProvenancePolicy.PRESERVE) {
            nextManifest = StructureOwnershipManifest.from(bundle, previousManifest.provenance());
        } else if (!managedDatapackUpgradeAllowed(previousManifest, bundle.source())) {
            conflicts.add(new StructureWriteResult.Conflict(
                    manifestRelativePath,
                    StructureWriteResult.ConflictReason.PROVENANCE_MISMATCH,
                    "",
                    "",
                    "Managed datapack import cannot replace non-managed structure provenance"
            ));
            return createPlan(
                    bundle,
                    nextManifest,
                    previousResourceHashes,
                    StructureWriteResult.Action.OVERWRITE,
                    conflicts
            );
        }
        previousResourceHashes.putAll(previousManifest.resourceHashes());
        verifyOwnedResources(previousResourceHashes, conflicts);
        findUnownedResources(bundle, previousResourceHashes, conflicts);
        StructureWriteResult.Action action = nextManifest.equals(previousManifest) && conflicts.isEmpty()
                ? StructureWriteResult.Action.NONE
                : StructureWriteResult.Action.OVERWRITE;
        return createPlan(bundle, nextManifest, previousResourceHashes, action, conflicts);
    }

    private void findAddOnlyConflicts(
            StructureResourceBundle bundle,
            List<StructureWriteResult.Conflict> conflicts
    ) {
        for (String relativePath : bundle.resources().keySet()) {
            if (files.exists(resolveTarget(relativePath))) {
                conflicts.add(StructureWriteResult.Conflict.at(
                        relativePath,
                        StructureWriteResult.ConflictReason.RESOURCE_EXISTS
                ));
            }
        }
    }

    private void findUnownedResources(
            StructureResourceBundle bundle,
            Map<String, String> previousResourceHashes,
            List<StructureWriteResult.Conflict> conflicts
    ) {
        for (String relativePath : bundle.resources().keySet()) {
            if (!previousResourceHashes.containsKey(relativePath) && files.exists(resolveTarget(relativePath))) {
                conflicts.add(StructureWriteResult.Conflict.at(
                        relativePath,
                        StructureWriteResult.ConflictReason.UNOWNED_RESOURCE
                ));
            }
        }
    }

    private void verifyOwnedResources(
            Map<String, String> previousResourceHashes,
            List<StructureWriteResult.Conflict> conflicts
    ) throws IOException {
        for (Map.Entry<String, String> entry : previousResourceHashes.entrySet()) {
            String relativePath = entry.getKey();
            Path target = resolveTarget(relativePath);
            if (!files.exists(target)) {
                conflicts.add(StructureWriteResult.Conflict.at(
                        relativePath,
                        StructureWriteResult.ConflictReason.MISSING_OWNED_RESOURCE
                ));
                continue;
            }
            if (!files.isRegularFile(target)) {
                conflicts.add(StructureWriteResult.Conflict.at(
                        relativePath,
                        StructureWriteResult.ConflictReason.NON_FILE_RESOURCE
                ));
                continue;
            }
            String actualHash = files.sha256(target);
            if (!entry.getValue().equals(actualHash)) {
                conflicts.add(StructureWriteResult.Conflict.modified(relativePath, entry.getValue(), actualHash));
            }
        }
    }

    private WritePlan createPlan(
            StructureResourceBundle bundle,
            StructureOwnershipManifest nextManifest,
            Map<String, String> previousResourceHashes,
            StructureWriteResult.Action action,
            List<StructureWriteResult.Conflict> conflicts
    ) {
        TreeSet<String> affectedResources = new TreeSet<>(previousResourceHashes.keySet());
        affectedResources.addAll(bundle.resources().keySet());
        affectedResources.add(nextManifest.relativePath());
        ArrayList<StructureWriteResult.Conflict> orderedConflicts = new ArrayList<>(conflicts);
        orderedConflicts.sort((left, right) -> left.relativePath().compareTo(right.relativePath()));
        return new WritePlan(
                bundle,
                nextManifest,
                nextManifest.toJson(),
                action,
                List.copyOf(orderedConflicts),
                List.copyOf(affectedResources)
        );
    }

    private List<StructureWriteResult.Conflict> verifyReadSet(StructureTransactionReadSet readSet) {
        if (readSet.isEmpty()) {
            return List.of();
        }
        ArrayList<StructureWriteResult.Conflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, String> entry : readSet.fileHashes().entrySet()) {
            String relativePath = entry.getKey();
            String expectedHash = entry.getValue();
            try {
                Path target = resolveTarget(relativePath);
                if (!files.exists(target)) {
                    conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                            relativePath,
                            expectedHash,
                            "",
                            "Read-set file is missing"));
                } else if (!files.isRegularFile(target)) {
                    conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                            relativePath,
                            expectedHash,
                            "",
                            "Read-set path is not a regular file"));
                } else {
                    String actualHash = sha256ReadSetTarget(target);
                    if (!expectedHash.equals(actualHash)) {
                        conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                                relativePath,
                                expectedHash,
                                actualHash,
                                "Read-set file content changed"));
                    }
                }
            } catch (IOException | RuntimeException exception) {
                conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                        relativePath,
                        expectedHash,
                        "",
                        "Cannot verify read-set file: " + describe(exception)));
            }
        }
        for (String relativePath : readSet.absentPaths()) {
            try {
                Path target = resolveTarget(relativePath);
                if (files.exists(target)) {
                    conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                            relativePath,
                            "",
                            "",
                            "Read-set path was expected to remain absent"));
                }
            } catch (RuntimeException exception) {
                conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                        relativePath,
                        "",
                        "",
                        "Cannot verify absent read-set path: " + describe(exception)));
            }
        }
        for (Map.Entry<String, List<String>> entry : readSet.directoryEntries().entrySet()) {
            String relativePath = entry.getKey();
            List<String> expectedEntries = entry.getValue();
            try {
                List<String> actualEntries = actualDirectoryEntries(relativePath);
                if (!expectedEntries.equals(actualEntries)) {
                    String expectedHash = StructureHash.sha256(
                            String.join("\n", expectedEntries).getBytes(StandardCharsets.UTF_8));
                    String actualHash = StructureHash.sha256(
                            String.join("\n", actualEntries).getBytes(StandardCharsets.UTF_8));
                    conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                            relativePath,
                            expectedHash,
                            actualHash,
                            "Read-set directory membership changed"));
                }
            } catch (IOException | RuntimeException exception) {
                conflicts.add(StructureWriteResult.Conflict.staleReadSet(
                        relativePath,
                        "",
                        "",
                        "Cannot verify read-set directory: " + describe(exception)));
            }
        }
        return orderedConflicts(conflicts);
    }

    private List<String> actualDirectoryEntries(String relativePath) throws IOException {
        Path directory = resolveTarget(relativePath);
        if (!files.exists(directory)) {
            return List.of();
        }
        if (!files.isDirectory(directory)) {
            throw new IOException("Read-set directory is not a directory: " + relativePath);
        }
        ArrayList<String> entries = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (path.equals(directory)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Read-set directory contains a symbolic link: " + path);
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Read-set directory contains a non-file entry: " + path);
                }
                entries.add(packRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'));
                if (entries.size() > StructureTransactionReadSet.MAX_ENTRIES) {
                    throw new IOException("Read-set directory exceeds "
                            + StructureTransactionReadSet.MAX_ENTRIES + " entries");
                }
            }
        }
        Collections.sort(entries);
        return List.copyOf(entries);
    }

    private String sha256ReadSetTarget(Path target) throws IOException {
        try (InputStream input = Files.newInputStream(
                target,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            return StructureHash.sha256(new BoundedReadSetInputStream(input));
        }
    }

    private void requireReadSetUnchanged(StructureTransactionReadSet readSet) throws IOException {
        List<StructureWriteResult.Conflict> conflicts = verifyReadSet(readSet);
        if (conflicts.isEmpty()) {
            return;
        }
        StructureWriteResult.Conflict conflict = conflicts.getFirst();
        throw new ReadSetChangedException(conflict);
    }

    private StructureWriteResult readSetConflictResult(
            StructureResourceBundle bundle,
            List<StructureWriteResult.Conflict> conflicts
    ) {
        StructureOwnershipManifest manifest = StructureOwnershipManifest.from(bundle);
        TreeSet<String> affectedResources = new TreeSet<>(bundle.resources().keySet());
        affectedResources.addAll(conflicts.stream().map(StructureWriteResult.Conflict::relativePath).toList());
        affectedResources.add(manifest.relativePath());
        return new StructureWriteResult(
                StructureWriteResult.Status.OWNERSHIP_CONFLICT,
                StructureWriteResult.Action.NONE,
                orderedConflicts(conflicts),
                List.copyOf(affectedResources),
                manifest.relativePath(),
                Optional.empty()
        );
    }

    private List<StructureWriteResult.Conflict> orderedConflicts(
            List<StructureWriteResult.Conflict> conflicts
    ) {
        ArrayList<StructureWriteResult.Conflict> ordered = new ArrayList<>(conflicts);
        ordered.sort((left, right) -> left.relativePath().compareTo(right.relativePath()));
        return List.copyOf(ordered);
    }

    private StructureWriteResult commitClaim(
            StructureOwnershipManifest manifest,
            StructureTransactionReadSet readSet
    ) {
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = stagingRoot().resolve(transactionId.toString()).normalize();
        Path stagedRoot = transactionRoot.resolve("staged");
        Path backupRoot = transactionRoot.resolve("backup");
        Path stagedManifest = stagedRoot.resolve("ownership-manifest.json");
        byte[] manifestContent = manifest.toJson();
        ArrayList<InstalledTarget> installedTargets = new ArrayList<>();
        StructureTransactionJournal journal;
        try {
            if (manifestContent.length > MAX_STRUCTURE_STATE_BYTES) {
                throw new IOException("Structure ownership manifest exceeds "
                        + MAX_STRUCTURE_STATE_BYTES + " bytes");
            }
            files.createDirectories(stagedRoot);
            files.createDirectories(backupRoot);
            files.writeNew(stagedManifest, manifestContent);
            files.forceFile(stagedManifest);
            StructureTransactionJournal.Target target = new StructureTransactionJournal.Target(
                    manifest.relativePath(),
                    false,
                    "",
                    StructureHash.sha256(manifestContent));
            journal = StructureTransactionJournal.prepared(transactionId, List.of(target));
            writeJournal(transactionRoot, journal);
            files.forceDirectory(transactionRoot);
            files.forceDirectory(stagingRoot());
            requireReadSetUnchanged(readSet);
            verifyTargetSnapshot(journal);
            Path manifestTarget = resolveTarget(manifest.relativePath());
            files.createDirectories(Objects.requireNonNull(manifestTarget.getParent(), "manifest target parent"));
            files.moveNew(stagedManifest, manifestTarget);
            installedTargets.add(new InstalledTarget(manifestTarget, StructureHash.sha256(manifestContent)));
            files.forceFile(manifestTarget);
            files.forceDirectory(Objects.requireNonNull(manifestTarget.getParent(), "manifest target parent"));
        } catch (IOException | RuntimeException commitFailure) {
            return rollbackClaimResult(manifest, transactionRoot, installedTargets, commitFailure);
        }

        boolean committedJournalWritten = false;
        try {
            writeJournal(transactionRoot, journal.committed());
            committedJournalWritten = true;
            files.forceDirectory(transactionRoot);
        } catch (IOException | RuntimeException commitPhaseFailure) {
            if (committedJournalWritten || isCommittedJournal(transactionRoot, commitPhaseFailure)) {
                return claimResult(
                        StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED,
                        manifest,
                        List.of(),
                        Optional.of(commitPhaseFailure));
            }
            return rollbackClaimResult(manifest, transactionRoot, installedTargets, commitPhaseFailure);
        }

        try {
            cleanupTransaction(transactionRoot);
        } catch (IOException | RuntimeException cleanupFailure) {
            return claimResult(
                    StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED,
                    manifest,
                    List.of(),
                    Optional.of(cleanupFailure));
        }
        return claimResult(
                StructureWriteResult.Status.ADDED,
                manifest,
                List.of(),
                Optional.empty());
    }

    private StructureWriteResult rollbackClaimResult(
            StructureOwnershipManifest manifest,
            Path transactionRoot,
            List<InstalledTarget> installedTargets,
            Throwable failure
    ) {
        Optional<Throwable> rollbackFailure = rollback(Map.of(), installedTargets);
        if (rollbackFailure.isPresent()) {
            failure.addSuppressed(new IOException(
                    "Transaction recovery data retained at " + transactionRoot,
                    rollbackFailure.get()));
            return claimResult(
                    StructureWriteResult.Status.FAILED,
                    manifest,
                    List.of(),
                    Optional.of(failure));
        }
        cleanupAfterFailure(transactionRoot, failure);
        if (failure instanceof ReadSetChangedException readSetChanged) {
            return claimResult(
                    StructureWriteResult.Status.OWNERSHIP_CONFLICT,
                    manifest,
                    List.of(readSetChanged.conflict()),
                    Optional.empty());
        }
        return claimResult(
                StructureWriteResult.Status.ROLLED_BACK,
                manifest,
                List.of(),
                Optional.of(failure));
    }

    private StructureWriteResult claimResult(
            StructureWriteResult.Status status,
            StructureOwnershipManifest manifest,
            List<StructureWriteResult.Conflict> conflicts,
            Optional<Throwable> failure
    ) {
        TreeSet<String> affectedResources = new TreeSet<>(manifest.resourceHashes().keySet());
        affectedResources.add(manifest.relativePath());
        return new StructureWriteResult(
                status,
                StructureWriteResult.Action.ADD,
                orderedConflicts(conflicts),
                List.copyOf(affectedResources),
                manifest.relativePath(),
                failure
        );
    }

    private StructureWriteResult commit(WritePlan plan, StructureTransactionReadSet readSet) {
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = stagingRoot().resolve(transactionId.toString()).normalize();
        Path stagedRoot = transactionRoot.resolve("staged");
        Path backupRoot = transactionRoot.resolve("backup");
        Path stagedManifest = stagedRoot.resolve("ownership-manifest.json");
        LinkedHashMap<Path, Path> backups = new LinkedHashMap<>();
        ArrayList<InstalledTarget> installedTargets = new ArrayList<>();
        StructureTransactionJournal journal;

        try {
            files.createDirectories(stagedRoot);
            files.createDirectories(backupRoot);
            stageResources(plan, stagedRoot, stagedManifest);
            journal = createJournal(plan, transactionId);
            writeJournal(transactionRoot, journal);
            files.forceDirectory(transactionRoot);
            files.forceDirectory(stagingRoot());
            requireReadSetUnchanged(readSet);
            verifyTargetSnapshot(journal);
            backupTargets(journal, backupRoot, backups);
            installResources(plan, stagedRoot, stagedManifest, installedTargets);
        } catch (IOException | RuntimeException commitFailure) {
            return rollbackResult(plan, transactionRoot, backups, installedTargets, commitFailure);
        }

        boolean committedJournalWritten = false;
        try {
            writeJournal(transactionRoot, journal.committed());
            committedJournalWritten = true;
            files.forceDirectory(transactionRoot);
        } catch (IOException | RuntimeException commitPhaseFailure) {
            if (committedJournalWritten || isCommittedJournal(transactionRoot, commitPhaseFailure)) {
                return result(
                        StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED,
                        plan,
                        Optional.of(commitPhaseFailure)
                );
            }
            return rollbackResult(plan, transactionRoot, backups, installedTargets, commitPhaseFailure);
        }

        try {
            cleanupTransaction(transactionRoot);
        } catch (IOException | RuntimeException cleanupFailure) {
            return result(
                    StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED,
                    plan,
                    Optional.of(cleanupFailure)
            );
        }

        StructureWriteResult.Status status = plan.action() == StructureWriteResult.Action.ADD
                ? StructureWriteResult.Status.ADDED
                : StructureWriteResult.Status.OVERWRITTEN;
        return result(status, plan, Optional.empty());
    }

    private void stageResources(WritePlan plan, Path stagedRoot, Path stagedManifest) throws IOException {
        for (StructureResourceBundle.Resource resource : plan.bundle().resources().values()) {
            Path staged = stagedRoot.resolve(resource.relativePath()).normalize();
            files.createDirectories(Objects.requireNonNull(staged.getParent(), "staged parent"));
            files.writeNew(staged, resource.contentForWrite());
            files.forceFile(staged);
        }
        files.createDirectories(Objects.requireNonNull(stagedManifest.getParent(), "manifest parent"));
        files.writeNew(stagedManifest, plan.nextManifestContent());
        files.forceFile(stagedManifest);
    }

    private StructureTransactionJournal createJournal(WritePlan plan, UUID transactionId) throws IOException {
        ArrayList<StructureTransactionJournal.Target> targets = new ArrayList<>(plan.affectedResources().size());
        for (String relativePath : plan.affectedResources()) {
            Path target = resolveTarget(relativePath);
            boolean hadOriginal = files.exists(target);
            targets.add(new StructureTransactionJournal.Target(
                    relativePath,
                    hadOriginal,
                    hadOriginal ? files.sha256(target) : "",
                    replacementHash(plan, relativePath)
            ));
        }
        return StructureTransactionJournal.prepared(transactionId, targets);
    }

    private void writeJournal(
            Path transactionRoot,
            StructureTransactionJournal journal
    ) throws IOException {
        byte[] content = journal.toJson();
        if (content.length > MAX_STRUCTURE_STATE_BYTES) {
            throw new IOException("Structure transaction journal exceeds "
                    + MAX_STRUCTURE_STATE_BYTES + " bytes");
        }
        Path journalPath = transactionRoot.resolve(StructureTransactionJournal.FILE_NAME);
        Path nextJournalPath = transactionRoot.resolve(StructureTransactionJournal.NEXT_FILE_NAME);
        files.deleteIfExists(nextJournalPath);
        files.writeNew(nextJournalPath, content);
        files.forceFile(nextJournalPath);
        files.move(nextJournalPath, journalPath);
    }

    private boolean isCommittedJournal(Path transactionRoot, Throwable commitPhaseFailure) {
        Path journalPath = transactionRoot.resolve(StructureTransactionJournal.FILE_NAME);
        if (!files.isRegularFile(journalPath)) {
            return false;
        }
        try {
            return StructureTransactionJournal.fromJson(readBoundedBytes(
                    journalPath,
                    MAX_STRUCTURE_STATE_BYTES,
                    "Structure transaction journal"
            )).phase()
                    == StructureTransactionJournal.Phase.COMMITTED;
        } catch (IOException | RuntimeException e) {
            commitPhaseFailure.addSuppressed(e);
            return false;
        }
    }

    private void verifyTargetSnapshot(StructureTransactionJournal journal) throws IOException {
        for (StructureTransactionJournal.Target state : journal.targets()) {
            Path target = resolveTarget(state.relativePath());
            boolean exists = files.exists(target);
            if (exists != state.hadOriginal()) {
                throw new IOException("Structure transaction target changed during preparation: "
                        + state.relativePath());
            }
            if (state.hadOriginal()) {
                verifyOriginalTarget(target, state);
            }
        }
    }

    private void backupTargets(
            StructureTransactionJournal journal,
            Path backupRoot,
            Map<Path, Path> backups
    ) throws IOException {
        for (StructureTransactionJournal.Target state : journal.targets()) {
            if (!state.hadOriginal()) {
                continue;
            }
            Path target = resolveTarget(state.relativePath());
            Path backup = resolveTransactionPath(backupRoot, state.relativePath());
            files.createDirectories(Objects.requireNonNull(backup.getParent(), "backup parent"));
            backups.put(target, backup);
            files.moveNew(target, backup);
            verifyOriginalContent(backup, state);
            files.forceDirectory(Objects.requireNonNull(target.getParent(), "backup target parent"));
            files.forceDirectory(Objects.requireNonNull(backup.getParent(), "backup parent"));
        }
    }

    private void installResources(
            WritePlan plan,
            Path stagedRoot,
            Path stagedManifest,
            List<InstalledTarget> installedTargets
    ) throws IOException {
        for (StructureResourceBundle.Resource resource : plan.bundle().resources().values()) {
            Path source = stagedRoot.resolve(resource.relativePath()).normalize();
            Path target = resolveTarget(resource.relativePath());
            files.createDirectories(Objects.requireNonNull(target.getParent(), "target parent"));
            files.moveNew(source, target);
            installedTargets.add(new InstalledTarget(target, resource.contentHash()));
            files.forceFile(target);
            files.forceDirectory(Objects.requireNonNull(target.getParent(), "target parent"));
        }
        Path manifestTarget = resolveTarget(plan.nextManifest().relativePath());
        files.createDirectories(Objects.requireNonNull(manifestTarget.getParent(), "manifest target parent"));
        files.moveNew(stagedManifest, manifestTarget);
        installedTargets.add(new InstalledTarget(
                manifestTarget,
                StructureHash.sha256(plan.nextManifestContent())
        ));
        files.forceFile(manifestTarget);
        files.forceDirectory(Objects.requireNonNull(manifestTarget.getParent(), "manifest target parent"));
    }

    private StructureWriteResult rollbackResult(
            WritePlan plan,
            Path transactionRoot,
            Map<Path, Path> backups,
            List<InstalledTarget> installedTargets,
            Throwable commitFailure
    ) {
        Optional<Throwable> rollbackFailure = rollback(backups, installedTargets);
        if (rollbackFailure.isPresent()) {
            IOException retainedRecovery = new IOException(
                    "Transaction recovery data retained at " + transactionRoot,
                    rollbackFailure.get()
            );
            commitFailure.addSuppressed(retainedRecovery);
            return result(StructureWriteResult.Status.FAILED, plan, Optional.of(commitFailure));
        }
        cleanupAfterFailure(transactionRoot, commitFailure);
        if (commitFailure instanceof ReadSetChangedException readSetChanged) {
            return readSetConflictResult(plan.bundle(), List.of(readSetChanged.conflict()));
        }
        return result(StructureWriteResult.Status.ROLLED_BACK, plan, Optional.of(commitFailure));
    }

    private Optional<Throwable> rollback(Map<Path, Path> backups, List<InstalledTarget> installedTargets) {
        Throwable rollbackFailure = null;
        ArrayList<InstalledTarget> reversedTargets = new ArrayList<>(installedTargets);
        Collections.reverse(reversedTargets);
        for (InstalledTarget installedTarget : reversedTargets) {
            try {
                removeInstalledTarget(installedTarget);
            } catch (IOException | RuntimeException e) {
                rollbackFailure = appendFailure(rollbackFailure, e);
            }
        }

        ArrayList<Map.Entry<Path, Path>> reversedBackups = new ArrayList<>(backups.entrySet());
        Collections.reverse(reversedBackups);
        for (Map.Entry<Path, Path> entry : reversedBackups) {
            Path target = entry.getKey();
            Path backup = entry.getValue();
            if (!files.exists(backup)) {
                continue;
            }
            try {
                files.createDirectories(Objects.requireNonNull(target.getParent(), "rollback target parent"));
                files.moveNew(backup, target);
                files.forceFile(target);
                files.forceDirectory(Objects.requireNonNull(target.getParent(), "rollback target parent"));
                files.forceDirectory(Objects.requireNonNull(backup.getParent(), "rollback backup parent"));
            } catch (IOException | RuntimeException e) {
                rollbackFailure = appendFailure(rollbackFailure, e);
            }
        }
        return Optional.ofNullable(rollbackFailure);
    }

    private void cleanupAfterFailure(Path transactionRoot, Throwable failure) {
        try {
            cleanupTransaction(transactionRoot);
        } catch (IOException | RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private void cleanupTransaction(Path transactionRoot) throws IOException {
        files.deleteTree(transactionRoot.resolve("staged"));
        files.deleteTree(transactionRoot.resolve("backup"));
        files.deleteIfExists(transactionRoot.resolve(StructureTransactionJournal.NEXT_FILE_NAME));
        forceExistingDirectory(transactionRoot);
        files.deleteIfExists(transactionRoot.resolve(StructureTransactionJournal.FILE_NAME));
        files.deleteTree(transactionRoot);
        Path stagingRoot = stagingRoot();
        if (files.exists(stagingRoot)) {
            files.forceDirectory(stagingRoot);
        }
    }

    private Throwable appendFailure(Throwable existing, Throwable next) {
        if (existing == null) {
            return next;
        }
        existing.addSuppressed(next);
        return existing;
    }

    private String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private void verifyOriginalTarget(
            Path target,
            StructureTransactionJournal.Target state
    ) throws IOException {
        if (!files.isRegularFile(target)) {
            throw new IOException("Structure transaction original target is missing or is not a regular file for "
                    + state.relativePath());
        }
        verifyOriginalContent(target, state);
    }

    private void verifyOriginalContent(
            Path path,
            StructureTransactionJournal.Target state
    ) throws IOException {
        String actualHash = files.sha256(path);
        if (!state.originalHash().equals(actualHash)) {
            throw new IOException("Structure transaction original content hash mismatch for "
                    + state.relativePath());
        }
    }

    private void removeReplacementIfPresent(
            Path target,
            StructureTransactionJournal.Target state
    ) throws IOException {
        if (!files.exists(target)) {
            return;
        }
        if (!files.isRegularFile(target)) {
            throw new IOException("Structure transaction replacement is not a regular file for "
                    + state.relativePath());
        }
        if (state.replacementHash().isEmpty()) {
            throw new IOException("Unexpected replacement appeared while recovering " + state.relativePath());
        }
        String actualHash = files.sha256(target);
        if (!state.replacementHash().equals(actualHash)) {
            throw new IOException("Structure transaction replacement content changed after the interrupted write for "
                    + state.relativePath());
        }
        files.deleteIfExists(target);
        forceExistingDirectory(Objects.requireNonNull(target.getParent(), "replacement target parent"));
    }

    private String replacementHash(WritePlan plan, String relativePath) {
        StructureResourceBundle.Resource resource = plan.bundle().resources().get(relativePath);
        if (resource != null) {
            return resource.contentHash();
        }
        if (plan.nextManifest().relativePath().equals(relativePath)) {
            return StructureHash.sha256(plan.nextManifestContent());
        }
        return "";
    }

    private void forceExistingDirectory(Path path) throws IOException {
        if (files.isDirectory(path)) {
            files.forceDirectory(path);
        }
    }

    private void removeInstalledTarget(InstalledTarget installedTarget) throws IOException {
        Path target = installedTarget.path();
        if (!files.exists(target)) {
            return;
        }
        if (!files.isRegularFile(target) || !installedTarget.contentHash().equals(files.sha256(target))) {
            throw new IOException("Installed structure target changed before rollback: " + target);
        }
        files.deleteIfExists(target);
        forceExistingDirectory(Objects.requireNonNull(target.getParent(), "rollback target parent"));
    }

    private StructureWriteResult result(
            StructureWriteResult.Status status,
            WritePlan plan,
            Optional<Throwable> failure
    ) {
        return new StructureWriteResult(
                status,
                plan.action(),
                plan.conflicts(),
                plan.affectedResources(),
                plan.nextManifest().relativePath(),
                failure
        );
    }

    private StructureWriteResult failedResult(StructureResourceBundle bundle, Throwable failure) {
        StructureOwnershipManifest manifest = StructureOwnershipManifest.from(bundle);
        TreeSet<String> affectedResources = new TreeSet<>(bundle.resources().keySet());
        affectedResources.add(manifest.relativePath());
        return new StructureWriteResult(
                StructureWriteResult.Status.FAILED,
                StructureWriteResult.Action.NONE,
                List.of(),
                List.copyOf(affectedResources),
                manifest.relativePath(),
                Optional.of(failure)
        );
    }

    private Path resolveTarget(String relativePath) {
        return resolveWithin(packRoot, relativePath, "Resource path escapes the pack root: ");
    }

    private Path resolveTransactionPath(Path root, String relativePath) {
        return resolveWithin(root, relativePath, "Transaction path escapes its root: ");
    }

    private Path stagingRoot() {
        return resolveWithin(packRoot, STAGING_RELATIVE_PATH, "Structure staging path escapes the pack root: ");
    }

    private ProcessLock acquireProcessLock() throws IOException {
        Path lockPath = resolveWithin(
                packRoot,
                PROCESS_LOCK_RELATIVE_PATH,
                "Structure authoring lock path escapes the pack root: "
        );
        Files.createDirectories(Objects.requireNonNull(lockPath.getParent(), "structure authoring lock parent"));
        FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        try {
            return new ProcessLock(channel, channel.lock());
        } catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Unable to acquire the structure authoring lock for " + packRoot, e);
        }
    }

    private byte[] readBoundedBytes(Path path, int maxBytes, String purpose) throws IOException {
        byte[] content;
        try (InputStream input = Files.newInputStream(
                path,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        )) {
            content = input.readNBytes(maxBytes + 1);
        }
        if (content.length > maxBytes) {
            throw new IOException(purpose + " exceeds " + maxBytes + " bytes");
        }
        return content;
    }

    private Path resolveWithin(Path root, String relativePath, String errorPrefix) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException(errorPrefix + relativePath);
        }
        rejectSymbolicLinks(root, target);
        return target;
    }

    private void rejectSymbolicLinks(Path root, Path target) {
        Path current = root;
        Path relative = root.relativize(target);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Structure authoring path contains a symbolic link: " + current);
            }
        }
    }

    private static Path canonicalPackRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            return normalized;
        }
        try {
            return normalized.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve the structure pack root " + normalized, e);
        }
    }

    private static Path validateRecoveryOwnerRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        Path parent = Objects.requireNonNull(normalized.getParent(), "recovery owner parent");
        Path parentName = Objects.requireNonNull(parent.getFileName(), "recovery owner directory");
        Path transactionName = Objects.requireNonNull(normalized.getFileName(), "recovery owner transaction");
        if (!".iris-datapack-transactions".equals(parentName.toString())) {
            throw new IllegalArgumentException("Recovery owner is outside the datapack transaction directory");
        }
        try {
            UUID.fromString(transactionName.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Recovery owner has an invalid transaction directory", e);
        }
        try {
            if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Recovery owner transaction parent is unsafe");
            }
            return parent.toRealPath().resolve(transactionName.toString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve recovery owner transaction parent", e);
        }
    }

    private record RecoveryClaim(
            int schemaVersion,
            String coordinatorTransactionRoot,
            UUID coordinatorTransactionId,
            UUID claimId
    ) {
    }

    private enum RecoveryOutcome {
        RESTORED_PREPARED,
        CLEANED_COMMITTED,
        CLEANED_ORPHAN
    }

    private enum ExistingProvenancePolicy {
        PRESERVE,
        INSTALL_MANAGED_DATAPACK
    }

    private record InstalledTarget(Path path, String contentHash) {
        private InstalledTarget {
            Objects.requireNonNull(path, "path");
            if (!StructureHash.isSha256(contentHash)) {
                throw new IllegalArgumentException("Installed target content hash is invalid");
            }
        }
    }

    private static final class ReadSetChangedException extends IOException {
        private final StructureWriteResult.Conflict conflict;

        private ReadSetChangedException(StructureWriteResult.Conflict conflict) {
            super("Structure transaction read set changed at " + conflict.relativePath()
                    + ": " + conflict.detail());
            this.conflict = conflict;
        }

        private StructureWriteResult.Conflict conflict() {
            return conflict;
        }
    }

    private static final class BoundedReadSetInputStream extends InputStream {
        private final InputStream delegate;
        private long consumed;

        private BoundedReadSetInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                recordBytes(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                recordBytes(read);
            }
            return read;
        }

        private void recordBytes(long bytes) throws IOException {
            consumed = Math.addExact(consumed, bytes);
            if (consumed > MAX_VERIFIED_READ_FILE_BYTES) {
                throw new IOException("Verified read-set file exceeds "
                        + MAX_VERIFIED_READ_FILE_BYTES + " bytes");
            }
        }
    }

    private record RemovalPlan(List<StructureTransactionJournal.Target> targets) {
    }

    private record ProcessLock(FileChannel channel, FileLock lock) implements AutoCloseable {
        private ProcessLock {
            Objects.requireNonNull(channel, "channel");
            Objects.requireNonNull(lock, "lock");
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                lock.release();
            } catch (IOException e) {
                failure = e;
            }
            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record WritePlan(
            StructureResourceBundle bundle,
            StructureOwnershipManifest nextManifest,
            byte[] nextManifestContent,
            StructureWriteResult.Action action,
            List<StructureWriteResult.Conflict> conflicts,
            List<String> affectedResources
    ) {
        private WritePlan {
            nextManifestContent = nextManifestContent.clone();
        }

        @Override
        public byte[] nextManifestContent() {
            return nextManifestContent.clone();
        }
    }
}
