package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

public final class JigsawStudioProjectDeletionService {
    private static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_SCANNED_FILES = 100_000;

    private JigsawStudioProjectDeletionService() {
    }

    public static DeletionPlan inspect(Path packRoot, String structureKey) throws IOException {
        Path root = canonicalRoot(packRoot);
        StructureKey key = StructureKey.parse(structureKey, "iris");
        ManifestSnapshot snapshot = loadSnapshot(root, key);
        List<ReverseReference> blockers = scanReverseReferences(root, snapshot);
        return new DeletionPlan(
                UUID.randomUUID(),
                root,
                key,
                snapshot.manifest().source(),
                snapshot.manifestHash(),
                snapshot.sourceHash(),
                snapshot.manifest().resourceHashes(),
                blockers);
    }

    public static ProjectDeletionResult delete(DeletionPlan plan) throws IOException {
        DeletionPlan expected = Objects.requireNonNull(plan, "Jigsaw Studio project deletion plan");
        if (!expected.deletable()) {
            throw new IOException("Jigsaw Studio project deletion is blocked by "
                    + expected.blockers().size() + " reverse references");
        }
        Path root = canonicalRoot(expected.packRoot());
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureTransactionWriter.OwnedRemoval request = new StructureTransactionWriter.OwnedRemoval(
                expected.structureKey(),
                expected.expectedSource().kind(),
                expected.expectedSource().key(),
                Optional.empty(),
                Optional.of(expected.expectedManifestHash()));
        try (StructureTransactionWriter.PreparedRemoval removal = writer.prepareOwnedRemovals(
                List.of(request),
                () -> validateDeletion(root, expected))) {
            if (!removal.changed()) {
                throw new IOException("Jigsaw Studio project no longer exists");
            }
            removal.markCommitted();
            removal.finishCommit();
        }
        return new ProjectDeletionResult(
                expected.planId(),
                expected.structureKey(),
                expected.expectedResourceHashes().size(),
                true);
    }

    private static void validateDeletion(Path root, DeletionPlan expected) throws IOException {
        ManifestSnapshot current = loadSnapshot(root, expected.structureKey());
        if (!expected.expectedManifestHash().equals(current.manifestHash())) {
            throw new IOException("Jigsaw Studio project changed after deletion was inspected");
        }
        if (!expected.expectedSource().equals(current.manifest().source())
                || !expected.expectedSourceHash().equals(current.sourceHash())) {
            throw new IOException("Jigsaw Studio project source changed after deletion was inspected");
        }
        if (!expected.expectedResourceHashes().equals(current.manifest().resourceHashes())) {
            throw new IOException("Jigsaw Studio project resources changed after deletion was inspected");
        }
        List<ReverseReference> currentBlockers = scanReverseReferences(root, current);
        if (!currentBlockers.isEmpty()) {
            throw new IOException("Jigsaw Studio project gained " + currentBlockers.size()
                    + " reverse references after deletion was inspected");
        }
    }

    private static Path canonicalRoot(Path packRoot) throws IOException {
        Path normalized = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        Path resolved;
        try {
            resolved = normalized.toRealPath();
        } catch (IOException exception) {
            throw new IOException("Cannot resolve Jigsaw Studio pack root " + normalized, exception);
        }
        if (!Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Jigsaw Studio pack root is not a directory: " + resolved);
        }
        return resolved;
    }

    private static ManifestSnapshot loadSnapshot(Path root, StructureKey key) throws IOException {
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        Path manifestPath = writer.ownershipManifestPath(key);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This graph is read-only because it is not Studio-owned; only Studio-owned projects can be deleted");
        }
        byte[] manifestContent = readBoundedJson(manifestPath, "Jigsaw Studio ownership manifest");
        StructureOwnershipManifest manifest;
        try {
            manifest = JigsawStudioAuthoringAccess.requireEditable(
                    StructureOwnershipManifest.fromJson(manifestContent));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid Jigsaw Studio ownership manifest at " + manifestPath, exception);
        }
        if (!manifest.structure().equals(key)) {
            throw new IOException("Jigsaw Studio ownership manifest belongs to " + manifest.structure());
        }
        String structureResource = "structures/" + manifest.structure().path() + ".json";
        String structureHash = manifest.resourceHashes().get(structureResource);
        if (structureHash == null) {
            throw new IOException("Jigsaw Studio ownership manifest does not include " + structureResource);
        }
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            Path resourcePath = resolveOwnedResource(root, resource.getKey());
            String actualHash;
            try (InputStream input = Files.newInputStream(resourcePath)) {
                actualHash = StructureHash.sha256(input);
            }
            if (!resource.getValue().equals(actualHash)) {
                throw new IOException("Owned graph resource changed outside Studio: " + resource.getKey());
            }
        }
        String sourceHash = manifest.source().contentHash().isEmpty()
                ? structureHash
                : manifest.source().contentHash();
        return new ManifestSnapshot(
                manifest,
                StructureHash.sha256(manifestContent),
                sourceHash,
                manifestPath);
    }

    private static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root)
                || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned graph resource is missing or unsafe: " + relativePath);
        }
        return resource;
    }

    private static List<ReverseReference> scanReverseReferences(
            Path root,
            ManifestSnapshot snapshot
    ) throws IOException {
        Map<String, Set<String>> targetsByKey = referenceTargets(snapshot.manifest());
        Set<String> ownedPaths = snapshot.manifest().resourceHashes().keySet();
        Set<ReverseReference> references = new LinkedHashSet<>();
        int scannedFiles = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                Path relative = root.relativize(path);
                String relativePath = relative.toString().replace(path.getFileSystem().getSeparator(), "/");
                if (relativePath.isEmpty() || relativePath.startsWith(".iris/")) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    if (relativePath.endsWith(".json") || Files.isDirectory(path)) {
                        throw new IOException("Cannot prove deletion safety through symbolic pack path "
                                + relativePath);
                    }
                    continue;
                }
                if (!relativePath.endsWith(".json") || ownedPaths.contains(relativePath)) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                scannedFiles++;
                if (scannedFiles > MAX_SCANNED_FILES) {
                    throw new IOException("Jigsaw Studio project deletion scan exceeds "
                            + MAX_SCANNED_FILES + " JSON resources");
                }
                JsonElement json = parseBoundedJson(path, relativePath);
                collectReferences(json, "$", relativePath, targetsByKey, references);
            }
        }
        scanOwnershipManifests(root, snapshot, references);
        List<ReverseReference> ordered = new ArrayList<>(references);
        ordered.sort(Comparator.comparing(ReverseReference::ownerPath)
                .thenComparing(ReverseReference::location)
                .thenComparing(ReverseReference::targetResourcePath));
        return List.copyOf(ordered);
    }

    private static Map<String, Set<String>> referenceTargets(StructureOwnershipManifest manifest) {
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        addReferenceTarget(targets, manifest.structure().path(),
                "structures/" + manifest.structure().path() + ".json");
        addReferenceTarget(targets, manifest.structure().value(),
                "structures/" + manifest.structure().path() + ".json");
        for (String relativePath : manifest.resourceHashes().keySet()) {
            addResourceReferenceTarget(targets, relativePath, "jigsaw-pools/", ".json");
            addResourceReferenceTarget(targets, relativePath, "jigsaw-pieces/", ".json");
            addResourceReferenceTarget(targets, relativePath, "objects/", ".iob");
        }
        return targets;
    }

    private static void addResourceReferenceTarget(
            Map<String, Set<String>> targets,
            String relativePath,
            String prefix,
            String suffix
    ) {
        if (!relativePath.startsWith(prefix) || !relativePath.endsWith(suffix)) {
            return;
        }
        String key = relativePath.substring(prefix.length(), relativePath.length() - suffix.length());
        addReferenceTarget(targets, key, relativePath);
        addReferenceTarget(targets, "iris:" + key, relativePath);
    }

    private static void addReferenceTarget(
            Map<String, Set<String>> targets,
            String key,
            String relativePath
    ) {
        targets.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(relativePath);
    }

    private static void collectReferences(
            JsonElement element,
            String location,
            String ownerPath,
            Map<String, Set<String>> targetsByKey,
            Set<ReverseReference> references
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            Set<String> targets = targetsByKey.get(element.getAsString());
            if (targets != null) {
                for (String target : targets) {
                    references.add(new ReverseReference(ownerPath, location, target));
                }
            }
            return;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int index = 0; index < array.size(); index++) {
                collectReferences(
                        array.get(index),
                        location + "[" + index + "]",
                        ownerPath,
                        targetsByKey,
                        references);
            }
            return;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                collectReferences(
                        entry.getValue(),
                        location + "." + entry.getKey(),
                        ownerPath,
                        targetsByKey,
                        references);
            }
        }
    }

    private static void scanOwnershipManifests(
            Path root,
            ManifestSnapshot target,
            Set<ReverseReference> references
    ) throws IOException {
        Path manifestRoot = root.resolve(".iris/structure-manifests");
        if (!Files.isDirectory(manifestRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.list(manifestRoot)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSameFile(path, target.manifestPath())
                        || !path.getFileName().toString().endsWith(".json")) {
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cannot prove deletion safety through ownership manifest " + path);
                }
                StructureOwnershipManifest manifest;
                try {
                    manifest = StructureOwnershipManifest.fromJson(
                            readBoundedJson(path, "Structure ownership manifest"));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid structure ownership manifest at " + path, exception);
                }
                for (String relativePath : target.manifest().resourceHashes().keySet()) {
                    if (manifest.resourceHashes().containsKey(relativePath)) {
                        references.add(new ReverseReference(
                                root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/"),
                                "$.resourceHashes",
                                relativePath));
                    }
                }
            }
        }
    }

    private static JsonElement parseBoundedJson(Path path, String relativePath) throws IOException {
        byte[] content = readBoundedJson(path, "JSON resource");
        try {
            return JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IOException("Cannot prove deletion safety through invalid JSON resource "
                    + relativePath, exception);
        }
    }

    private static byte[] readBoundedJson(Path path, String kind) throws IOException {
        long size = Files.size(path);
        if (size > MAX_JSON_BYTES) {
            throw new IOException(kind + " exceeds " + MAX_JSON_BYTES + " bytes: " + path);
        }
        return Files.readAllBytes(path);
    }

    public record DeletionPlan(
            UUID planId,
            Path packRoot,
            StructureKey structureKey,
            StructureSource expectedSource,
            String expectedManifestHash,
            String expectedSourceHash,
            Map<String, String> expectedResourceHashes,
            List<ReverseReference> blockers
    ) {
        public DeletionPlan {
            Objects.requireNonNull(planId, "Jigsaw Studio project deletion plan ID");
            packRoot = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                    .toAbsolutePath().normalize();
            Objects.requireNonNull(structureKey, "Jigsaw Studio project deletion structure key");
            Objects.requireNonNull(expectedSource, "Jigsaw Studio project deletion source");
            if (!StructureHash.isSha256(expectedManifestHash)) {
                throw new IllegalArgumentException("Expected Jigsaw Studio ownership manifest hash must be SHA-256");
            }
            if (!StructureHash.isSha256(expectedSourceHash)) {
                throw new IllegalArgumentException("Expected Jigsaw Studio source hash must be SHA-256");
            }
            Objects.requireNonNull(expectedResourceHashes, "Jigsaw Studio project deletion resources");
            expectedResourceHashes = Collections.unmodifiableMap(new TreeMap<>(expectedResourceHashes));
            blockers = List.copyOf(Objects.requireNonNull(
                    blockers,
                    "Jigsaw Studio project deletion blockers"));
        }

        public boolean deletable() {
            return blockers.isEmpty();
        }
    }

    public record ReverseReference(
            String ownerPath,
            String location,
            String targetResourcePath
    ) {
        public ReverseReference {
            ownerPath = requireNonBlank(ownerPath, "reverse-reference owner path");
            location = requireNonBlank(location, "reverse-reference location");
            targetResourcePath = StructureResourceBundle.validateRelativePath(targetResourcePath);
        }
    }

    public record ProjectDeletionResult(
            UUID planId,
            StructureKey structureKey,
            int removedResourceCount,
            boolean manifestRemoved
    ) {
        public ProjectDeletionResult {
            Objects.requireNonNull(planId, "Jigsaw Studio project deletion plan ID");
            Objects.requireNonNull(structureKey, "Deleted Jigsaw Studio structure key");
            if (removedResourceCount < 0) {
                throw new IllegalArgumentException("Removed Jigsaw Studio resource count cannot be negative");
            }
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return normalized;
    }

    private record ManifestSnapshot(
            StructureOwnershipManifest manifest,
            String manifestHash,
            String sourceHash,
            Path manifestPath
    ) {
    }
}
