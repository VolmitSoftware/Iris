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

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class StructureCaptureImporter {
    public record Report(int total, int imported, int skipped, int failed) {
    }

    private static final int MAX_SPAN = 48;
    private static final int CELL_STRIDE = 128;
    private static final int CELL_COLUMNS = 8;
    private static final long REGION_TIMEOUT_SECONDS = 30L;

    private StructureCaptureImporter() {
    }

    public static Report importAllStructures(IrisData data, StructureImporter.Mode mode, VolmitSender sender) {
        return importStructures(data, mode, sender, null);
    }

    public static Report importStructures(IrisData data, StructureImporter.Mode mode, VolmitSender sender,
                                          Set<String> allowedStructureKeys) {
        StructureImporter.Mode activeMode = mode == null ? StructureImporter.Mode.ADD_ONLY : mode;
        if (!INMS.get().supportsStructureCapture()) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_IS_NOT_SUPPORTED_BY_ACTIVE_NMS_BINDING_SKIPPING_CAPTURE_PASS));
            return new Report(0, 0, 0, 0);
        }

        KList<String> keys = INMS.get().getStructureKeys();
        if (keys == null || keys.isEmpty()) {
            return new Report(0, 0, 0, 0);
        }

        ArrayList<String> targets = new ArrayList<>();
        for (String key : selectCaptureKeys(keys, allowedStructureKeys)) {
            String name = StructureImporter.deriveName(key);
            File structureFile = new File(data.getDataFolder(), "structures/" + name + ".json");
            if (structureFile.exists() && activeMode != StructureImporter.Mode.OVERWRITE) {
                continue;
            }
            targets.add(key);
        }

        int total = targets.size();
        if (total == 0) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_NO_CODE_GENERATED_STRUCTURES_LEFT_CAPTURE_EVERYTHING_IS_ALREADY_IMPORTED_AS_STRUCTURE));
            return new Report(0, 0, 0, 0);
        }

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_CAPTURING_CODE_GENERATED_STRUCTURES_NO_NBT_TEMPLATE_INTO_SCRATCH_WORLD_SKIPPING_ANY, MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("MAXSPAN", String.valueOf(MAX_SPAN))));

        World scratch = FeatureImporter.createScratchWorld(sender);
        if (scratch == null) {
            return new Report(total, 0, 0, total);
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int cellIndex = 0;

        try {
            for (String key : targets) {
                String name = StructureImporter.deriveName(key);
                try {
                    IrisObject object = captureOne(scratch, key, cellIndex++);
                    if (object == null || object.getBlocks().isEmpty()) {
                        skipped++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_SKIP_DID_NOT_PLACE_CAPTURABLE_STRUCTURE_HERE_TOO_LARGE_WRONG_DIMENSION_NO, MessageArgument.untrusted("key", String.valueOf(key))));
                        continue;
                    }
                    object.shrinkwrap();
                    StructureImporter.Result result = StructureImporter.writeSinglePieceStructure(
                            data, name, key, object, "CENTER_HEIGHT", activeMode);
                    if (!result.success()) {
                        failed++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_FAIL, MessageArgument.untrusted("key", String.valueOf(key)), MessageArgument.untrusted("message", String.valueOf(result.message()))));
                        continue;
                    }
                    imported++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_CAPTURE_OBJECTS_IOB_X_X, MessageArgument.untrusted("key", String.valueOf(key)), MessageArgument.untrusted("name", String.valueOf(name)), MessageArgument.untrusted("w", String.valueOf(object.getW())), MessageArgument.untrusted("h", String.valueOf(object.getH())), MessageArgument.untrusted("d", String.valueOf(object.getD()))));
                } catch (Throwable e) {
                    failed++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_FAIL_2, MessageArgument.untrusted("key", String.valueOf(key)), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
                    IrisLogging.reportError(e);
                }

                int processed = imported + skipped + failed;
                if (processed % 10 == 0) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_CAPTURED_SKIPPED_FAILED, MessageArgument.untrusted("processed", String.valueOf(processed)), MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed))));
                }
            }
        } finally {
            FeatureImporter.destroyScratchWorld(scratch, sender);
        }

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.STRUCTURE_CAPTURE_IMPORTER_STRUCTURE_CAPTURE_COMPLETE_CAPTURED_SKIPPED_FAILED_TOTAL, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed)), MessageArgument.untrusted("total", String.valueOf(total))));
        return new Report(total, imported, skipped, failed);
    }

    static List<String> selectCaptureKeys(Iterable<String> keys, Set<String> allowedStructureKeys) {
        TreeSet<String> allowed = null;
        if (allowedStructureKeys != null) {
            allowed = normalizeKeys(allowedStructureKeys);
        }
        TreeSet<String> selected = new TreeSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (allowed == null || allowed.contains(normalized)) {
                selected.add(normalized);
            }
        }
        return new ArrayList<>(selected);
    }

    private static TreeSet<String> normalizeKeys(Iterable<String> keys) {
        TreeSet<String> normalized = new TreeSet<>();
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            normalized.add(key.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private static IrisObject captureOne(World world, String key, int cellIndex) {
        int originX = (cellIndex % CELL_COLUMNS) * CELL_STRIDE;
        int originZ = (cellIndex / CELL_COLUMNS) * CELL_STRIDE;
        int anchorChunkX = (originX + CELL_STRIDE / 2) >> 4;
        int anchorChunkZ = (originZ + CELL_STRIDE / 2) >> 4;
        long seed = key.hashCode() * 0x9E3779B97F4A7C15L + cellIndex * 0x100000001B3L + 0x5DEECE66DL;

        AtomicReference<IrisObject> objectRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        boolean scheduled = J.runRegion(world, anchorChunkX, anchorChunkZ, () -> {
            try {
                int[] box = INMS.get().placeStructure(world, anchorChunkX, anchorChunkZ, key, seed, MAX_SPAN);
                if (box == null) {
                    return;
                }
                int width = box[3] - box[0] + 1;
                int height = box[4] - box[1] + 1;
                int depth = box[5] - box[2] + 1;
                if (width <= 0 || height <= 0 || depth <= 0) {
                    return;
                }
                IrisObject object = new IrisObject(width, height, depth);
                boolean any = false;
                for (int dx = 0; dx < width; dx++) {
                    for (int dy = 0; dy < height; dy++) {
                        for (int dz = 0; dz < depth; dz++) {
                            Block block = world.getBlockAt(box[0] + dx, box[1] + dy, box[2] + dz);
                            if (block.getType() == Material.AIR) {
                                continue;
                            }
                            object.setUnsigned(dx, dy, dz, block, false);
                            any = true;
                        }
                    }
                }
                if (any) {
                    objectRef.set(object);
                }

                int minCX = box[0] >> 4;
                int maxCX = box[3] >> 4;
                int minCZ = box[2] >> 4;
                int maxCZ = box[5] >> 4;
                for (int cx = minCX; cx <= maxCX; cx++) {
                    for (int cz = minCZ; cz <= maxCZ; cz++) {
                        try {
                            world.unloadChunk(cx, cz, false);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable e) {
                errorRef.set(e);
            } finally {
                latch.countDown();
            }
        });

        if (!scheduled) {
            throw captureFailure(key, anchorChunkX, anchorChunkZ,
                    "region task was not accepted", null);
        }

        try {
            if (!latch.await(REGION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw captureFailure(key, anchorChunkX, anchorChunkZ,
                        "region task did not complete within " + REGION_TIMEOUT_SECONDS + " seconds", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw captureFailure(key, anchorChunkX, anchorChunkZ,
                    "interrupted while waiting for the region task", e);
        }

        Throwable error = errorRef.get();
        if (error != null) {
            throw captureFailure(key, anchorChunkX, anchorChunkZ,
                    "platform placement failed", error);
        }
        return objectRef.get();
    }

    static IllegalStateException captureFailure(String key, int chunkX, int chunkZ,
                                                String detail, Throwable cause) {
        String message = "Structure capture failed for '" + key + "' at chunk "
                + chunkX + "," + chunkZ + ": " + detail;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
