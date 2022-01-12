/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.util.collection.KList;
import io.th0rgal.oraxen.items.OraxenItems;
import io.th0rgal.oraxen.mechanics.Mechanic;
import io.th0rgal.oraxen.mechanics.MechanicFactory;
import io.th0rgal.oraxen.mechanics.MechanicsManager;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanic;
import io.th0rgal.oraxen.mechanics.provided.gameplay.block.BlockMechanicFactory;
import io.th0rgal.oraxen.mechanics.provided.gameplay.noteblock.NoteBlockMechanicFactory;
import io.th0rgal.oraxen.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.Map;

public class OraxenLink {
    private static final String[] EMPTY = new String[0];

    public boolean supported() {
        return getOraxen() != null;
    }

    public BlockData getBlockDataFor(String id) {
        if(!supported()) {
            return null;
        }

        MechanicFactory f = getFactory(id);

        if(f == null) {
            return null;
        }

        Mechanic m = f.getMechanic(id);

        // TODO: Why isnt there a simple getBlockData() function?
        if(m.getFactory() instanceof NoteBlockMechanicFactory) {
            return ((NoteBlockMechanicFactory) m.getFactory()).createNoteBlockData(id);
        } else if(m.getFactory() instanceof BlockMechanicFactory) {
            MultipleFacing newBlockData = (MultipleFacing) Bukkit.createBlockData(Material.MUSHROOM_STEM);
            Utils.setBlockFacing(newBlockData, ((BlockMechanic) m).getCustomVariation());
            return newBlockData;
        }

        return null;
    }

    public MechanicFactory getFactory(String id) {
        if(!supported()) {
            return null;
        }

        try {
            Field f = MechanicsManager.class.getDeclaredField("FACTORIES_BY_MECHANIC_ID");
            f.setAccessible(true);
            Map<String, MechanicFactory> map = (Map<String, MechanicFactory>) f.get(null);

            for(MechanicFactory i : map.values()) {
                if(i.getItems().contains(id)) {
                    return i;
                }
            }
        } catch(Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    public String[] getItemTypes() {
        if(!supported()) {
            return EMPTY;
        }

        KList<String> v = new KList<>();

        for(String i : OraxenItems.getItemNames()) {
            if(getBlockDataFor(i) != null) {
                v.add(i);
            }
        }

        return v.toArray(new String[0]);
    }

    public Plugin getOraxen() {

        return Bukkit.getPluginManager().getPlugin("Oraxen");
    }
}
