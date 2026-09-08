package art.arcane.iris.engine.history;

import art.arcane.iris.util.common.io.Durability;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GenerationHistoryStore {
    public static final String MANIFEST_FILE_NAME = "manifest.json";
    private static final int MAX_MANIFEST_BYTES = 16 * 1_024 * 1_024;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private final Path generationDirectory;
    private final Path manifestPath;
    private final Clock clock;
    private final DirectorySync directorySync;
    private GenerationManifest manifest;
    private IOException failure;

    private GenerationHistoryStore(
            Path generationDirectory,
            GenerationManifest manifest,
            Clock clock,
            DirectorySync directorySync
    ) {
        this.generationDirectory = generationDirectory;
        this.manifestPath = generationDirectory.resolve(MANIFEST_FILE_NAME);
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.directorySync = Objects.requireNonNull(directorySync, "directorySync");
    }

    public static GenerationHistoryStore initialize(
            Path generationDirectory,
            GenerationEpoch initialEpoch
    ) throws IOException {
        return initialize(generationDirectory, initialEpoch, Clock.systemUTC());
    }

    static GenerationHistoryStore initialize(
            Path generationDirectory,
            GenerationEpoch initialEpoch,
            Clock clock
    ) throws IOException {
        return initialize(generationDirectory, initialEpoch, clock, GenerationHistoryStore::forceDirectory);
    }

    static GenerationHistoryStore initialize(
            Path generationDirectory,
            GenerationEpoch initialEpoch,
            Clock clock,
            DirectorySync directorySync
    ) throws IOException {
        Path directory = normalizeDirectory(generationDirectory);
        requireSafeParent(directory);
        Path parent = Objects.requireNonNull(directory.getParent(), "Generation history parent");
        String lockName = "." + Objects.requireNonNull(directory.getFileName(), "Generation history directory name")
                + ".manifest.lock";
        try (GenerationPublicationLock ignored = GenerationPublicationLock.acquire(parent, lockName)) {
            ensureDirectory(directory);
            Path manifestPath = directory.resolve(MANIFEST_FILE_NAME);
            if (Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(manifestPath.toString());
            }
            Clock requiredClock = Objects.requireNonNull(clock, "clock");
            GenerationManifest manifest = GenerationManifest.initial(
                    Objects.requireNonNull(initialEpoch, "initialEpoch"),
                    requiredClock.millis()
            );
            DirectorySync requiredDirectorySync = Objects.requireNonNull(directorySync, "directorySync");
            writeManifestAtomically(directory, manifestPath, manifest, null, false, requiredDirectorySync);
            return new GenerationHistoryStore(directory, manifest, requiredClock, requiredDirectorySync);
        }
    }

    public static GenerationHistoryStore open(Path generationDirectory) throws IOException {
        return open(generationDirectory, Clock.systemUTC());
    }

    static GenerationHistoryStore open(Path generationDirectory, Clock clock) throws IOException {
        Path directory = normalizeDirectory(generationDirectory);
        requireDirectory(directory);
        Path manifestPath = directory.resolve(MANIFEST_FILE_NAME);
        requireRegularFile(manifestPath);
        GenerationManifest manifest = readManifest(manifestPath);
        return new GenerationHistoryStore(
                directory,
                manifest,
                clock,
                GenerationHistoryStore::forceDirectory
        );
    }

    public Path generationDirectory() {
        return generationDirectory;
    }

    public synchronized GenerationManifest manifest() {
        requireUsable();
        return manifest;
    }

    public synchronized GenerationActivation activeActivation() {
        requireUsable();
        return manifest.activeActivation();
    }

    public synchronized GenerationEpoch activeEpoch() {
        requireUsable();
        return manifest.activeEpoch();
    }

    public synchronized Optional<GenerationActivation> pendingActivation() {
        requireUsable();
        return manifest.pendingActivation();
    }

    public synchronized Optional<GenerationEpoch> pendingEpoch() {
        requireUsable();
        return manifest.pendingEpoch();
    }

    public synchronized Optional<GenerationEpoch> epoch(String epochId) {
        requireUsable();
        return manifest.epoch(epochId);
    }

    public synchronized Optional<GenerationActivation> activation(long activationId) {
        requireUsable();
        return manifest.activation(activationId);
    }

    public synchronized GenerationActivation preparePendingActivation(
            GenerationEpoch epoch,
            int transitionWidthBlocks
    ) throws IOException {
        requireUsable();
        GenerationManifest updated = manifest.preparePending(
                epoch,
                clock.millis(),
                transitionWidthBlocks
        );
        if (updated == manifest) {
            return manifest.pendingActivation().orElseThrow();
        }
        writeUpdatedManifest(updated);
        manifest = updated;
        return updated.pendingActivation().orElseThrow();
    }

    public synchronized GenerationActivation completePendingTransition(
            long expectedActivationId,
            String boundaryIdentity,
            String terrainSignatureIdentity
    ) throws IOException {
        requireUsable();
        GenerationManifest updated = manifest.completePendingTransition(
                expectedActivationId,
                boundaryIdentity,
                terrainSignatureIdentity
        );
        if (updated != manifest) {
            writeUpdatedManifest(updated);
            manifest = updated;
        }
        return manifest.pendingActivation().orElseThrow();
    }

    public synchronized GenerationActivation activatePending(
            long expectedActivationId
    ) throws IOException {
        requireUsable();
        GenerationManifest updated = manifest.activatePending(expectedActivationId);
        writeUpdatedManifest(updated);
        manifest = updated;
        return updated.activeActivation();
    }

    private void writeUpdatedManifest(GenerationManifest updated) throws IOException {
        try {
            writeManifestAtomically(generationDirectory, manifestPath, updated, manifest, true, directorySync);
        } catch (IOException error) {
            reconcileAfterWriteFailure(error);
            throw error;
        }
    }

    private static GenerationManifest readManifest(Path manifestPath) throws IOException {
        long size = Files.size(manifestPath);
        if (size < 2L || size > MAX_MANIFEST_BYTES) {
            throw new IOException("Generation manifest size is invalid: " + manifestPath);
        }
        byte[] encoded = Files.readAllBytes(manifestPath);
        if (encoded.length < 2 || encoded.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Generation manifest size changed while reading: " + manifestPath);
        }
        try (Reader input = new StringReader(new String(encoded, StandardCharsets.UTF_8));
             JsonReader reader = new JsonReader(input)) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Generation manifest must be a JSON object.");
            }
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Generation manifest contains trailing content.");
            }
            JsonObject index = parsed.getAsJsonObject();
            JsonArray references = GenerationManifest.JsonSchema.requireArray(index, "epochs", "generation manifest");
            List<GenerationEpoch> epochs = new ArrayList<>(references.size());
            for (JsonElement reference : references) {
                if (!reference.isJsonPrimitive() || !reference.getAsJsonPrimitive().isString()) {
                    throw new IOException("Generation epoch reference must be a digest.");
                }
                epochs.add(readEpoch(manifestPath.getParent(), reference.getAsString()));
            }
            return GenerationManifest.fromJson(index, epochs);
        } catch (JsonParseException | IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("Invalid generation manifest: " + manifestPath, exception);
        }
    }

    private static void writeManifestAtomically(
            Path generationDirectory,
            Path manifestPath,
            GenerationManifest manifest,
            GenerationManifest previous,
            boolean replaceExisting,
            DirectorySync directorySync
    ) throws IOException {
        requireSafeParent(generationDirectory);
        requireDirectory(generationDirectory);
        byte[] content = GSON.toJson(manifest.toJson()).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Generation manifest exceeds the supported size: " + manifestPath);
        }
        for (GenerationEpoch epoch : manifest.epochs()) {
            if (previous == null || previous.epoch(epoch.epochId()).isEmpty()) {
                publishEpoch(generationDirectory, epoch);
            }
        }
        Path temporary = Files.createTempFile(generationDirectory, ".manifest-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                Durability.force(channel);
            }
            try {
                if (replaceExisting) {
                    Files.move(
                            temporary,
                            manifestPath,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } else {
                    Files.move(temporary, manifestPath, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Generation history requires atomic manifest publication.", exception);
            }
            directorySync.force(generationDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static GenerationEpoch readEpoch(Path generationDirectory, String epochId) throws IOException {
        Path epochDirectory = epochDirectory(generationDirectory, epochId);
        requireDirectory(generationDirectory.resolve("epochs"));
        requireDirectory(epochDirectory);
        Path metadata = epochDirectory.resolve("epoch.json");
        requireRegularFile(metadata);
        if (Files.size(metadata) > MAX_MANIFEST_BYTES) {
            throw new IOException("Generation epoch metadata exceeds the supported size: " + metadata);
        }
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(metadata, StandardCharsets.UTF_8))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject() || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Invalid generation epoch metadata: " + metadata);
            }
            GenerationEpoch epoch = GenerationEpoch.fromJson(parsed.getAsJsonObject());
            if (!epoch.epochId().equals(epochId)) {
                throw new IOException("Generation epoch metadata identity differs from its reference: " + metadata);
            }
            return epoch;
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new IOException("Invalid generation epoch metadata: " + metadata, exception);
        }
    }

    private static void publishEpoch(Path generationDirectory, GenerationEpoch epoch) throws IOException {
        Path epochsDirectory = generationDirectory.resolve("epochs");
        ensureDirectory(epochsDirectory);
        Path epochDirectory = epochDirectory(generationDirectory, epoch.epochId());
        ensureDirectory(epochDirectory);
        Path metadata = epochDirectory.resolve("epoch.json");
        if (Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) {
            if (!readEpoch(generationDirectory, epoch.epochId()).equals(epoch)) {
                throw new IOException("Generation epoch metadata is immutable: " + metadata);
            }
            return;
        }
        byte[] content = GSON.toJson(epoch.toJson()).getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Generation epoch metadata exceeds the supported size: " + metadata);
        }
        Path temporary = Files.createTempFile(epochDirectory, ".epoch-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                Durability.force(channel);
            }
            try {
                Files.move(temporary, metadata, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Generation history requires atomic epoch publication.", exception);
            }
            forceDirectory(epochDirectory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path epochDirectory(Path generationDirectory, String epochId) throws IOException {
        if (!epochId.matches("[0-9a-f]{64}")) {
            throw new IOException("Generation epoch reference must be a digest.");
        }
        return generationDirectory.resolve("epochs").resolve(epochId);
    }

    private static Path normalizeDirectory(Path directory) {
        return Objects.requireNonNull(directory, "generationDirectory").toAbsolutePath().normalize();
    }

    private static void ensureDirectory(Path directory) throws IOException {
        requireSafeParent(directory);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(directory);
            forceDirectory(Objects.requireNonNull(directory.getParent(), "Generation history parent"));
            return;
        }
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException race) {
            requireDirectory(directory);
            forceDirectory(Objects.requireNonNull(directory.getParent(), "Generation history parent"));
            return;
        }
        forceDirectory(Objects.requireNonNull(directory.getParent(), "Generation history parent"));
        requireDirectory(directory);
    }

    private static void requireDirectory(Path directory) throws IOException {
        requireSafeParent(directory);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(directory.toString());
        }
        BasicFileAttributes attributes = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation history path is not a safe directory: " + directory);
        }
    }

    private static void requireSafeParent(Path directory) throws IOException {
        Path parent = directory.getParent();
        if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Generation history parent directory is missing: " + parent);
        }
        BasicFileAttributes attributes = Files.readAttributes(
                parent,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation history parent path is not a safe directory: " + parent);
        }
    }

    private static void requireRegularFile(Path file) throws IOException {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(file.toString());
        }
        BasicFileAttributes attributes = Files.readAttributes(
                file,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("Generation manifest is not a safe regular file: " + file);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        if (!Durability.enabled()) {
            return;
        }

        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            Durability.force(channel);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("Generation history directory cannot be durability-synced: " + directory, exception);
        }
    }

    private void reconcileAfterWriteFailure(IOException writeFailure) {
        try {
            requireDirectory(generationDirectory);
            requireRegularFile(manifestPath);
            manifest = readManifest(manifestPath);
        } catch (IOException reconciliationFailure) {
            writeFailure.addSuppressed(reconciliationFailure);
            failure = writeFailure;
        }
    }

    private void requireUsable() {
        if (failure != null) {
            throw new IllegalStateException("Generation history store is unusable after a failed manifest write", failure);
        }
    }

    @FunctionalInterface
    interface DirectorySync {
        void force(Path directory) throws IOException;
    }
}
