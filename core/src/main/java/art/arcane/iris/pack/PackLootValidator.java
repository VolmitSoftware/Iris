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

package art.arcane.iris.pack;

import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootReference;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PackLootValidator {
    private PackLootValidator() {
    }

    record LootGraphIssues(List<String> errors, List<String> warnings) {
        LootGraphIssues {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    static LootGraphIssues validateLootGraph(File packFolder) {
        List<String> blockingErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (packFolder == null || !packFolder.isDirectory()) {
            return new LootGraphIssues(blockingErrors, warnings);
        }

        File lootFolder = new File(packFolder, PackValidator.LOOT_FOLDER);
        Set<String> lootKeys = ContentKeyValidator.deriveRegistrantKeysExact(lootFolder);
        if (lootFolder.isDirectory()) {
            List<File> lootFiles = PackValidationIo.listJsonRecursive(lootFolder);
            lootFiles.sort(Comparator.comparing(File::getPath));
            for (File lootFile : lootFiles) {
                String lootKey = PackValidationIo.deriveKey(lootFolder, lootFile);
                JSONObject table = PackStructurePlacementValidator.readGraphJson(
                        lootFile, "Loot table", lootKey, blockingErrors);
                if (table != null) {
                    validateLootTable(lootKey, table, blockingErrors);
                }
            }
        }

        for (String folderName : PackValidator.STRUCTURE_HOST_FOLDERS) {
            File resourceFolder = new File(packFolder, folderName);
            if (!resourceFolder.isDirectory()) {
                continue;
            }
            List<File> resourceFiles = PackValidationIo.listJsonRecursive(resourceFolder);
            resourceFiles.sort(Comparator.comparing(File::getPath));
            for (File resourceFile : resourceFiles) {
                JSONObject resource = PackValidationIo.readJson(resourceFile);
                if (resource == null || !resource.has("loot")) {
                    continue;
                }
                String resourceType = PackStructurePlacementValidator.structureHostType(folderName);
                String resourceKey = PackValidationIo.deriveKey(resourceFolder, resourceFile);
                validateLootReference(resourceType, resourceKey, resource.opt("loot"), lootKeys, blockingErrors, warnings);
            }
        }
        return new LootGraphIssues(blockingErrors, warnings);
    }

    private static void validateLootTable(String lootKey, JSONObject table, List<String> blockingErrors) {
        String path = "Loot table '" + lootKey + "'";
        Integer rarity = lootInteger(table, "rarity", 1, path, blockingErrors);
        Integer minimumPicked = lootInteger(table, "minPicked", 1, path, blockingErrors);
        Integer maximumPicked = lootInteger(table, "maxPicked", 5, path, blockingErrors);
        Integer maximumTries = lootInteger(table, "maxTries", 10, path, blockingErrors);
        requireMinimum(path + ".rarity", rarity, 1, blockingErrors);
        requireMinimum(path + ".minPicked", minimumPicked, 0, blockingErrors);
        requireMinimum(path + ".maxPicked", maximumPicked, 1, blockingErrors);
        requireMinimum(path + ".maxTries", maximumTries, 1, blockingErrors);
        requireMaximum(path + ".minPicked", minimumPicked, IrisLootTable.MAX_PICKED, blockingErrors);
        requireMaximum(path + ".maxPicked", maximumPicked, IrisLootTable.MAX_PICKED, blockingErrors);
        requireMaximum(path + ".maxTries", maximumTries, IrisLootTable.MAX_TRIES, blockingErrors);
        requireOrdered(path + ".minPicked", minimumPicked, path + ".maxPicked", maximumPicked, blockingErrors);

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
            Integer entryRarity = lootInteger(entry, "rarity", 1, entryPath, blockingErrors);
            Integer minimumAmount = lootInteger(entry, "minAmount", 1, entryPath, blockingErrors);
            Integer maximumAmount = lootInteger(entry, "maxAmount", 1, entryPath, blockingErrors);
            requireMinimum(entryPath + ".rarity", entryRarity, 1, blockingErrors);
            requireMinimum(entryPath + ".minAmount", minimumAmount, 1, blockingErrors);
            requireMinimum(entryPath + ".maxAmount", maximumAmount, 1, blockingErrors);
            requireMaximum(entryPath + ".minAmount", minimumAmount, IrisLoot.MAX_AMOUNT, blockingErrors);
            requireMaximum(entryPath + ".maxAmount", maximumAmount, IrisLoot.MAX_AMOUNT, blockingErrors);
            requireOrdered(entryPath + ".minAmount", minimumAmount,
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
            Integer minimumLevel = lootInteger(enchantment, "minLevel", 1, enchantmentPath, blockingErrors);
            Integer maximumLevel = lootInteger(enchantment, "maxLevel", 1, enchantmentPath, blockingErrors);
            requireMinimum(enchantmentPath + ".minLevel", minimumLevel, 1, blockingErrors);
            requireMinimum(enchantmentPath + ".maxLevel", maximumLevel, 1, blockingErrors);
            requireOrdered(enchantmentPath + ".minLevel", minimumLevel,
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
                    || multiplier.doubleValue() > IrisLootReference.MAX_MULTIPLIER) {
                blockingErrors.add(path + ".multiplier must be a finite number from 0 to "
                        + (int) IrisLootReference.MAX_MULTIPLIER + ".");
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

    static Integer lootInteger(JSONObject object, String field, int defaultValue,
                               String path, List<String> blockingErrors) {
        if (!object.has(field)) {
            return defaultValue;
        }
        Object rawValue = object.opt(field);
        if (!(rawValue instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.longValue() < Integer.MIN_VALUE
                || number.longValue() > Integer.MAX_VALUE) {
            blockingErrors.add(path + "." + field + " must be an integer.");
            return null;
        }
        return number.intValue();
    }

    static void requireMinimum(String fieldPath, Integer value, int minimum,
                               List<String> blockingErrors) {
        if (value != null && value < minimum) {
            blockingErrors.add(fieldPath + " must be at least " + minimum + ".");
        }
    }

    static void requireMaximum(String fieldPath, Integer value, int maximum,
                               List<String> blockingErrors) {
        if (value != null && value > maximum) {
            blockingErrors.add(fieldPath + " must be at most " + maximum + ".");
        }
    }

    static void requireOrdered(String minimumPath, Integer minimum, String maximumPath,
                               Integer maximum, List<String> blockingErrors) {
        if (minimum != null && maximum != null && minimum > maximum) {
            blockingErrors.add(minimumPath + " must not exceed " + maximumPath + ".");
        }
    }
}
