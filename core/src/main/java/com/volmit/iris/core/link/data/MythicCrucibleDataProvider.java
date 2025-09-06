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

package com.volmit.iris.core.link.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.container.BlockProperty;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.data.IrisCustomData;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.utils.serialize.Chroma;
import io.lumine.mythiccrucible.MythicCrucible;
import io.lumine.mythiccrucible.items.CrucibleItem;
import io.lumine.mythiccrucible.items.ItemManager;
import io.lumine.mythiccrucible.items.blocks.CustomBlockItemContext;
import io.lumine.mythiccrucible.items.furniture.FurnitureItemContext;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;

public class MythicCrucibleDataProvider extends ExternalDataProvider {

    private ItemManager itemManager;

    public MythicCrucibleDataProvider() {
        super("MythicCrucible");
    }

    @Override
    public void init() {
        Iris.info("Setting up MythicCrucible Link...");
        try {
            this.itemManager = MythicCrucible.inst().getItemManager();
        } catch (Exception e) {
            Iris.error("Failed to set up MythicCrucible Link: Unable to fetch MythicCrucible instance!");
        }
    }

    @NotNull
    @Override
    public BlockData getBlockData(@NotNull Identifier blockId, @NotNull KMap<String, String> state) throws MissingResourceException {
        CrucibleItem crucibleItem = this.itemManager.getItem(blockId.key())
                .orElseThrow(() -> new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key()));
        CustomBlockItemContext blockItemContext = crucibleItem.getBlockData();
        FurnitureItemContext furnitureItemContext = crucibleItem.getFurnitureData();
        if (furnitureItemContext != null) {
            return new IrisCustomData(B.getAir(), ExternalDataSVC.buildState(blockId, state));
        } else if (blockItemContext != null) {
            return blockItemContext.getBlockData();
        }
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public @NotNull List<BlockProperty> getBlockProperties(@NotNull Identifier blockId) throws MissingResourceException {
        CrucibleItem crucibleItem = this.itemManager.getItem(blockId.key())
                .orElseThrow(() -> new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key()));

        if (crucibleItem.getFurnitureData() != null) {
            return YAW_FACE_BIOME_PROPERTIES;
        } else if (crucibleItem.getBlockData() != null) {
            return List.of();
        }
        throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @NotNull
    @Override
    public ItemStack getItemStack(@NotNull Identifier itemId, @NotNull KMap<String, Object> customNbt) throws MissingResourceException {
        Optional<CrucibleItem> opt = this.itemManager.getItem(itemId.key());
        return BukkitAdapter.adapt(opt.orElseThrow(() ->
                new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key()))
                .getMythicItem()
                .generateItemStack(1));
    }

    @Override
    public @NotNull Collection<@NotNull Identifier> getTypes(@NotNull DataType dataType) {
        return itemManager.getItems()
                .stream()
                .map(i -> new Identifier("crucible", i.getInternalName()))
                .filter(dataType.asPredicate(this))
                .toList();
    }

    @Override
    public void processUpdate(@NotNull Engine engine, @NotNull Block block, @NotNull Identifier blockId) {
        var parsedState = ExternalDataSVC.parseState(blockId);
        var state = parsedState.getB();
        blockId = parsedState.getA();

        Optional<CrucibleItem> item = itemManager.getItem(blockId.key());
        if (item.isEmpty()) return;
        FurnitureItemContext furniture = item.get().getFurnitureData();
        if (furniture == null) return;

        var pair = parseYawAndFace(engine, block, state);
        BiomeColor type = null;
        Chroma color = null;
        try {
            type = BiomeColor.valueOf(state.get("matchBiome").toUpperCase());
        } catch (NullPointerException | IllegalArgumentException ignored) {}
        if (type != null) {
            var biomeColor = INMS.get().getBiomeColor(block.getLocation(), type);
            if (biomeColor == null) return;
            color = Chroma.of(biomeColor.getRGB());
        }
        furniture.place(block, pair.getB(), pair.getA(), color);
    }

    @Override
    public boolean isValidProvider(@NotNull Identifier key, DataType dataType) {
        if (dataType == DataType.ENTITY) return false;
        return key.namespace().equalsIgnoreCase("crucible");
    }
}
