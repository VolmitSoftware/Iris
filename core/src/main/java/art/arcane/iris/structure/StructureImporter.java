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

package art.arcane.iris.structure;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.authoring.IrisStructureBundleFactory;
import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureCapability;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureLoss;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureSource;
import art.arcane.iris.structure.authoring.StructureTransactionWriter;
import art.arcane.iris.structure.authoring.StructureWriteMode;
import art.arcane.iris.structure.authoring.StructureWriteResult;
import art.arcane.iris.structure.graph.StructureGraphValidationException;
import art.arcane.iris.structure.graph.StructureResourceBundleGraphCompiler;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.block.LegacyTileData;
import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.structure.Palette;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StructureImporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Mode {
        OVERWRITE,
        ADD_ONLY
    }

    enum Ownership {
        EDITABLE,
        MANAGED_DATAPACK;

        StructureWriteResult write(
                IrisData data,
                StructureResourceBundle bundle,
                StructureWriteMode mode
        ) {
            StructureTransactionWriter writer = new StructureTransactionWriter(data.getDataFolder().toPath());
            return this == MANAGED_DATAPACK
                    ? writer.writeManagedDatapack(bundle, mode)
                    : writer.write(bundle, mode);
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
            IrisObject object,
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

    public static String deriveName(NamespacedKey key) {
        return (key.getNamespace() + "_" + key.getKey()).replace('/', '_').replace(':', '_');
    }

    public static String deriveName(String key) {
        return key.toLowerCase().replace('/', '_').replace(':', '_');
    }

    public static Result importStructure(IrisData data, NamespacedKey key, String name, Mode mode) {
        return importStructure(data, key, name, mode, false, Ownership.EDITABLE);
    }

    public static Result importStructure(IrisData data, NamespacedKey key, String name, Mode mode, boolean objectOnly) {
        return importStructure(data, key, name, mode, objectOnly, Ownership.EDITABLE);
    }

    static Result importStructure(
            IrisData data,
            NamespacedKey key,
            String name,
            Mode mode,
            boolean objectOnly,
            Ownership ownership
    ) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(key);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new Result(false, "Failed to load structure " + key + ": " + e.getMessage(), 0, List.of(), true);
        }
        if (structure == null || structure.getPalettes().isEmpty()) {
            return new Result(false, "No loadable structure NBT for key " + key + " (jigsaw structures must be imported by their piece keys)", 0, List.of());
        }

        CapturedStructure captured;
        try {
            captured = captureStructure(structure);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new Result(false, "Failed to capture structure " + key + ": " + e.getMessage(), 0, List.of(), true);
        }

        IrisObject object = captured.object();
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
            StructureSource.Kind sourceKind = key.getNamespace().equals("minecraft")
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            StructureSource source = StructureSource.of(sourceKind, sourceKey);
            IrisStructureBundleFactory.SinglePieceOptions options = new IrisStructureBundleFactory.SinglePieceOptions(
                    bundleKey, source, name, object, Math.max(w, d), "CENTER_HEIGHT", objectOnly,
                    captured.capabilities(), losses);
            StructureResourceBundle bundle = IrisStructureBundleFactory.singlePiece(options);
            if (!objectOnly) {
                StructureResourceBundleGraphCompiler.requireViable(bundle);
            }
            StructureWriteMode writeMode = activeMode == Mode.ADD_ONLY
                    ? StructureWriteMode.ADD_ONLY : StructureWriteMode.OVERWRITE;
            StructureWriteResult writeResult = ownership.write(data, bundle, writeMode);
            reportWriteFailure(writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, activeMode, writeResult), count, losses,
                        writeResult.failure().isPresent());
            }
            if (writeResult.committed()) {
                data.invalidateStructureResources();
            }
            writeNote = writeResultNote(writeResult);
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing import for '" + name + "': " + e.getMessage(), count, losses);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new Result(false, "Failed writing import for '" + name + "': " + e.getMessage(), count, losses,
                    true);
        }

        String lossSummary = losses.isEmpty() ? "" : ", " + losses.size() + " fidelity warning(s) recorded";
        return new Result(true, "Imported " + key + " as '" + name + "' (" + count + " blocks, " + tiles
                + " tiles, " + w + "x" + h + "x" + d + lossSummary + ")" + writeNote, count, losses);
    }

    static CapturedStructure captureStructure(Structure structure) {
        Structure activeStructure = Objects.requireNonNull(structure);
        if (activeStructure.getPalettes().isEmpty()) {
            throw new IllegalArgumentException("Structure has no palettes");
        }

        BlockVector size = activeStructure.getSize();
        int w = Math.max(1, size.getBlockX());
        int h = Math.max(1, size.getBlockY());
        int d = Math.max(1, size.getBlockZ());
        IrisObject object = new IrisObject(w, h, d);
        int count = 0;
        int nonAirBlocks = 0;
        int tiles = 0;
        int structureMarkers = 0;
        int invalidFinalStates = 0;
        Palette palette = activeStructure.getPalettes().get(0);
        for (BlockState block : palette.getBlocks()) {
            Location loc = block.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d) {
                continue;
            }
            BlockData blockData = block.getBlockData();
            Material mat = blockData.getMaterial();
            if (mat == Material.STRUCTURE_VOID) {
                continue;
            }
            boolean structural = mat == Material.JIGSAW || mat == Material.STRUCTURE_BLOCK;
            if (structural) {
                structureMarkers++;
            }
            if (mat == Material.JIGSAW) {
                FinalStateResult finalState = readJigsawFinalState(block);
                BlockData resolved = finalState.blockData();
                if (finalState.invalidSourceState()) {
                    invalidFinalStates++;
                }
                if (resolved == null || isAir(resolved)) {
                    continue;
                }
                blockData = resolved;
            } else if (mat == Material.STRUCTURE_BLOCK) {
                continue;
            }
            object.setUnsigned(x, y, z, art.arcane.iris.platform.bukkit.BukkitBlockState.of(blockData));
            count++;
            if (!isAir(blockData)) {
                nonAirBlocks++;
            }
            if (!structural) {
                LegacyTileData tile = captureTile(block);
                if (tile != null) {
                    object.setUnsignedTile(x, y, z, tile);
                    tiles++;
                }
            }
        }

        List<StructureCapability> capabilities = new ArrayList<>();
        capabilities.add(StructureCapability.BLOCKS);
        if (tiles > 0) {
            capabilities.add(StructureCapability.BLOCK_ENTITIES);
        }
        return new CapturedStructure(
                object,
                count,
                nonAirBlocks,
                tiles,
                w,
                h,
                d,
                structureMarkers,
                capabilities,
                importLosses(activeStructure, structureMarkers, invalidFinalStates)
        );
    }

    private static boolean isAir(BlockData data) {
        Material m = data.getMaterial();
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }

    private static List<StructureLoss> importLosses(
            Structure structure,
            int structureMarkers,
            int invalidFinalStates
    ) {
        List<StructureLoss> losses = new ArrayList<>();
        if (structure.getPaletteCount() > 1) {
            losses.add(StructureLoss.warning(
                    StructureCapability.BLOCKS,
                    "palette_variants_not_imported",
                    "Only palette 0 was converted; " + (structure.getPaletteCount() - 1) + " additional palette(s) remain native-only."));
        }
        if (structure.getEntityCount() > 0) {
            losses.add(StructureLoss.warning(
                    StructureCapability.ENTITIES,
                    "entities_not_imported",
                    structure.getEntityCount() + " structure entit" + (structure.getEntityCount() == 1 ? "y was" : "ies were")
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

    private static void reportWriteFailure(StructureWriteResult result) {
        result.failure().ifPresent((Throwable failure) -> {
            IrisLogging.reportError(failure);
        });
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

    private static FinalStateResult readJigsawFinalState(BlockState block) {
        String finalState;
        try {
            Object nbt = block.getClass().getMethod("getSnapshotNBT").invoke(block);
            if (nbt == null) {
                return new FinalStateResult(null, false);
            }
            Object res = nbt.getClass().getMethod("getString", String.class).invoke(nbt, "final_state");
            finalState = null;
            if (res instanceof String s) {
                finalState = s;
            } else if (res instanceof Optional<?> o && o.isPresent()) {
                finalState = String.valueOf(o.get());
            }
            if (finalState == null || finalState.isBlank()) {
                return new FinalStateResult(null, false);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            if (VillageImporter.shouldPrintFullTrace(e)) {
            }
            return new FinalStateResult(null, false);
        }
        try {
            return new FinalStateResult(Bukkit.createBlockData(normalizeJigsawFinalState(finalState)), false);
        } catch (IllegalArgumentException e) {
            return new FinalStateResult(null, true);
        }
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

    private record FinalStateResult(BlockData blockData, boolean invalidSourceState) {
    }

    private static LegacyTileData captureTile(BlockState block) {
        try {
            return LegacyTileData.fromBukkit(block);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    public static Result importTemplateGroup(IrisData data, String groupName, String vanillaSource, String prefix, Mode mode) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        File objectsRoot = new File(data.getDataFolder(), "objects");
        File prefixDir = new File(objectsRoot, prefix);
        if (!prefixDir.isDirectory()) {
            return new Result(false, "No imported templates under objects/" + prefix + " for " + groupName, 0, List.of());
        }

        List<File> iobs = new ArrayList<>();
        collectIob(prefixDir, iobs);
        if (iobs.isEmpty()) {
            return new Result(false, "No .iob templates under objects/" + prefix + " for " + groupName, 0, List.of());
        }
        iobs.sort(Comparator.comparing(File::getAbsolutePath));

        String rootPath = objectsRoot.getAbsolutePath() + File.separator;
        List<String> pieceNames = new ArrayList<>();
        int maxSpan = 1;
        for (File iob : iobs) {
            String rel = iob.getAbsolutePath().substring(rootPath.length()).replace(File.separatorChar, '/');
            if (rel.endsWith(".iob")) {
                rel = rel.substring(0, rel.length() - ".iob".length());
            }
            pieceNames.add(rel);
            try {
                maxSpan = Math.max(maxSpan, readObjectSpan(iob));
            } catch (IOException e) {
                IrisLogging.reportError(e);
                return new Result(false, "Cannot read imported object '" + rel + "': " + e.getMessage(), 0,
                        List.of(), true);
            }
        }

        try {
            for (String piece : pieceNames) {
                File pieceFile = new File(data.getDataFolder(), "jigsaw-pieces/" + piece + ".json");
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
            StructureWriteResult writeResult = new StructureTransactionWriter(data.getDataFolder().toPath())
                    .write(bundle, writeMode);
            reportWriteFailure(writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(groupName, activeMode, writeResult), 0, List.of(),
                        writeResult.failure().isPresent());
            }
            if (writeResult.committed()) {
                data.invalidateStructureResources();
            }
            return new Result(true, "Built structure '" + groupName + "' from " + pieceNames.size()
                    + " template variants" + writeResultNote(writeResult), pieceNames.size(), List.of());
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return new Result(false, "Failed writing group structure '" + groupName + "': " + e.getMessage(), 0,
                    List.of(), true);
        }

    }

    private static void collectIob(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectIob(f, out);
            } else if (f.getName().endsWith(".iob")) {
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
            IrisData data,
            String name,
            String vanillaSource,
            IrisObject object,
            String placeMode,
            Mode mode
    ) {
        Mode activeMode = mode == null ? Mode.ADD_ONLY : mode;
        int span = Math.max(object.getW(), object.getD());
        try {
            StructureKey sourceKey = StructureKey.parse(vanillaSource, "minecraft");
            StructureSource.Kind sourceKind = sourceKey.namespace().equals("minecraft")
                    ? StructureSource.Kind.VANILLA : StructureSource.Kind.DATAPACK;
            IrisStructureBundleFactory.SinglePieceOptions options = new IrisStructureBundleFactory.SinglePieceOptions(
                    new StructureKey("iris", name),
                    StructureSource.of(sourceKind, sourceKey),
                    name,
                    object,
                    span,
                    placeMode,
                    false,
                    List.of(StructureCapability.BLOCKS, StructureCapability.IRIS_PLACEMENT),
                    List.of());
            StructureResourceBundle bundle = IrisStructureBundleFactory.singlePiece(options);
            StructureResourceBundleGraphCompiler.requireViable(bundle);
            StructureWriteMode writeMode = activeMode == Mode.ADD_ONLY
                    ? StructureWriteMode.ADD_ONLY : StructureWriteMode.OVERWRITE;
            StructureWriteResult writeResult = new StructureTransactionWriter(data.getDataFolder().toPath())
                    .write(bundle, writeMode);
            reportWriteFailure(writeResult);
            if (!writeResult.successful()) {
                return new Result(false, writeFailureMessage(name, activeMode, writeResult), 0, List.of(),
                        writeResult.failure().isPresent());
            }
            if (writeResult.committed()) {
                data.invalidateStructureResources();
            }
            return new Result(true, "Captured '" + vanillaSource + "' as '" + name + "'"
                    + writeResultNote(writeResult), object.getBlocks().size(), List.of());
        } catch (StructureGraphValidationException e) {
            return new Result(false, "Failed writing capture for '" + name + "': " + e.getMessage(), 0, List.of());
        } catch (Throwable e) {
            IrisLogging.reportError(e);
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
