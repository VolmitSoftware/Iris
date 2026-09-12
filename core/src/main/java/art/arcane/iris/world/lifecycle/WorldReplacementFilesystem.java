package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.ExactWorldSlotPathPolicy;
import art.arcane.iris.world.SnapshotDirectoryTreeDeleter;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.pack.PackDirectoryResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class WorldReplacementFilesystem {
    private static final String CODE_WORKSPACE_SUFFIX = ".code-workspace";
    private static final String MACOS_FINDER_METADATA_FILE = ".DS_Store";
    private static final List<Path> PAPER_WORLD_METADATA = List.of(
            Path.of("data/paper/metadata.dat"),
            Path.of("data/paper/level_overrides.dat"),
            Path.of("data/minecraft/world_gen_settings.dat")
    );
    private static final Pattern STAGE_NAME = Pattern.compile(
            "^\\.iris-replace-[a-z0-9_-]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.stage$");
    private static final Pattern BACKUP_NAME = Pattern.compile(
            "^\\.iris-replace-[a-z0-9_-]+-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.backup$");

    private WorldReplacementFilesystem() {
    }

    public static ReplacementPaths paths(ExactWorldSlotPathPolicy.Target target, UUID id) {
        ExactWorldSlotPathPolicy.Target requiredTarget = Objects.requireNonNull(target, "target");
        UUID requiredId = Objects.requireNonNull(id, "id");
        String artifactBase = ".iris-replace-" + requiredTarget.worldKey().key() + "-" + requiredId;
        return new ReplacementPaths(
                requiredTarget.worldDirectory(),
                requiredTarget.namespaceRoot().resolve(artifactBase + ".stage"),
                requiredTarget.namespaceRoot().resolve(artifactBase + ".backup")
        );
    }

    public static void requireExistingTarget(ReplacementPaths paths) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        State state = inspect(requiredPaths);
        if (!state.targetPresent()) {
            throw new IOException("/iris replace requires an existing exact world slot; use /iris create for a new world.");
        }
        if (state.stagePresent() || state.backupPresent()) {
            throw new IOException("A replacement artifact already exists for this transaction.");
        }
        requireCurrentPaperWorld(requiredPaths.target());
    }

    public static void requireCurrentPaperWorld(Path retainedWorld) throws IOException {
        try {
            requireDirectory(retainedWorld, "retained world");
            requireDirectory(retainedWorld.resolve("data"), "retained world data");
            requireDirectory(retainedWorld.resolve("data/paper"), "retained Paper data");
            requireDirectory(retainedWorld.resolve("data/minecraft"), "retained Minecraft data");
            for (Path relative : PAPER_WORLD_METADATA) {
                BasicFileAttributes sourceAttributes = requireSafeEntry(retainedWorld.resolve(relative));
                if (!sourceAttributes.isRegularFile()) {
                    throw new IOException("Retained Paper world metadata is not a regular file: " + relative);
                }
            }
        } catch (NoSuchFileException e) {
            throw new IOException("World slot " + retainedWorld.getFileName()
                    + " is missing Paper world metadata (" + e.getFile()
                    + "); load the world once on this server before replacing it.", e);
        }
    }

    public static void publish(
            ReplacementPaths paths,
            boolean originalTargetPresent,
            String expectedPackFingerprint
    ) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        String expectedFingerprint = requireFingerprint(expectedPackFingerprint);
        State state = inspect(requiredPaths);
        if (state.stagePresent()) {
            requireSafeTree(requiredPaths.stage(), "replacement stage");
            requireFingerprint(activePackRoot(requiredPaths.stage()), expectedFingerprint);
            if (originalTargetPresent) {
                Path retainedWorld = state.targetPresent()
                        ? requiredPaths.target()
                        : requiredPaths.backup();
                preservePaperWorldMetadata(retainedWorld, requiredPaths.stage());
            }
            if (state.targetPresent()) {
                if (state.backupPresent()) {
                    throw new IOException("Replacement target, stage, and backup are all present.");
                }
                if (!originalTargetPresent) {
                    throw new IOException("Replacement target appeared after an absent target was staged.");
                }
                move(requiredPaths.target(), requiredPaths.backup());
                state = inspect(requiredPaths);
            }
            if (state.targetPresent()) {
                throw new IOException("Replacement target is still present after backup publication.");
            }
            if (originalTargetPresent != state.backupPresent()) {
                throw new IOException("Replacement backup state does not match the original target state.");
            }
            requireSafeTree(requiredPaths.stage(), "replacement stage");
            requireFingerprint(activePackRoot(requiredPaths.stage()), expectedFingerprint);
            move(requiredPaths.stage(), requiredPaths.target());
            state = inspect(requiredPaths);
        }

        if (!state.targetPresent() || state.stagePresent()) {
            throw new IOException("Replacement publication did not produce one exact target directory.");
        }
        if (originalTargetPresent != state.backupPresent()) {
            throw new IOException("Replacement publication lost or unexpectedly created its backup.");
        }
        requireSafeTree(requiredPaths.target(), "replacement target");
        requireFingerprint(activePackRoot(requiredPaths.target()), expectedFingerprint);
        if (originalTargetPresent) {
            preservePaperWorldMetadata(requiredPaths.backup(), requiredPaths.target());
            requireSafeTree(requiredPaths.target(), "replacement target");
            requireFingerprint(activePackRoot(requiredPaths.target()), expectedFingerprint);
        }
    }

    public static void rollback(ReplacementPaths paths, boolean originalTargetPresent) throws IOException {
        prepareRollback(paths, originalTargetPresent);
        discardStage(paths);
    }

    public static void prepareRollback(ReplacementPaths paths, boolean originalTargetPresent) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        State state = inspect(requiredPaths);
        if (originalTargetPresent) {
            if (state.backupPresent()) {
                if (state.targetPresent()) {
                    if (state.stagePresent()) {
                        throw new IOException("Rollback cannot quarantine two replacement directories.");
                    }
                    move(requiredPaths.target(), requiredPaths.stage());
                }
                move(requiredPaths.backup(), requiredPaths.target());
                state = inspect(requiredPaths);
            }
            if (!state.targetPresent() || !state.stagePresent() || state.backupPresent()) {
                throw new IOException("Rollback could not restore the original world target.");
            }
        } else {
            if (state.backupPresent()) {
                throw new IOException("An originally absent world acquired an unexpected backup.");
            }
            if (state.targetPresent()) {
                if (state.stagePresent()) {
                    throw new IOException("Rollback cannot quarantine two replacement directories.");
                }
                move(requiredPaths.target(), requiredPaths.stage());
                state = inspect(requiredPaths);
            }
            if (state.targetPresent()) {
                throw new IOException("Rollback could not remove the replacement target.");
            }
            if (!state.stagePresent()) {
                throw new IOException("Rollback lost the quarantined replacement target.");
            }
        }
    }

    public static void finishPreparedRollback(ReplacementPaths paths, boolean originalTargetPresent)
            throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        State state = inspect(requiredPaths);
        if (state.backupPresent()) {
            throw new IOException("Rollback cleanup cannot continue while a retained backup is still published.");
        }
        if (originalTargetPresent != state.targetPresent()) {
            throw new IOException("Rollback cleanup target state does not match the retained world state.");
        }
        discardStage(requiredPaths);
    }

    public static void discardStage(ReplacementPaths paths) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        State state = inspect(requiredPaths);
        if (state.backupPresent()) {
            throw new IOException("Cannot discard a replacement stage after a backup was published.");
        }
        if (state.stagePresent()) {
            SnapshotDirectoryTreeDeleter.delete(requiredPaths.stage());
        }
    }

    public static void cleanupBackup(ReplacementPaths paths) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        State state = inspect(requiredPaths);
        if (!state.targetPresent() || state.stagePresent()) {
            throw new IOException("Cannot clean a replacement backup before publication is complete.");
        }
        if (state.backupPresent()) {
            SnapshotDirectoryTreeDeleter.delete(requiredPaths.backup());
        }
    }

    public static void validateCommittedTarget(ReplacementPaths paths, String expectedPackFingerprint)
            throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        String expectedFingerprint = requireFingerprint(expectedPackFingerprint);
        State state = inspect(requiredPaths);
        if (!state.targetPresent() || state.stagePresent()) {
            throw new IOException("Committed replacement storage does not contain one exact target directory.");
        }
        requireSafeTree(requiredPaths.target(), "replacement target");
        requireFingerprint(activePackRoot(requiredPaths.target()), expectedFingerprint);
    }

    public static void validatePublishedTarget(
            ReplacementPaths paths,
            boolean originalTargetPresent,
            String expectedPackFingerprint
    ) throws IOException {
        ReplacementPaths requiredPaths = Objects.requireNonNull(paths, "paths");
        String expectedFingerprint = requireFingerprint(expectedPackFingerprint);
        State state = inspect(requiredPaths);
        if (!state.targetPresent() || state.stagePresent()
                || originalTargetPresent != state.backupPresent()) {
            throw new IOException("Published replacement storage does not match its retained-world contract.");
        }
        requireSafeTree(requiredPaths.target(), "replacement target");
        requireFingerprint(activePackRoot(requiredPaths.target()), expectedFingerprint);
    }

    public static String fingerprintPack(Path packRoot) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "packRoot").toAbsolutePath().normalize();
        requireDirectory(root, "pack root");
        MessageDigest digest = sha256();
        List<FingerprintEntry> entries;
        try (Stream<Path> stream = Files.walk(root)) {
            entries = stream
                    .filter(path -> !path.equals(root))
                    .map(path -> fingerprintEntry(root, path))
                    .sorted(Comparator.comparing(FingerprintEntry::relativeString))
                    .toList();
        }
        String separator = root.getFileSystem().getSeparator();
        byte[] buffer = new byte[8192];
        for (FingerprintEntry entry : entries) {
            BasicFileAttributes attributes = requireSafeEntry(entry.path());
            Path relative = entry.relative();
            if (isGeneratedPackMetadata(relative, attributes)) {
                continue;
            }
            update(digest, entry.relativeString().replace(separator, "/"));
            digest.update((byte) (attributes.isDirectory() ? 1 : 0));
            if (!attributes.isRegularFile()) {
                continue;
            }
            update(digest, Long.toString(attributes.size()));
            try (InputStream input = Files.newInputStream(entry.path())) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Path activePackRoot(Path worldRoot) throws IOException {
        return GenerationHistory.open(worldRoot).activePackRoot();
    }

    private static FingerprintEntry fingerprintEntry(Path root, Path path) {
        Path relative = root.relativize(path);
        return new FingerprintEntry(path, relative, relative.toString());
    }

    private static State inspect(ReplacementPaths paths) throws IOException {
        return new State(
                directoryPresent(paths.target(), "replacement target"),
                directoryPresent(paths.stage(), "replacement stage"),
                directoryPresent(paths.backup(), "replacement backup")
        );
    }

    private static boolean directoryPresent(Path path, String label) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        requireDirectory(path, label);
        return true;
    }

    private static void requireFingerprint(Path packRoot, String expected) throws IOException {
        String actual = fingerprintPack(packRoot);
        if (!actual.equals(expected)) {
            throw new IOException("Staged world pack fingerprint changed before publication.");
        }
    }

    private static String requireFingerprint(String value) {
        String fingerprint = Objects.requireNonNull(value, "expectedPackFingerprint");
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Pack fingerprint must be lowercase SHA-256.");
        }
        return fingerprint;
    }

    private static boolean isGeneratedPackMetadata(Path relative, BasicFileAttributes attributes) {
        String fileName = relative.getFileName().toString();
        if (PackDirectoryResolver.isHiddenName(relative.getName(0).toString())) {
            return true;
        }
        return attributes.isRegularFile()
                && (MACOS_FINDER_METADATA_FILE.equals(fileName) || fileName.endsWith(CODE_WORKSPACE_SUFFIX));
    }

    private static BasicFileAttributes requireSafeEntry(Path path) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (attributes.isSymbolicLink()) {
            throw new IOException("Replacement storage contains a symbolic link: " + path);
        }
        if (!attributes.isDirectory() && !attributes.isRegularFile()) {
            throw new IOException("Replacement storage contains an unsafe entry: " + path);
        }
        return attributes;
    }

    private static void requireDirectory(Path path, String label) throws IOException {
        BasicFileAttributes attributes = requireSafeEntry(path);
        if (!attributes.isDirectory()) {
            throw new IOException("The " + label + " is not a directory: " + path);
        }
    }

    private static void requireSafeTree(Path root, String label) throws IOException {
        requireDirectory(root, label);
        List<Path> entries;
        try (Stream<Path> stream = Files.walk(root)) {
            entries = stream.sorted().toList();
        }
        for (Path entry : entries) {
            requireSafeEntry(entry);
        }
    }

    private static void preservePaperWorldMetadata(Path retainedWorld, Path replacementWorld) throws IOException {
        requireCurrentPaperWorld(retainedWorld);
        requireDirectory(replacementWorld, "replacement world");
        ensureDirectory(replacementWorld.resolve("data"), "replacement world data");
        ensureDirectory(replacementWorld.resolve("data/paper"), "replacement Paper data");
        ensureDirectory(replacementWorld.resolve("data/minecraft"), "replacement Minecraft data");
        for (Path relative : PAPER_WORLD_METADATA) {
            Path destination = replacementWorld.resolve(relative);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes destinationAttributes = requireSafeEntry(destination);
                if (!destinationAttributes.isRegularFile()) {
                    throw new IOException("Replacement Paper world metadata is not a regular file: " + relative);
                }
                continue;
            }
            copyMetadataFile(retainedWorld.resolve(relative), destination);
        }
    }

    private static void ensureDirectory(Path directory, String label) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(directory, label);
            return;
        }
        Files.createDirectory(directory);
        forceDirectoryRequired(directory.getParent());
    }

    private static void copyMetadataFile(Path source, Path destination) throws IOException {
        Path temporary = destination.resolveSibling("." + destination.getFileName() + ".iris-replace.tmp");
        if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
            BasicFileAttributes attributes = requireSafeEntry(temporary);
            if (!attributes.isRegularFile()) {
                throw new IOException("Replacement metadata staging path is unsafe: " + temporary);
            }
            Files.delete(temporary);
        }
        try {
            Files.copy(source, temporary, StandardCopyOption.COPY_ATTRIBUTES);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "Exact world replacement requires atomic Paper metadata publication on this filesystem.",
                        exception
                );
            }
            forceDirectoryRequired(destination.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        forceDirectoryRequired(source.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException(
                    "Exact world replacement requires atomic directory moves on this filesystem.",
                    exception
            );
        }
        forceDirectoryAfterCommit(source.getParent());
    }

    private static void forceDirectoryRequired(Path directory) throws IOException {
        DirectoryDurability.forceDirectoryRequired(directory);
    }

    private static void forceDirectoryAfterCommit(Path directory) {
        DirectoryDurability.forceDirectoryAfterCommit(directory, "A world-replacement move");
    }

    public record ReplacementPaths(Path target, Path stage, Path backup) {
        public ReplacementPaths {
            target = normalize(target, "target");
            stage = normalize(stage, "stage");
            backup = normalize(backup, "backup");
            Path parent = target.getParent();
            if (parent == null || !parent.equals(stage.getParent()) || !parent.equals(backup.getParent())) {
                throw new IllegalArgumentException("Replacement paths must have one exact parent.");
            }
            if (target.equals(stage) || target.equals(backup) || stage.equals(backup)) {
                throw new IllegalArgumentException("Replacement paths must be distinct.");
            }
            if (!STAGE_NAME.matcher(stage.getFileName().toString()).matches()
                    || !BACKUP_NAME.matcher(backup.getFileName().toString()).matches()) {
                throw new IllegalArgumentException("Replacement artifact names are invalid.");
            }
            String stageStem = stage.getFileName().toString().replaceFirst("\\.stage$", "");
            String backupStem = backup.getFileName().toString().replaceFirst("\\.backup$", "");
            if (!stageStem.equals(backupStem)) {
                throw new IllegalArgumentException("Replacement stage and backup do not belong to one transaction.");
            }
        }

        private static Path normalize(Path path, String label) {
            Path required = Objects.requireNonNull(path, label).toAbsolutePath();
            for (Path component : required) {
                if ("..".equals(component.toString())) {
                    throw new IllegalArgumentException("Replacement " + label + " contains path traversal.");
                }
            }
            return required.normalize();
        }
    }

    private record State(boolean targetPresent, boolean stagePresent, boolean backupPresent) {
    }

    private record FingerprintEntry(Path path, Path relative, String relativeString) {
    }
}
