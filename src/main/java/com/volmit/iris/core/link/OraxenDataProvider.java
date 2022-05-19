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

package com.volmit.iris.core.link;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanicFactory;
import io.th0rgal.oraxen.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;

public class OraxenDataProvider extends ExternalDataProvider {

    private static final String FIELD_FACTORIES_MAP = "FACTORIES_BY_MECHANIC_ID";

    private Map<String, MechanicFactory> factories;

    public OraxenDataProvider() { super("Oraxen"); }

    @Override
    public void init() {
        try {
            Field f = MechanicsManager.class.getDeclaredField(FIELD_FACTORIES_MAP);
            f.setAccessible(true);
            factories = (Map<String, MechanicFactory>) f.get(null);
        } catch(NoSuchFieldException | IllegalAccessException e) {
            Iris.error("Failed to set up Oraxen Link:");
            Iris.error("\t" + e.getClass().getSimpleName());
        }
    }

    @Override
    public BlockData getBlockData(NamespacedKey blockId) throws MissingResourceException {
        MechanicFactory f = getFactory(blockId);
        if(f instanceof NoteBlockMechanicFactory)
            return ((NoteBlockMechanicFactory)f).createNoteBlockData(blockId.getKey());
        else if(f instanceof BlockMechanicFactory) {
            MultipleFacing newBlockData = (MultipleFacing) Bukkit.createBlockData(Material.MUSHROOM_STEM);
            Utils.setBlockFacing(newBlockData, ((BlockMechanic)f.getMechanic(blockId.getKey())).getCustomVariation());
            return newBlockData;
        } else
            throw new MissingResourceException("Failed to find BlockData!", blockId.getNamespace(), blockId.getKey());
    }

    @Override
    public ItemStack getItemStack(NamespacedKey itemId) throws MissingResourceException {
        Optional<ItemBuilder> opt = OraxenItems.getOptionalItemById(itemId.getKey());
        return opt.orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.getNamespace(), itemId.getKey())).build();
    }

    @Override
    public NamespacedKey[] getBlockTypes() {
        KList<NamespacedKey> names = new KList<>();
        for(String name : OraxenItems.getItemNames()) {
            try {
                NamespacedKey key = new NamespacedKey("oraxen", name);
                if(getBlockData(key) != null)
                    names.add(key);
            } catch(MissingResourceException ignored) { }
        }

        return names.toArray(new NamespacedKey[0]);
    }

    @Override
    public boolean isPresent() {
        return super.isPresent() && factories != null;
    }

    @Override
    public boolean isValidProvider(NamespacedKey key) {
        return key.getNamespace().equalsIgnoreCase("oraxen");
    }

    private MechanicFactory getFactory(NamespacedKey key) throws MissingResourceException {
        return factories.values().stream()
                .filter(i -> i.getItems().contains(key.getKey()))
                .findFirst()
                .orElseThrow(() -> new MissingResourceException("Failed to find BlockData!", key.getNamespace(), key.getKey()));
    }
}
