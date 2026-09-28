package art.arcane.iris.generation.hydrology;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

final class PreparedHydrologyTileStore {
    private static final long MAXIMUM_COMPRESSED_BYTES = 128L * 1024L * 1024L;
    private static final long MAXIMUM_DECOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private final Path directory;
    private final String scopeIdentity;
    private final HydrologyTileCache.SharedCacheScope scope;
    private final int expectedTileSize;

    PreparedHydrologyTileStore(Path root, HydrologyTileCache.SharedCacheScope scope, int expectedTileSize) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (expectedTileSize < 1) {
            throw new IllegalArgumentException("Hydrology tile size must be positive.");
        }
        this.expectedTileSize = expectedTileSize;
        this.scopeIdentity = scopeFingerprint(scope);
        this.directory = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize()
                .resolve(scopeIdentity);
    }

    boolean contains(HydrologyTileKey key) {
        Path file = file(key);
        try {
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(file)
                    && Files.size(file) <= MAXIMUM_COMPRESSED_BYTES;
        } catch (IOException failure) {
            return false;
        }
    }

    Optional<HydrologyTile> load(HydrologyTileKey key) {
        if (!contains(key)) {
            return Optional.empty();
        }
        try (InputStream raw = Files.newInputStream(file(key));
             InputStream buffered = new BufferedInputStream(raw);
             InputStream compressed = new GZIPInputStream(buffered, 32768);
             InputStream bounded = new LimitedInputStream(compressed, MAXIMUM_DECOMPRESSED_BYTES);
             DataInputStream input = new DataInputStream(new BufferedInputStream(bounded, 32768))) {
            HydrologyTile tile = new HydrologyTileCodec().read(input, scopeIdentity, key, scope, expectedTileSize);
            return valid(tile, key) ? Optional.of(tile) : Optional.empty();
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    void save(HydrologyTile tile) throws IOException {
        if (!valid(tile, tile.key())) {
            throw new IOException("Refused to persist a hydrology tile outside the active prepared-plan scope.");
        }
        Files.createDirectories(directory);
        Path target = file(tile.key());
        Path staged = Files.createTempFile(directory, ".hydrology-tile-", ".tmp");
        try {
            try (OutputStream raw = Files.newOutputStream(staged);
                 OutputStream buffered = new BufferedOutputStream(raw);
                 OutputStream compressed = new GZIPOutputStream(buffered, 32768);
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(compressed, 32768))) {
                new HydrologyTileCodec().write(output, tile, scopeIdentity);
            }
            if (Files.size(staged) > MAXIMUM_COMPRESSED_BYTES) {
                throw new IOException("Prepared hydrology tile cache entry exceeds the size limit.");
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    Path file(HydrologyTileKey key) {
        return directory.resolve("tile-" + key.tileX() + "-" + key.tileZ() + ".bin.gz");
    }

    private boolean valid(HydrologyTile tile, HydrologyTileKey key) {
        return tile != null
                && key.equals(tile.key())
                && tile.worldSeed() == scope.worldSeed()
                && tile.settingsFingerprint() == scope.settingsFingerprint()
                && tile.tileSize() == expectedTileSize;
    }

    private static String scopeFingerprint(HydrologyTileCache.SharedCacheScope scope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, scope.runtimeIdentity());
            update(digest, Long.toString(scope.worldSeed()));
            update(digest, Integer.toString(scope.worldHeight()));
            update(digest, scope.dimensionKey());
            update(digest, Long.toString(scope.settingsFingerprint()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
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

    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long count;

        private LimitedInputStream(InputStream input, long limit) {
            super(input);
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                advance(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                advance(read);
            }
            return read;
        }

        private void advance(long amount) throws IOException {
            count += amount;
            if (count > limit) {
                throw new IOException("Prepared hydrology tile cache entry exceeds the decompressed size limit.");
            }
        }
    }
}
