package art.arcane.iris.core.runtime.jigsaw;

import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureHash;
import art.arcane.iris.core.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteOptions;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class JigsawStudioPoolEditor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JigsawStudioPoolEditor() {
    }

    public static WeightUpdate updateWeight(
            Path packRoot,
            String structureKey,
            String poolKey,
            String pieceKey,
            int weight
    ) throws IOException {
        if (weight < 1) {
            throw new IllegalArgumentException("Jigsaw variant weight must be positive");
        }
        PoolUpdate update = updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateWeight(content, pieceKey, weight, poolPath));
        return new WeightUpdate(
                update.changed(),
                update.changedEntries(),
                update.poolPath(),
                update.writeResult());
    }

    public static WeightUpdate updateWeightAtIndex(
            Path packRoot,
            String structureKey,
            String poolKey,
            int entryIndex,
            String expectedPieceKey,
            int weight
    ) throws IOException {
        if (weight < 1) {
            throw new IllegalArgumentException("Jigsaw variant weight must be positive");
        }
        if (entryIndex < 0) {
            throw new IllegalArgumentException("Jigsaw pool entry index cannot be negative");
        }
        PoolUpdate update = updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateWeightAtIndex(
                        content,
                        entryIndex,
                        expectedPieceKey,
                        weight,
                        poolPath));
        return new WeightUpdate(
                update.changed(),
                update.changedEntries(),
                update.poolPath(),
                update.writeResult());
    }

    public static ChanceUpdate updateChanceAtIndex(
            Path packRoot,
            String structureKey,
            String poolKey,
            int entryIndex,
            String expectedPieceKey,
            double chance
    ) throws IOException {
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D) {
            throw new IllegalArgumentException("Jigsaw variant chance must be finite and within 0 and 1");
        }
        if (entryIndex < 0) {
            throw new IllegalArgumentException("Jigsaw pool entry index cannot be negative");
        }
        PoolUpdate update = updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateChanceAtIndex(
                        content,
                        entryIndex,
                        expectedPieceKey,
                        chance,
                        poolPath));
        return new ChanceUpdate(
                update.changed(),
                update.changedEntries(),
                update.poolPath(),
                update.writeResult());
    }

    public static PoolUpdate addPiece(
            Path packRoot,
            String structureKey,
            String poolKey,
            String pieceKey,
            int weight
    ) throws IOException {
        if (weight < 1) {
            throw new IllegalArgumentException("Jigsaw piece weight must be positive");
        }
        return updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateAdd(content, pieceKey, weight, poolPath));
    }

    public static PoolUpdate removePiece(
            Path packRoot,
            String structureKey,
            String poolKey,
            String pieceKey
    ) throws IOException {
        return updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateRemove(content, pieceKey, poolPath));
    }

    public static PoolUpdate removeEntry(
            Path packRoot,
            String structureKey,
            String poolKey,
            int entryIndex,
            String expectedPieceKey
    ) throws IOException {
        if (entryIndex < 0) {
            throw new IllegalArgumentException("Jigsaw pool entry index cannot be negative");
        }
        return updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateRemoveAtIndex(
                        content,
                        entryIndex,
                        expectedPieceKey,
                        poolPath));
    }

    public static PoolUpdate updateFallback(
            Path packRoot,
            String structureKey,
            String poolKey,
            String fallbackPoolKey
    ) throws IOException {
        String fallback = fallbackPoolKey == null ? "" : fallbackPoolKey.trim();
        return updateOwnedPool(
                packRoot,
                structureKey,
                poolKey,
                (content, poolPath) -> mutateFallback(content, fallback, poolPath));
    }

    private static PoolUpdate updateOwnedPool(
            Path packRoot,
            String structureKey,
            String poolKey,
            PoolContentEditor editor
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        StructureKey ownershipKey = StructureKey.parse(structureKey, "iris");
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        Path manifestPath = writer.ownershipManifestPath(ownershipKey);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This graph is read-only because it is not Studio-owned; create a new Jigsaw Studio project before editing pools");
        }
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        String expectedManifestHash = StructureHash.sha256(manifestContent);
        StructureOwnershipManifest manifest = JigsawStudioAuthoringAccess.requireEditable(
                StructureOwnershipManifest.fromJson(manifestContent));
        String normalizedPool = JigsawStudioProjectCreator.Options.requireResourceKey(poolKey);
        String targetResource = "jigsaw-pools/" + normalizedPool + ".json";
        Path targetPath = root.resolve(targetResource).normalize();
        if (!manifest.resourceHashes().containsKey(targetResource)) {
            return new PoolUpdate(false, 0, targetPath, null);
        }

        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(manifest.structure())
                .source(manifest.source())
                .backend(manifest.backend())
                .capabilities(manifest.capabilities())
                .losses(manifest.losses());
        int changedEntries = 0;
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            Path resourcePath = resolveOwnedResource(root, resource.getKey());
            byte[] content = Files.readAllBytes(resourcePath);
            if (resource.getKey().equals(targetResource)) {
                PoolMutation mutation = editor.edit(content, resourcePath);
                changedEntries = mutation.changedEntries();
                content = mutation.content();
            }
            bundle.resource(resource.getKey(), content);
        }
        if (changedEntries == 0) {
            return new PoolUpdate(false, 0, targetPath, null);
        }
        StructureResourceBundle updatedBundle = bundle.build();
        StructureResourceBundleGraphCompiler.requireViable(updatedBundle);
        StructureWriteResult writeResult = writer.write(
                updatedBundle,
                StructureWriteOptions.overwriteExpected(expectedManifestHash));
        if (!writeResult.successful()) {
            String conflict = writeResult.conflicts().isEmpty()
                    ? writeResult.status().name()
                    : writeResult.conflicts().getFirst().relativePath() + ": "
                    + writeResult.conflicts().getFirst().reason();
            throw new IOException("Atomic graph update was rejected: " + conflict);
        }
        return new PoolUpdate(true, changedEntries, targetPath, writeResult);
    }

    private static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root) || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned graph resource is missing or unsafe: " + relativePath);
        }
        return resource;
    }

    private static PoolMutation mutateWeight(
            byte[] content,
            String pieceKey,
            int weight,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        int changedEntries = 0;
        for (JsonElement element : pieces) {
            if (!isPiece(element, pieceKey)) {
                continue;
            }
            element.getAsJsonObject().addProperty("weight", weight);
            changedEntries++;
        }
        return mutation(root, changedEntries);
    }

    private static PoolMutation mutateWeightAtIndex(
            byte[] content,
            int entryIndex,
            String expectedPieceKey,
            int weight,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        JsonObject entry = requirePieceEntry(pieces, entryIndex, expectedPieceKey, poolPath);
        int currentWeight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
        if (currentWeight == weight) {
            return mutation(root, 0);
        }
        entry.addProperty("weight", weight);
        return mutation(root, 1);
    }

    private static PoolMutation mutateChanceAtIndex(
            byte[] content,
            int entryIndex,
            String expectedPieceKey,
            double chance,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        JsonObject entry = requirePieceEntry(pieces, entryIndex, expectedPieceKey, poolPath);
        double currentChance = entry.has("chance") ? entry.get("chance").getAsDouble() : 1D;
        if (Double.compare(currentChance, chance) == 0) {
            return mutation(root, 0);
        }
        entry.addProperty("chance", chance);
        return mutation(root, 1);
    }

    private static PoolMutation mutateAdd(
            byte[] content,
            String pieceKey,
            int weight,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        for (JsonElement element : pieces) {
            if (isPiece(element, pieceKey)) {
                return mutation(root, 0);
            }
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("piece", pieceKey);
        entry.addProperty("weight", weight);
        pieces.add(entry);
        return mutation(root, 1);
    }

    private static PoolMutation mutateRemove(
            byte[] content,
            String pieceKey,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        int changedEntries = 0;
        for (int index = pieces.size() - 1; index >= 0; index--) {
            if (isPiece(pieces.get(index), pieceKey)) {
                pieces.remove(index);
                changedEntries++;
            }
        }
        return mutation(root, changedEntries);
    }

    private static PoolMutation mutateRemoveAtIndex(
            byte[] content,
            int entryIndex,
            String expectedPieceKey,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        JsonArray pieces = root.getAsJsonArray("pieces");
        requirePieceEntry(pieces, entryIndex, expectedPieceKey, poolPath);
        pieces.remove(entryIndex);
        return mutation(root, 1);
    }

    private static PoolMutation mutateFallback(
            byte[] content,
            String fallbackPoolKey,
            Path poolPath
    ) throws IOException {
        JsonObject root = parsePool(content, poolPath);
        String existing = root.has("fallback") && !root.get("fallback").isJsonNull()
                ? root.get("fallback").getAsString().trim()
                : "";
        if (existing.equals(fallbackPoolKey)) {
            return mutation(root, 0);
        }
        root.addProperty("fallback", fallbackPoolKey);
        return mutation(root, 1);
    }

    private static JsonObject parsePool(byte[] content, Path poolPath) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw pool is not a JSON object: " + poolPath);
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("pieces") || !root.get("pieces").isJsonArray()) {
            throw new IOException("Jigsaw pool does not declare a pieces array: " + poolPath);
        }
        return root;
    }

    private static boolean isPiece(JsonElement element, String pieceKey) {
        return element.isJsonObject()
                && element.getAsJsonObject().has("piece")
                && pieceKey.equals(element.getAsJsonObject().get("piece").getAsString());
    }

    private static JsonObject requirePieceEntry(
            JsonArray pieces,
            int entryIndex,
            String expectedPieceKey,
            Path poolPath
    ) throws IOException {
        if (entryIndex >= pieces.size()) {
            throw new IOException("Jigsaw pool entry " + entryIndex + " no longer exists in " + poolPath);
        }
        JsonElement element = pieces.get(entryIndex);
        if (!isPiece(element, expectedPieceKey)) {
            throw new IOException("Jigsaw pool entry " + entryIndex + " changed before the update in "
                    + poolPath);
        }
        return element.getAsJsonObject();
    }

    private static PoolMutation mutation(JsonObject root, int changedEntries) {
        return new PoolMutation(
                (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8),
                changedEntries);
    }

    public record WeightUpdate(
            boolean changed,
            int changedEntries,
            Path poolPath,
            StructureWriteResult writeResult
    ) {
    }

    public record ChanceUpdate(
            boolean changed,
            int changedEntries,
            Path poolPath,
            StructureWriteResult writeResult
    ) {
    }

    public record PoolUpdate(
            boolean changed,
            int changedEntries,
            Path poolPath,
            StructureWriteResult writeResult
    ) {
    }

    @FunctionalInterface
    private interface PoolContentEditor {
        PoolMutation edit(byte[] content, Path poolPath) throws IOException;
    }

    private record PoolMutation(byte[] content, int changedEntries) {
    }
}
