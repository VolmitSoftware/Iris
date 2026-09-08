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
import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.UUID;

final class DatapackRemoval {

    private DatapackRemoval() {
    }

    static RemoveOutcome removeOutcomeLocked(VolmitSender sender, String id) {
        File root = IrisPlatforms.get().dataFolder("datapacks");
        return removeOutcomeLocked(sender, id, root, ServerConfigurator.getDatapacksFolder());
    }

    static boolean removeLocked(VolmitSender sender, String id, File root, List<File> worldFolders) {
        return removeOutcomeLocked(sender, id, root, worldFolders) == RemoveOutcome.REMOVED;
    }

    static RemoveOutcome removeOutcomeLocked(VolmitSender sender, String id, File root, List<File> worldFolders) {
        String requested = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        String cleaned = DatapackArchive.sanitizeId(id);
        if (requested.isBlank() || !requested.equals(cleaned) || DatapackArchive.RESERVED_IDS.contains(cleaned)) {
            DatapackSupport.message(sender, C.RED + "Invalid Iris-managed datapack id '" + requested + "'. Run /iris datapack list and use the exact listed id.");
            return RemoveOutcome.REJECTED;
        }
        try {
            DatapackScratchRecovery.recoverTransactions(root, worldFolders);
        } catch (IOException e) {
            DatapackSupport.message(sender, C.RED + "Datapack removal blocked by incomplete transaction recovery: " + e.getMessage());
            IrisLogging.reportError(e);
            return RemoveOutcome.REJECTED;
        }
        Manifest manifest = DatapackManifestStore.readManifest(root);
        Entry ownedEntry = manifest.findById(cleaned);
        if (ownedEntry == null) {
            DatapackSupport.message(sender, C.YELLOW + "No Iris-managed datapack named '" + cleaned + "'. Unmanaged world datapacks are never removed by Iris.");
            return RemoveOutcome.REJECTED;
        }

        List<File> targets;
        try {
            targets = preflightRemovalTargets(root, worldFolders, ownedEntry);
        } catch (IOException e) {
            DatapackSupport.message(sender, C.RED + "Refused to remove datapack '" + cleaned + "': " + e.getMessage());
            IrisLogging.reportError(e);
            return RemoveOutcome.REJECTED;
        }

        EditableImportRemoval editableRemoval = null;
        DirectoryRemoval directoryRemoval = null;
        ManifestWrite manifestWrite = null;
        DatapackCoordinator coordinator = null;
        try {
            editableRemoval = prepareEntryImportRemoval(ownedEntry, manifest.entries);
            directoryRemoval = prepareOwnedDirectories(targets);
            if (!manifest.removeById(cleaned)) {
                throw new IOException("Datapack manifest entry disappeared during removal");
            }
            manifestWrite = DatapackManifestStore.prepareManifestWrite(root, manifest);
            coordinator = DatapackTransactions.createRemovalCoordinator(root, ownedEntry, directoryRemoval, editableRemoval);
            coordinator.phase(CoordinatorPhase.PUBLISHING);
            directoryRemoval.prepare();
            coordinator.phase(CoordinatorPhase.PUBLISHED);
            manifestWrite.publish();
        } catch (IOException | RuntimeException removalFailure) {
            boolean manifestCommitted = manifestWrite != null && manifestWrite.published();
            if (manifestCommitted) {
                finishCommittedRemoval(cleaned, manifestWrite, directoryRemoval, editableRemoval, coordinator,
                        removalFailure);
                DatapackSupport.message(sender, C.GREEN + "Removed datapack '" + C.WHITE + cleaned + C.GREEN
                        + "'. Restart for it to stop generating, and delete its URL from the pack's datapackImports to keep it gone.");
                return RemoveOutcome.REMOVED;
            }
            boolean restored = rollbackRemoval(manifestWrite, directoryRemoval, editableRemoval, removalFailure);
            if (restored && coordinator != null) {
                try {
                    coordinator.finish();
                } catch (IOException cleanupFailure) {
                    removalFailure.addSuppressed(cleanupFailure);
                }
            }
            DatapackSupport.message(sender, C.RED + "Failed to remove datapack '" + cleaned
                    + "'; Iris attempted to restore every prior location: " + removalFailure.getMessage());
            IrisLogging.reportError(removalFailure);
            // A mutation was attempted; even a successful rollback is not provably identical
            // (the fingerprint does not cover datapacks/), so stay conservatively invalidated.
            return RemoveOutcome.FAILED_AFTER_MUTATION;
        }
        finishCommittedRemoval(cleaned, manifestWrite, directoryRemoval, editableRemoval, coordinator, null);
        DatapackSupport.message(sender, C.GREEN + "Removed datapack '" + C.WHITE + cleaned + C.GREEN
                + "'. Restart for it to stop generating, and delete its URL from the pack's datapackImports to keep it gone.");
        return RemoveOutcome.REMOVED;
    }

    static void finishCommittedRemoval(
            String id,
            ManifestWrite manifestWrite,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval,
            DatapackCoordinator coordinator,
            Throwable priorFailure
    ) {
        if (priorFailure != null) {
            IOException recoveryFailure = new IOException(
                    "Datapack manifest committed before publication durability was confirmed",
                    priorFailure
            );
            if (editableRemoval != null) {
                try {
                    editableRemoval.leaveForRecovery();
                } catch (IOException releaseFailure) {
                    recoveryFailure.addSuppressed(releaseFailure);
                }
            }
            try {
                manifestWrite.discard();
            } catch (IOException cleanupFailure) {
                recoveryFailure.addSuppressed(cleanupFailure);
            }
            IrisLogging.reportError("Datapack '" + id
                    + "' was removed but transaction cleanup requires restart recovery.", recoveryFailure);
            return;
        }

        IOException failure = null;
        if (coordinator != null) {
            try {
                coordinator.phase(CoordinatorPhase.COMMITTED);
            } catch (IOException phaseFailure) {
                failure = DatapackSupport.appendIOException(failure, phaseFailure);
                if (editableRemoval != null) {
                    try {
                        editableRemoval.leaveForRecovery();
                    } catch (IOException releaseFailure) {
                        failure = DatapackSupport.appendIOException(failure, releaseFailure);
                    }
                }
                try {
                    manifestWrite.discard();
                } catch (IOException cleanupFailure) {
                    failure = DatapackSupport.appendIOException(failure, cleanupFailure);
                }
                IrisLogging.reportError("Datapack '" + id
                        + "' was removed but transaction cleanup requires restart recovery.", failure);
                return;
            }
        }
        if (editableRemoval != null) {
            try {
                editableRemoval.markCommitted();
                editableRemoval.finishCommit();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure = DatapackSupport.appendIOException(failure, cleanupFailure);
                try {
                    editableRemoval.leaveForRecovery();
                } catch (IOException releaseFailure) {
                    failure = DatapackSupport.appendIOException(failure, releaseFailure);
                }
            }
        }
        if (directoryRemoval != null) {
            try {
                directoryRemoval.finishCommit();
            } catch (IOException | RuntimeException cleanupFailure) {
                failure = DatapackSupport.appendIOException(failure, cleanupFailure);
            }
        }
        try {
            manifestWrite.discard();
        } catch (IOException cleanupFailure) {
            failure = DatapackSupport.appendIOException(failure, cleanupFailure);
        }
        if (failure == null && coordinator != null) {
            try {
                coordinator.finish();
            } catch (IOException cleanupFailure) {
                failure = cleanupFailure;
            }
        }
        if (failure != null) {
            IrisLogging.reportError("Datapack '" + id
                    + "' was removed but transaction cleanup requires restart recovery.", failure);
        }
    }

    static List<File> preflightRemovalTargets(File root, List<File> worldFolders, Entry entry) throws IOException {
        List<File> targets = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        File staging = new File(root, "staging");
        DatapackSupport.verifyDirectoryContainerIfPresent(staging, "datapack staging");
        File stagedDir = new File(staging, entry.id);
        verifyAndCollectRemovalTarget(stagedDir, entry, targets, seen);
        for (File worldFolder : worldFolders) {
            DatapackSupport.verifyDirectoryContainerIfPresent(worldFolder, "world datapacks");
            verifyAndCollectRemovalTarget(new File(worldFolder, entry.id), entry, targets, seen);
        }
        return targets;
    }

    static void verifyAndCollectRemovalTarget(
            File target,
            Entry entry,
            List<File> targets,
            Set<Path> seen
    ) throws IOException {
        verifyOwnedDirectoryIfPresent(target, entry);
        Path normalized = target.toPath().toAbsolutePath().normalize();
        if (DatapackSupport.pathExists(normalized, "datapack removal target")) {
            Path identity = normalized.toRealPath();
            if (!seen.add(identity)) {
                throw new IOException("Aliased datapack removal target " + normalized);
            }
            targets.add(target);
        }
    }

    static EditableImportRemoval prepareEntryImportRemoval(
            Entry entry,
            List<Entry> manifestEntries
    ) throws IOException {
        List<PreparedEditableImport> prepared = new ArrayList<>();
        Set<String> targetIdSet = new TreeSet<>(entry.importedBundles.keySet());
        targetIdSet.addAll(entry.importedTargets.keySet());
        targetIdSet.addAll(entry.importAttempts.keySet());
        List<String> targetIds = new ArrayList<>(targetIdSet);
        targetIds.sort(String::compareTo);
        try {
            for (String targetId : targetIds) {
                Set<String> retainedKeys = invalidateRetainedImportClaims(
                        entry, targetId, manifestEntries);
                File dataFolder = new File(targetId);
                if (!dataFolder.isDirectory()) {
                    continue;
                }
                StructureTransactionWriter writer = new StructureTransactionWriter(dataFolder.toPath());
                List<StructureTransactionWriter.OwnedRemoval> removals = ownedImportRemovals(
                        writer,
                        entry,
                        targetId,
                        retainedKeys
                );
                StructureTransactionWriter.PreparedRemoval removal =
                        writer.prepareMatchingOwnedRemovals(removals);
                prepared.add(new PreparedEditableImport(dataFolder, removal));
            }
            return new EditableImportRemoval(prepared);
        } catch (IOException | RuntimeException preparationFailure) {
            IOException failure = preparationFailure instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException("Failed preparing editable datapack import cleanup", preparationFailure);
            for (int i = prepared.size() - 1; i >= 0; i--) {
                try {
                    prepared.get(i).removal().rollback();
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    static List<StructureTransactionWriter.OwnedRemoval> ownedImportRemovals(
            StructureTransactionWriter writer,
            Entry removingEntry,
            String targetId,
            Set<String> retainedKeys
    ) throws IOException {
        List<StructureTransactionWriter.OwnedRemoval> removals = new ArrayList<>();
        Map<String, Set<String>> removingClaims = importBundleClaims(removingEntry, targetId);
        for (Map.Entry<String, Set<String>> bundle : removingClaims.entrySet()) {
            try {
                StructureKey targetKey = StructureKey.parse(bundle.getKey());
                if (retainedKeys.contains(bundle.getKey())) {
                    continue;
                }
                Optional<StructureSource> ownedSource = writer.ownedSource(targetKey);
                if (ownedSource.isEmpty() || !sourceClaimsContain(bundle.getValue(), ownedSource.get())) {
                    continue;
                }
                removals.add(StructureTransactionWriter.OwnedRemoval.managedDatapack(
                        targetKey,
                        ownedSource.get().kind(),
                        ownedSource.get().key()
                ));
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure import inventory entry '"
                        + bundle.getKey() + "' -> '" + bundle.getValue() + "'", e);
            }
        }
        return List.copyOf(removals);
    }

    static Set<String> invalidateRetainedImportClaims(
            Entry removingEntry,
            String targetId,
            List<Entry> manifestEntries
    ) {
        Set<String> removingKeys = importBundleClaims(removingEntry, targetId).keySet();
        Set<String> retainedKeys = new TreeSet<>();
        for (Entry candidate : manifestEntries) {
            if (candidate == removingEntry) {
                continue;
            }
            Set<String> candidateKeys = importBundleClaims(candidate, targetId).keySet();
            boolean candidateRetained = false;
            for (String candidateKey : candidateKeys) {
                if (removingKeys.contains(candidateKey)) {
                    retainedKeys.add(candidateKey);
                    candidateRetained = true;
                }
            }
            if (candidateRetained) {
                candidate.importedTargets.remove(targetId);
                candidate.importAttempts.remove(targetId);
                candidate.structuresImported = false;
            }
        }
        return Set.copyOf(retainedKeys);
    }

    static Map<String, Set<String>> importBundleClaims(Entry entry, String targetId) {
        Map<String, Set<String>> claims = new TreeMap<>();
        addImportBundleClaims(claims, entry.importedBundles.getOrDefault(targetId, Map.of()));
        if (entry.importedBundles.containsKey(targetId) || entry.importedTargets.containsKey(targetId)) {
            addImportBundleClaims(claims, DatapackStructureImports.importBundleInventory(entry));
        }
        return claims;
    }

    static void addImportBundleClaims(
            Map<String, Set<String>> claims,
            Map<String, String> inventory
    ) {
        for (Map.Entry<String, String> bundle : inventory.entrySet()) {
            claims.computeIfAbsent(bundle.getKey(), ignored -> new TreeSet<>()).add(bundle.getValue());
        }
    }

    static boolean sourceClaimsContain(Set<String> claims, StructureSource source) throws IOException {
        for (String claimedKey : claims) {
            try {
                StructureKey sourceKey = StructureKey.parse(claimedKey);
                StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                        ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
                if (source.kind() == sourceKind && source.key().equals(sourceKey)) {
                    return true;
                }
            } catch (RuntimeException e) {
                throw new IOException("Invalid editable structure source key '" + claimedKey + "'", e);
            }
        }
        return false;
    }

    static DirectoryRemoval prepareOwnedDirectories(List<File> targets) throws IOException {
        List<DirectoryMove> planned = new ArrayList<>();
        for (File target : targets) {
            File parent = target.getParentFile();
            File backupRoot = new File(parent.getParentFile() == null ? parent : parent.getParentFile(), ".iris-datapack-remove");
            File backup = new File(backupRoot, target.getName() + "-" + UUID.randomUUID());
            DatapackSupport.ensureScratchDirectory(backupRoot, "datapack removal backup");
            DatapackInstall.validateInstallTree(target, parent, "Datapack removal target");
            planned.add(new DirectoryMove(
                    target,
                    backup,
                    DatapackOwnership.directoryHash(target),
                    DatapackOwnership.ownershipMarkerFingerprint(target),
                    DatapackSupport.directoryIdentity(target),
                    DatapackSupport.realDirectoryPath(parent, "datapack target root"),
                    DatapackSupport.realDirectoryPath(backupRoot, "datapack removal scratch root"),
                    DatapackSupport.directoryIdentity(parent),
                    DatapackSupport.directoryIdentity(backupRoot)
            ));
        }
        return new DirectoryRemoval(planned);
    }

    static boolean rollbackRemoval(
            ManifestWrite manifestWrite,
            DirectoryRemoval directoryRemoval,
            EditableImportRemoval editableRemoval,
            Throwable removalFailure
    ) {
        if (manifestWrite != null) {
            try {
                manifestWrite.discard();
            } catch (IOException discardFailure) {
                removalFailure.addSuppressed(discardFailure);
            }
        }
        if (directoryRemoval != null) {
            try {
                directoryRemoval.rollback();
            } catch (IOException rollbackFailure) {
                removalFailure.addSuppressed(rollbackFailure);
            }
        }
        if (editableRemoval != null) {
            try {
                editableRemoval.rollback();
            } catch (IOException rollbackFailure) {
                removalFailure.addSuppressed(rollbackFailure);
            }
        }
        return removalFailure.getSuppressed().length == 0;
    }

    static void verifyOwnedDirectoryIfPresent(File directory, Entry entry) throws IOException {
        if (!Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(directory.toPath())
                || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing non-directory or symbolic-link target " + directory.getPath());
        }
        Ownership ownership = DatapackOwnership.readOwnership(directory);
        if (!DatapackInstall.ownershipSourceMatches(ownership, entry)) {
            throw new IOException("Ownership marker at " + directory.getPath() + " belongs to '" + ownership.id + "'");
        }
        DatapackOwnership.removeFinderMetadata(directory);
        if (!Objects.equals(ownership.contentHash, DatapackOwnership.directoryHash(directory))) {
            throw new IOException("Refusing to remove modified or corrupt Iris-managed datapack " + directory.getPath());
        }
    }
}
