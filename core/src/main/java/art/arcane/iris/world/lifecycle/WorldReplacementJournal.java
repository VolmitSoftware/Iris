package art.arcane.iris.world.lifecycle;

import art.arcane.iris.world.ExactWorldSlotPathPolicy;
import art.arcane.iris.world.IrisWorldStorage;
import art.arcane.iris.world.WorldSlotKey;
import art.arcane.iris.world.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;

import art.arcane.iris.world.storage.Durability;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class WorldReplacementJournal {
    public static final String DIRECTORY_NAME = "pending-world-replacements";

    private static final String JOURNAL_SUFFIX = ".properties";

    private WorldReplacementJournal() {
    }

    public static List<Transaction> load(Path dataDirectory, Path currentLevelRoot) throws IOException {
        Path directory = directory(dataDirectory, false);
        if (directory == null) {
            return List.of();
        }
        ArrayList<Transaction> transactions = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + JOURNAL_SUFFIX)) {
            for (Path file : files) {
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Replacement journal entry is unsafe: " + file);
                }
                try {
                    transactions.add(read(file));
                } catch (IOException failure) {
                    throw new IOException("Invalid replacement journal " + file + ": " + failure.getMessage(), failure);
                }
            }
        }
        transactions.sort(Comparator.comparing(transaction -> transaction.id().toString()));
        // Only transactions that target the current level root compete for a slot; a stale
        // journal recorded against another level root must not collide with a live one.
        Set<WorldSlotKey> worldKeys = new HashSet<>();
        for (Transaction transaction : transactions) {
            if (appliesTo(transaction, currentLevelRoot) && !worldKeys.add(transaction.worldKey())) {
                throw new IOException("Multiple replacement journals target " + transaction.worldKey() + ".");
            }
        }
        return List.copyOf(transactions);
    }

    /**
     * Whether this journal entry targets the current level root and logical world name. A
     * mismatch means the operator changed level-name or world container after the replacement
     * was staged; such entries are skipped at bootstrap instead of aborting the whole boot.
     */
    public static boolean appliesTo(Transaction transaction, Path currentLevelRoot) {
        Transaction requiredTransaction = Objects.requireNonNull(transaction, "transaction");
        ExactWorldSlotPathPolicy.Target target;
        try {
            target = ExactWorldSlotPathPolicy.resolve(currentLevelRoot, requiredTransaction.worldKey());
        } catch (RuntimeException failure) {
            return false;
        }
        if (!target.levelRoot().equals(requiredTransaction.levelRoot())) {
            return false;
        }
        String expectedWorldName;
        try {
            expectedWorldName = logicalWorldName(target.levelRoot(), requiredTransaction.worldKey());
        } catch (IllegalArgumentException failure) {
            return false;
        }
        return expectedWorldName.equals(requiredTransaction.worldName());
    }

    public static void write(Path dataDirectory, Transaction transaction) throws IOException {
        Transaction requiredTransaction = Objects.requireNonNull(transaction, "transaction");
        Path directory = Objects.requireNonNull(directory(dataDirectory, true));
        Path target = directory.resolve(requiredTransaction.id() + JOURNAL_SUFFIX);
        Properties properties = new Properties();
        properties.setProperty("id", requiredTransaction.id().toString());
        properties.setProperty("worldKey", requiredTransaction.worldKey().toString());
        properties.setProperty("worldName", requiredTransaction.worldName());
        properties.setProperty("levelRoot", requiredTransaction.levelRoot().toString());
        properties.setProperty("dimension", requiredTransaction.dimension());
        properties.setProperty("seed", Long.toString(requiredTransaction.seed()));
        properties.setProperty("packFingerprint", requiredTransaction.packFingerprint());
        properties.setProperty(
                "originalTargetPresent",
                Boolean.toString(requiredTransaction.originalTargetPresent())
        );
        properties.setProperty("phase", requiredTransaction.phase().name());
        writeSnapshot(properties, "original.", requiredTransaction.originalConfiguration());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        properties.store(output, null);
        writeAtomic(target, output.toByteArray());
    }

    public static void delete(Path dataDirectory, UUID id) throws IOException {
        Path directory = directory(dataDirectory, false);
        if (directory == null) {
            return;
        }
        forceDirectoryRequired(directory);
        Files.deleteIfExists(directory.resolve(Objects.requireNonNull(id, "id") + JOURNAL_SUFFIX));
        forceDirectoryAfterCommit(directory);
    }

    public static ExactWorldSlotPathPolicy.Target resolveTarget(Transaction transaction, Path currentLevelRoot)
            throws IOException {
        Transaction requiredTransaction = Objects.requireNonNull(transaction, "transaction");
        ExactWorldSlotPathPolicy.Target target = ExactWorldSlotPathPolicy.resolve(
                currentLevelRoot,
                requiredTransaction.worldKey()
        );
        if (!target.levelRoot().equals(requiredTransaction.levelRoot())) {
            throw new IOException("The configured level root changed after the world replacement was staged.");
        }
        String expectedWorldName;
        try {
            expectedWorldName = logicalWorldName(target.levelRoot(), requiredTransaction.worldKey());
        } catch (IllegalArgumentException failure) {
            throw new IOException("The replacement journal targets an ambiguous logical world name.", failure);
        }
        if (!expectedWorldName.equals(requiredTransaction.worldName())) {
            throw new IOException("The logical world name changed after the world replacement was staged.");
        }
        return target;
    }

    public static String logicalWorldName(Path levelRoot, WorldSlotKey worldKey) {
        Path requiredLevelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
        WorldSlotKey requiredWorldKey = Objects.requireNonNull(worldKey, "worldKey");
        Path fileName = requiredLevelRoot.getFileName();
        if (fileName == null || fileName.toString().isBlank()) {
            throw new IllegalArgumentException("Level root must have a logical world name.");
        }
        String levelName = fileName.toString();
        if ("iris".equals(requiredWorldKey.namespace())) {
            return IrisWorldStorage.configuredWorldName(requiredWorldKey, levelName);
        }
        if (WorldSlotKey.minecraft("overworld").equals(requiredWorldKey)) {
            return levelName;
        }
        if (WorldSlotKey.minecraft("the_nether").equals(requiredWorldKey)) {
            return levelName + "_nether";
        }
        if (WorldSlotKey.minecraft("the_end").equals(requiredWorldKey)) {
            return levelName + "_the_end";
        }
        throw new IllegalArgumentException("World key is not an exact replaceable world slot: " + requiredWorldKey);
    }

    private static Transaction read(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        UUID id;
        try {
            id = UUID.fromString(required(properties, "id"));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Replacement journal contains an invalid transaction id.", failure);
        }
        if (!file.getFileName().toString().equals(id + JOURNAL_SUFFIX)) {
            throw new IOException("Replacement journal filename does not match its transaction id.");
        }
        WorldSlotKey worldKey;
        try {
            worldKey = WorldSlotKey.parse(required(properties, "worldKey"));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Replacement journal contains an invalid world key.", failure);
        }
        String worldName = exact(properties, "worldName");
        Path recordedLevelRoot = recordedLevelRoot(properties);
        String dimension = required(properties, "dimension");
        if (!safeDimension(dimension)) {
            throw new IOException("Replacement journal contains an invalid dimension key.");
        }
        long seed = parseLong(properties, "seed");
        String packFingerprint = required(properties, "packFingerprint");
        if (!packFingerprint.matches("[0-9a-f]{64}")) {
            throw new IOException("Replacement journal contains an invalid pack fingerprint.");
        }
        WorldGeneratorSnapshot original = readSnapshot(properties, "original.");
        boolean originalTargetPresent = parseBoolean(properties, "originalTargetPresent");
        Phase phase;
        try {
            phase = Phase.valueOf(required(properties, "phase"));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Replacement journal contains an invalid phase.", failure);
        }
        Transaction transaction = new Transaction(
                id,
                worldKey,
                worldName,
                recordedLevelRoot,
                dimension,
                seed,
                packFingerprint,
                original,
                originalTargetPresent,
                phase
        );
        return transaction;
    }

    private static Path recordedLevelRoot(Properties properties) throws IOException {
        String value = exact(properties, "levelRoot");
        Path parsed;
        try {
            parsed = Path.of(value);
        } catch (RuntimeException failure) {
            throw new IOException("Replacement journal contains an invalid level root.", failure);
        }
        if (!parsed.isAbsolute() || !parsed.normalize().equals(parsed)) {
            throw new IOException("Replacement journal level root must be an absolute normalized path.");
        }
        return parsed;
    }

    private static Path directory(Path dataDirectory, boolean create) throws IOException {
        Path dataRoot = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        Path directory = dataRoot.resolve(DIRECTORY_NAME);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!create) {
                return null;
            }
            Files.createDirectories(directory);
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Replacement journal storage is unsafe: " + directory);
        }
        return directory;
    }

    private static void writeSnapshot(Properties properties, String prefix, WorldGeneratorSnapshot snapshot) {
        properties.setProperty(prefix + "worldsSectionPresent", Boolean.toString(snapshot.worldsSectionPresent()));
        properties.setProperty(prefix + "worldSectionPresent", Boolean.toString(snapshot.worldSectionPresent()));
        properties.setProperty(prefix + "generatorPresent", Boolean.toString(snapshot.generatorPresent()));
        if (snapshot.generatorPresent()) {
            properties.setProperty(prefix + "generator", snapshot.generator());
        }
        properties.setProperty(prefix + "seedPresent", Boolean.toString(snapshot.seedPresent()));
        if (snapshot.seedPresent()) {
            properties.setProperty(prefix + "seed", Long.toString(snapshot.seed()));
        }
    }

    private static WorldGeneratorSnapshot readSnapshot(Properties properties, String prefix) throws IOException {
        boolean worldsPresent = parseBoolean(properties, prefix + "worldsSectionPresent");
        boolean worldPresent = parseBoolean(properties, prefix + "worldSectionPresent");
        boolean generatorPresent = parseBoolean(properties, prefix + "generatorPresent");
        String generator = generatorPresent ? exact(properties, prefix + "generator") : null;
        boolean seedPresent = parseBoolean(properties, prefix + "seedPresent");
        Long seed = seedPresent ? parseLong(properties, prefix + "seed") : null;
        try {
            return new WorldGeneratorSnapshot(
                    worldsPresent,
                    worldPresent,
                    generatorPresent,
                    generator,
                    seedPresent,
                    seed
            );
        } catch (IllegalArgumentException failure) {
            throw new IOException("Replacement journal contains an invalid configuration snapshot.", failure);
        }
    }

    private static boolean safeDimension(String value) {
        if (value.isEmpty() || value.length() > 256 || value.startsWith(".") || value.contains("..")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        if (segments.length > 16) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !segment.matches("[A-Za-z0-9_-]+")) {
                return false;
            }
        }
        return true;
    }

    private static String exact(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IOException("Replacement journal is missing " + key + ".");
        }
        return value;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = exact(properties, key);
        if (value.isBlank()) {
            throw new IOException("Replacement journal is missing " + key + ".");
        }
        return value.trim();
    }

    private static boolean parseBoolean(Properties properties, String key) throws IOException {
        String value = required(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IOException("Replacement journal contains an invalid boolean for " + key + ".");
        }
        return Boolean.parseBoolean(value);
    }

    private static long parseLong(Properties properties, String key) throws IOException {
        try {
            return Long.parseLong(required(properties, key));
        } catch (NumberFormatException failure) {
            throw new IOException("Replacement journal contains an invalid integer for " + key + ".", failure);
        }
    }

    private static void writeAtomic(Path target, byte[] content) throws IOException {
        Path parent = Objects.requireNonNull(target.getParent(), "journal parent");
        Path temporary = parent.resolve("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                Durability.force(channel);
            }
            forceDirectoryRequired(parent);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException(
                        "Exact world replacement requires atomic journal publication on this filesystem.",
                        failure
                );
            }
            forceDirectoryAfterCommit(parent);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceDirectoryRequired(Path directory) throws IOException {
        DirectoryDurability.forceDirectoryRequired(directory);
    }

    private static void forceDirectoryAfterCommit(Path directory) {
        DirectoryDurability.forceDirectoryAfterCommit(directory, "A world-replacement journal change");
    }

    public record Transaction(
            UUID id,
            WorldSlotKey worldKey,
            String worldName,
            Path levelRoot,
            String dimension,
            long seed,
            String packFingerprint,
            WorldGeneratorSnapshot originalConfiguration,
            boolean originalTargetPresent,
            Phase phase
    ) {
        public Transaction {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(worldKey, "worldKey");
            Objects.requireNonNull(worldName, "worldName");
            levelRoot = Objects.requireNonNull(levelRoot, "levelRoot").toAbsolutePath().normalize();
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(packFingerprint, "packFingerprint");
            Objects.requireNonNull(originalConfiguration, "originalConfiguration");
            Objects.requireNonNull(phase, "phase");
        }

        public Transaction withPhase(Phase nextPhase) {
            return new Transaction(
                    id,
                    worldKey,
                    worldName,
                    levelRoot,
                    dimension,
                    seed,
                    packFingerprint,
                    originalConfiguration,
                    originalTargetPresent,
                    nextPhase
            );
        }
    }

    public enum Phase {
        PREPARED,
        ARMED,
        PUBLISHED,
        ROLLBACK_PENDING,
        ROLLBACK_CLEANUP,
        CLEANUP_PENDING
    }
}
