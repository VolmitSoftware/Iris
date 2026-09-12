package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.pack.StructurePackageClosure;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class JigsawStudioResourceBundleAssembler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JigsawStudioResourceBundleAssembler() {
    }

    static Assembly assemble(
            Path packRoot,
            String structureKey,
            String pieceKey,
            byte[] objectContent,
            List<IrisJigsawConnector> connectors,
            boolean hasBlockEntities
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root").toAbsolutePath().normalize();
        String rootStructure = requireResourceKey(structureKey, "structure");
        String editedPiece = requireResourceKey(pieceKey, "piece");
        StructureKey ownershipKey = new StructureKey("iris", rootStructure);
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        Path manifestPath = writer.ownershipManifestPath(ownershipKey);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Jigsaw Studio cannot save '" + rootStructure
                    + "' because it is not Studio-owned. Existing unowned graphs are read-only; create a new Jigsaw Studio project to author in-game.");
        }
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        StructureOwnershipManifest manifest;
        try {
            manifest = StructureOwnershipManifest.fromJson(manifestContent);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw Studio cannot read the ownership manifest for '" + rootStructure + "'", exception);
        }
        JigsawStudioAuthoringAccess.requireEditable(manifest);
        if (!manifest.structure().equals(ownershipKey)) {
            throw new IOException("Jigsaw Studio ownership manifest belongs to " + manifest.structure()
                    + ", not " + ownershipKey);
        }

        StructurePackageClosure closure = StructurePackageClosure.collect(root.toFile(), List.of(rootStructure));
        if (!closure.isValid()) {
            throw new IOException("Jigsaw Studio cannot save an invalid structure graph: "
                    + String.join("; ", closure.errors()));
        }
        Set<String> reachablePaths = resourcePaths(closure);
        for (String relativePath : reachablePaths) {
            if (!manifest.resourceHashes().containsKey(relativePath)) {
                throw new IOException("Jigsaw Studio ownership conflict: reachable resource '" + relativePath
                        + "' is not owned by structure '" + rootStructure + "'.");
            }
        }

        String piecePath = "jigsaw-pieces/" + editedPiece + ".json";
        if (!manifest.resourceHashes().containsKey(piecePath)) {
            throw new IOException("Jigsaw Studio ownership conflict: piece '" + editedPiece
                    + "' is not owned by structure '" + rootStructure + "'.");
        }
        Path absolutePiecePath = resolveOwnedResource(root, piecePath);
        IrisJigsawPiece piece;
        JsonObject pieceJson;
        try {
            JsonElement parsed = GSON.fromJson(
                    Files.readString(absolutePiecePath, StandardCharsets.UTF_8),
                    JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalArgumentException("Jigsaw piece is not a JSON object");
            }
            pieceJson = parsed.getAsJsonObject();
            piece = GSON.fromJson(pieceJson, IrisJigsawPiece.class);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw Studio cannot parse piece '" + editedPiece + "'", exception);
        }
        if (piece == null || piece.getObject() == null || piece.getObject().isBlank()) {
            throw new IOException("Jigsaw Studio piece '" + editedPiece + "' does not declare an object");
        }
        String objectKey = requireResourceKey(piece.getObject(), "object");
        String objectPath = "objects/" + objectKey + ".iob";
        if (!manifest.resourceHashes().containsKey(objectPath)) {
            throw new IOException("Jigsaw Studio ownership conflict: object '" + objectKey
                    + "' is not owned by structure '" + rootStructure + "'.");
        }
        piece.setConnectors(new KList<>(List.copyOf(connectors)));
        pieceJson.add("connectors", GSON.toJsonTree(piece.getConnectors()));

        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(ownershipKey)
                .source(manifest.source())
                .backend(manifest.backend())
                .capabilities(manifest.capabilities())
                .losses(manifest.losses())
                .capability(StructureCapability.CONNECTORS);
        if (hasBlockEntities) {
            bundle.capability(StructureCapability.BLOCK_ENTITIES);
        }
        for (String relativePath : manifest.resourceHashes().keySet()) {
            if (relativePath.equals(piecePath)) {
                bundle.textResource(relativePath, GSON.toJson(pieceJson) + "\n");
            } else if (relativePath.equals(objectPath)) {
                bundle.resource(relativePath, objectContent);
            } else {
                Path resource = resolveOwnedResource(root, relativePath);
                bundle.resource(relativePath, Files.readAllBytes(resource));
            }
        }
        return new Assembly(bundle.build(), objectKey, piece, StructureHash.sha256(manifestContent));
    }

    private static Set<String> resourcePaths(StructurePackageClosure closure) {
        Set<String> resources = new LinkedHashSet<>();
        addPaths(resources, "structures", closure.structures(), ".json");
        addPaths(resources, "jigsaw-pools", closure.pools(), ".json");
        addPaths(resources, "jigsaw-pieces", closure.pieces(), ".json");
        addPaths(resources, "objects", closure.objects(), ".iob");
        addPaths(resources, "loot", closure.loot(), ".json");
        return resources;
    }

    private static void addPaths(Set<String> resources, String folder, Set<String> keys, String extension) {
        for (String key : keys) {
            resources.add(folder + "/" + key + extension);
        }
    }

    private static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root)) {
            throw new IOException("Jigsaw Studio resource escapes its pack root: " + relativePath);
        }
        if (!Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Jigsaw Studio owned resource is missing or not a regular file: " + relativePath);
        }
        Path realRoot = root.toRealPath();
        Path realResource = resource.toRealPath();
        if (!realResource.startsWith(realRoot)) {
            throw new IOException("Jigsaw Studio owned resource escapes through a symbolic link: " + relativePath);
        }
        return resource;
    }

    private static String requireResourceKey(String key, String kind) {
        String normalized = Objects.requireNonNull(key, "Jigsaw Studio " + kind + " key").trim();
        StructureResourceBundle.validateRelativePath(normalized);
        return normalized;
    }

    record Assembly(
            StructureResourceBundle bundle,
            String objectKey,
            IrisJigsawPiece piece,
            String expectedManifestHash
    ) {
        Assembly {
            Objects.requireNonNull(bundle, "Jigsaw Studio resource bundle");
            Objects.requireNonNull(objectKey, "Jigsaw Studio object key");
            Objects.requireNonNull(piece, "Jigsaw Studio piece");
            Objects.requireNonNull(expectedManifestHash, "Jigsaw Studio expected manifest hash");
        }
    }
}
