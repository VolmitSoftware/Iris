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

import art.arcane.iris.core.structure.tooling.SenderText;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.CapturedStructure;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.TemplateBlock;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class StructureCaptureImporter {
    public record Report(int total, int imported, int skipped, int failed) {
    }

    private static final int MAX_SPAN = 48;
    private static final int PROGRESS_INTERVAL = 10;

    private StructureCaptureImporter() {
    }

    public static Report importAllStructures(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                             Sender sender) {
        return importStructures(pack, platform, mode, sender, null);
    }

    public static Report importStructures(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                          Sender sender, Set<String> allowedStructureKeys) {
        StructureImporter.Mode activeMode = mode == null ? StructureImporter.Mode.ADD_ONLY : mode;
        if (!platform.supportsCapture()) {
            SenderText.send(sender, TextColor.YELLOW,
                    "Structure capture is not supported by the active NMS binding; skipping the capture pass.");
            return new Report(0, 0, 0, 0);
        }

        List<String> keys = platform.structures().stream().map(ResourceId::toString).toList();
        if (keys.isEmpty()) {
            return new Report(0, 0, 0, 0);
        }

        PackResources resources = pack.resources();
        ArrayList<String> targets = new ArrayList<>();
        for (String key : selectCaptureKeys(keys, allowedStructureKeys)) {
            String name = StructureImporter.deriveName(key);
            boolean alreadyImported = resources.ids(StructureResources.STRUCTURES).contains(resources.idOf(name));
            if (alreadyImported && activeMode != StructureImporter.Mode.OVERWRITE) {
                continue;
            }
            targets.add(key);
        }

        int total = targets.size();
        if (total == 0) {
            SenderText.send(sender, TextColor.GRAY,
                    "No code-generated structures left to capture (everything is already imported as a structure).");
            return new Report(0, 0, 0, 0);
        }

        SenderText.send(sender, ToolText.builder()
                .green("Capturing ").white(String.valueOf(total))
                .green(" code-generated structures (no NBT template) into a scratch world (skipping any wider/taller than "
                        + MAX_SPAN + " blocks)...").build());

        Optional<PlatformStructures.CaptureArea> opened = platform.openCaptureArea(sender);
        if (opened.isEmpty()) {
            return new Report(total, 0, 0, total);
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;
        int cellIndex = 0;

        try (PlatformStructures.CaptureArea area = opened.get()) {
            for (String key : targets) {
                String name = StructureImporter.deriveName(key);
                try {
                    IrisObject object = captureOne(area, key, cellIndex++);
                    if (object == null || object.getBlocks().isEmpty()) {
                        skipped++;
                        SenderText.send(sender, TextColor.YELLOW, "[skip] " + key
                                + ": did not place a capturable structure here (too large, wrong dimension, or no valid placement in a flat world). Stays vanilla-generated.");
                        continue;
                    }
                    object.shrinkwrap();
                    StructureImporter.Result result = StructureImporter.writeSinglePieceStructure(
                            pack, name, key, object, "CENTER_HEIGHT", activeMode);
                    if (!result.success()) {
                        failed++;
                        SenderText.send(sender, TextColor.RED, "[fail] " + key + ": " + result.message());
                        continue;
                    }
                    imported++;
                    SenderText.send(sender, TextColor.GRAY, "[capture] " + key + " -> objects/" + name + ".iob ("
                            + object.getW() + "x" + object.getH() + "x" + object.getD() + ")");
                } catch (Throwable e) {
                    failed++;
                    SenderText.send(sender, TextColor.RED, "[fail] " + key + ": " + e.getMessage());
                    pack.log().error("Could not capture structure " + key, e);
                }

                int processed = imported + skipped + failed;
                if (processed % PROGRESS_INTERVAL == 0) {
                    SenderText.send(sender, TextColor.GRAY, "..." + processed + "/" + total + " (" + imported
                            + " captured, " + skipped + " skipped, " + failed + " failed)");
                }
            }
        }

        SenderText.send(sender, ToolText.builder()
                .green("Structure capture complete: ").white(String.valueOf(imported))
                .green(" captured, ").white(String.valueOf(skipped))
                .green(" skipped, ").white(String.valueOf(failed))
                .green(" failed (").white(String.valueOf(total))
                .green(" total).").build());
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

    private static IrisObject captureOne(PlatformStructures.CaptureArea area, String key, int cellIndex) {
        long seed = key.hashCode() * 0x9E3779B97F4A7C15L + cellIndex * 0x100000001B3L + 0x5DEECE66DL;
        Optional<CapturedStructure> captured = area.capture(ResourceId.parse(key), seed, MAX_SPAN);
        return captured.map(StructureCaptureImporter::objectOf).orElse(null);
    }

    private static IrisObject objectOf(CapturedStructure captured) {
        IrisObject object = new IrisObject(captured.size().x(), captured.size().y(), captured.size().z());
        for (TemplateBlock block : captured.blocks()) {
            object.setUnsignedState(block.position().x(), block.position().y(), block.position().z(), block.state());
            block.blockEntity().ifPresent(entity ->
                    object.setUnsignedBlockEntity(block.position().x(), block.position().y(), block.position().z(), entity));
        }
        return object;
    }
}
