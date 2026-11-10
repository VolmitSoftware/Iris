/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.structure;

import art.arcane.iris.core.structure.authoring.IrisStructureBundleFactory;
import art.arcane.iris.core.structure.authoring.StructureBackend;
import art.arcane.iris.core.structure.authoring.StructureBundleNames;
import art.arcane.iris.core.structure.authoring.StructureCapability;
import art.arcane.iris.core.structure.authoring.StructureKey;
import art.arcane.iris.core.structure.authoring.StructureLoss;
import art.arcane.iris.core.structure.authoring.StructureResourceBundle;
import art.arcane.iris.core.structure.authoring.StructureSource;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.core.structure.authoring.StructureWriteMode;
import art.arcane.iris.core.structure.authoring.StructureWriteResult;
import art.arcane.iris.engine.framework.structure.StructureGraphValidationException;
import art.arcane.iris.engine.framework.structure.StructureResourceBundleGraphCompiler;
import com.volmit.object.GeneratedObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplateBlock;
import com.volmit.tooling.ToolPack;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StructureImporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STRUCTURE_VOID = "minecraft:structure_void";
    private static final String JIGSAW = "minecraft:jigsaw";
    private static final String STRUCTURE_BLOCK = "minecraft:structure_block";
    private static final Set<String> AIR_BLOCKS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");
    private static final String PLATFORM_NAMESPACE = "minecraft";

    public enum Mode {
        OVERWRITE,
        ADD_ONLY
    }

    enum Ownership {
        EDITABLE,
        MANAGED_DATAPACK;

        StructureWriteResult write(
                ToolPack pack,
                StructureResourceBundle bundle,
                StructureWriteMode mode
        ) throws IOException {
            return pack.edit(root -> {
                StructureTransactionWriter writer = new StructureTransactionWriter(root);
                return this == MANAGED_DATAPACK
                        ? writer.writeManagedDatapack(bundle, mode)
                        : writer.write(bundle, mode);
            });
        }
    }

    public record Result(
            boolean success,
            String message,
            int blocks,
            List<StructureLoss> losses,
            boolean retryableFailure
    ) {
        public Result(boolean success, String message, int blocks, List<StructureLoss> losses) {
            this(success, message, blocks, losses, false);
        }

        public Result {
            losses = List.copyOf(losses);
        }
    }

    record CapturedStructure(
            GeneratedObject object,
            int blocks,
            int nonAirBlocks,
            int tiles,
            int width,
            int height,
            int depth,
            int structureMarkers,
            List<StructureCapability> capabilities,
            List<StructureLoss> losses
    ) {
        CapturedStructure {
            Objects.requireNonNull(object);
            capabilities = List.copyOf(capabilities);
            losses = List.copyOf(losses);
        }
    }

    private StructureImporter() {
    }

    public static Mode parseMode(String s) {
        if (s == null || s.isBlank()) {
            return Mode.ADD_ONLY;
        }
        return switch (s.toLowerCase().replace('-', '_')) {
            case "add_only", "addonly", "add" -> Mode.ADD_ONLY;
            case "overwrite", "replace" -> Mode.OVERWRITE;
            default -> throw new IllegalArgumentException("Unknown structure import mode: " + s);
        };
    }

    public static String deriveName(ResourceId key) {
        return (key.namespace() + "_" + key.path()).replace('/', '_').replace(':', '_');
    }

    public static String deriveName(String key) {
        return StructureBundleNames.structureName(key);
    }

    public static Result importStructure(ToolPack pack, PlatformStructures platform, ResourceId key, String name, Mode mode) {
        return importStructure(pack, platform, key, name, mode, false, Ownership.EDITABLE);
    }

    public static Result importStructure(ToolPack pack, PlatformStructures platform, ResourceId key, String name, Mode mode,
                                         boolean objectOnly) {
        return importStructure(pack, platform, key, name, mode, objectOnly, Ownership.EDITABLE);
    }

    static Result importStructure(
            ToolPack pack,
            PlatformStructures platform,
            ResourceId key,
            String name,
            Mode mode,
            boolean objectOnly,
            Ownership ownership
    ) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        Optional<StructureTemplate> loaded;
        try {
            loaded = platform.template(key);
        } catch (Throwable e) {
            pack.log().error("Could not load structure template " + key, e);
            return new Result(false, "Failed to load structure " + key + ": " + e.getMessage(), 0, List.of(), true);
        }
        if (loaded.isEmpty()) {
            return new Result(false, "No loadable structure NBT for key " + key + " (jigsaw structures must be imported by their piece keys)", 0, List.of());
        }

        CapturedStructure captured;
        try {
            captured = captureStructure(loaded.get());
        } catch (Throwable e) {
            pack.log().error("Could not capture structure template " + key, e);
            return new Result(false, "Failed to capture structure " + key + ": " + e.getMessage(), 0, List.of(), true);
        }

        GeneratedObject object = captured.object();
        int count = captured.blocks();
        int tiles = captured.tiles();
        int w = captured.width();
        int h = captured.height();
        int d = captured.depth();
        List<StructureLoss> losses = new ArrayList<>(captured.losses());
        if (captured.structureMarkers() > 0) {
            losses.add(StructureLoss.warning(
                    StructureCapability.CONNECTORS,
                    "connectors_not_imported",
                    captured.structureMarkers() + " jigsaw or structure marker(s) were flattened without importing their structure graph."));
        }
        String writeNote = "";
        try {
            StructureKey sourceKey = StructureKey.parse(key.toString());
            StructureKey bundleKey = new StructureKey("iris", name);
            StructureSource.Kind sourceKind = key.namespace().equals(PLATFORM_NAMESPACE)
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            StructureSource source = StructureSource.of(sourceKind, sourceKey);
            IrisStructureBundleFactory.SinglePieceOptions options = new IrisStructureBundleFactory.SinglePieceOptions(
                    bundleKey, source, name, object, pack.objects(), Math.max(w, d), "CENTER_HEIGHT", objectOnly,
                    captured.capabilities(), losses);
            StructureResourceBundle bundle = IrisStructureBundleFactory.singlePiece(options);
            if (!objectOnly) {
                StructureResourceBundleGraphCompiler.requireViable(bundle);
            }
            StructureWriteMode writeMode = activeMode == Mode.ADD_ONLY
                    ? StructureWriteMode.ADD_ONLY : StructureWriteMode.OVERWRITE;
            StructureWriteResult writeResult = ownership.write(pack, bundle, writeMode);
            reportWriteFailure(pack, writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, activeMode, writeResult), count, losses,
                        writeResult.failure().isPresent());
            }
            writeNote = writeResultNote(writeResult);
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing import for '" + name + "': " + e.getMessage(), count, losses);
        } catch (Throwable e) {
            pack.log().error("Could not write the import of " + key, e);
            return new Result(false, "Failed writing import for '" + name + "': " + e.getMessage(), count, losses,
                    true);
        }

        String lossSummary = losses.isEmpty() ? "" : ", " + losses.size() + " fidelity warning(s) recorded";
        return new Result(true, "Imported " + key + " as '" + name + "' (" + count + " blocks, " + tiles
                + " tiles, " + w + "x" + h + "x" + d + lossSummary + ")" + writeNote, count, losses);
    }

    static CapturedStructure captureStructure(StructureTemplate template) {
        StructureTemplate activeTemplate = Objects.requireNonNull(template);
        if (activeTemplate.paletteCount() < 1) {
            throw new IllegalArgumentException("Structure has no palettes");
        }

        BlockPosition size = activeTemplate.size();
        int w = Math.max(1, size.x());
        int h = Math.max(1, size.y());
        int d = Math.max(1, size.z());
        DeclaredObjects.Builder object = DeclaredObjects.builder(w, h, d);
        int count = 0;
        int nonAirBlocks = 0;
        int tiles = 0;
        int structureMarkers = 0;
        int invalidFinalStates = 0;
        for (TemplateBlock block : activeTemplate.blocks()) {
            int x = block.position().x();
            int y = block.position().y();
            int z = block.position().z();
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                continue;
            }
            String material = block.state().material().toString();
            if (material.equals(STRUCTURE_VOID)) {
                continue;
            }
            boolean structural = material.equals(JIGSAW) || material.equals(STRUCTURE_BLOCK);
            if (structural) {
                structureMarkers++;
            }
            BlockState placed = block.state();
            if (material.equals(JIGSAW)) {
                FinalStateResult finalState = readJigsawFinalState(block);
                if (finalState.invalidSourceState()) {
                    invalidFinalStates++;
                }
                if (finalState.state() == null || isAir(finalState.state())) {
                    continue;
                }
                placed = finalState.state();
            } else if (material.equals(STRUCTURE_BLOCK)) {
                continue;
            }
            object.state(x, y, z, placed);
            count++;
            if (!isAir(placed)) {
                nonAirBlocks++;
            }
            if (!structural && block.blockEntity().isPresent()) {
                object.blockEntity(x, y, z, block.blockEntity().get());
                tiles++;
            }
        }

        List<StructureCapability> capabilities = new ArrayList<>();
        capabilities.add(StructureCapability.BLOCKS);
        if (tiles > 0) {
            capabilities.add(StructureCapability.BLOCK_ENTITIES);
        }
        return new CapturedStructure(
                object.build(),
                count,
                nonAirBlocks,
                tiles,
                w,
                h,
                d,
                structureMarkers,
                capabilities,
                importLosses(activeTemplate, structureMarkers, invalidFinalStates)
        );
    }

    private static boolean isAir(BlockState state) {
        return AIR_BLOCKS.contains(state.material().toString());
    }

    private static List<StructureLoss> importLosses(
            StructureTemplate structure,
            int structureMarkers,
            int invalidFinalStates
    ) {
        List<StructureLoss> losses = new ArrayList<>();
        if (structure.paletteCount() > 1) {
            losses.add(StructureLoss.warning(
                    StructureCapability.BLOCKS,
                    "palette_variants_not_imported",
                    "Only palette 0 was converted; " + (structure.paletteCount() - 1) + " additional palette(s) remain native-only."));
        }
        if (structure.entityCount() > 0) {
            losses.add(StructureLoss.warning(
                    StructureCapability.ENTITIES,
                    "entities_not_imported",
                    structure.entityCount() + " structure entit" + (structure.entityCount() == 1 ? "y was" : "ies were")
                            + " not converted into the Iris snapshot."));
        }
        if (structureMarkers > 0) {
            losses.add(StructureLoss.warning(
                    StructureCapability.BLOCKS,
                    "structure_markers_resolved",
                    structureMarkers + " jigsaw or structure marker(s) were resolved to final blocks or omitted from the Iris snapshot."));
        }
        if (invalidFinalStates > 0) {
            losses.add(StructureLoss.warning(
                    StructureCapability.BLOCKS,
                    "invalid_jigsaw_final_state",
                    invalidFinalStates + " jigsaw final-state value(s) were invalid and omitted from the Iris snapshot."));
        }
        return losses;
    }

    private static void reportWriteFailure(ToolPack pack, StructureWriteResult result) {
        result.failure().ifPresent((Throwable failure) -> pack.log().error("Structure write failed", failure));
    }

    private static String writeResultNote(StructureWriteResult result) {
        return result.status() == StructureWriteResult.Status.COMMITTED_CLEANUP_REQUIRED
                ? " (committed; staging cleanup is required, see console)" : "";
    }

    private static String writeFailureMessage(String name, Mode mode, StructureWriteResult result) {
        if (result.status() == StructureWriteResult.Status.ADD_ONLY_CONFLICT) {
            return "Skipped (add-only): '" + name + "' already exists";
        }
        if (!result.conflicts().isEmpty()) {
            StructureWriteResult.Conflict conflict = result.conflicts().getFirst();
            return "Import conflict for '" + name + "': " + conflict.relativePath() + " is "
                    + conflict.reason().name().toLowerCase() + ". Existing authored files were preserved.";
        }
        String failure = result.failure().map(Throwable::getMessage).orElse(result.status().name());
        return "Failed writing import for '" + name + "' in " + mode.name().toLowerCase() + " mode: " + failure;
    }

    private static FinalStateResult readJigsawFinalState(TemplateBlock block) {
        if (block.jigsawFinalState().isEmpty() || block.jigsawFinalState().get().isBlank()) {
            return new FinalStateResult(null, false);
        }
        try {
            return new FinalStateResult(BlockState.parse(withNamespace(normalizeJigsawFinalState(block.jigsawFinalState().get()))), false);
        } catch (IllegalArgumentException e) {
            return new FinalStateResult(null, true);
        }
    }

    private static String withNamespace(String blockState) {
        int propertiesStart = blockState.indexOf('[');
        String blockId = propertiesStart < 0 ? blockState : blockState.substring(0, propertiesStart);
        return blockId.indexOf(':') < 0 ? PLATFORM_NAMESPACE + ":" + blockState : blockState;
    }

    static String normalizeJigsawFinalState(String finalState) {
        if (finalState == null) {
            return null;
        }
        int propertiesStart = finalState.indexOf('[');
        String blockId = propertiesStart < 0 ? finalState : finalState.substring(0, propertiesStart);
        if (blockId.equals("minecraft:chisled_polished_blackstone")) {
            finalState = "minecraft:chiseled_polished_blackstone"
                    + (propertiesStart < 0 ? "" : finalState.substring(propertiesStart));
            propertiesStart = finalState.indexOf('[');
        }
        if (propertiesStart < 0 || !finalState.substring(0, propertiesStart).endsWith("_slab")
                || finalState.contains("type=")) {
            return finalState;
        }
        return finalState.replaceAll("([\\[,])half=(top|bottom)(?=[,\\]])", "$1type=$2");
    }

    private record FinalStateResult(BlockState state, boolean invalidSourceState) {
    }

    public static Result importTemplateGroup(ToolPack pack, String groupName, String vanillaSource, String prefix, Mode mode) {
        try {
            return pack.edit(root -> importTemplateGroup(pack, root, groupName, vanillaSource, prefix, mode));
        } catch (Throwable e) {
            pack.log().error("Could not build group structure " + groupName, e);
            return new Result(false, "Failed writing group structure '" + groupName + "': " + e.getMessage(), 0,
                    List.of(), true);
        }
    }

    private static Result importTemplateGroup(ToolPack pack, Path root, String groupName, String vanillaSource,
                                              String prefix, Mode mode) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        String objectExtension = pack.objects().extension();
        File objectsRoot = new File(root.toFile(), "objects");
        File prefixDir = new File(objectsRoot, prefix);
        if (!prefixDir.isDirectory()) {
            return new Result(false, "No imported templates under objects/" + prefix + " for " + groupName, 0, List.of());
        }

        List<File> iobs = new ArrayList<>();
        collectObjects(prefixDir, objectExtension, iobs);
        if (iobs.isEmpty()) {
            return new Result(false, "No " + objectExtension + " templates under objects/" + prefix + " for " + groupName, 0, List.of());
        }
        iobs.sort(Comparator.comparing(File::getAbsolutePath));

        String rootPath = objectsRoot.getAbsolutePath() + File.separator;
        List<String> pieceNames = new ArrayList<>();
        int maxSpan = 1;
        for (File iob : iobs) {
            String rel = iob.getAbsolutePath().substring(rootPath.length()).replace(File.separatorChar, '/');
            if (rel.endsWith(objectExtension)) {
                rel = rel.substring(0, rel.length() - objectExtension.length());
            }
            pieceNames.add(rel);
            try {
                maxSpan = Math.max(maxSpan, readObjectSpan(iob));
            } catch (IOException e) {
                pack.log().error("Could not read the imported object " + rel, e);
                return new Result(false, "Cannot read imported object '" + rel + "': " + e.getMessage(), 0,
                        List.of(), true);
            }
        }

        for (String piece : pieceNames) {
            File pieceFile = new File(root.toFile(), "jigsaw-pieces/" + piece + ".json");
            if (!pieceFile.isFile()) {
                return new Result(false, "Missing imported jigsaw piece '" + piece
                        + "'; import the source templates before building group '" + groupName + "'", 0, List.of());
            }
        }

        StructureKey sourceKey = StructureKey.parse(vanillaSource, "minecraft");
        StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
        StructureResourceBundle bundle = StructureResourceBundle.builder(new StructureKey("iris", groupName))
                .source(StructureSource.of(sourceKind, sourceKey))
                .backend(StructureBackend.IRIS_ASSEMBLY)
                .capability(StructureCapability.BLOCKS)
                .capability(StructureCapability.IRIS_PLACEMENT)
                .textResource("jigsaw-pools/" + groupName + ".json", GSON.toJson(poolJsonMulti(pieceNames)))
                .textResource("structures/" + groupName + ".json",
                        GSON.toJson(structureJson(groupName, vanillaSource, maxSpan)))
                .build();
        StructureWriteMode writeMode = activeMode == Mode.ADD_ONLY
                ? StructureWriteMode.ADD_ONLY : StructureWriteMode.OVERWRITE;
        StructureWriteResult writeResult = new StructureTransactionWriter(root).write(bundle, writeMode);
        reportWriteFailure(pack, writeResult);
        if (!writeResult.successful()) {
            return new Result(false, writeFailureMessage(groupName, activeMode, writeResult), 0, List.of(),
                    writeResult.failure().isPresent());
        }
        return new Result(true, "Built structure '" + groupName + "' from " + pieceNames.size()
                + " template variants" + writeResultNote(writeResult), pieceNames.size(), List.of());
    }

    private static void collectObjects(File dir, String extension, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectObjects(f, extension, out);
            } else if (f.getName().endsWith(extension)) {
                out.add(f);
            }
        }
    }

    private static int readObjectSpan(File iob) throws IOException {
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(iob)))) {
            int w = din.readInt();
            int h = din.readInt();
            int d = din.readInt();
            if (w < 1 || h < 1 || d < 1) {
                throw new IOException("Invalid IOB dimensions " + w + "x" + h + "x" + d + " in " + iob);
            }
            return Math.max(w, d);
        }
    }

    private static Map<String, Object> poolJsonMulti(List<String> pieceNames) {
        List<Object> pieces = new ArrayList<>();
        for (String name : pieceNames) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("piece", name);
            entry.put("weight", 1);
            pieces.add(entry);
        }
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("pieces", pieces);
        return pool;
    }

    public static Result writeSinglePieceStructure(
            ToolPack pack,
            String name,
            String vanillaSource,
            GeneratedObject object,
            String placeMode,
            Mode mode
    ) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        BlockPosition size = DeclaredObjects.sizeOf(object);
        int span = Math.max(size.x(), size.z());
        try {
            StructureKey sourceKey = StructureKey.parse(vanillaSource, "minecraft");
            StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            IrisStructureBundleFactory.SinglePieceOptions options = new IrisStructureBundleFactory.SinglePieceOptions(
                    new StructureKey("iris", name),
                    StructureSource.of(sourceKind, sourceKey),
                    name,
                    object,
                    pack.objects(),
                    span,
                    placeMode,
                    false,
                    List.of(StructureCapability.BLOCKS, StructureCapability.IRIS_PLACEMENT),
                    List.of());
            StructureResourceBundle bundle = IrisStructureBundleFactory.singlePiece(options);
            StructureResourceBundleGraphCompiler.requireViable(bundle);
            StructureWriteMode writeMode = activeMode == Mode.ADD_ONLY
                    ? StructureWriteMode.ADD_ONLY : StructureWriteMode.OVERWRITE;
            StructureWriteResult writeResult = Ownership.EDITABLE.write(pack, bundle, writeMode);
            reportWriteFailure(pack, writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, activeMode, writeResult), 0, List.of(),
                        writeResult.failure().isPresent());
            }
            return new Result(true, "Captured '" + vanillaSource + "' as '" + name + "'"
                    + writeResultNote(writeResult), object.blocks().size(), List.of());
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing capture for '" + name + "': " + e.getMessage(), 0, List.of());
        } catch (Throwable e) {
            pack.log().error("Could not write the capture of " + vanillaSource, e);
            return new Result(false, "Failed writing capture for '" + name + "': " + e.getMessage(), 0, List.of(),
                    true);
        }
    }

    private static Map<String, Object> structureJson(String name, String source, int maxSpan) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("startPool", name);
        root.put("maxDepth", 1);
        root.put("maxSizeChunks", Math.max(1, (maxSpan / 16) + 1));
        root.put("placeMode", "CENTER_HEIGHT");
        root.put("vanillaSource", source);
        return root;
    }

}
