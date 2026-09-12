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
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class BulkStructureImporter {
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

    public static Report importAllVanilla(IrisData data, StructureImporter.Mode mode, boolean includeNonJigsaw, VolmitSender sender) {
        KList<String> keys = INMS.get().getStructureKeys();
        List<String> vanilla = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                vanilla.add(k);
            }
        }
        Collections.sort(vanilla);

        int total = vanilla.size();
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        TreeSet<String> captureCandidates = new TreeSet<>();

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_IMPORTING_VANILLA_DATAPACK_STRUCTURES_MODE_INCLUDENONJIGSAW, MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("mode", String.valueOf(mode)), MessageArgument.untrusted("includeNonJigsaw", String.valueOf(includeNonJigsaw))));

        for (String keyString : vanilla) {
            NamespacedKey nk = NamespacedKey.fromString(keyString.toLowerCase());
            if (nk == null) {
                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY, MessageArgument.untrusted("keyString", String.valueOf(keyString))));
                continue;
            }
            String name = StructureImporter.deriveName(nk);

            try {
                VillageImporter.Result jigsaw = VillageImporter.importVillage(data, nk, name, mode);
                if (jigsaw.success()) {
                    imported++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_JIGSAW, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("name", String.valueOf(name))));
                    continue;
                }

                String message = jigsaw.message() == null ? "" : jigsaw.message();
                if (message.contains("is not a jigsaw structure")) {
                    if (!includeNonJigsaw) {
                        skipped++;
                        continue;
                    }
                    StructureImporter.Result single = StructureImporter.importStructure(data, nk, name, mode);
                    if (single.success()) {
                        imported++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_SINGLE, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("name", String.valueOf(name))));
                    } else if (single.message() != null && single.message().startsWith("Skipped")) {
                        skipped++;
                    } else if (isCaptureCandidate(message, single.message())) {
                        skipped++;
                        captureCandidates.add(nk.toString());
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_SKIP_NO_SINGLE_TEMPLATE_NBT_VANILLA_BUILDS_THIS_CODE_FROM_SEPARATE_PIECE, MessageArgument.untrusted("keyString", String.valueOf(keyString))));
                    } else {
                        failed++;
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(single.message()))));
                    }
                    continue;
                }

                if (message.startsWith("Skipped")) {
                    skipped++;
                    continue;
                }

                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_2, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(message))));
            } catch (Throwable e) {
                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_3, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
            }
        }

        StructureIndexService.write(data);

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_BULK_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed)), MessageArgument.untrusted("total", String.valueOf(total))));
        return new Report(total, imported, skipped, failed, Map.of(), false, captureCandidates);
    }

    public static Report importTemplateGroups(IrisData data, StructureImporter.Mode mode, VolmitSender sender) {
        String[][] groups = {
                {"shipwreck", "minecraft:shipwreck", "shipwreck"},
                {"ruined_portal", "minecraft:ruined_portal", "ruined_portal"},
                {"ocean_ruin", "minecraft:ocean_ruin_cold", "underwater_ruin"},
                {"nether_fossil", "minecraft:nether_fossil", "nether_fossils"},
        };

        int imported = 0;
        int skipped = 0;
        int failed = 0;

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_BUILDING_SINGLE_TEMPLATE_STRUCTURES_FROM_IMPORTED_PIECES_ONE_VARIANT_PLACED_PER_GENERATION));
        for (String[] g : groups) {
            try {
                StructureImporter.Result result = StructureImporter.importTemplateGroup(data, g[0], g[1], g[2], mode);
                if (result.success()) {
                    imported++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_GROUP_VARIANTS, MessageArgument.untrusted("value", String.valueOf(g[1])), MessageArgument.untrusted("value2", String.valueOf(g[0])), MessageArgument.untrusted("blocks", String.valueOf(result.blocks()))));
                } else if (result.message() != null && result.message().startsWith("Skipped")) {
                    skipped++;
                } else {
                    skipped++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_SKIP, MessageArgument.untrusted("value", String.valueOf(g[0])), MessageArgument.untrusted("message", String.valueOf(result.message()))));
                }
            } catch (Throwable e) {
                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_4, MessageArgument.untrusted("value", String.valueOf(g[0])), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
            }
        }

        StructureIndexService.write(data);
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_SINGLE_TEMPLATE_STRUCTURES_BUILT_SKIPPED_FAILED, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed))));
        return new Report(groups.length, imported, skipped, failed);
    }

    public static Report importAllTemplates(IrisData data, StructureImporter.Mode mode, VolmitSender sender) {
        List<String> templateKeys;
        try {
            templateKeys = enumerateTemplateKeys();
        } catch (Throwable e) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAILED_ENUMERATE_STRUCTURE_TEMPLATES_VIA_SERVER_RESOURCEMANAGER, MessageArgument.untrusted("e", String.valueOf(e))));
            return enumerationFailureReport();
        }

        List<String> all = new ArrayList<>();
        for (String key : templateKeys) {
            if (key != null && !key.isBlank()) {
                all.add(key);
            }
        }
        Collections.sort(all);

        int total = all.size();
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        if (total == 0) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_NO_STRUCTURE_TEMPLATES_WERE_FOUND_UNDER_STRUCTURE_RESOURCE_PATH));
            return new Report(0, 0, 0, 0);
        }

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_IMPORTING_STRUCTURE_TEMPLATES_MODE, MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("mode", String.valueOf(mode))));

        for (String keyString : all) {
            NamespacedKey nk = NamespacedKey.fromString(keyString.toLowerCase());
            if (nk == null) {
                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_2, MessageArgument.untrusted("keyString", String.valueOf(keyString))));
                continue;
            }
            String name = templateNameFor(keyString);

            try {
                StructureImporter.Result result = StructureImporter.importStructure(data, nk, name, mode, true);
                if (result.success()) {
                    imported++;
                } else if (result.message() != null && result.message().startsWith("Skipped")) {
                    skipped++;
                } else {
                    failed++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_5, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(result.message()))));
                }
            } catch (Throwable e) {
                failed++;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_6, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
            }

            int processed = imported + skipped + failed;
            if (processed % 50 == 0) {
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_IMPORTED_SKIPPED_FAILED, MessageArgument.untrusted("processed", String.valueOf(processed)), MessageArgument.untrusted("total", String.valueOf(total)), MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed))));
            }
        }

        StructureIndexService.write(data);

        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_TEMPLATE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED_TOTAL, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed)), MessageArgument.untrusted("total", String.valueOf(total))));
        return new Report(total, imported, skipped, failed);
    }

    public static Report importDatapackStructures(IrisData data, StructureImporter.Mode mode, VolmitSender sender) {
        return importDatapackStructures(
                data,
                mode,
                sender,
                null,
                null,
                StructureImporter.Ownership.EDITABLE
        );
    }

    public static Report importManagedDatapackStructures(
            IrisData data,
            StructureImporter.Mode mode,
            VolmitSender sender,
            Set<String> allowedStructureKeys,
            Set<String> allowedTemplateKeys
    ) {
        return importDatapackStructures(
                data,
                mode,
                sender,
                allowedStructureKeys,
                allowedTemplateKeys,
                StructureImporter.Ownership.MANAGED_DATAPACK
        );
    }

    private static Report importDatapackStructures(
            IrisData data,
            StructureImporter.Mode mode,
            VolmitSender sender,
            Set<String> allowedStructureKeys,
            Set<String> allowedTemplateKeys,
            StructureImporter.Ownership ownership
    ) {
        KList<String> keys = INMS.get().getStructureKeys();
        KeySelection structureSelection = selectDatapackKeys(keys, allowedStructureKeys);
        List<String> datapack = structureSelection.present();

        int structureAttempts = structureSelection.total();
        int templateAttempts = 0;
        int imported = 0;
        int skipped = 0;
        int failed = structureSelection.missing().size();
        boolean retryRequired = false;
        Map<String, String> successfulBundles = new TreeMap<>();

        if (structureAttempts == 0) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_NO_DATAPACK_NON_MINECRAFT_STRUCTURES_ARE_REGISTERED_INGEST_DATAPACK_RESTART_FIRST_THEN));
        } else {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURES_MODE, MessageArgument.untrusted("total", String.valueOf(structureAttempts)), MessageArgument.untrusted("mode", String.valueOf(mode))));
            for (String missingKey : structureSelection.missing()) {
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_8, MessageArgument.untrusted("keyString", missingKey), MessageArgument.untrusted("message", "allowed structure key is absent from the live registry")));
            }
            for (String keyString : datapack) {
                NamespacedKey nk = NamespacedKey.fromString(keyString.toLowerCase());
                if (nk == null) {
                    failed++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_3, MessageArgument.untrusted("keyString", String.valueOf(keyString))));
                    continue;
                }
                String name = StructureImporter.deriveName(nk);

                try {
                    VillageImporter.Result jigsaw = VillageImporter.importVillage(
                            data,
                            nk,
                            name,
                            mode,
                            ownership
                    );
                    if (jigsaw.success()) {
                        imported++;
                        successfulBundles.put(bundleKey(name), keyString);
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_JIGSAW_2, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("name", String.valueOf(name))));
                        continue;
                    }
                    retryRequired |= jigsaw.retryableFailure();

                    String message = jigsaw.message() == null ? "" : jigsaw.message();
                    if (message.contains("is not a jigsaw structure")) {
                        StructureImporter.Result single = StructureImporter.importStructure(
                                data,
                                nk,
                                name,
                                mode,
                                false,
                                ownership
                        );
                        if (single.success()) {
                            imported++;
                            successfulBundles.put(bundleKey(name), keyString);
                            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_SINGLE_2, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("name", String.valueOf(name))));
                        } else if (single.message() != null && single.message().startsWith("Skipped")) {
                            skipped++;
                        } else if (single.message() != null && single.message().contains("No loadable structure NBT")) {
                            skipped++;
                        } else {
                            failed++;
                            retryRequired |= single.retryableFailure();
                            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_7, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(single.message()))));
                        }
                        continue;
                    }

                    if (message.startsWith("Skipped")) {
                        skipped++;
                        continue;
                    }

                    failed++;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_8, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(message))));
                } catch (Throwable e) {
                    failed++;
                    retryRequired = true;
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_9, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
                }
            }
        }

        if (allowedTemplateKeys == null || !allowedTemplateKeys.isEmpty()) {
            List<String> templateKeys = List.of();
            boolean enumerationSucceeded = false;
            try {
                templateKeys = enumerateTemplateKeys();
                enumerationSucceeded = true;
            } catch (Throwable e) {
                templateAttempts++;
                failed++;
                retryRequired = true;
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_COULD_NOT_ENUMERATE_DATAPACK_TEMPLATES_VIA_SERVER_RESOURCEMANAGER, MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
            }
            if (enumerationSucceeded) {
                KeySelection templateSelection = selectDatapackKeys(templateKeys, allowedTemplateKeys);
                List<String> datapackTemplates = templateSelection.present();
                templateAttempts += templateSelection.total();
                failed += templateSelection.missing().size();
                for (String missingKey : templateSelection.missing()) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_10, MessageArgument.untrusted("keyString", missingKey), MessageArgument.untrusted("message", "allowed template key is absent from the live server resources")));
                }
                if (!datapackTemplates.isEmpty()) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_IMPORTING_DATAPACK_STRUCTURE_TEMPLATES, MessageArgument.untrusted("size", String.valueOf(datapackTemplates.size()))));
                    for (String keyString : datapackTemplates) {
                        NamespacedKey nk = NamespacedKey.fromString(keyString.toLowerCase());
                        if (nk == null) {
                            failed++;
                            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_INVALID_KEY_2, MessageArgument.untrusted("keyString", String.valueOf(keyString))));
                            continue;
                        }
                        String name = templateNameFor(keyString);
                        try {
                            StructureImporter.Result result = StructureImporter.importStructure(
                                    data,
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
                                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_10, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("message", String.valueOf(result.message()))));
                            }
                        } catch (Throwable e) {
                            failed++;
                            retryRequired = true;
                            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_FAIL_11, MessageArgument.untrusted("keyString", String.valueOf(keyString)), MessageArgument.untrusted("error", String.valueOf(e.getMessage()))));
                        }
                    }
                }
            }
        }

        StructureIndexService.write(data);
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.BULK_STRUCTURE_IMPORTER_DATAPACK_STRUCTURE_IMPORT_COMPLETE_IMPORTED_SKIPPED_FAILED, MessageArgument.untrusted("imported", String.valueOf(imported)), MessageArgument.untrusted("skipped", String.valueOf(skipped)), MessageArgument.untrusted("failed", String.valueOf(failed))));
        return datapackReport(
                structureAttempts,
                templateAttempts,
                imported,
                skipped,
                failed,
                successfulBundles,
                retryRequired);
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
        int colon = key.indexOf(':');
        String namespace = colon >= 0 ? key.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        String safePath = path.toLowerCase().replaceAll("[^a-z0-9_/-]", "_");
        while (safePath.contains("//")) {
            safePath = safePath.replace("//", "/");
        }
        if (safePath.startsWith("/")) {
            safePath = safePath.substring(1);
        }
        return "minecraft".equals(namespace) ? safePath : namespace + "/" + safePath;
    }

    private static List<String> enumerateTemplateKeys() throws Exception {
        Object craftServer = Bukkit.getServer();
        Object dedicated = invoke(craftServer, "getHandle");
        Object server = invoke(dedicated, "getServer");
        Object resourceManager = resolveResourceManager(server);
        if (resourceManager == null) {
            throw new IllegalStateException("Could not resolve the server ResourceManager via reflection");
        }

        Method listResources = null;
        for (Method m : resourceManager.getClass().getMethods()) {
            if (m.getName().equals("listResources") && m.getParameterCount() == 2
                    && m.getParameterTypes()[0] == String.class
                    && m.getParameterTypes()[1] == Predicate.class
                    && Map.class.isAssignableFrom(m.getReturnType())) {
                listResources = m;
                break;
            }
        }
        if (listResources == null) {
            throw new NoSuchMethodException("listResources(String, Predicate) on " + resourceManager.getClass().getName());
        }
        listResources.setAccessible(true);

        Predicate<Object> endsWithNbt = location -> {
            String path = identifierPath(location);
            return path != null && path.endsWith(".nbt");
        };

        Object resultMap = listResources.invoke(resourceManager, "structure", endsWithNbt);
        TreeSet<String> keys = new TreeSet<>();
        if (resultMap instanceof Map<?, ?> map) {
            for (Object location : map.keySet()) {
                String namespace = identifierNamespace(location);
                String path = identifierPath(location);
                if (namespace == null || path == null) {
                    continue;
                }
                String stripped = path;
                if (stripped.startsWith("structure/")) {
                    stripped = stripped.substring("structure/".length());
                }
                if (stripped.endsWith(".nbt")) {
                    stripped = stripped.substring(0, stripped.length() - ".nbt".length());
                }
                if (stripped.isEmpty()) {
                    continue;
                }
                keys.add(namespace + ":" + stripped);
            }
        }
        return new ArrayList<>(keys);
    }

    private static Object resolveResourceManager(Object server) {
        try {
            Class<?> resourceManagerClass = Class.forName("net.minecraft.server.packs.resources.ResourceManager");
            Method getter = null;
            for (Method m : server.getClass().getMethods()) {
                if (m.getName().equals("getResourceManager") && m.getParameterCount() == 0
                        && resourceManagerClass.isAssignableFrom(m.getReturnType())) {
                    getter = m;
                    break;
                }
            }
            if (getter == null) {
                for (Method m : server.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && resourceManagerClass.isAssignableFrom(m.getReturnType())) {
                        getter = m;
                        break;
                    }
                }
            }
            if (getter == null) {
                return null;
            }
            getter.setAccessible(true);
            return getter.invoke(server);
        } catch (Throwable e) {
            return null;
        }
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

    private static String identifierNamespace(Object location) {
        try {
            Method m = location.getClass().getMethod("getNamespace");
            m.setAccessible(true);
            Object value = m.invoke(location);
            return value == null ? null : value.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String identifierPath(Object location) {
        try {
            Method m = location.getClass().getMethod("getPath");
            m.setAccessible(true);
            Object value = m.invoke(location);
            return value == null ? null : value.toString();
        } catch (Throwable e) {
            return null;
        }
    }

    private static Object invoke(Object target, String method) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m.invoke(target);
                }
            }
            c = c.getSuperclass();
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(method) && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m.invoke(target);
            }
        }
        throw new NoSuchMethodException(method + " on " + target.getClass().getName());
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
