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

package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeSpawnedEntity;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEntityLoot;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedDeathLoot {
    private static final String TAG_PREFIX = "iris_loot|";
    private static final Set<String> WARNED_TABLES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_ENTITY_TYPES = ConcurrentHashMap.newKeySet();

    private ModdedDeathLoot() {
    }

    public static void bind(Engine engine, NativeSpawnedEntity spawned, KList<String> tableKeys, int spawnX, int spawnY, int spawnZ, RNG rng) {
        NativeEntityLoot entity = NativeEntityLoot.of(spawned);
        if (engine == null || entity == null || tableKeys == null || tableKeys.isEmpty()) {
            return;
        }
        LootBinding binding = new LootBinding(rng.getSeed(), spawnX, spawnY, spawnZ, new KList<>(tableKeys));
        if (entity.mob()) {
            entity.tag(encode(binding));
            return;
        }
        if (entity.container()) {
            fillContainer(engine, entity, binding);
            return;
        }

        String type = entity.type();
        if (WARNED_ENTITY_TYPES.add(type)) {
            IrisLogging.warn("Iris entity loot: entity type '" + type + "' does not expose a vanilla lootable path");
        }
    }

    public static boolean replaceBaseLoot(NativeEntityLoot entity) {
        if (entity == null || entity.world() == null) {
            return false;
        }
        NativeWorld level = entity.world();
        String encoded = findTag(entity);
        if (encoded == null) {
            return false;
        }
        LootBinding binding = decode(encoded);
        if (binding == null) {
            return true;
        }
        Engine engine = engineFor(level);
        if (engine == null) {
            return true;
        }
        KList<NativeItemStack> stacks = resolve(engine, level, binding);
        entity.emit(stacks);
        return true;
    }

    static boolean hasBindingTag(Set<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith(TAG_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    private static void fillContainer(Engine engine, NativeEntityLoot entity, LootBinding binding) {
        entity.prepareContainer();
        KList<NativeItemStack> items = resolve(engine, entity.world(), binding);
        entity.fill(items, new RNG(binding.seed()), message -> IrisLogging.debug("Iris loot: " + message));
    }

    private static KList<NativeItemStack> resolve(Engine engine, NativeWorld level, LootBinding binding) {
        KList<NativeItemStack> drops = new KList<>();
        for (String key : binding.tableKeys()) {
            IrisLootTable table = engine.getData().getLootLoader().load(key);
            if (table == null) {
                if (WARNED_TABLES.add(key)) {
                    IrisLogging.warn("Iris death loot: unknown loot table '" + key + "'");
                }
                continue;
            }
            drops.addAll(ModdedItemTranslator.loot(table, engine.getSeedManager().getLoot(), InventorySlotType.STORAGE, ModdedItemTranslator.context(level),
                    binding.spawnX(), binding.spawnY(), binding.spawnZ()));
        }
        return drops;
    }

    private static String encode(LootBinding binding) {
        return TAG_PREFIX + binding.seed() + "|" + binding.spawnX() + "|" + binding.spawnY() + "|"
                + binding.spawnZ() + "|" + String.join(",", binding.tableKeys());
    }

    private static LootBinding decode(String encoded) {
        String[] parts = encoded.substring(TAG_PREFIX.length()).split("\\|", 5);
        if (parts.length < 5) {
            IrisLogging.debug("Iris death loot: malformed loot tag '" + encoded + "'");
            return null;
        }
        try {
            long seed = Long.parseLong(parts[0]);
            int spawnX = Integer.parseInt(parts[1]);
            int spawnY = Integer.parseInt(parts[2]);
            int spawnZ = Integer.parseInt(parts[3]);
            KList<String> tableKeys = new KList<>();
            for (String key : parts[4].split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty()) {
                    tableKeys.add(trimmed);
                }
            }
            return new LootBinding(seed, spawnX, spawnY, spawnZ, tableKeys);
        } catch (NumberFormatException error) {
            IrisLogging.debug("Iris death loot: malformed loot tag '" + encoded + "'");
            return null;
        }
    }

    private static String findTag(NativeEntityLoot entity) {
        for (String tag : entity.tags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    private static Engine engineFor(NativeWorld level) {
        IrisModdedChunkGenerator generator = NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class);
        return generator == null ? null : generator.engineIfBound();
    }

    private record LootBinding(long seed, int spawnX, int spawnY, int spawnZ, KList<String> tableKeys) {
    }
}
