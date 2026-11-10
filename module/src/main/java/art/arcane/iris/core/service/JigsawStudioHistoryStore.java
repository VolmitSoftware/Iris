package art.arcane.iris.core.service;

import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureLoss;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

final class JigsawStudioHistoryStore {
    static final int MAX_ITERATIONS = 5;
    private static final int SCHEMA_VERSION = 1;
    private static final long MAX_HISTORY_BYTES = 512L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private final Path packRoot;
    private final StructureKey structureKey;
    private final StructureTransactionWriter writer;
    private final Path historyPath;
    private final ReentrantLock lock;

    JigsawStudioHistoryStore(Path packRoot, String structureKey) {
        this.packRoot = canonicalPackRoot(packRoot);
        this.structureKey = new StructureKey(
                "iris",
                Objects.requireNonNull(structureKey, "Jigsaw Studio history structure key"));
        writer = new StructureTransactionWriter(this.packRoot);
        String identityHash = StructureHash.sha256(this.structureKey.value().getBytes(StandardCharsets.UTF_8));
        historyPath = this.packRoot.resolve(".iris/jigsaw-history/key-" + identityHash + ".json").normalize();
        if (!historyPath.startsWith(this.packRoot)) {
            throw new IllegalArgumentException("Jigsaw Studio history path escapes its pack root");
        }
        lock = LOCKS.computeIfAbsent(historyPath, ignored -> new ReentrantLock());
    }

    Snapshot snapshotCurrent(String pieceKey) throws IOException {
        lock.lock();
        try {
            return readCurrentSnapshot(pieceKey);
        } finally {
            lock.unlock();
        }
    }

    int append(Snapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "Jigsaw Studio history snapshot");
        lock.lock();
        try {
            HistoryDocument document = readDocument();
            if (!document.structure().equals(structureKey.value())) {
                throw new IOException("Jigsaw Studio history belongs to " + document.structure());
            }
            ArrayList<HistoryIteration> iterations = new ArrayList<>(document.iterations());
            HistoryIteration iteration = snapshot.iteration();
            if (!iterations.isEmpty() && iterations.getLast().sameState(iteration)) {
                return iterations.size();
            }
            iterations.add(iteration);
            while (iterations.size() > MAX_ITERATIONS) {
                iterations.removeFirst();
            }
            TreeMap<String, String> blobs = new TreeMap<>(document.blobs());
            for (Map.Entry<String, byte[]> resource : snapshot.resources().entrySet()) {
                String hash = StructureHash.sha256(resource.getValue());
                blobs.putIfAbsent(hash, Base64.getEncoder().encodeToString(resource.getValue()));
            }
            retainReferencedBlobs(blobs, iterations);
            writeDocument(new HistoryDocument(
                    SCHEMA_VERSION,
                    structureKey.value(),
                    List.copyOf(iterations),
                    Map.copyOf(blobs)));
            return iterations.size();
        } finally {
            lock.unlock();
        }
    }

    UndoResult undoLatest() throws IOException {
        lock.lock();
        try {
            HistoryDocument document = readDocument();
            if (document.iterations().isEmpty()) {
                return UndoResult.unavailable();
            }
            HistoryIteration iteration = document.iterations().getLast();
            StructureResourceBundle bundle = restoreBundle(iteration, document.blobs());
            StructureResourceBundleGraphCompiler.requireViable(bundle);
            Path manifestPath = writer.ownershipManifestPath(structureKey);
            byte[] currentManifest = readRegularFile(manifestPath, "ownership manifest");
            StructureWriteResult result = writer.write(
                    bundle,
                    StructureWriteOptions.overwriteExpected(StructureHash.sha256(currentManifest)));
            if (!result.successful()) {
                return new UndoResult(
                        false,
                        true,
                        document.iterations().size(),
                        iteration.pieceKey(),
                        result,
                        "");
            }
            ArrayList<HistoryIteration> remaining = new ArrayList<>(document.iterations());
            remaining.removeLast();
            TreeMap<String, String> blobs = new TreeMap<>(document.blobs());
            retainReferencedBlobs(blobs, remaining);
            String warning = "";
            try {
                if (remaining.isEmpty()) {
                    Files.deleteIfExists(historyPath);
                    forceDirectory(historyPath.getParent());
                } else {
                    writeDocument(new HistoryDocument(
                            SCHEMA_VERSION,
                            structureKey.value(),
                            List.copyOf(remaining),
                            Map.copyOf(blobs)));
                }
            } catch (IOException historyFailure) {
                warning = historyFailure.getMessage() == null
                        ? historyFailure.getClass().getSimpleName()
                        : historyFailure.getMessage();
            }
            return new UndoResult(
                    true,
                    true,
                    remaining.size(),
                    iteration.pieceKey(),
                    result,
                    warning);
        } finally {
            lock.unlock();
        }
    }

    int availableIterations() throws IOException {
        lock.lock();
        try {
            return readDocument().iterations().size();
        } finally {
            lock.unlock();
        }
    }

    void delete() throws IOException {
        lock.lock();
        try {
            if (Files.deleteIfExists(historyPath)) {
                forceDirectory(historyPath.getParent());
            }
        } finally {
            lock.unlock();
        }
    }

    Path historyPath() {
        return historyPath;
    }

    private Snapshot readCurrentSnapshot(String pieceKey) throws IOException {
        Path manifestPath = writer.ownershipManifestPath(structureKey);
        byte[] manifestContent = readRegularFile(manifestPath, "ownership manifest");
        StructureOwnershipManifest manifest;
        try {
            manifest = StructureOwnershipManifest.fromJson(manifestContent);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw Studio history cannot parse the ownership manifest", exception);
        }
        if (!manifest.structure().equals(structureKey)) {
            throw new IOException("Jigsaw Studio ownership manifest belongs to " + manifest.structure());
        }
        TreeMap<String, byte[]> resources = new TreeMap<>();
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            Path resourcePath = resolveOwnedResource(resource.getKey());
            byte[] content = readRegularFile(resourcePath, "owned resource " + resource.getKey());
            String actualHash = StructureHash.sha256(content);
            if (!resource.getValue().equals(actualHash)) {
                throw new IOException("Jigsaw Studio owned resource changed before history capture: "
                        + resource.getKey());
            }
            resources.put(resource.getKey(), content);
        }
        return new Snapshot(
                new HistoryIteration(
                        System.currentTimeMillis(),
                        Objects.requireNonNull(pieceKey, "Jigsaw Studio history piece key"),
                        manifest.source(),
                        manifest.backend(),
                        manifest.capabilities(),
                        manifest.losses(),
                        manifest.resourceHashes()),
                resources);
    }

    private StructureResourceBundle restoreBundle(
            HistoryIteration iteration,
            Map<String, String> blobs
    ) throws IOException {
        StructureResourceBundle.Builder builder = StructureResourceBundle.builder(structureKey)
                .source(iteration.source())
                .backend(iteration.backend())
                .capabilities(iteration.capabilities())
                .losses(iteration.losses());
        for (Map.Entry<String, String> resource : iteration.resourceHashes().entrySet()) {
            String encoded = blobs.get(resource.getValue());
            if (encoded == null) {
                throw new IOException("Jigsaw Studio history is missing resource blob " + resource.getValue());
            }
            byte[] content;
            try {
                content = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Jigsaw Studio history contains invalid resource data", exception);
            }
            if (!resource.getValue().equals(StructureHash.sha256(content))) {
                throw new IOException("Jigsaw Studio history resource hash does not match "
                        + resource.getKey());
            }
            builder.resource(resource.getKey(), content);
        }
        return builder.build();
    }

    private HistoryDocument readDocument() throws IOException {
        if (!Files.exists(historyPath, LinkOption.NOFOLLOW_LINKS)) {
            return HistoryDocument.empty(structureKey.value());
        }
        byte[] content = readRegularFile(historyPath, "history file");
        if (content.length > MAX_HISTORY_BYTES) {
            throw new IOException("Jigsaw Studio history exceeds " + MAX_HISTORY_BYTES + " bytes");
        }
        HistoryDocument document;
        try {
            document = GSON.fromJson(new String(content, StandardCharsets.UTF_8), HistoryDocument.class);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw Studio history is invalid", exception);
        }
        if (document == null || document.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("Unsupported Jigsaw Studio history schema");
        }
        HistoryDocument validated;
        try {
            validated = document.validated();
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw Studio history is invalid", exception);
        }
        if (!validated.structure().equals(structureKey.value())) {
            throw new IOException("Jigsaw Studio history belongs to " + validated.structure());
        }
        return validated;
    }

    private void writeDocument(HistoryDocument document) throws IOException {
        HistoryDocument validated = document.validated();
        byte[] content = (GSON.toJson(validated) + "\n").getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_HISTORY_BYTES) {
            throw new IOException("Jigsaw Studio history exceeds " + MAX_HISTORY_BYTES + " bytes");
        }
        Path historyRoot = historyPath.getParent();
        Files.createDirectories(historyRoot);
        rejectSymbolicPath(historyRoot);
        Path temporary = historyRoot.resolve(historyPath.getFileName() + "."
                + UUID.randomUUID() + ".tmp").normalize();
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        historyPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, historyPath, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(historyRoot);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Path resolveOwnedResource(String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = packRoot.resolve(relativePath).normalize();
        if (!resource.startsWith(packRoot)) {
            throw new IOException("Jigsaw Studio history resource escapes its pack root: " + relativePath);
        }
        rejectSymbolicPath(resource.getParent());
        return resource;
    }

    private static byte[] readRegularFile(Path path, String kind) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Jigsaw Studio " + kind + " is missing or not a regular file: " + path);
        }
        return Files.readAllBytes(path);
    }

    private static void retainReferencedBlobs(
            Map<String, String> blobs,
            List<HistoryIteration> iterations
    ) {
        Set<String> retained = new TreeSet<>();
        for (HistoryIteration iteration : iterations) {
            retained.addAll(iteration.resourceHashes().values());
        }
        blobs.keySet().retainAll(retained);
    }

    private void rejectSymbolicPath(Path path) throws IOException {
        Path current = path;
        while (current != null && current.startsWith(packRoot)) {
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Jigsaw Studio history path contains a symbolic link: " + current);
            }
            if (current.equals(packRoot)) {
                return;
            }
            current = current.getParent();
        }
        throw new IOException("Jigsaw Studio history path escapes its pack root: " + path);
    }

    private static Path canonicalPackRoot(Path root) {
        Path normalized = Objects.requireNonNull(root, "Jigsaw Studio history pack root")
                .toAbsolutePath().normalize();
        try {
            return normalized.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Jigsaw Studio history pack root is unavailable: "
                    + normalized, exception);
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

    record Snapshot(HistoryIteration iteration, Map<String, byte[]> resources) {
        Snapshot {
            Objects.requireNonNull(iteration, "Jigsaw Studio history iteration");
            Objects.requireNonNull(resources, "Jigsaw Studio history resources");
            LinkedHashMap<String, byte[]> copies = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> resource : resources.entrySet()) {
                copies.put(resource.getKey(), resource.getValue().clone());
            }
            resources = Map.copyOf(copies);
        }

        boolean matches(StructureResourceBundle bundle) {
            if (!iteration.source().equals(bundle.source())
                    || iteration.backend() != bundle.backend()
                    || !Set.copyOf(iteration.capabilities()).equals(bundle.capabilities())
                    || !iteration.losses().equals(bundle.losses())
                    || iteration.resourceHashes().size() != bundle.resources().size()) {
                return false;
            }
            for (Map.Entry<String, StructureResourceBundle.Resource> resource
                    : bundle.resources().entrySet()) {
                if (!resource.getValue().contentHash().equals(
                        iteration.resourceHashes().get(resource.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    record UndoResult(
            boolean successful,
            boolean available,
            int remainingIterations,
            String pieceKey,
            StructureWriteResult writeResult,
            String warning
    ) {
        UndoResult {
            pieceKey = pieceKey == null ? "" : pieceKey;
            warning = warning == null ? "" : warning;
        }

        static UndoResult unavailable() {
            return new UndoResult(false, false, 0, "", null, "");
        }
    }

    private record HistoryDocument(
            int schemaVersion,
            String structure,
            List<HistoryIteration> iterations,
            Map<String, String> blobs
    ) {
        private HistoryDocument {
            structure = structure == null ? "" : structure;
            iterations = iterations == null ? List.of() : List.copyOf(iterations);
            blobs = blobs == null ? Map.of() : Map.copyOf(blobs);
        }

        private static HistoryDocument empty(String structure) {
            return new HistoryDocument(SCHEMA_VERSION, structure, List.of(), Map.of());
        }

        private HistoryDocument validated() throws IOException {
            if (schemaVersion != SCHEMA_VERSION || structure.isBlank()) {
                throw new IOException("Jigsaw Studio history header is invalid");
            }
            if (iterations.size() > MAX_ITERATIONS) {
                throw new IOException("Jigsaw Studio history contains too many iterations");
            }
            TreeMap<String, String> validatedBlobs = new TreeMap<>();
            for (Map.Entry<String, String> blob : blobs.entrySet()) {
                if (!StructureHash.isSha256(blob.getKey()) || blob.getValue() == null) {
                    throw new IOException("Jigsaw Studio history contains an invalid resource blob");
                }
                validatedBlobs.put(blob.getKey(), blob.getValue());
            }
            return new HistoryDocument(
                    SCHEMA_VERSION,
                    structure,
                    List.copyOf(iterations),
                    Map.copyOf(validatedBlobs));
        }
    }

    private record HistoryIteration(
            long recordedAtEpochMilli,
            String pieceKey,
            StructureSource source,
            StructureBackend backend,
            List<StructureCapability> capabilities,
            List<StructureLoss> losses,
            Map<String, String> resourceHashes
    ) {
        private HistoryIteration {
            pieceKey = Objects.requireNonNull(pieceKey, "Jigsaw Studio history piece key").trim();
            if (pieceKey.isEmpty()) {
                throw new IllegalArgumentException("Jigsaw Studio history piece key cannot be empty");
            }
            Objects.requireNonNull(source, "Jigsaw Studio history source");
            Objects.requireNonNull(backend, "Jigsaw Studio history backend");
            capabilities = List.copyOf(Objects.requireNonNull(
                    capabilities,
                    "Jigsaw Studio history capabilities"));
            losses = List.copyOf(Objects.requireNonNull(losses, "Jigsaw Studio history losses"));
            TreeMap<String, String> hashes = new TreeMap<>();
            for (Map.Entry<String, String> resource : Objects.requireNonNull(
                    resourceHashes,
                    "Jigsaw Studio history resource hashes").entrySet()) {
                String relativePath = StructureResourceBundle.validateRelativePath(resource.getKey());
                if (!StructureHash.isSha256(resource.getValue())) {
                    throw new IllegalArgumentException("Invalid Jigsaw Studio history resource hash");
                }
                hashes.put(relativePath, resource.getValue());
            }
            if (hashes.isEmpty()) {
                throw new IllegalArgumentException("Jigsaw Studio history iteration cannot be empty");
            }
            resourceHashes = Map.copyOf(hashes);
        }

        private boolean sameState(HistoryIteration other) {
            return source.equals(other.source)
                    && backend == other.backend
                    && pieceKey.equals(other.pieceKey)
                    && capabilities.equals(other.capabilities)
                    && losses.equals(other.losses)
                    && resourceHashes.equals(other.resourceHashes);
        }
    }
}
