package art.arcane.iris.pack;

import art.arcane.iris.spi.IrisLogging;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class PackResourceCleanup {
    private static final String TRASH_ROOT = ".iris-trash";
    private static final List<String> MANAGED_RESOURCE_FOLDERS = List.of(
            "biomes",
            "regions",
            "entities",
            "spawners",
            "loot",
            "generators",
            "expressions",
            "markers",
            "blocks",
            "mods"
    );
    private static final List<String> CORPUS_EXCLUSIONS = List.of(
            TRASH_ROOT,
            "datapack-imports",
            "externaldatapacks",
            "internaldatapacks",
            "datapacks",
            "cache",
            "objects",
            ".iris"
    );
    private static final DateTimeFormatter TRASH_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Map<Path, Object> PACK_LOCKS = new ConcurrentHashMap<>();

    private PackResourceCleanup() {
    }

    public static Preview preview(File packFolder) {
        Path lockPath = lockPath(packFolder);
        if (lockPath == null) {
            return new Preview(List.of(), "Pack folder is required.");
        }
        synchronized (packLock(lockPath)) {
            try {
                Path packRoot = requirePackRoot(packFolder);
                CleanupScan scan = scanCleanup(packRoot);
                return new Preview(scan.paths(), null);
            } catch (IOException | RuntimeException e) {
                return new Preview(List.of(), errorMessage("Unable to scan pack resources", e));
            }
        }
    }

    public static ApplyResult apply(File packFolder) {
        Path lockPath = lockPath(packFolder);
        if (lockPath == null) {
            return new ApplyResult(null, List.of(), "Pack folder is required.");
        }
        synchronized (packLock(lockPath)) {
            try {
                Path packRoot = requirePackRoot(packFolder);
                CleanupScan scan = scanCleanup(packRoot);
                if (scan.paths().isEmpty()) {
                    return new ApplyResult(null, List.of(), null);
                }
                return quarantine(packRoot, scan.paths());
            } catch (IOException | RuntimeException e) {
                return new ApplyResult(null, List.of(), errorMessage("Unable to quarantine pack resources", e));
            }
        }
    }

    public static RestorePreview previewRestore(File packFolder) {
        Path lockPath = lockPath(packFolder);
        if (lockPath == null) {
            return new RestorePreview(null, List.of(), List.of(), "Pack folder is required.");
        }
        synchronized (packLock(lockPath)) {
            try {
                Path packRoot = requirePackRoot(packFolder);
                RestoreScan scan = scanRestore(packRoot);
                return new RestorePreview(scan.dumpPath(), scan.paths(), scan.conflicts(), null);
            } catch (IOException | RuntimeException e) {
                return new RestorePreview(null, List.of(), List.of(), errorMessage("Unable to scan quarantined resources", e));
            }
        }
    }

    public static RestoreResult restoreLatest(File packFolder) {
        Path lockPath = lockPath(packFolder);
        if (lockPath == null) {
            return new RestoreResult(null, List.of(), List.of(), "Pack folder is required.");
        }
        synchronized (packLock(lockPath)) {
            try {
                Path packRoot = requirePackRoot(packFolder);
                RestoreScan scan = scanRestore(packRoot);
                if (scan.dump() == null) {
                    return new RestoreResult(null, List.of(), List.of(), null);
                }
                if (!scan.conflicts().isEmpty()) {
                    return new RestoreResult(scan.dumpPath(), List.of(), scan.conflicts(), null);
                }
                return restore(packRoot, scan);
            } catch (IOException | RuntimeException e) {
                return new RestoreResult(null, List.of(), List.of(), errorMessage("Unable to restore quarantined resources", e));
            }
        }
    }

    private static ApplyResult quarantine(Path packRoot, List<String> paths) throws IOException {
        Path dump = createUniqueDump(packRoot);
        List<Transfer> moved = new ArrayList<>(paths.size());
        try {
            for (String path : paths) {
                Path source = resolveContained(packRoot, path);
                requireRegularFile(source, "Cleanup candidate");
                Path destination = resolveContained(dump, path);
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    throw new FileAlreadyExistsException(destination.toString());
                }
                Files.createDirectories(destination.getParent());
                Files.move(source, destination);
                moved.add(new Transfer(path, source, destination));
            }
            return new ApplyResult(relativePath(packRoot, dump), paths, null);
        } catch (IOException | RuntimeException e) {
            List<String> remaining = rollbackMoves(moved);
            deleteEmptyDirectories(dump);
            deleteIfEmpty(dump.getParent());
            String rollbackState = remaining.isEmpty()
                    ? "Quarantine failed and was rolled back"
                    : "Quarantine failed and rollback left " + remaining.size() + " file(s) quarantined";
            IrisLogging.reportError(rollbackState + " for pack " + packRoot.getFileName(), e);
            return new ApplyResult(relativePath(packRoot, dump), remaining, errorMessage(rollbackState, e));
        }
    }

    private static RestoreResult restore(Path packRoot, RestoreScan scan) throws IOException {
        List<Path> copiedDestinations = new ArrayList<>(scan.transfers().size());
        List<Path> createdDirectories = new ArrayList<>();
        try {
            for (Transfer transfer : scan.transfers()) {
                createDirectories(packRoot, transfer.destination().getParent(), createdDirectories);
                Files.copy(transfer.source(), transfer.destination(), StandardCopyOption.COPY_ATTRIBUTES);
                copiedDestinations.add(transfer.destination());
            }
        } catch (IOException | RuntimeException e) {
            List<String> rollbackRemainders = rollbackCopies(packRoot, copiedDestinations, createdDirectories);
            String rollbackState = rollbackRemainders.isEmpty()
                    ? "Restore copy failed and was rolled back"
                    : "Restore copy failed and rollback left " + rollbackRemainders.size() + " destination(s)";
            IrisLogging.reportError(rollbackState + " for pack " + packRoot.getFileName(), e);
            return new RestoreResult(scan.dumpPath(), rollbackRemainders, List.of(), errorMessage(rollbackState, e));
        }

        List<Transfer> removedSources = new ArrayList<>(scan.transfers().size());
        for (Transfer transfer : scan.transfers()) {
            try {
                Files.delete(transfer.source());
                removedSources.add(transfer);
            } catch (IOException | RuntimeException e) {
                return rollbackSourceRemoval(packRoot, scan, copiedDestinations, createdDirectories, removedSources, e);
            }
        }
        deleteEmptyDirectories(scan.dump());
        deleteIfEmpty(scan.dump().getParent());
        return new RestoreResult(scan.dumpPath(), scan.paths(), List.of(), null);
    }

    private static CleanupScan scanCleanup(Path packRoot) throws IOException {
        List<Path> corpusFiles = listTree(packRoot);
        StringBuilder corpus = new StringBuilder(1 << 16);
        for (Path path : corpusFiles) {
            if (!isScannableJson(packRoot, path)) {
                continue;
            }
            requireRegularFile(path, "Corpus file");
            corpus.append(Files.readString(path, StandardCharsets.UTF_8)).append('\n');
        }
        if (corpus.isEmpty()) {
            return new CleanupScan(List.of());
        }
        String corpusText = corpus.toString();

        List<String> candidates = new ArrayList<>();
        for (String folderName : MANAGED_RESOURCE_FOLDERS) {
            Path resourceFolder = resolveContained(packRoot, folderName);
            if (!Files.exists(resourceFolder, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireDirectory(resourceFolder, "Managed resource folder");
            for (Path resource : listTree(resourceFolder)) {
                if (!isJsonFile(resource)) {
                    continue;
                }
                requireRegularFile(resource, "Managed resource");
                String key = stripJsonExtension(relativePath(resourceFolder, resource));
                if (!key.isBlank() && !isReferenced(corpusText, key)) {
                    candidates.add(relativePath(packRoot, resource));
                }
            }
        }
        candidates.sort(String::compareTo);
        return new CleanupScan(List.copyOf(candidates));
    }

    private static RestoreScan scanRestore(Path packRoot) throws IOException {
        Path trashRoot = resolveContained(packRoot, TRASH_ROOT);
        if (!Files.exists(trashRoot, LinkOption.NOFOLLOW_LINKS)) {
            return RestoreScan.empty();
        }
        requireDirectory(trashRoot, "Quarantine root");
        List<Path> dumps;
        try (Stream<Path> stream = Files.list(trashRoot)) {
            dumps = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        List<Path> directories = new ArrayList<>();
        for (Path dump : dumps) {
            if (Files.isSymbolicLink(dump)) {
                throw new IOException("Quarantine entry is a symbolic link: " + dump);
            }
            if (Files.isDirectory(dump, LinkOption.NOFOLLOW_LINKS)) {
                directories.add(dump);
            }
        }
        if (directories.isEmpty()) {
            return RestoreScan.empty();
        }

        Path latest = directories.get(directories.size() - 1);
        String dumpPath = relativePath(packRoot, latest);
        List<Transfer> transfers = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (Path source : listTree(latest)) {
            if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            requireRegularFile(source, "Quarantined resource");
            String relative = relativePath(latest, source);
            Path destination = resolveContained(packRoot, relative);
            if (destination.startsWith(trashRoot)) {
                throw new IOException("Quarantined resource resolves inside the quarantine root: " + relative);
            }
            if (hasDestinationConflict(packRoot, destination)) {
                conflicts.add(relative);
            }
            transfers.add(new Transfer(relative, source, destination));
        }
        transfers.sort(Comparator.comparing(Transfer::path));
        conflicts.sort(String::compareTo);
        List<String> paths = transfers.stream().map(Transfer::path).toList();
        return new RestoreScan(latest, dumpPath, List.copyOf(transfers), List.copyOf(paths), List.copyOf(conflicts));
    }

    private static List<Path> listTree(Path root) throws IOException {
        requireDirectory(root, "Scan root");
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.comparing(path -> relativePath(root, path))).toList();
            for (Path path : paths) {
                if (!path.equals(root) && Files.isSymbolicLink(path)) {
                    throw new IOException("Symbolic links are not supported during pack cleanup: " + path);
                }
            }
            return paths;
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static Path createUniqueDump(Path packRoot) throws IOException {
        Path trashRoot = resolveContained(packRoot, TRASH_ROOT);
        if (Files.exists(trashRoot, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(trashRoot, "Quarantine root");
        } else {
            Files.createDirectory(trashRoot);
        }
        String baseName = LocalDateTime.now().format(TRASH_STAMP);
        for (int attempt = 0; attempt < 10_000; attempt++) {
            String name = attempt == 0 ? baseName : baseName + '-' + String.format(Locale.ROOT, "%04d", attempt);
            Path dump = resolveContained(trashRoot, name);
            try {
                return Files.createDirectory(dump);
            } catch (FileAlreadyExistsException ignored) {
            }
        }
        throw new IOException("Unable to allocate a unique quarantine directory.");
    }

    private static List<String> rollbackMoves(List<Transfer> moved) {
        for (int i = moved.size() - 1; i >= 0; i--) {
            Transfer transfer = moved.get(i);
            try {
                if (!Files.exists(transfer.source(), LinkOption.NOFOLLOW_LINKS)
                        && Files.exists(transfer.destination(), LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(transfer.destination(), transfer.source());
                }
            } catch (IOException | RuntimeException e) {
                IrisLogging.reportError("Failed to roll back quarantined resource '" + transfer.path() + "'", e);
            }
        }
        List<String> remaining = new ArrayList<>();
        for (Transfer transfer : moved) {
            if (Files.exists(transfer.destination(), LinkOption.NOFOLLOW_LINKS)) {
                remaining.add(transfer.path());
            }
        }
        remaining.sort(String::compareTo);
        return List.copyOf(remaining);
    }

    private static RestoreResult rollbackSourceRemoval(Path packRoot,
                                                       RestoreScan scan,
                                                       List<Path> copiedDestinations,
                                                       List<Path> createdDirectories,
                                                       List<Transfer> removedSources,
                                                       Throwable failure) {
        List<String> sourceRollbackFailures = new ArrayList<>();
        for (int i = removedSources.size() - 1; i >= 0; i--) {
            Transfer transfer = removedSources.get(i);
            try {
                Files.copy(transfer.destination(), transfer.source(), StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException | RuntimeException e) {
                sourceRollbackFailures.add(transfer.path());
                IrisLogging.reportError("Failed to restore quarantined source '" + transfer.path() + "' during rollback", e);
            }
        }
        List<Path> removableDestinations = new ArrayList<>();
        for (Path destination : copiedDestinations) {
            String path = relativePath(packRoot, destination);
            Path source = scan.dump().resolve(path).normalize();
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                removableDestinations.add(destination);
            }
        }
        List<String> destinationRemainders = rollbackCopies(packRoot, removableDestinations, createdDirectories);
        List<String> remainders = new ArrayList<>(sourceRollbackFailures.size() + destinationRemainders.size());
        remainders.addAll(sourceRollbackFailures);
        for (String destinationRemainder : destinationRemainders) {
            if (!remainders.contains(destinationRemainder)) {
                remainders.add(destinationRemainder);
            }
        }
        remainders.sort(String::compareTo);
        String rollbackState = remainders.isEmpty()
                ? "Restore source removal failed and was rolled back"
                : "Restore source removal failed and rollback left " + remainders.size() + " destination(s)";
        IrisLogging.reportError(rollbackState + " for pack " + packRoot.getFileName(), failure);
        return new RestoreResult(scan.dumpPath(), remainders, List.of(), errorMessage(rollbackState, failure));
    }

    private static List<String> rollbackCopies(Path packRoot,
                                               List<Path> destinations,
                                               List<Path> createdDirectories) {
        for (int i = destinations.size() - 1; i >= 0; i--) {
            try {
                Files.deleteIfExists(destinations.get(i));
            } catch (IOException | RuntimeException e) {
                IrisLogging.reportError("Failed to roll back restored resource '" + relativePath(packRoot, destinations.get(i)) + "'", e);
            }
        }
        for (int i = createdDirectories.size() - 1; i >= 0; i--) {
            deleteIfEmpty(createdDirectories.get(i));
        }
        List<String> remaining = new ArrayList<>();
        for (Path destination : destinations) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                remaining.add(relativePath(packRoot, destination));
            }
        }
        remaining.sort(String::compareTo);
        return List.copyOf(remaining);
    }

    private static void createDirectories(Path packRoot, Path target, List<Path> createdDirectories) throws IOException {
        Path contained = requireContained(packRoot, target);
        List<Path> missing = new ArrayList<>();
        Path current = contained;
        while (!current.equals(packRoot) && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missing.add(current);
            current = current.getParent();
        }
        if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(current)) {
            throw new IOException("Restore parent is not a safe directory: " + current);
        }
        for (int i = missing.size() - 1; i >= 0; i--) {
            Path directory = missing.get(i);
            Files.createDirectory(directory);
            createdDirectories.add(directory);
        }
    }

    private static boolean hasDestinationConflict(Path packRoot, Path destination) throws IOException {
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        Path parent = destination.getParent();
        while (parent != null && !parent.equals(packRoot)) {
            if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))) {
                return true;
            }
            parent = parent.getParent();
        }
        return parent == null;
    }

    private static void deleteEmptyDirectories(Path root) {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> directories = stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path directory : directories) {
                deleteIfEmpty(directory);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static void deleteIfEmpty(Path directory) {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static boolean isScannableJson(Path packRoot, Path path) {
        if (!isJsonFile(path)) {
            return false;
        }
        Path relative = packRoot.relativize(path);
        for (Path segment : relative) {
            if (CORPUS_EXCLUSIONS.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJsonFile(Path path) {
        return path.getFileName() != null && path.getFileName().toString().endsWith(".json")
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isReferenced(String corpus, String key) {
        if (corpus.contains("\"" + key + "\"")) {
            return true;
        }
        int slash = key.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String tail = key.substring(slash + 1);
        return !tail.isBlank() && corpus.contains("\"" + tail + "\"");
    }

    private static String stripJsonExtension(String path) {
        return path.substring(0, path.length() - ".json".length());
    }

    private static Path requirePackRoot(File packFolder) throws IOException {
        if (packFolder == null) {
            throw new IOException("Pack folder is required.");
        }
        Path root = packFolder.toPath().toAbsolutePath().normalize();
        requireDirectory(root, "Pack folder");
        return root;
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a safe directory: " + path);
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is not a safe regular file: " + path);
        }
    }

    private static Path resolveContained(Path root, String relative) throws IOException {
        return requireContained(root, root.resolve(relative).normalize());
    }

    private static Path requireContained(Path root, Path path) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes pack boundary: " + path);
        }
        return normalizedPath;
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    private static Path lockPath(File packFolder) {
        return packFolder == null ? null : packFolder.toPath().toAbsolutePath().normalize();
    }

    private static Object packLock(Path path) {
        return PACK_LOCKS.computeIfAbsent(path, ignored -> new Object());
    }

    private static String errorMessage(String prefix, Throwable error) {
        String message = error.getMessage();
        return prefix + (message == null || message.isBlank() ? "." : ": " + message);
    }

    public record Preview(List<String> candidatePaths, String error) {
        public Preview {
            candidatePaths = candidatePaths == null ? List.of() : List.copyOf(candidatePaths);
        }

        public boolean success() {
            return error == null;
        }

        public boolean hasCandidates() {
            return !candidatePaths.isEmpty();
        }
    }

    public record ApplyResult(String quarantinePath, List<String> quarantinedPaths, String error) {
        public ApplyResult {
            quarantinedPaths = quarantinedPaths == null ? List.of() : List.copyOf(quarantinedPaths);
        }

        public boolean success() {
            return error == null;
        }

        public boolean changed() {
            return !quarantinedPaths.isEmpty();
        }
    }

    public record RestorePreview(String dumpPath, List<String> filePaths, List<String> conflicts, String error) {
        public RestorePreview {
            filePaths = filePaths == null ? List.of() : List.copyOf(filePaths);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }

        public boolean success() {
            return error == null;
        }

        public boolean hasFiles() {
            return !filePaths.isEmpty();
        }

        public boolean canRestore() {
            return success() && hasFiles() && conflicts.isEmpty();
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    public record RestoreResult(String dumpPath, List<String> restoredPaths, List<String> conflicts, String error) {
        public RestoreResult {
            restoredPaths = restoredPaths == null ? List.of() : List.copyOf(restoredPaths);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }

        public boolean success() {
            return error == null && conflicts.isEmpty();
        }

        public boolean changed() {
            return !restoredPaths.isEmpty();
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }
    }

    private record CleanupScan(List<String> paths) {
    }

    private record RestoreScan(Path dump, String dumpPath, List<Transfer> transfers, List<String> paths,
                               List<String> conflicts) {
        private static RestoreScan empty() {
            return new RestoreScan(null, null, List.of(), List.of(), List.of());
        }
    }

    private record Transfer(String path, Path source, Path destination) {
    }
}
