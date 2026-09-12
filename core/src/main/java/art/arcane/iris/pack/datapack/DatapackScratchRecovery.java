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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

final class DatapackScratchRecovery {
    static final int WINDOWS_LEGACY_PATH_LIMIT = 247;

    private DatapackScratchRecovery() {
    }

    static boolean recoverTransactions(File root, List<File> worldFolders) throws IOException {
        boolean changed = recoverStagingScratch(new File(root, "staging"));
        File transactionDirectory = new File(root, DatapackTransactions.TRANSACTION_DIRECTORY);
        Path transactionPath = transactionDirectory.toPath();
        if (!Files.exists(transactionPath, LinkOption.NOFOLLOW_LINKS)) {
            return recoverInstallScratch(root, worldFolders) | changed;
        }
        DatapackSupport.verifyDirectoryContainerIfPresent(transactionDirectory, "datapack transaction");
        Manifest committedManifest = DatapackManifestStore.readCommittedManifest(root);
        List<Path> transactionRoots;
        try (Stream<Path> paths = Files.list(transactionPath)) {
            transactionRoots = paths.limit(DatapackTransactions.MAX_TRANSACTION_COUNT + 2L).sorted().toList();
        }
        if (transactionRoots.size() == DatapackTransactions.MAX_TRANSACTION_COUNT + 2) {
            throw new IOException("Datapack transaction directory contains too many entries");
        }
        int transactionCount = 0;
        for (Path transactionRoot : transactionRoots) {
            if (isHarmlessRecoveryArtifact(transactionRoot)) {
                continue;
            }
            transactionCount++;
            if (transactionCount > DatapackTransactions.MAX_TRANSACTION_COUNT) {
                throw new IOException("Datapack transaction count exceeds " + DatapackTransactions.MAX_TRANSACTION_COUNT);
            }
        }
        for (Path transactionRoot : transactionRoots) {
            if (isHarmlessRecoveryArtifact(transactionRoot)) {
                changed |= Files.deleteIfExists(transactionRoot);
                continue;
            }
            recoverTransaction(root, worldFolders, committedManifest, transactionPath, transactionRoot);
            changed = true;
        }
        changed |= transactionDirectory.delete();
        return recoverInstallScratch(root, worldFolders) | changed;
    }

    static boolean recoverInstallScratch(File root, List<File> worldFolders) throws IOException {
        Set<Path> scratchRoots = new TreeSet<>();
        scratchRoots.add(DatapackInstall.installScratchRoot(new File(root, "staging")).toPath().toAbsolutePath().normalize());
        for (File worldFolder : worldFolders) {
            scratchRoots.add(DatapackInstall.installScratchRoot(worldFolder).toPath().toAbsolutePath().normalize());
        }
        boolean changed = false;
        for (Path scratchRoot : scratchRoots) {
            changed |= recoverInstallScratchRoot(scratchRoot);
        }
        return changed;
    }

    static boolean recoverInstallScratchRoot(Path scratchRoot) throws IOException {
        if (!Files.exists(scratchRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        DatapackSupport.verifyDirectoryContainerIfPresent(scratchRoot.toFile(), "datapack install scratch");
        List<Path> children;
        try (Stream<Path> paths = Files.list(scratchRoot)) {
            children = paths.limit(DatapackOwnership.MAX_MANAGED_PATHS + 2L).sorted().toList();
        }
        int managedEntries = 0;
        for (Path child : children) {
            if (!isHarmlessRecoveryArtifact(child)) {
                managedEntries++;
            }
            if (managedEntries > DatapackOwnership.MAX_MANAGED_PATHS) {
                throw new IOException("Datapack install scratch contains too many entries");
            }
        }

        List<StagingScratch> pending = new ArrayList<>();
        List<StagingScratch> backups = new ArrayList<>();
        boolean changed = false;
        for (Path child : children) {
            if (isHarmlessRecoveryArtifact(child)) {
                changed |= Files.deleteIfExists(child);
                continue;
            }
            StagingScratch scratch = parseInstallScratch(scratchRoot, child);
            if (scratch == null) {
                throw new IOException("Unexpected datapack install scratch artifact " + child);
            }
            if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid datapack install scratch artifact " + child);
            }
            validateScratchTree(child);
            if (scratch.kind() == StagingScratchKind.BACKUP) {
                backups.add(scratch);
            } else {
                pending.add(scratch);
            }
        }
        if (!backups.isEmpty()) {
            throw new IOException("Preserving unjournaled datapack install backup "
                    + backups.getFirst().path());
        }
        for (StagingScratch scratch : pending) {
            DatapackInstall.deleteInstallScratch(scratch.path().toFile(), "orphan datapack install pending directory");
            changed = true;
        }
        changed |= scratchRoot.toFile().delete();
        return changed;
    }

    static StagingScratch parseInstallScratch(Path scratchRoot, Path child) throws IOException {
        Path normalized = child.toAbsolutePath().normalize();
        if (!Objects.equals(normalized.getParent(), scratchRoot)) {
            throw new IOException("Datapack install scratch artifact escapes its root: " + child);
        }
        String name = normalized.getFileName().toString();
        int uuidStart = name.length() - 36;
        if (uuidStart <= 1 || name.charAt(uuidStart - 1) != '-') {
            return null;
        }
        try {
            UUID.fromString(name.substring(uuidStart));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String stem = name.substring(0, uuidStart - 1);
        StagingScratchKind kind = stem.endsWith("-backup")
                ? StagingScratchKind.BACKUP : StagingScratchKind.PENDING;
        String id = kind == StagingScratchKind.BACKUP
                ? stem.substring(0, stem.length() - "-backup".length()) : stem;
        if (id.isBlank() || !id.equals(DatapackArchive.sanitizeId(id)) || DatapackArchive.RESERVED_IDS.contains(id)) {
            return null;
        }
        return new StagingScratch(kind, id, normalized);
    }

    static boolean recoverStagingScratch(File stagingDirectory) throws IOException {
        Path stagingRoot = stagingDirectory.toPath().toAbsolutePath().normalize();
        if (!Files.exists(stagingRoot, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        DatapackSupport.verifyDirectoryContainerIfPresent(stagingDirectory, "datapack staging");
        List<Path> children;
        try (Stream<Path> paths = Files.list(stagingRoot)) {
            children = paths.limit(DatapackOwnership.MAX_MANAGED_PATHS + 1L).sorted().toList();
        }
        if (children.size() > DatapackOwnership.MAX_MANAGED_PATHS) {
            throw new IOException("Datapack staging contains too many entries");
        }

        List<StagingScratch> pending = new ArrayList<>();
        Map<String, List<StagingScratch>> backups = new TreeMap<>();
        for (Path child : children) {
            StagingScratch scratch = parseStagingScratch(stagingRoot, child);
            if (scratch == null) {
                continue;
            }
            if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid datapack staging scratch artifact " + child);
            }
            if (scratch.kind() == StagingScratchKind.PENDING) {
                validateScratchTree(child);
                pending.add(scratch);
            } else {
                backups.computeIfAbsent(scratch.id(), ignored -> new ArrayList<>()).add(scratch);
            }
        }

        for (Map.Entry<String, List<StagingScratch>> entry : backups.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new IOException("Ambiguous datapack staging backups for '" + entry.getKey() + "'");
            }
            StagingScratch backup = entry.getValue().getFirst();
            Ownership backupOwnership = verifyManagedScratchDirectory(backup.path().toFile(), backup.id());
            Path target = stagingRoot.resolve(backup.id()).normalize();
            if (!Objects.equals(target.getParent(), stagingRoot)) {
                throw new IOException("Datapack staging backup target escapes its root");
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                Ownership targetOwnership = verifyManagedScratchDirectory(target.toFile(), backup.id());
                if (!Objects.equals(backupOwnership.url, targetOwnership.url)) {
                    throw new IOException("Datapack staging backup source does not match its target: " + target);
                }
            }
        }

        boolean changed = false;
        for (List<StagingScratch> matches : backups.values()) {
            StagingScratch backup = matches.getFirst();
            Path target = stagingRoot.resolve(backup.id()).normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                DatapackCoordinatorResolution.deleteVerifiedDirectory(backup.path().toFile());
            } else {
                DatapackSupport.moveNew(backup.path(), target);
            }
            changed = true;
        }
        for (StagingScratch scratch : pending) {
            DatapackCoordinatorResolution.deleteVerifiedDirectory(scratch.path().toFile());
            changed = true;
        }
        DatapackSupport.forceDirectoryIfSupported(stagingRoot);
        return changed;
    }

    static StagingScratch parseStagingScratch(Path stagingRoot, Path child) throws IOException {
        Path normalized = child.toAbsolutePath().normalize();
        if (!Objects.equals(normalized.getParent(), stagingRoot)) {
            throw new IOException("Datapack staging artifact escapes its root: " + child);
        }
        String name = normalized.getFileName().toString();
        StagingScratchKind kind;
        String prefix;
        if (name.startsWith(".pending-")) {
            kind = StagingScratchKind.PENDING;
            prefix = ".pending-";
        } else if (name.startsWith(".backup-")) {
            kind = StagingScratchKind.BACKUP;
            prefix = ".backup-";
        } else {
            return null;
        }
        int uuidStart = name.length() - 36;
        if (uuidStart <= prefix.length() || name.charAt(uuidStart - 1) != '-') {
            return null;
        }
        String id = name.substring(prefix.length(), uuidStart - 1);
        if (!id.equals(DatapackArchive.sanitizeId(id)) || DatapackArchive.RESERVED_IDS.contains(id)) {
            return null;
        }
        try {
            UUID.fromString(name.substring(uuidStart));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new StagingScratch(kind, id, normalized);
    }

    static void validateScratchTree(Path root) throws IOException {
        FileStore rootStore = Files.getFileStore(root);
        int[] pathCount = new int[]{0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            private FileVisitResult inspect(Path entry, BasicFileAttributes attributes) throws IOException {
                pathCount[0]++;
                if (pathCount[0] > DatapackOwnership.MAX_MANAGED_PATHS) {
                    throw new IOException("Datapack scratch contains too many paths: " + root);
                }
                if (attributes.isSymbolicLink()
                        || attributes.isOther()
                        || (!attributes.isDirectory() && !attributes.isRegularFile())) {
                    throw new IOException("Datapack scratch contains an unsupported file: " + entry);
                }
                if (!DatapackOwnership.sameDatapackVolume(root, rootStore, entry)) {
                    throw new IOException("Datapack scratch crosses a filesystem boundary: " + entry);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) throws IOException {
                return inspect(directory, attributes);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                return inspect(file, attributes);
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect datapack scratch entry: " + file, failure);
            }
        });
    }

    static boolean sameScratchVolume(Path first, Path second) throws IOException {
        return sameScratchVolume(first, Files.getFileStore(first), second, Files.getFileStore(second));
    }

    static boolean sameScratchVolume(
            Path first,
            FileStore firstStore,
            Path second,
            FileStore secondStore
    ) {
        if (Objects.equals(firstStore, secondStore)) {
            return true;
        }
        if (!isDefaultWindowsPath(first) || !isDefaultWindowsPath(second)) {
            return false;
        }
        Path firstAbsolute = first.toAbsolutePath().normalize();
        Path secondAbsolute = second.toAbsolutePath().normalize();
        if ((firstAbsolute.toString().length() > WINDOWS_LEGACY_PATH_LIMIT)
                == (secondAbsolute.toString().length() > WINDOWS_LEGACY_PATH_LIMIT)) {
            return false;
        }
        Path firstRoot = firstAbsolute.getRoot();
        Path secondRoot = secondAbsolute.getRoot();
        if (firstRoot == null || secondRoot == null) {
            return false;
        }
        return sameWindowsVolume(
                firstStore, firstRoot.toString(), secondStore, secondRoot.toString());
    }

    static boolean sameWindowsVolume(
            FileStore firstStore,
            String firstRoot,
            FileStore secondStore,
            String secondRoot
    ) {
        if (firstRoot == null || secondRoot == null || !firstRoot.equalsIgnoreCase(secondRoot)) {
            return false;
        }
        try {
            Object firstSerial = firstStore.getAttribute("volume:vsn");
            Object secondSerial = secondStore.getAttribute("volume:vsn");
            return firstSerial != null && firstSerial.equals(secondSerial);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    static boolean isSupportedScratchDirectory(BasicFileAttributes attributes) {
        return attributes != null
                && attributes.isDirectory()
                && !attributes.isSymbolicLink()
                && !attributes.isOther();
    }

    static boolean isDefaultWindowsPath(Path path) {
        return File.separatorChar == '\\'
                && path.getFileSystem().equals(FileSystems.getDefault());
    }

    static Ownership verifyManagedScratchDirectory(File directory, String id) throws IOException {
        DatapackOwnership.validateManagedDirectory(directory, id);
        Ownership ownership = DatapackOwnership.readOwnership(directory);
        if (!Objects.equals(ownership.contentHash, DatapackOwnership.directoryHash(directory))) {
            throw new IOException("Datapack staging backup is modified or corrupt: " + directory.getPath());
        }
        return ownership;
    }

    static boolean isHarmlessRecoveryArtifact(Path path) throws IOException {
        if (!DatapackOwnership.isFinderMetadata(path)) {
            return false;
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Suspicious datapack recovery artifact " + path);
        }
        return true;
    }

    static void recoverTransaction(
            File root,
            List<File> worldFolders,
            Manifest committedManifest,
            Path transactionDirectory,
            Path transactionRoot
    ) throws IOException {
        Path normalizedRoot = transactionRoot.toAbsolutePath().normalize();
        if (!Objects.equals(normalizedRoot.getParent(), transactionDirectory.toAbsolutePath().normalize())
                || Files.isSymbolicLink(normalizedRoot)
                || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid datapack transaction directory " + transactionRoot);
        }
        try {
            UUID.fromString(normalizedRoot.getFileName().toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid datapack transaction directory name " + transactionRoot, e);
        }
        CoordinatorJournal journal = DatapackTransactions.readCoordinatorJournal(normalizedRoot);
        if (journal == null) {
            DatapackTransactions.deleteCoordinatorTransaction(normalizedRoot);
            return;
        }
        DatapackTransactions.validateCoordinatorJournal(root, worldFolders, committedManifest, normalizedRoot, journal);
        boolean commit = DatapackCoordinatorResolution.coordinatorCommitDecision(committedManifest, journal);
        DatapackCoordinatorResolution.resolveCoordinatorDirectories(journal, commit);
        DatapackCoordinatorResolution.resolveCoordinatorEditables(journal, normalizedRoot, commit);
        DatapackTransactions.deleteCoordinatorTransaction(normalizedRoot);
    }

    enum StagingScratchKind {
        PENDING,
        BACKUP
    }

    record StagingScratch(StagingScratchKind kind, String id, Path path) {
    }
}
