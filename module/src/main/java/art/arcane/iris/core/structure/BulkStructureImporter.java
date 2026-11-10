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
import com.volmit.resource.ResourceId;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;
import com.volmit.tooling.world.TextColor;
import com.volmit.tooling.world.ToolText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import art.arcane.iris.core.structure.authoring.StructureBundleNames;
import art.arcane.iris.core.structure.authoring.StructureIndexService;

public final class BulkStructureImporter {
    private static final int TEMPLATE_PROGRESS_INTERVAL = 50;

    public record Report(
            int total,
            int imported,
            int skipped,
            int failed,
            Map<String, String> successfulBundles,
            boolean retryRequired,
            Set<String> captureCandidates
    ) {
        public Report(int total, int imported, int skipped, int failed) {
            this(total, imported, skipped, failed, Map.of(), false, Set.of());
        }

        public Report(int total, int imported, int skipped, int failed, Map<String, String> successfulBundles) {
            this(total, imported, skipped, failed, successfulBundles, false, Set.of());
        }

        public Report(int total, int imported, int skipped, int failed,
                      Map<String, String> successfulBundles, boolean retryRequired) {
            this(total, imported, skipped, failed, successfulBundles, retryRequired, Set.of());
        }

        public Report {
            successfulBundles = Collections.unmodifiableMap(new TreeMap<>(successfulBundles));
            captureCandidates = Collections.unmodifiableSet(new TreeSet<>(captureCandidates));
        }
    }

    private BulkStructureImporter() {
    }

    public static Report importAllVanilla(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                          boolean includeNonJigsaw, Sender sender) {
        List<ResourceId> vanilla = new ArrayList<>(platform.structures());
        Collections.sort(vanilla);

        int total = vanilla.size();
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        TreeSet<String> captureCandidates = new TreeSet<>();

        SenderText.send(sender, ToolText.builder()
                .green("Importing ").white(String.valueOf(total))
                .green(" vanilla & datapack structures (mode=" + mode + ", includeNonJigsaw=" + includeNonJigsaw + ")...")
                .build());

        for (ResourceId nk : vanilla) {
            String keyString = nk.toString();
            String name = StructureImporter.deriveName(nk);

            try {
                VillageImporter.Result jigsaw = VillageImporter.importVillage(pack, platform, nk, name, mode);
                if (jigsaw.success()) {
                    imported++;
                    SenderText.send(sender, TextColor.GRAY, "[jigsaw] " + keyString + " -> " + name);
                    continue;
                }

                String message = jigsaw.message() == null ? "" : jigsaw.message();
                if (message.contains("is not a jigsaw structure")) {
                    if (!includeNonJigsaw) {
                        skipped++;
                        continue;
                    }
                    StructureImporter.Result single = StructureImporter.importStructure(pack, platform, nk, name, mode);
                    if (single.success()) {
                        imported++;
                        SenderText.send(sender, TextColor.GRAY, "[single] " + keyString + " -> " + name);
                    } else if (single.message() != null && single.message().startsWith("Skipped")) {
                        skipped++;
                    } else if (isCaptureCandidate(message, single.message())) {
                        skipped++;
                        captureCandidates.add(keyString);
                        SenderText.send(sender, TextColor.YELLOW, "[skip] " + keyString
                                + ": no single-template NBT - vanilla builds this in code or from separate piece templates (imported via the templates pass); nothing to import as one structure.");
                    } else {
                        failed++;
                        SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + single.message());
                    }
                    continue;
                }

                if (message.startsWith("Skipped")) {
                    skipped++;
                    continue;
                }

                failed++;
                SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + message);
            } catch (Throwable e) {
                failed++;
                SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + e.getMessage());
            }
        }

        refreshIndex(pack, platform);

        SenderText.send(sender, completion("Bulk import complete: ", imported, skipped, failed, total));
        return new Report(total, imported, skipped, failed, Map.of(), false, captureCandidates);
    }

    public static Report importTemplateGroups(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                              Sender sender) {
        String[][] groups = {
                {"shipwreck", "minecraft:shipwreck", "shipwreck"},
                {"ruined_portal", "minecraft:ruined_portal", "ruined_portal"},
                {"ocean_ruin", "minecraft:ocean_ruin_cold", "underwater_ruin"},
                {"nether_fossil", "minecraft:nether_fossil", "nether_fossils"},
        };

        int imported = 0;
        int skipped = 0;
        int failed = 0;

        SenderText.send(sender, TextColor.GREEN,
                "Building single-template structures from imported pieces (one variant placed per generation)...");
        for (String[] g : groups) {
            try {
                StructureImporter.Result result = StructureImporter.importTemplateGroup(pack, g[0], g[1], g[2], mode);
                if (result.success()) {
                    imported++;
                    SenderText.send(sender, TextColor.GRAY, "[group] " + g[1] + " -> " + g[0] + " (" + result.blocks() + " variants)");
                } else if (result.message() != null && result.message().startsWith("Skipped")) {
                    skipped++;
                } else {
                    skipped++;
                    SenderText.send(sender, TextColor.YELLOW, "[skip] " + g[0] + ": " + result.message());
                }
            } catch (Throwable e) {
                failed++;
                SenderText.send(sender, TextColor.RED, "[fail] " + g[0] + ": " + e.getMessage());
            }
        }

        refreshIndex(pack, platform);
        SenderText.send(sender, ToolText.builder()
                .green("Single-template structures: ").white(String.valueOf(imported))
                .green(" built, ").white(String.valueOf(skipped))
                .green(" skipped, ").white(String.valueOf(failed))
                .green(" failed.").build());
        return new Report(groups.length, imported, skipped, failed);
    }

    public static Report importAllTemplates(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                            Sender sender) {
        List<ResourceId> all;
        try {
            all = new ArrayList<>(platform.templates());
        } catch (Throwable e) {
            SenderText.send(sender, TextColor.RED,
                    "Failed to enumerate structure templates via the server ResourceManager: " + e.getMessage());
            return enumerationFailureReport();
        }
        Collections.sort(all);

        int total = all.size();
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        if (total == 0) {
            SenderText.send(sender, TextColor.YELLOW, "No structure templates were found under the 'structure' resource path.");
            return new Report(0, 0, 0, 0);
        }

        SenderText.send(sender, ToolText.builder()
                .green("Importing ").white(String.valueOf(total))
                .green(" structure templates (mode=" + mode + ")...").build());

        for (ResourceId nk : all) {
            String keyString = nk.toString();
            String name = templateNameFor(keyString);

            try {
                StructureImporter.Result result = StructureImporter.importStructure(pack, platform, nk, name, mode, true);
                if (result.success()) {
                    imported++;
                } else if (result.message() != null && result.message().startsWith("Skipped")) {
                    skipped++;
                } else {
                    failed++;
                    SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + result.message());
                }
            } catch (Throwable e) {
                failed++;
                SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + e.getMessage());
            }

            int processed = imported + skipped + failed;
            if (processed % TEMPLATE_PROGRESS_INTERVAL == 0) {
                SenderText.send(sender, TextColor.GRAY, "..." + processed + "/" + total + " (" + imported
                        + " imported, " + skipped + " skipped, " + failed + " failed)");
            }
        }

        refreshIndex(pack, platform);

        SenderText.send(sender, completion("Template import complete: ", imported, skipped, failed, total));
        return new Report(total, imported, skipped, failed);
    }

    public static Report importDatapackStructures(ToolPack pack, PlatformStructures platform, StructureImporter.Mode mode,
                                                  Sender sender) {
        return importDatapackStructures(
                pack,
                platform,
                mode,
                sender,
                null,
                null,
                StructureImporter.Ownership.EDITABLE
        );
    }

    public static Report importManagedDatapackStructures(
            ToolPack pack,
            PlatformStructures platform,
            StructureImporter.Mode mode,
            Sender sender,
            Set<String> allowedStructureKeys,
            Set<String> allowedTemplateKeys
    ) {
        return importDatapackStructures(
                pack,
                platform,
                mode,
                sender,
                allowedStructureKeys,
                allowedTemplateKeys,
                StructureImporter.Ownership.MANAGED_DATAPACK
        );
    }

    private static Report importDatapackStructures(
            ToolPack pack,
            PlatformStructures platform,
            StructureImporter.Mode mode,
            Sender sender,
            Set<String> allowedStructureKeys,
            Set<String> allowedTemplateKeys,
            StructureImporter.Ownership ownership
    ) {
        KeySelection structureSelection = selectDatapackKeys(keysOf(platform.structures()), allowedStructureKeys);
        List<String> datapack = structureSelection.present();

        int structureAttempts = structureSelection.total();
        int templateAttempts = 0;
        int imported = 0;
        int skipped = 0;
        int failed = structureSelection.missing().size();
        boolean retryRequired = false;
        Map<String, String> successfulBundles = new TreeMap<>();

        if (structureAttempts == 0) {
            SenderText.send(sender, TextColor.YELLOW,
                    "No datapack (non-minecraft) structures are registered. Ingest a datapack and restart first, then run this again.");
        } else {
            SenderText.send(sender, ToolText.builder()
                    .green("Importing ").white(String.valueOf(structureAttempts))
                    .green(" datapack structures (mode=" + mode + ")...").build());
            for (String missingKey : structureSelection.missing()) {
                SenderText.send(sender, TextColor.RED, "[fail] " + missingKey
                        + ": allowed structure key is absent from the live registry");
            }
            for (String keyString : datapack) {
                ResourceId nk = ResourceId.parse(keyString);
                String name = StructureImporter.deriveName(nk);

                try {
                    VillageImporter.Result jigsaw = VillageImporter.importVillage(
                            pack,
                            platform,
                            nk,
                            name,
                            mode,
                            ownership
                    );
                    if (jigsaw.success()) {
                        imported++;
                        successfulBundles.put(bundleKey(name), keyString);
                        SenderText.send(sender, TextColor.GRAY, "[jigsaw] " + keyString + " -> " + name);
                        continue;
                    }
                    retryRequired |= jigsaw.retryableFailure();

                    String message = jigsaw.message() == null ? "" : jigsaw.message();
                    if (message.contains("is not a jigsaw structure")) {
                        StructureImporter.Result single = StructureImporter.importStructure(
                                pack,
                                platform,
                                nk,
                                name,
                                mode,
                                false,
                                ownership
                        );
                        if (single.success()) {
                            imported++;
                            successfulBundles.put(bundleKey(name), keyString);
                            SenderText.send(sender, TextColor.GRAY, "[single] " + keyString + " -> " + name);
                        } else if (single.message() != null && single.message().startsWith("Skipped")) {
                            skipped++;
                        } else if (single.message() != null && single.message().contains("No loadable structure NBT")) {
                            skipped++;
                        } else {
                            failed++;
                            retryRequired |= single.retryableFailure();
                            SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + single.message());
                        }
                        continue;
                    }

                    if (message.startsWith("Skipped")) {
                        skipped++;
                        continue;
                    }

                    failed++;
                    SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + message);
                } catch (Throwable e) {
                    failed++;
                    retryRequired = true;
                    SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + e.getMessage());
                }
            }
        }

        if (allowedTemplateKeys == null || !allowedTemplateKeys.isEmpty()) {
            List<String> templateKeys = List.of();
            boolean enumerationSucceeded = false;
            try {
                templateKeys = keysOf(platform.templates());
                enumerationSucceeded = true;
            } catch (Throwable e) {
                templateAttempts++;
                failed++;
                retryRequired = true;
                SenderText.send(sender, TextColor.YELLOW,
                        "Could not enumerate datapack templates via the server ResourceManager: " + e.getMessage());
            }
            if (enumerationSucceeded) {
                KeySelection templateSelection = selectDatapackKeys(templateKeys, allowedTemplateKeys);
                List<String> datapackTemplates = templateSelection.present();
                templateAttempts += templateSelection.total();
                failed += templateSelection.missing().size();
                for (String missingKey : templateSelection.missing()) {
                    SenderText.send(sender, TextColor.RED, "[fail] " + missingKey
                            + ": allowed template key is absent from the live server resources");
                }
                if (!datapackTemplates.isEmpty()) {
                    SenderText.send(sender, ToolText.builder()
                            .green("Importing ").white(String.valueOf(datapackTemplates.size()))
                            .green(" datapack structure templates...").build());
                    for (String keyString : datapackTemplates) {
                        ResourceId nk = ResourceId.parse(keyString);
                        String name = templateNameFor(keyString);
                        try {
                            StructureImporter.Result result = StructureImporter.importStructure(
                                    pack,
                                    platform,
                                    nk,
                                    name,
                                    mode,
                                    true,
                                    ownership
                            );
                            if (result.success()) {
                                imported++;
                                successfulBundles.put(bundleKey(name), keyString);
                            } else if (result.message() != null && result.message().startsWith("Skipped")) {
                                skipped++;
                            } else {
                                failed++;
                                retryRequired |= result.retryableFailure();
                                SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + result.message());
                            }
                        } catch (Throwable e) {
                            failed++;
                            retryRequired = true;
                            SenderText.send(sender, TextColor.RED, "[fail] " + keyString + ": " + e.getMessage());
                        }
                    }
                }
            }
        }

        refreshIndex(pack, platform);
        SenderText.send(sender, ToolText.builder()
                .green("Datapack structure import complete: ").white(String.valueOf(imported))
                .green(" imported, ").white(String.valueOf(skipped))
                .green(" skipped, ").white(String.valueOf(failed))
                .green(" failed.").build());
        return datapackReport(
                structureAttempts,
                templateAttempts,
                imported,
                skipped,
                failed,
                successfulBundles,
                retryRequired);
    }

    private static ToolText completion(String label, int imported, int skipped, int failed, int total) {
        return ToolText.builder()
                .green(label).white(String.valueOf(imported))
                .green(" imported, ").white(String.valueOf(skipped))
                .green(" skipped, ").white(String.valueOf(failed))
                .green(" failed (").white(String.valueOf(total))
                .green(" total).").build();
    }

    private static List<String> keysOf(List<ResourceId> ids) {
        return ids.stream().map(ResourceId::toString).toList();
    }

    private static void refreshIndex(ToolPack pack, PlatformStructures platform) {
        try {
            StructureIndexService.write(pack, platform);
        } catch (IOException | RuntimeException e) {
            pack.log().error("Could not refresh the structure index", e);
        }
    }

    static boolean isAllowedDatapackKey(String key, Set<String> allowedKeys) {
        return allowedKeys == null ? !key.startsWith("minecraft:") : allowedKeys.contains(key);
    }

    static boolean isCaptureCandidate(String jigsawMessage, String singleMessage) {
        return jigsawMessage != null
                && jigsawMessage.contains("is not a jigsaw structure")
                && singleMessage != null
                && singleMessage.contains("No loadable structure NBT");
    }

    static KeySelection selectDatapackKeys(Iterable<String> discoveredKeys, Set<String> allowedKeys) {
        TreeSet<String> discovered = normalizeKeys(discoveredKeys);
        TreeSet<String> present = new TreeSet<>();
        TreeSet<String> missing = new TreeSet<>();
        if (allowedKeys == null) {
            for (String key : discovered) {
                if (isAllowedDatapackKey(key, null)) {
                    present.add(key);
                }
            }
        } else {
            TreeSet<String> allowed = normalizeKeys(allowedKeys);
            for (String key : allowed) {
                if (discovered.contains(key)) {
                    present.add(key);
                } else {
                    missing.add(key);
                }
            }
        }
        return new KeySelection(new ArrayList<>(present), new ArrayList<>(missing));
    }

    static Report datapackReport(
            int structureAttempts,
            int templateAttempts,
            int imported,
            int skipped,
            int failed,
            Map<String, String> successfulBundles
    ) {
        return new Report(structureAttempts + templateAttempts, imported, skipped, failed, successfulBundles);
    }

    static Report datapackReport(
            int structureAttempts,
            int templateAttempts,
            int imported,
            int skipped,
            int failed,
            Map<String, String> successfulBundles,
            boolean retryRequired
    ) {
        return new Report(
                structureAttempts + templateAttempts,
                imported,
                skipped,
                failed,
                successfulBundles,
                retryRequired);
    }

    static Report enumerationFailureReport() {
        return new Report(1, 0, 0, 1, Map.of(), true);
    }

    public static String templateNameFor(String key) {
        return StructureBundleNames.templateName(key);
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

    private static String bundleKey(String name) {
        return "iris:" + name;
    }

    record KeySelection(List<String> present, List<String> missing) {
        KeySelection {
            present = List.copyOf(present);
            missing = List.copyOf(missing);
        }

        int total() {
            return present.size() + missing.size();
        }
    }
}
