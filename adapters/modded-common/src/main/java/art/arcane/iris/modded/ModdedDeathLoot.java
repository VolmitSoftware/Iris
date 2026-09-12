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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedDeathLoot {
    private static final String TAG_PREFIX = "iris_loot|";
    private static final Set<String> WARNED_TABLES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_ENTITY_TYPES = ConcurrentHashMap.newKeySet();

    private ModdedDeathLoot() {
    }

    public static void bind(Engine engine, Entity entity, KList<String> tableKeys, int spawnX, int spawnY, int spawnZ, RNG rng) {
        if (engine == null || entity == null || tableKeys == null || tableKeys.isEmpty()) {
            return;
        }
        LootBinding binding = new LootBinding(rng.getSeed(), spawnX, spawnY, spawnZ, new KList<>(tableKeys));
        if (entity instanceof Mob) {
            entity.addTag(encode(binding));
            return;
        }
        if (entity instanceof ContainerEntity container && entity.level() instanceof ServerLevel level) {
            fillContainer(engine, level, container, binding);
            return;
        }

        String type = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        if (WARNED_ENTITY_TYPES.add(type)) {
            IrisLogging.warn("Iris entity loot: entity type '" + type + "' does not expose a vanilla lootable path");
        }
    }

    public static boolean replaceBaseLoot(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return false;
        }
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
        KList<ItemStack> stacks = resolve(engine, level, binding);
        emit(level, entity, stacks);
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

    private static void fillContainer(Engine engine, ServerLevel level, ContainerEntity container, LootBinding binding) {
        container.setContainerLootTable(null);
        container.clearItemStacks();
        KList<ItemStack> items = resolve(engine, level, binding);
        ModdedLootApplier.fillContainer(container, items, new RNG(binding.seed()));
    }

    private static KList<ItemStack> resolve(Engine engine, ServerLevel level, LootBinding binding) {
        KList<ItemStack> drops = new KList<>();
        for (String key : binding.tableKeys()) {
            IrisLootTable table = engine.getData().getLootLoader().load(key);
            if (table == null) {
                if (WARNED_TABLES.add(key)) {
                    IrisLogging.warn("Iris death loot: unknown loot table '" + key + "'");
                }
                continue;
            }
            drops.addAll(ModdedItemTranslator.loot(table, engine.getSeedManager().getLoot(), InventorySlotType.STORAGE, level,
                    binding.spawnX(), binding.spawnY(), binding.spawnZ()));
        }
        return drops;
    }

    private static void emit(ServerLevel level, LivingEntity entity, KList<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            entity.spawnAtLocation(level, stack);
        }
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

    private static String findTag(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    private static Engine engineFor(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof IrisModdedChunkGenerator irisGenerator) {
            return irisGenerator.engineIfBound();
        }
        return null;
    }

    private record LootBinding(long seed, int spawnX, int spawnY, int spawnZ, KList<String> tableKeys) {
    }
}
