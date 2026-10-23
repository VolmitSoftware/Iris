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

import art.arcane.iris.builtin.loot.LootEnchantment;
import art.arcane.iris.builtin.loot.LootEntry;
import art.arcane.iris.builtin.loot.LootMode;
import art.arcane.iris.builtin.loot.LootSettings;
import art.arcane.iris.builtin.loot.LootTable;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;
import com.volmit.validation.ValidationContext;
import com.volmit.validation.Validator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Module {@code iris:loot}'s check of a pack: every loot table has sane picks, tries and entries, and every loot
 * reference of a dimension, region or biome names a table the pack has. It checks the tables and settings as they
 * decoded; a file that is not valid JSON, or holds a value of the wrong type, fails to load, and the check reports why.
 */
public final class LootValidator implements Validator {
    private static final String JSON_EXTENSION = ".json";
    private static final String LEVEL_FOLDER_SEPARATOR = "/";

    @Override
    public void validate(ValidationContext pack) {
        checkTables(pack.resources()).forEach(pack::report);
        checkReferences(pack).forEach(pack::report);
    }

    private static List<Problem> checkTables(PackResources resources) {
        List<Problem> problems = new ArrayList<>();
        for (ResourceId id : resources.ids(LootResources.TABLES)) {
            String lootKey = id.path();
            List<String> errors = new ArrayList<>();
            LootTable table = readTable(resources, id, lootKey, errors);
            if (table != null) {
                validateLootTable(lootKey, table, errors);
            }
            report(problems, Problem::error, Subject.file(LootResources.TABLES.folder() + "/" + lootKey + JSON_EXTENSION), errors);
        }
        return problems;
    }

    private static List<Problem> checkReferences(ValidationContext pack) {
        Set<ResourceId> lootIds = Set.copyOf(pack.resources().ids(LootResources.TABLES));
        List<Problem> problems = new ArrayList<>();
        List<Entry<LootSettings>> declared = new ArrayList<>(pack.settings().declared(LootSettings.class).entries());
        declared.sort(Comparator.comparing(Entry<LootSettings>::level).thenComparing(Entry::source));
        for (Entry<LootSettings> entry : declared) {
            String resourceKey = keyOf(entry);
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            validateLootReference(pack.resources(), hostType(entry.level()), resourceKey, entry.value(), lootIds, errors, warnings);
            Subject subject = Subject.file(folderOf(entry.level()) + "/" + resourceKey + JSON_EXTENSION);
            report(problems, Problem::error, subject, errors);
            report(problems, Problem::warning, subject, warnings);
        }
        return problems;
    }

    private static void report(List<Problem> problems, BiFunction<Subject, String, Problem> severity,
                               Subject subject, List<String> messages) {
        for (String message : messages) {
            problems.add(severity.apply(subject, message));
        }
    }

    private static LootTable readTable(PackResources resources, ResourceId id, String key, List<String> errors) {
        try {
            return resources.find(LootResources.TABLES, id).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            errors.add("Loot table '" + key + "' cannot be read: " + failedToLoad.getMessage());
            return null;
        }
    }

    /** The key of the dimension, region or biome that declared {@code entry}: its source id is {@code <pack>:<level>/<key>}. */
    private static String keyOf(Entry<LootSettings> entry) {
        String path = entry.source().path();
        return path.substring(path.indexOf(LEVEL_FOLDER_SEPARATOR) + 1);
    }

    private static String hostType(Level level) {
        return switch (level) {
            case DIMENSION -> "Dimension";
            case REGION -> "Region";
            case BIOME -> "Biome";
            case PLACEMENT -> "Resource";
        };
    }

    private static String folderOf(Level level) {
        return switch (level) {
            case DIMENSION -> "dimensions";
            case REGION -> "regions";
            case BIOME -> "biomes";
            case PLACEMENT -> "placements";
        };
    }

    private static void validateLootTable(String lootKey, LootTable table, List<String> blockingErrors) {
        String path = "Loot table '" + lootKey + "'";
        requireMinimum(path + ".rarity", table.rarity(), 1, blockingErrors);
        requireMinimum(path + ".minPicked", table.minPicked(), 0, blockingErrors);
        requireMinimum(path + ".maxPicked", table.maxPicked(), 1, blockingErrors);
        requireMinimum(path + ".maxTries", table.maxTries(), 1, blockingErrors);
        requireMaximum(path + ".minPicked", table.minPicked(), LootTable.MAX_PICKED, blockingErrors);
        requireMaximum(path + ".maxPicked", table.maxPicked(), LootTable.MAX_PICKED, blockingErrors);
        requireMaximum(path + ".maxTries", table.maxTries(), LootTable.MAX_TRIES, blockingErrors);
        requireOrdered(path + ".minPicked", table.minPicked(), path + ".maxPicked", table.maxPicked(), blockingErrors);

        List<LootEntry> entries = table.loot();
        if (entries.isEmpty()) {
            blockingErrors.add(path + ".loot must be a non-empty array.");
            return;
        }
        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            validateLootEntry(path + ".loot[" + entryIndex + "]", entries.get(entryIndex), blockingErrors);
        }
    }

    private static void validateLootEntry(String entryPath, LootEntry entry, List<String> blockingErrors) {
        if (entry == null) {
            blockingErrors.add(entryPath + " must be an object.");
            return;
        }
        if (entry.type().trim().isEmpty()) {
            blockingErrors.add(entryPath + ".type must not be blank.");
        }
        requireMinimum(entryPath + ".rarity", entry.rarity(), 1, blockingErrors);
        requireMinimum(entryPath + ".minAmount", entry.minAmount(), 1, blockingErrors);
        requireMinimum(entryPath + ".maxAmount", entry.maxAmount(), 1, blockingErrors);
        requireMaximum(entryPath + ".minAmount", entry.minAmount(), LootEntry.MAX_AMOUNT, blockingErrors);
        requireMaximum(entryPath + ".maxAmount", entry.maxAmount(), LootEntry.MAX_AMOUNT, blockingErrors);
        requireOrdered(entryPath + ".minAmount", entry.minAmount(), entryPath + ".maxAmount", entry.maxAmount(), blockingErrors);

        List<LootEnchantment> enchantments = entry.enchantments();
        for (int enchantmentIndex = 0; enchantmentIndex < enchantments.size(); enchantmentIndex++) {
            validateEnchantment(entryPath + ".enchantments[" + enchantmentIndex + "]", enchantments.get(enchantmentIndex), blockingErrors);
        }
    }

    private static void validateEnchantment(String enchantmentPath, LootEnchantment enchantment, List<String> blockingErrors) {
        if (enchantment == null) {
            blockingErrors.add(enchantmentPath + " must be an object.");
            return;
        }
        String name = enchantment.enchantment();
        if (name == null || name.isBlank()) {
            blockingErrors.add(enchantmentPath + ".enchantment must not be blank.");
        }
        requireMinimum(enchantmentPath + ".minLevel", enchantment.minLevel(), 1, blockingErrors);
        requireMinimum(enchantmentPath + ".maxLevel", enchantment.maxLevel(), 1, blockingErrors);
        requireOrdered(enchantmentPath + ".minLevel", enchantment.minLevel(),
                enchantmentPath + ".maxLevel", enchantment.maxLevel(), blockingErrors);
        double chance = enchantment.chance();
        if (!Double.isFinite(chance) || chance < 0D || chance > 1D) {
            blockingErrors.add(enchantmentPath + ".chance must be a finite number from 0 to 1.");
        }
    }

    private static void validateLootReference(PackResources resources, String resourceType, String resourceKey, LootSettings reference,
                                              Set<ResourceId> lootIds, List<String> blockingErrors, List<String> warnings) {
        String path = resourceType + " '" + resourceKey + "'.loot";
        double multiplier = reference.multiplier();
        if (!Double.isFinite(multiplier) || multiplier < 0D || multiplier > LootSettings.MAX_MULTIPLIER) {
            blockingErrors.add(path + ".multiplier must be a finite number from 0 to " + (int) LootSettings.MAX_MULTIPLIER + ".");
        }
        List<String> tables = reference.tables();
        if (reference.mode() == LootMode.CLEAR && !tables.isEmpty()) {
            warnings.add(path + " uses mode CLEAR and lists " + tables.size()
                    + " table(s); CLEAR clears parent tables and contributes no tables of its own, so these entries are dead. Use REPLACE to substitute them.");
        }
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            String tableKey = tables.get(tableIndex);
            if (tableKey == null || tableKey.isBlank()) {
                blockingErrors.add(path + ".tables[" + tableIndex + "] must name a loot table.");
            } else if (!lootIds.contains(resources.idOf(tableKey))) {
                blockingErrors.add(path + ".tables[" + tableIndex + "] references missing loot table '" + tableKey + "'.");
            }
        }
    }

    private static void requireMinimum(String fieldPath, int value, int minimum, List<String> blockingErrors) {
        if (value < minimum) {
            blockingErrors.add(fieldPath + " must be at least " + minimum + ".");
        }
    }

    private static void requireMaximum(String fieldPath, int value, int maximum, List<String> blockingErrors) {
        if (value > maximum) {
            blockingErrors.add(fieldPath + " must be at most " + maximum + ".");
        }
    }

    private static void requireOrdered(String minimumPath, int minimum, String maximumPath, int maximum, List<String> blockingErrors) {
        if (minimum > maximum) {
            blockingErrors.add(minimumPath + " must not exceed " + maximumPath + ".");
        }
    }
}
