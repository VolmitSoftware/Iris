package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureHash;
import art.arcane.iris.structure.authoring.StructureOwnershipManifest;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteOptions;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.generation.geometry.IrisBlockVector;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JigsawStudioStructureEditor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JigsawStudioStructureEditor() {
    }

    public static StructureWriteResult updateCellSize(
            Path packRoot,
            String structureKey,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        requireOwnedObjectsFit(root, snapshot.manifest(), dimensions);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateCellSize(content, dimensions, structurePath));
    }

    public static StructureWriteResult updateLimits(
            Path packRoot,
            String structureKey,
            int maxDepth,
            int maxSizeChunks
    ) throws IOException {
        if (maxDepth < 1 || maxDepth > 30) {
            throw new IllegalArgumentException("Jigsaw max depth must be between 1 and 30");
        }
        if (maxSizeChunks < 1 || maxSizeChunks > 32) {
            throw new IllegalArgumentException("Jigsaw maximum size must be between 1 and 32 chunks");
        }
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateLimits(
                        content, maxDepth, maxSizeChunks, structurePath));
    }

    public static StructureWriteResult updateThemeSets(
            Path packRoot,
            String structureKey,
            List<IrisJigsawThemeSet> themeSets
    ) throws IOException {
        List<IrisJigsawThemeSet> normalizedThemeSets = normalizeThemeSets(themeSets);
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateThemeSets(
                        content,
                        normalizedThemeSets,
                        structurePath));
    }

    public static StructureWriteResult updateRequireCaps(
            Path packRoot,
            String structureKey,
            boolean requireCaps
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateRequireCaps(
                        content,
                        requireCaps,
                        structurePath));
    }

    public static StructureWriteResult updateWorkcellEnabled(
            Path packRoot,
            String structureKey,
            JigsawPlanarArchetype archetype,
            boolean enabled
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        JigsawPlanarArchetype target = Objects.requireNonNull(archetype, "Planar Jigsaw Studio archetype");
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateWorkcell(
                        content,
                        target,
                        null,
                        enabled,
                        null,
                        structurePath));
    }

    public static StructureWriteResult updateWorkcellDimensions(
            Path packRoot,
            String structureKey,
            JigsawPlanarArchetype archetype,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        return JigsawStudioGraphEditor.updatePlanarWorkcellCapacity(
                packRoot,
                structureKey,
                archetype,
                dimensions).writeResult();
    }

    public static StructureWriteResult updateWorkcellDisplayName(
            Path packRoot,
            String structureKey,
            JigsawPlanarArchetype archetype,
            String displayName
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        JigsawPlanarArchetype target = Objects.requireNonNull(archetype, "Planar Jigsaw Studio archetype");
        String normalizedName = JigsawStudioGraphEditor.normalizeDisplayName(displayName);
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateWorkcell(
                        content,
                        target,
                        null,
                        null,
                        normalizedName,
                        structurePath));
    }

    public static StructureWriteResult updateSpatialWorkcellDisplayName(
            Path packRoot,
            String structureKey,
            String displayName
    ) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "Jigsaw Studio pack root")
                .toAbsolutePath().normalize();
        String normalizedName = JigsawStudioGraphEditor.normalizeDisplayName(displayName);
        ManifestSnapshot snapshot = loadManifest(root, structureKey);
        return updateOwnedStructure(
                root,
                structureKey,
                snapshot,
                (content, structurePath) -> updateSpatialWorkcellDisplayName(
                        content,
                        normalizedName,
                        structurePath));
    }

    private static ManifestSnapshot loadManifest(Path root, String structureKey) throws IOException {
        StructureKey ownershipKey = StructureKey.parse(structureKey, "iris");
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        Path manifestPath = writer.ownershipManifestPath(ownershipKey);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("This graph is read-only because it is not Studio-owned; create a new Jigsaw Studio project before editing rules");
        }
        byte[] manifestContent = Files.readAllBytes(manifestPath);
        return new ManifestSnapshot(
                JigsawStudioAuthoringAccess.requireEditable(
                        StructureOwnershipManifest.fromJson(manifestContent)),
                StructureHash.sha256(manifestContent));
    }

    private static StructureWriteResult updateOwnedStructure(
            Path root,
            String structureKey,
            ManifestSnapshot snapshot,
            StructureContentEditor editor
    ) throws IOException {
        StructureTransactionWriter writer = new StructureTransactionWriter(root);
        StructureOwnershipManifest manifest = snapshot.manifest();
        String normalizedStructure = JigsawStudioProjectCreator.Options.requireResourceKey(structureKey);
        String targetResource = "structures/" + normalizedStructure + ".json";
        if (!manifest.resourceHashes().containsKey(targetResource)) {
            throw new IOException("The owned graph manifest does not include " + targetResource);
        }

        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(manifest.structure())
                .source(manifest.source())
                .backend(manifest.backend())
                .capabilities(manifest.capabilities())
                .losses(manifest.losses());
        for (Map.Entry<String, String> resource : manifest.resourceHashes().entrySet()) {
            Path resourcePath = resolveOwnedResource(root, resource.getKey());
            byte[] content = Files.readAllBytes(resourcePath);
            if (resource.getKey().equals(targetResource)) {
                content = editor.edit(content, resourcePath);
            }
            bundle.resource(resource.getKey(), content);
        }
        StructureResourceBundle updatedBundle = bundle.build();
        StructureResourceBundleGraphCompiler.requireViable(updatedBundle);
        StructureWriteResult result = writer.write(
                updatedBundle,
                StructureWriteOptions.overwriteExpected(snapshot.expectedManifestHash()));
        if (!result.successful()) {
            String conflict = result.conflicts().isEmpty()
                    ? result.status().name()
                    : result.conflicts().getFirst().relativePath() + ": "
                    + result.conflicts().getFirst().reason();
            throw new IOException("Atomic graph structure update was rejected: " + conflict);
        }
        return result;
    }

    private static Path resolveOwnedResource(Path root, String relativePath) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path resource = root.resolve(relativePath).normalize();
        if (!resource.startsWith(root) || !Files.isRegularFile(resource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned graph resource is missing or unsafe: " + relativePath);
        }
        return resource;
    }

    private static void requireOwnedObjectsFit(
            Path root,
            StructureOwnershipManifest manifest,
            JigsawStudioCellDimensions dimensions
    ) throws IOException {
        for (String relativePath : manifest.resourceHashes().keySet()) {
            if (!relativePath.startsWith("objects/") || !relativePath.endsWith(".iob")) {
                continue;
            }
            Path objectPath = resolveOwnedResource(root, relativePath);
            IrisBlockVector size = IrisObject.sampleSize(objectPath.toFile());
            if (size.getBlockX() > dimensions.width()
                    || size.getBlockY() > dimensions.height()
                    || size.getBlockZ() > dimensions.depth()) {
                throw new IOException("Owned object '" + relativePath + "' is "
                        + size.getBlockX() + "x" + size.getBlockY() + "x" + size.getBlockZ()
                        + " and does not fit the requested cell bounds");
            }
        }
    }

    private static byte[] updateCellSize(
            byte[] content,
            JigsawStudioCellDimensions dimensions,
            Path structurePath
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structurePath);
        }
        JsonObject cellSize = new JsonObject();
        JsonObject structure = parsed.getAsJsonObject();
        if (structure.has("mode")
                && "PLANAR_JIGSAW".equals(structure.get("mode").getAsString())
                && dimensions.width() != dimensions.depth()) {
            throw new IOException("Planar Jigsaw Studio cells require equal width and depth");
        }
        cellSize.addProperty("x", dimensions.width());
        cellSize.addProperty("y", dimensions.height());
        cellSize.addProperty("z", dimensions.depth());
        structure.add("cellSize", cellSize);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] updateLimits(
            byte[] content,
            int maxDepth,
            int maxSizeChunks,
            Path structurePath
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structurePath);
        }
        JsonObject structure = parsed.getAsJsonObject();
        structure.addProperty("maxDepth", maxDepth);
        structure.addProperty("maxSizeChunks", maxSizeChunks);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static List<IrisJigsawThemeSet> normalizeThemeSets(List<IrisJigsawThemeSet> themeSets) {
        Objects.requireNonNull(themeSets, "Jigsaw Studio theme sets");
        List<IrisJigsawThemeSet> normalized = new ArrayList<>(themeSets.size());
        Set<String> keys = new LinkedHashSet<>();
        for (IrisJigsawThemeSet themeSet : themeSets) {
            IrisJigsawThemeSet source = Objects.requireNonNull(themeSet, "Jigsaw Studio theme set");
            String key = source.getKey() == null ? "" : source.getKey().trim();
            if (key.isEmpty() || !key.equals(source.getKey())) {
                throw new IllegalArgumentException(
                        "Jigsaw Studio theme keys must be non-blank and whitespace-normalized");
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate Jigsaw Studio theme key '" + key + "'");
            }
            if (source.getWeight() < 1) {
                throw new IllegalArgumentException("Jigsaw Studio theme weights must be positive");
            }
            normalized.add(new IrisJigsawThemeSet(key, source.getWeight()));
        }
        return List.copyOf(normalized);
    }

    private static byte[] updateThemeSets(
            byte[] content,
            List<IrisJigsawThemeSet> themeSets,
            Path structurePath
    ) throws IOException {
        JsonObject structure = parseStructure(content, structurePath);
        JsonArray values = new JsonArray();
        for (IrisJigsawThemeSet themeSet : themeSets) {
            JsonObject value = new JsonObject();
            value.addProperty("key", themeSet.getKey());
            value.addProperty("weight", themeSet.getWeight());
            values.add(value);
        }
        structure.add("themeSets", values);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] updateRequireCaps(
            byte[] content,
            boolean requireCaps,
            Path structurePath
    ) throws IOException {
        JsonObject structure = parseStructure(content, structurePath);
        structure.addProperty("requireCaps", requireCaps);
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] updateSpatialWorkcellDisplayName(
            byte[] content,
            String displayName,
            Path structurePath
    ) throws IOException {
        JsonObject structure = parseStructure(content, structurePath);
        IrisStructure model;
        try {
            model = GSON.fromJson(structure, IrisStructure.class);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw structure is not valid JSON: " + structurePath, exception);
        }
        if (model == null || model.resolvedMode() != IrisJigsawMode.SPATIAL_JIGSAW) {
            throw new IOException("Spatial workcell labels require a spatial Jigsaw Studio structure");
        }
        if (displayName.isEmpty()) {
            structure.remove("spatialWorkcellDisplayName");
        } else {
            structure.addProperty("spatialWorkcellDisplayName", displayName);
        }
        return (GSON.toJson(structure) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static JsonObject parseStructure(byte[] content, Path structurePath) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structurePath);
        }
        return parsed.getAsJsonObject();
    }

    static byte[] updateWorkcell(
            byte[] content,
            JigsawPlanarArchetype archetype,
            JigsawStudioCellDimensions dimensions,
            Boolean enabled,
            String displayName,
            Path structurePath
    ) throws IOException {
        JsonElement parsed = JsonParser.parseString(new String(content, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IOException("Jigsaw structure is not a JSON object: " + structurePath);
        }
        JsonObject structureJson = parsed.getAsJsonObject();
        IrisStructure structure;
        try {
            structure = GSON.fromJson(structureJson, IrisStructure.class);
        } catch (RuntimeException exception) {
            throw new IOException("Jigsaw structure is not valid JSON: " + structurePath, exception);
        }
        if (structure == null || structure.resolvedMode() != IrisJigsawMode.PLANAR_JIGSAW) {
            throw new IOException("Workcell settings require a planar Jigsaw Studio structure");
        }
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> resolved;
        try {
            resolved = PlanarJigsawWorkcellResolver.resolve(structure);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Planar workcell configuration is invalid: " + exception.getMessage(), exception);
        }
        JsonArray workcells = new JsonArray();
        for (JigsawPlanarArchetype current : JigsawPlanarArchetype.values()) {
            PlanarJigsawWorkcellResolver.ResolvedWorkcell source = resolved.get(current.modelArchetype());
            JsonObject workcell = new JsonObject();
            workcell.addProperty("archetype", current.name());
            String resolvedDisplayName = current == archetype && displayName != null
                    ? displayName : source.displayName();
            if (!resolvedDisplayName.isEmpty()) {
                workcell.addProperty("displayName", resolvedDisplayName);
            }
            workcell.addProperty("width", current == archetype && dimensions != null
                    ? dimensions.width() : source.width());
            workcell.addProperty("height", current == archetype && dimensions != null
                    ? dimensions.height() : source.height());
            workcell.addProperty("depth", current == archetype && dimensions != null
                    ? dimensions.depth() : source.depth());
            workcell.addProperty("enabled", current == archetype && enabled != null
                    ? enabled : source.enabled());
            workcells.add(workcell);
        }
        structureJson.add("planarWorkcells", workcells);
        return (GSON.toJson(structureJson) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface StructureContentEditor {
        byte[] edit(byte[] content, Path structurePath) throws IOException;
    }

    private record ManifestSnapshot(
            StructureOwnershipManifest manifest,
            String expectedManifestHash
    ) {
    }
}
