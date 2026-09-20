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

import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemContext;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemTranslator;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLoot;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedItemTranslator {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final NativeItemContext.Options OPTIONS = new NativeItemContext.Options("iris", ModdedItemTranslator::warnOnce);

    private ModdedItemTranslator() {
    }

    public static KList<NativeItemStack> loot(IrisLootTable table, long lootSeed, InventorySlotType slot, NativeItemContext context, int x, int y, int z) {
        KList<NativeItemStack> out = new KList<>();
        KList<IrisLoot> entries = table.getLoot();
        if (entries.isEmpty() || table.getMaxTries() <= 0) {
            return out;
        }

        RNG rng = LootResolver.tableRng(lootSeed, table, x, y, z);
        int m = 0;
        int c = 0;
        int mx = Math.max(0, LootResolver.inclusive(rng, table.getMinPicked(), table.getMaxPicked()));

        while (m < mx && c++ < table.getMaxTries()) {
            int entryIndex = rng.nextInt(entries.size());
            IrisLoot entry = entries.get(entryIndex);
            if (entry == null || entry.getSlotTypes() != slot) {
                continue;
            }
            NativeItemStack item = item(entry, table, rng, lootSeed, entryIndex, context, x, y, z);
            if (item != null && !item.isEmpty()) {
                out.add(item);
                m++;
            }
        }

        return out;
    }

    public static NativeItemStack stack(IrisLoot loot, RNG rng, NativeItemContext context) {
        try {
            return NativeItemTranslator.stack(new IrisItemRecipe(loot, rng), context);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    public static NativeItemStack item(IrisLoot loot, IrisLootTable table, RNG rng, long lootSeed, int entryIndex, NativeItemContext context, int x, int y, int z) {
        long combinedRarity = LootResolver.combinedRarity(loot.getRarity(), table.getRarity());
        if (!LootResolver.spatialOneIn(lootSeed, table, entryIndex, x, y, z, combinedRarity)) {
            return null;
        }

        try {
            return NativeItemTranslator.stack(new IrisItemRecipe(loot, rng), context);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    public static NativeItemContext context(NativeWorld world) {
        return new NativeItemContext(world, OPTIONS);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            IrisLogging.warn("Iris loot: " + message);
        }
    }
}
