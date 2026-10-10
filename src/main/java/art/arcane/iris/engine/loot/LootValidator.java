/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.loot;

import art.arcane.iris.core.modules.IrisValidationContext;
import art.arcane.iris.core.pack.PackIntegerChecks;
import art.arcane.iris.core.pack.PackValidationIo;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.ValidationContext;
import com.volmit.validation.Validator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Module {@code iris:loot}'s check of a pack: every loot table has sane picks, tries and entries, and every loot
 * reference of a dimension, region or biome names a table the pack has. The files are read as written, like the pack
 * validator's other file checks, so a malformed table is reported rather than failing the load.
 */
public final class LootValidator implements Validator {
    private static final List<String> LOOT_HOST_FOLDERS = List.of("dimensions", "regions", "biomes");
    private static final String JSON_EXTENSION = ".json";

    @Override
    public void validate(ValidationContext pack) {
        check(IrisValidationContext.of(pack).data().getDataFolder()).forEach(pack::report);
    }

    /** What is wrong with the loot tables and loot references of the pack in {@code packFolder}. */
    public static List<Problem> check(File packFolder) {
        List<Problem> problems = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return problems;
        }

        File lootFolder = new File(packFolder, LootResources.TABLES.folder());
        Set<String> lootKeys = keysOf(lootFolder);
        checkTables(lootFolder, problems);
        for (String folderName : LOOT_HOST_FOLDERS) {
            checkReferences(new File(packFolder, folderName), folderName, lootKeys, problems);
        }
        return problems;
    }

    private static void checkTables(File lootFolder, List<Problem> problems) {
        if (!lootFolder.isDirectory()) {
            return;
        }
        List<File> lootFiles = PackValidationIo.listJsonRecursive(lootFolder);
        lootFiles.sort(Comparator.comparing(File::getPath));
        for (File lootFile : lootFiles) {
            String lootKey = PackValidationIo.deriveKey(lootFolder, lootFile);
            List<String> errors = new ArrayList<>();
            JSONObject table = readTable(lootFile, lootKey, errors);
            if (table != null) {
                validateLootTable(lootKey, table, errors);
            }
            report(problems, Problem::error, Subject.file(LootResources.TABLES.folder() + "/" + lootKey + JSON_EXTENSION), errors);
        }
    }

    private static void checkReferences(File resourceFolder, String folderName, Set<String> lootKeys, List<Problem> problems) {
        if (!resourceFolder.isDirectory()) {
            return;
        }
        List<File> resourceFiles = PackValidationIo.listJsonRecursive(resourceFolder);
        resourceFiles.sort(Comparator.comparing(File::getPath));
        for (File resourceFile : resourceFiles) {
            JSONObject resource = PackValidationIo.readJson(resourceFile);
            if (resource == null || !resource.has("loot")) {
                continue;
            }
            String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            validateLootReference(hostType(folderName), resourceKey, resource.opt("loot"), lootKeys, errors, warnings);
            Subject subject = Subject.file(folderName + "/" + resourceKey + JSON_EXTENSION);
            report(problems, Problem::error, subject, errors);
            report(problems, Problem::warning, subject, warnings);
        }
    }

    private static void report(List<Problem> problems, java.util.function.BiFunction<Subject, String, Problem> severity,
                               Subject subject, List<String> messages) {
        for (String message : messages) {
            problems.add(severity.apply(subject, message));
        }
    }

    private static Set<String> keysOf(File folder) {
        Set<String> keys = new HashSet<>();
        if (!folder.isDirectory()) {
            return keys;
        }
        for (File file : PackValidationIo.listJsonRecursive(folder)) {
            String key = PackValidationIo.deriveKey(folder, file);
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static JSONObject readTable(File file, String key, List<String> errors) {
        try {
            return new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            String reason = e.getMessage();
            if (reason == null || reason.isBlank()) {
                reason = e.getClass().getSimpleName();
            }
            errors.add("Loot table '" + key + "' has invalid JSON: " + reason);
            return null;
        }
    }

    private static String hostType(String folderName) {
        return switch (folderName) {
            case "dimensions" -> "Dimension";
            case "regions" -> "Region";
            case "biomes" -> "Biome";
            default -> "Resource";
        };
    }

    private static void validateLootTable(String lootKey, JSONObject table, List<String> blockingErrors) {
        String path = "Loot table '" + lootKey + "'";
        Integer rarity = PackIntegerChecks.integerField(table, "rarity", 1, path, blockingErrors);
        Integer minimumPicked = PackIntegerChecks.integerField(table, "minPicked", 1, path, blockingErrors);
        Integer maximumPicked = PackIntegerChecks.integerField(table, "maxPicked", 5, path, blockingErrors);
        Integer maximumTries = PackIntegerChecks.integerField(table, "maxTries", 10, path, blockingErrors);
        PackIntegerChecks.requireMinimum(path + ".rarity", rarity, 1, blockingErrors);
        PackIntegerChecks.requireMinimum(path + ".minPicked", minimumPicked, 0, blockingErrors);
        PackIntegerChecks.requireMinimum(path + ".maxPicked", maximumPicked, 1, blockingErrors);
        PackIntegerChecks.requireMinimum(path + ".maxTries", maximumTries, 1, blockingErrors);
        PackIntegerChecks.requireMaximum(path + ".minPicked", minimumPicked, LootTable.MAX_PICKED, blockingErrors);
        PackIntegerChecks.requireMaximum(path + ".maxPicked", maximumPicked, LootTable.MAX_PICKED, blockingErrors);
        PackIntegerChecks.requireMaximum(path + ".maxTries", maximumTries, LootTable.MAX_TRIES, blockingErrors);
        PackIntegerChecks.requireOrdered(path + ".minPicked", minimumPicked, path + ".maxPicked", maximumPicked, blockingErrors);

        JSONArray entries = table.optJSONArray("loot");
        if (entries == null || entries.length() == 0) {
            blockingErrors.add(path + ".loot must be a non-empty array.");
            return;
        }
        for (int entryIndex = 0; entryIndex < entries.length(); entryIndex++) {
            JSONObject entry = entries.optJSONObject(entryIndex);
            String entryPath = path + ".loot[" + entryIndex + "]";
            if (entry == null) {
                blockingErrors.add(entryPath + " must be an object.");
                continue;
            }
            String type = entry.optString("type", "").trim();
            if (type.isEmpty()) {
                blockingErrors.add(entryPath + ".type must not be blank.");
            }
            Integer entryRarity = PackIntegerChecks.integerField(entry, "rarity", 1, entryPath, blockingErrors);
            Integer minimumAmount = PackIntegerChecks.integerField(entry, "minAmount", 1, entryPath, blockingErrors);
            Integer maximumAmount = PackIntegerChecks.integerField(entry, "maxAmount", 1, entryPath, blockingErrors);
            PackIntegerChecks.requireMinimum(entryPath + ".rarity", entryRarity, 1, blockingErrors);
            PackIntegerChecks.requireMinimum(entryPath + ".minAmount", minimumAmount, 1, blockingErrors);
            PackIntegerChecks.requireMinimum(entryPath + ".maxAmount", maximumAmount, 1, blockingErrors);
            PackIntegerChecks.requireMaximum(entryPath + ".minAmount", minimumAmount, LootEntry.MAX_AMOUNT, blockingErrors);
            PackIntegerChecks.requireMaximum(entryPath + ".maxAmount", maximumAmount, LootEntry.MAX_AMOUNT, blockingErrors);
            PackIntegerChecks.requireOrdered(entryPath + ".minAmount", minimumAmount,
                    entryPath + ".maxAmount", maximumAmount, blockingErrors);
            validateLootEnchantments(entryPath, entry.opt("enchantments"), blockingErrors);
        }
    }

    private static void validateLootEnchantments(String entryPath, Object rawEnchantments,
                                                  List<String> blockingErrors) {
        if (rawEnchantments == null || rawEnchantments == JSONObject.NULL) {
            return;
        }
        if (!(rawEnchantments instanceof JSONArray enchantments)) {
            blockingErrors.add(entryPath + ".enchantments must be an array.");
            return;
        }
        for (int enchantmentIndex = 0; enchantmentIndex < enchantments.length(); enchantmentIndex++) {
            JSONObject enchantment = enchantments.optJSONObject(enchantmentIndex);
            String enchantmentPath = entryPath + ".enchantments[" + enchantmentIndex + "]";
            if (enchantment == null) {
                blockingErrors.add(enchantmentPath + " must be an object.");
                continue;
            }
            if (enchantment.optString("enchantment", "").isBlank()) {
                blockingErrors.add(enchantmentPath + ".enchantment must not be blank.");
            }
            Integer minimumLevel = PackIntegerChecks.integerField(enchantment, "minLevel", 1, enchantmentPath, blockingErrors);
            Integer maximumLevel = PackIntegerChecks.integerField(enchantment, "maxLevel", 1, enchantmentPath, blockingErrors);
            PackIntegerChecks.requireMinimum(enchantmentPath + ".minLevel", minimumLevel, 1, blockingErrors);
            PackIntegerChecks.requireMinimum(enchantmentPath + ".maxLevel", maximumLevel, 1, blockingErrors);
            PackIntegerChecks.requireOrdered(enchantmentPath + ".minLevel", minimumLevel,
                    enchantmentPath + ".maxLevel", maximumLevel, blockingErrors);
            if (enchantment.has("chance")) {
                Object rawChance = enchantment.opt("chance");
                if (!(rawChance instanceof Number number)
                        || !Double.isFinite(number.doubleValue())
                        || number.doubleValue() < 0D
                        || number.doubleValue() > 1D) {
                    blockingErrors.add(enchantmentPath + ".chance must be a finite number from 0 to 1.");
                }
            }
        }
    }

    private static void validateLootReference(String resourceType, String resourceKey, Object rawLoot,
                                              Set<String> lootKeys, List<String> blockingErrors, List<String> warnings) {
        String path = resourceType + " '" + resourceKey + "'.loot";
        if (!(rawLoot instanceof JSONObject reference)) {
            blockingErrors.add(path + " must be an object.");
            return;
        }
        boolean clearMode = false;
        if (reference.has("mode")) {
            Object rawMode = reference.opt("mode");
            if (!(rawMode instanceof String mode)
                    || !Set.of("ADD", "CLEAR", "REPLACE", "FALLBACK").contains(mode)) {
                blockingErrors.add(path + ".mode must be ADD, CLEAR, REPLACE, or FALLBACK.");
            } else {
                clearMode = "CLEAR".equals(mode);
            }
        }
        if (reference.has("multiplier")) {
            Object rawMultiplier = reference.opt("multiplier");
            if (!(rawMultiplier instanceof Number multiplier)
                    || !Double.isFinite(multiplier.doubleValue())
                    || multiplier.doubleValue() < 0D
                    || multiplier.doubleValue() > LootSettings.MAX_MULTIPLIER) {
                blockingErrors.add(path + ".multiplier must be a finite number from 0 to "
                        + (int) LootSettings.MAX_MULTIPLIER + ".");
            }
        }
        if (!reference.has("tables")) {
            return;
        }
        JSONArray tables = reference.optJSONArray("tables");
        if (tables == null) {
            blockingErrors.add(path + ".tables must be an array.");
            return;
        }
        if (clearMode && tables.length() > 0) {
            warnings.add(path + " uses mode CLEAR and lists " + tables.length()
                    + " table(s); CLEAR clears parent tables and contributes no tables of its own, so these entries are dead. Use REPLACE to substitute them.");
        }
        for (int tableIndex = 0; tableIndex < tables.length(); tableIndex++) {
            Object rawTableKey = tables.opt(tableIndex);
            if (!(rawTableKey instanceof String tableKey) || tableKey.isBlank()) {
                blockingErrors.add(path + ".tables[" + tableIndex + "] must name a loot table.");
            } else if (!lootKeys.contains(tableKey)) {
                blockingErrors.add(path + ".tables[" + tableIndex
                        + "] references missing loot table '" + tableKey + "'.");
            }
        }
    }
}
