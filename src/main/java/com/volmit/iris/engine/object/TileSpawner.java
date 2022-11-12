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

package com.volmit.iris.engine.object;

import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.ListTag;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
public class TileSpawner implements TileData<CreatureSpawner> {

    public static final int id = 1;

    private EntityType entityType;

    @Override
    public String getTileId() {
        return "minecraft:mob_spawner";
    }

    @Override
    public boolean isApplicable(BlockData data) {
        return data.getMaterial() == Material.SPAWNER;
    }

    @Override
    public void toBukkit(CreatureSpawner t) {
        t.setSpawnedType(entityType);
    }

    @Override
    public void fromBukkit(CreatureSpawner sign) {
        entityType = sign.getSpawnedType();
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public TileSpawner clone() {
        TileSpawner ts = new TileSpawner();
        ts.setEntityType(getEntityType());
        return ts;
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        out.writeShort(id);
        out.writeShort(entityType.ordinal());
    }

    @Override
    public void fromBinary(DataInputStream in) throws IOException {
        entityType = EntityType.values()[in.readShort()];
    }

    @Override
    public CompoundTag toNBT(CompoundTag parent) {
        @SuppressWarnings("unchecked") ListTag<CompoundTag> potentials = (ListTag<CompoundTag>) ListTag.createUnchecked(CompoundTag.class);
        CompoundTag t = new CompoundTag();
        CompoundTag ent = new CompoundTag();
        ent.putString("id", entityType.getKey().toString());
        t.put("Entity", ent);
        t.putInt("Weight", 1);
        potentials.add(t);
        parent.put("SpawnPotentials", potentials);
        return parent;
    }
}
