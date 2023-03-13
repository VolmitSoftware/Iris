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
import com.volmit.iris.util.reflect.WrappedField;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.stringblock.StringBlockMechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;

public class OraxenDataProvider extends ExternalDataProvider {

    private static final String FIELD_FACTORIES_MAP = "FACTORIES_BY_MECHANIC_ID";

    private WrappedField<MechanicsManager, Map<String, MechanicFactory>> factories;

    public OraxenDataProvider() {
        super("Oraxen");
    }

    @Override
    public void init() {
        this.factories = new WrappedField<>(MechanicsManager.class, FIELD_FACTORIES_MAP);
        if(this.factories.hasFailed()) {
            Iris.error("Failed to set up Oraxen Link: Unable to fetch MechanicFactoriesMap!");
        }
    }

    @Override
    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
        MechanicFactory factory = getFactory(blockId);
        if (factory instanceof NoteBlockMechanicFactory f)
            return f.createNoteBlockData(blockId.key());
        else if (factory instanceof BlockMechanicFactory f) {
            MultipleFacing newBlockData = (MultipleFacing) Bukkit.createBlockData(Material.MUSHROOM_STEM);
            BlockMechanic.setBlockFacing(newBlockData, ((BlockMechanic) f.getMechanic(blockId.key())).getCustomVariation());
            return newBlockData;
        } else if (factory instanceof StringBlockMechanicFactory f) {
            return f.createTripwireData(blockId.key());
        } else
            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
    }

    @Override
    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
        Optional<ItemBuilder> opt = OraxenItems.getOptionalItemById(itemId.key());
        return opt.orElseThrow(() -> new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key())).build();
    }

    @Override
    public Identifier[] getBlockTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : OraxenItems.getItemNames()) {
            try {
                Identifier key = new Identifier("oraxen", name);
                if (getBlockData(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) { }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public Identifier[] getItemTypes() {
        KList<Identifier> names = new KList<>();
        for (String name : OraxenItems.getItemNames()) {
            try {
                Identifier key = new Identifier("oraxen", name);
                if (getItemStack(key) != null)
                    names.add(key);
            } catch (MissingResourceException ignored) { }
        }

        return names.toArray(new Identifier[0]);
    }

    @Override
    public boolean isPresent() {
        return super.isPresent() && factories != null;
    }

    @Override
    public boolean isValidProvider(Identifier key, boolean isItem) {
        return key.namespace().equalsIgnoreCase("oraxen");
    }

    private MechanicFactory getFactory(Identifier key) throws MissingResourceException {
        return factories.get().values().stream()
                .filter(i -> i.getItems().contains(key.key()))
                .findFirst()
                .orElseThrow(() -> new MissingResourceException("Failed to find BlockData!", key.namespace(), key.key()));
    }
}
