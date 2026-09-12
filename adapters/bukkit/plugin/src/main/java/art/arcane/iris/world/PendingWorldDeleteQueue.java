/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.world;

import art.arcane.iris.Iris;
import art.arcane.iris.world.runtime.WorldDeletionQueue;
import art.arcane.iris.world.runtime.TransientWorldCleanupSupport;
import art.arcane.iris.platform.bukkit.plugin.VolmitPlugin;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PendingWorldDeleteQueue implements WorldDeletionQueue {
    private static final String PENDING_WORLD_DELETE_FILE = "pending-world-deletes.txt";
    private static final String EXACT_PREFIX = "exact:";
    private static final Pattern SAFE_LOGICAL_NAME = Pattern.compile("^[a-z0-9_-]+$");
    private static final Pattern QUARANTINE_NAME = Pattern.compile("^\\.iris-delete-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Set<String> VANILLA_DIMENSION_ALIASES = Set.of("overworld", "the_nether", "the_end");

    private final VolmitPlugin plugin;

    public PendingWorldDeleteQueue(VolmitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized int queueExactForStartupDeletion(Collection<String> worldNames) throws IOException {
        return queueWorldDeletionOnStartup(worldNames, true);
    }

    @Override
    public synchronized int queueFamilyForStartupDeletion(Collection<String> worldNames) throws IOException {
        return queueWorldDeletionOnStartup(worldNames, false);
    }

    private int queueWorldDeletionOnStartup(Collection<String> worldNames, boolean exact) throws IOException {
        if (worldNames == null || worldNames.isEmpty()) {
            return 0;
        }

        File levelRoot = IrisWorldStorage.levelRoot();
        ArrayList<String> normalizedNames = new ArrayList<>(worldNames.size());
        for (String worldName : worldNames) {
            String normalized = normalizeQueueEntry(worldName, levelRoot.getName());
            if (normalized == null) {
                throw new IllegalArgumentException("Unsafe Iris world deletion target: " + worldName);
            }
            normalizedNames.add(exact && !QUARANTINE_NAME.matcher(normalized).matches()
                    ? EXACT_PREFIX + normalized
                    : normalized);
        }

        File queueFile = plugin.getDataFile(PENDING_WORLD_DELETE_FILE);
        LinkedHashMap<String, String> queue = loadPendingWorldDeleteMap(queueFile, levelRoot.getName());
        int before = queue.size();
        for (String normalized : normalizedNames) {
            mergeQueueEntry(queue, normalized);
        }

        if (queue.size() != before) {
            writePendingWorldDeleteMap(queueFile, queue);
        }
        return queue.size() - before;
    }

    public synchronized void processPendingStartupWorldDeletes() {
        try {
            unregisterTransientStudioWorlds();

            File levelRoot = IrisWorldStorage.levelRoot();
            File queueFile = plugin.getDataFile(PENDING_WORLD_DELETE_FILE);
            LinkedHashMap<String, String> queue = loadPendingWorldDeleteMap(queueFile, levelRoot.getName());
            for (String discoveredName : discoverStartupWorldNames(levelRoot)) {
                mergeQueueEntry(queue, discoveredName);
            }
            if (queue.isEmpty()) {
                if (queueFile.exists()) {
                    writePendingWorldDeleteMap(queueFile, queue);
                }
                return;
            }

            LinkedHashMap<String, String> remaining = new LinkedHashMap<>();
            for (String worldName : queue.values()) {
                processEntry(levelRoot, worldName, remaining);
            }
            writePendingWorldDeleteMap(queueFile, remaining);
        } catch (Throwable failure) {
            Iris.reportError("Failed to process queued startup world deletions.", failure);
        }
    }

    @Nullable
    static String normalizeQueueEntry(String worldName, String levelName) {
        if (worldName == null) {
            return null;
        }

        String candidate = worldName.trim();
        if (candidate.isEmpty()) {
            return null;
        }
        if (QUARANTINE_NAME.matcher(candidate).matches()) {
            return candidate;
        }

        String logicalName = candidate.startsWith("iris:") ? candidate.substring("iris:".length()) : candidate;
        if (!SAFE_LOGICAL_NAME.matcher(logicalName).matches()) {
            return null;
        }
        String normalizedLevelName = Objects.requireNonNull(levelName, "levelName").trim().toLowerCase(Locale.ROOT);
        if (VANILLA_DIMENSION_ALIASES.contains(logicalName)
                || logicalName.equals(normalizedLevelName)
                || logicalName.equals(normalizedLevelName + "_nether")
                || logicalName.equals(normalizedLevelName + "_the_end")) {
            return null;
        }

        try {
            NamespacedKey key = IrisWorldStorage.managedKeyFromName(candidate, normalizedLevelName);
            return key.getKey().equals(logicalName) ? logicalName : null;
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    static LinkedHashMap<String, String> loadPendingWorldDeleteMap(File queueFile, String levelName) throws IOException {
        LinkedHashMap<String, String> queue = new LinkedHashMap<>();
        Path queuePath = Objects.requireNonNull(queueFile, "queueFile").toPath();
        if (!Files.exists(queuePath, LinkOption.NOFOLLOW_LINKS)) {
            return queue;
        }

        try (BufferedReader reader = Files.newBufferedReader(queuePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String normalized = normalizeStoredQueueEntry(line, levelName);
                if (normalized != null) {
                    mergeQueueEntry(queue, normalized);
                }
            }
        }
        return queue;
    }

    @Nullable
    private static String normalizeStoredQueueEntry(String storedEntry, String levelName) {
        if (storedEntry == null) {
            return null;
        }
        String candidate = storedEntry.trim();
        boolean exact = candidate.startsWith(EXACT_PREFIX);
        String rawName = exact ? candidate.substring(EXACT_PREFIX.length()) : candidate;
        String normalized = normalizeQueueEntry(rawName, levelName);
        if (normalized == null || QUARANTINE_NAME.matcher(normalized).matches()) {
            return normalized;
        }
        return exact ? EXACT_PREFIX + normalized : normalized;
    }

    private static void mergeQueueEntry(Map<String, String> queue, String storedEntry) {
        String logicalKey = storedEntry.startsWith(EXACT_PREFIX)
                ? storedEntry.substring(EXACT_PREFIX.length())
                : storedEntry;
        String key = logicalKey.toLowerCase(Locale.ROOT);
        String existing = queue.get(key);
        if (existing == null || (existing.startsWith(EXACT_PREFIX) && !storedEntry.startsWith(EXACT_PREFIX))) {
            queue.put(key, storedEntry);
        }
    }

    static void writePendingWorldDeleteMap(File queueFile, Map<String, String> queue) throws IOException {
        Path queuePath = Objects.requireNonNull(queueFile, "queueFile").toPath().toAbsolutePath().normalize();
        Path parent = queuePath.getParent();
        if (parent == null) {
            throw new IOException("Queue file has no parent directory: " + queuePath);
        }
        Files.createDirectories(parent);

        StringBuilder content = new StringBuilder();
        for (String worldName : Objects.requireNonNull(queue, "queue").values()) {
            content.append(worldName).append('\n');
        }

        Path temporary = parent.resolve(queuePath.getFileName() + ".tmp-" + UUID.randomUUID());
        IOException failure = null;
        try {
            byte[] bytes = content.toString().getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            replaceQueueFile(temporary, queuePath);
            forceDirectory(parent);
        } catch (IOException writeFailure) {
            failure = writeFailure;
            throw writeFailure;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    static LinkedHashSet<String> discoverStartupWorldNames(File levelRoot) throws IOException {
        LinkedHashSet<String> worldNames = new LinkedHashSet<>();
        Path root = Objects.requireNonNull(levelRoot, "levelRoot").toPath().toAbsolutePath().normalize();
        Path dimensions = root.resolve("dimensions");
        Path irisNamespace = dimensions.resolve("iris");
        if (!Files.exists(irisNamespace, LinkOption.NOFOLLOW_LINKS)) {
            return worldNames;
        }
        if (Files.isSymbolicLink(dimensions) || Files.isSymbolicLink(irisNamespace)) {
            throw new IOException("Iris dimension storage contains a symbolic link: " + irisNamespace);
        }
        if (!Files.isDirectory(irisNamespace, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Iris dimension storage is not a directory: " + irisNamespace);
        }

        try (DirectoryStream<Path> children = Files.newDirectoryStream(irisNamespace)) {
            for (Path child : children) {
                if (Files.isSymbolicLink(child) || !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                String name = child.getFileName().toString();
                if (QUARANTINE_NAME.matcher(name).matches()) {
                    worldNames.add(name);
                    continue;
                }

                String transientBaseName = TransientWorldCleanupSupport.transientStudioBaseWorldName(name);
                String normalized = normalizeQueueEntry(transientBaseName, root.getFileName().toString());
                if (normalized != null) {
                    worldNames.add(normalized);
                }
            }
        }
        return worldNames;
    }

    static List<Path> resolveQueueEntryPaths(File levelRoot, String worldName) throws IOException {
        QueueEntry entry = QueueEntry.parse(worldName, levelRoot.getName());
        List<DeleteTarget> targets = entry.targets(levelRoot);
        ArrayList<Path> paths = new ArrayList<>(targets.size());
        for (DeleteTarget target : targets) {
            paths.add(target.path());
        }
        return List.copyOf(paths);
    }

    private static void replaceQueueFile(Path temporary, Path queuePath) throws IOException {
        try {
            Files.move(temporary, queuePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, queuePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        // A directory can only be opened and fsynced on a POSIX filesystem; Windows rejects the
        // open outright. Matches DirectoryDurability and DatapackIngestService, which already
        // skip the barrier there.
        if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void unregisterTransientStudioWorlds() {
        try {
            int unregistered = IrisCreator.removeTransientStudioWorldsFromBukkitYml();
            if (unregistered > 0) {
                Iris.info("Unregistered " + unregistered + " transient studio world(s) from bukkit.yml on startup.");
            }
        } catch (Throwable failure) {
            Iris.reportError("Failed to unregister transient studio worlds from bukkit.yml on startup.", failure);
        }
    }

    private static void processEntry(
            File levelRoot,
            String worldName,
            LinkedHashMap<String, String> remaining
    ) {
        try {
            EntryDeletionResult result = attemptEntry(
                    levelRoot,
                    worldName,
                    PendingWorldDeleteQueue::isLoaded
            );
            if (result.loaded()) {
                Iris.warn("Skipping queued deletion for \"" + worldName + "\" because it is currently loaded.");
                remaining.put(worldName.toLowerCase(Locale.ROOT), worldName);
                return;
            }

            for (Path deleted : result.deleted()) {
                Iris.info("Deleted queued world folder \"" + deleted.getFileName() + "\".");
            }
            for (DeletionFailure deletionFailure : result.failures()) {
                Iris.reportError(
                        "Failed to delete queued world folder \"" + deletionFailure.path() + "\".",
                        deletionFailure.failure()
                );
            }
            if (!result.foundAny()) {
                Iris.info("Queued world deletion skipped for \"" + worldName + "\" (folder missing).");
                return;
            }
            if (result.retainQueueEntry()) {
                remaining.put(worldName.toLowerCase(Locale.ROOT), worldName);
            }
        } catch (Throwable failure) {
            remaining.put(worldName.toLowerCase(Locale.ROOT), worldName);
            Iris.reportError("Failed to safely process queued world deletion for \"" + worldName + "\".", failure);
        }
    }

    static EntryDeletionResult attemptEntry(
            File levelRoot,
            String worldName,
            LoadedTargetCheck loadedTargetCheck
    ) throws IOException {
        QueueEntry entry = QueueEntry.parse(worldName, levelRoot.getName());
        List<DeleteTarget> targets = entry.targets(levelRoot);
        if (targets.stream().anyMatch(target -> loadedTargetCheck.isLoaded(target.key(), target.path()))) {
            return new EntryDeletionResult(true, false, List.of(), List.of());
        }

        boolean foundAny = false;
        ArrayList<Path> deleted = new ArrayList<>(targets.size());
        ArrayList<DeletionFailure> failures = new ArrayList<>(targets.size());
        for (DeleteTarget target : targets) {
            Path worldFolder = target.path();
            if (!Files.exists(worldFolder, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(worldFolder) || !Files.isDirectory(worldFolder, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Queued world target is not a safe directory: " + worldFolder);
            }

            foundAny = true;
            try {
                SnapshotDirectoryTreeDeleter.delete(worldFolder);
                deleted.add(worldFolder);
            } catch (IOException failure) {
                failures.add(new DeletionFailure(worldFolder, failure));
            }
        }
        return new EntryDeletionResult(
                false,
                foundAny,
                List.copyOf(deleted),
                List.copyOf(failures)
        );
    }

    private static boolean isLoaded(@Nullable NamespacedKey key, Path path) {
        if (key != null && WorldIdentity.resolve(key).isPresent()) {
            return true;
        }

        Path targetPath = path.toAbsolutePath().normalize();
        for (World world : Bukkit.getWorlds()) {
            if (world.getWorldFolder().toPath().toAbsolutePath().normalize().equals(targetPath)) {
                return true;
            }
        }
        return false;
    }

    private enum QueueEntryType {
        EXACT,
        LOGICAL,
        QUARANTINE
    }

    private record QueueEntry(String storedName, QueueEntryType type) {
        private static QueueEntry parse(String worldName, String levelName) {
            String stored = normalizeStoredQueueEntry(worldName, levelName);
            if (stored == null) {
                throw new IllegalArgumentException("Unsafe queued Iris world deletion target: " + worldName);
            }
            boolean exact = stored.startsWith(EXACT_PREFIX);
            String normalized = exact ? stored.substring(EXACT_PREFIX.length()) : stored;
            QueueEntryType type = QUARANTINE_NAME.matcher(normalized).matches()
                    ? QueueEntryType.QUARANTINE
                    : exact ? QueueEntryType.EXACT : QueueEntryType.LOGICAL;
            return new QueueEntry(normalized, type);
        }

        private List<DeleteTarget> targets(File levelRoot) throws IOException {
            if (type == QueueEntryType.QUARANTINE) {
                return List.of(new DeleteTarget(null, requireSafeQuarantinePath(levelRoot, storedName)));
            }

            if (type == QueueEntryType.EXACT) {
                NamespacedKey key = IrisWorldStorage.managedKeyFromName(storedName, levelRoot.getName());
                Path path = currentStorageRoot(levelRoot, key);
                return List.of(new DeleteTarget(key, path));
            }

            ArrayList<DeleteTarget> targets = new ArrayList<>(3);
            for (String familyWorldName : TransientWorldCleanupSupport.worldFamilyNames(storedName)) {
                NamespacedKey key = IrisWorldStorage.managedKeyFromName(familyWorldName, levelRoot.getName());
                Path path = currentStorageRoot(levelRoot, key);
                targets.add(new DeleteTarget(key, path));
            }
            return targets;
        }

        private static Path currentStorageRoot(File levelRoot, NamespacedKey key) {
            File worldContainer = levelRoot.getAbsoluteFile().getParentFile();
            if (worldContainer == null) {
                throw new IllegalArgumentException("Selected level root has no world container: " + levelRoot);
            }
            String configuredWorldName = IrisWorldStorage.configuredWorldName(key, levelRoot.getName());
            Path directRoot = IrisWorldStorage.requireSafeManagedDimensionRoot(levelRoot, key)
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
            Path dimensionRoot = IrisWorldStorage.frozenDimensionRoot(
                    worldContainer,
                    levelRoot,
                    configuredWorldName,
                    key
            ).map(file -> file.toPath().toAbsolutePath().normalize()).orElse(directRoot);
            if (dimensionRoot.equals(directRoot)) {
                return directRoot;
            }
            Path configuredDimensionRoot = IrisWorldStorage.configuredDimensionRoot(
                    worldContainer,
                    levelRoot,
                    key
            ).toPath().toAbsolutePath().normalize();
            if (!dimensionRoot.equals(configuredDimensionRoot)) {
                throw new IllegalStateException("Iris world storage does not match the current platform layout.");
            }
            return IrisWorldStorage.configuredLevelRoot(worldContainer, levelRoot, key)
                    .toPath()
                    .toAbsolutePath()
                    .normalize();
        }
    }

    private static Path requireSafeQuarantinePath(File levelRoot, String quarantineName) throws IOException {
        if (!QUARANTINE_NAME.matcher(quarantineName).matches()) {
            throw new IOException("Invalid Iris quarantine directory name: " + quarantineName);
        }

        Path root = levelRoot.toPath().toAbsolutePath().normalize();
        Path dimensions = root.resolve("dimensions");
        Path irisNamespace = dimensions.resolve("iris");
        Path target = irisNamespace.resolve(quarantineName).normalize();
        if (!Objects.equals(target.getParent(), irisNamespace)) {
            throw new IOException("Iris quarantine target escapes its namespace: " + target);
        }
        for (Path path : List.of(dimensions, irisNamespace, target)) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("Iris quarantine storage contains a symbolic link: " + path);
            }
        }
        return target;
    }

    private record DeleteTarget(@Nullable NamespacedKey key, Path path) {
    }

    @FunctionalInterface
    interface LoadedTargetCheck {
        boolean isLoaded(@Nullable NamespacedKey key, Path path);
    }

    record EntryDeletionResult(
            boolean loaded,
            boolean foundAny,
            List<Path> deleted,
            List<DeletionFailure> failures
    ) {
        boolean retainQueueEntry() {
            return loaded || !failures.isEmpty();
        }
    }

    record DeletionFailure(Path path, IOException failure) {
    }
}
