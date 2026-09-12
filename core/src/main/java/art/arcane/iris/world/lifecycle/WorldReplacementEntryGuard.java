package art.arcane.iris.world.lifecycle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WorldReplacementEntryGuard {
    public static final String MARKER_NAME = "replacement-entry.properties";

    private static final Pattern PLAYER_DATA_NAME = Pattern.compile(
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\.dat$");

    private WorldReplacementEntryGuard() {
    }

    public static Entry stage(Path levelRoot, Path stagedWorld, UUID transactionId) throws IOException {
        Path requiredLevelRoot = normalize(levelRoot, "levelRoot");
        Path requiredStagedWorld = normalize(stagedWorld, "stagedWorld");
        Entry entry = new Entry(
                Objects.requireNonNull(transactionId, "transactionId"),
                discoverPlayers(requiredLevelRoot)
        );
        write(requiredStagedWorld, entry);
        return entry;
    }

    public static Entry refreshPlayers(Path levelRoot, Path worldDirectory, UUID transactionId) throws IOException {
        Path requiredLevelRoot = normalize(levelRoot, "levelRoot");
        Path requiredWorldDirectory = normalize(worldDirectory, "worldDirectory");
        UUID requiredTransactionId = Objects.requireNonNull(transactionId, "transactionId");
        Optional<Entry> loaded = load(requiredWorldDirectory);
        if (loaded.isEmpty()) {
            throw new IOException("The staged Overworld replacement is missing its entry marker.");
        }
        Entry current = loaded.get();
        if (!current.transactionId().equals(requiredTransactionId)) {
            throw new IOException("Replacement entry marker belongs to another transaction.");
        }
        HashSet<UUID> pendingPlayers = new HashSet<>(current.pendingPlayers());
        pendingPlayers.addAll(discoverPlayers(requiredLevelRoot));
        Entry refreshed = new Entry(requiredTransactionId, pendingPlayers);
        write(requiredWorldDirectory, refreshed);
        return refreshed;
    }

    public static Optional<Entry> load(Path worldDirectory) throws IOException {
        Path marker = marker(worldDirectory);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Replacement entry marker is unsafe: " + marker);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(marker)) {
            properties.load(input);
        }
        UUID transactionId;
        try {
            transactionId = UUID.fromString(required(properties, "transaction"));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Replacement entry marker contains an invalid transaction id.", failure);
        }
        HashSet<UUID> pendingPlayers = new HashSet<>();
        String encodedPlayers = properties.getProperty("pendingPlayers", "").trim();
        if (!encodedPlayers.isEmpty()) {
            for (String encodedPlayer : encodedPlayers.split(",", -1)) {
                try {
                    if (!pendingPlayers.add(UUID.fromString(encodedPlayer))) {
                        throw new IOException("Replacement entry marker contains a duplicate player id.");
                    }
                } catch (IllegalArgumentException failure) {
                    throw new IOException("Replacement entry marker contains an invalid player id.", failure);
                }
            }
        }
        return Optional.of(new Entry(transactionId, pendingPlayers));
    }

    public static Optional<Entry> completePlayer(
            Path worldDirectory,
            UUID transactionId,
            UUID playerId
    ) throws IOException {
        Path requiredWorldDirectory = normalize(worldDirectory, "worldDirectory");
        UUID requiredTransactionId = Objects.requireNonNull(transactionId, "transactionId");
        UUID requiredPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<Entry> loaded = load(requiredWorldDirectory);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        Entry current = loaded.get();
        if (!current.transactionId().equals(requiredTransactionId)) {
            throw new IOException("Replacement entry marker belongs to another transaction.");
        }
        HashSet<UUID> remaining = new HashSet<>(current.pendingPlayers());
        remaining.remove(requiredPlayerId);
        Entry updated = new Entry(requiredTransactionId, remaining);
        write(requiredWorldDirectory, updated);
        return Optional.of(updated);
    }

    public static boolean retireIfEmpty(Path worldDirectory, UUID transactionId) throws IOException {
        Path requiredWorldDirectory = normalize(worldDirectory, "worldDirectory");
        Optional<Entry> loaded = load(requiredWorldDirectory);
        if (loaded.isEmpty()) {
            return true;
        }
        Entry current = loaded.get();
        if (!current.transactionId().equals(Objects.requireNonNull(transactionId, "transactionId"))) {
            throw new IOException("Replacement entry marker belongs to another transaction.");
        }
        if (!current.pendingPlayers().isEmpty()) {
            return false;
        }
        Path marker = marker(requiredWorldDirectory);
        Files.delete(marker);
        DirectoryDurability.forceDirectoryAfterCommit(marker.getParent(), "A replacement entry marker retirement");
        return true;
    }

    private static Set<UUID> discoverPlayers(Path levelRoot) throws IOException {
        Path playerData = levelRoot.resolve("players/data");
        if (!Files.exists(playerData, LinkOption.NOFOLLOW_LINKS)) {
            return Set.of();
        }
        if (Files.isSymbolicLink(playerData) || !Files.isDirectory(playerData, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Player data storage is unsafe: " + playerData);
        }
        HashSet<UUID> players = new HashSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(playerData)) {
            for (Path file : files) {
                Matcher matcher = PLAYER_DATA_NAME.matcher(file.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Player data entry is unsafe: " + file);
                }
                players.add(UUID.fromString(matcher.group(1)));
            }
        }
        return Set.copyOf(players);
    }

    private static void write(Path worldDirectory, Entry entry) throws IOException {
        Path marker = marker(worldDirectory);
        Path parent = marker.getParent();
        if (Files.isSymbolicLink(parent)) {
            throw new IOException("Replacement entry storage is unsafe: " + parent);
        }
        Files.createDirectories(parent);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Replacement entry storage is not a directory: " + parent);
        }
        Properties properties = new Properties();
        properties.setProperty("transaction", entry.transactionId().toString());
        ArrayList<UUID> orderedPlayers = new ArrayList<>(entry.pendingPlayers());
        orderedPlayers.sort(Comparator.comparing(UUID::toString));
        properties.setProperty(
                "pendingPlayers",
                String.join(",", orderedPlayers.stream().map(UUID::toString).toList())
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        properties.store(output, null);
        Path staged = Files.createTempFile(parent, ".replacement-entry-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    staged,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(output.toByteArray());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(staged, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException("Replacement entry marker requires atomic publication.", failure);
            }
            DirectoryDurability.forceDirectoryAfterCommit(parent, "A replacement entry marker change");
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static Path marker(Path worldDirectory) {
        return normalize(worldDirectory, "worldDirectory").resolve("iris").resolve(MARKER_NAME);
    }

    private static Path normalize(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Replacement entry marker is missing " + key + ".");
        }
        return value.trim();
    }

    public record Entry(UUID transactionId, Set<UUID> pendingPlayers) {
        public Entry {
            Objects.requireNonNull(transactionId, "transactionId");
            pendingPlayers = Set.copyOf(Objects.requireNonNull(pendingPlayers, "pendingPlayers"));
        }
    }
}
