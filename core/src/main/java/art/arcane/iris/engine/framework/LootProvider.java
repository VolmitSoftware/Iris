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

package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.object.InventorySlotType;
import art.arcane.iris.engine.object.IrisLootReference;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;

public interface LootProvider {
    void scramble(Inventory inventory, RNG rng);

    void injectTables(KList<IrisLootTable> list, IrisLootReference r, boolean fallback);

    KList<IrisLootTable> getLootTables(RNG rng, Block b);

    void addItems(boolean debug, Inventory inv, RNG rng, KList<IrisLootTable> tables, InventorySlotType slot, World world, int x, int y, int z, int mgf);
}
