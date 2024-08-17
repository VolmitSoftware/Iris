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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.collection.KMap;
import lombok.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@SuppressWarnings("ALL")
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TileData implements Cloneable {
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().setLenient().create();

    @NonNull
    private Material material;
    @NonNull
    private KMap<String, Object> properties;

    public static boolean setTileState(Block block, TileData data) {
        if (block.getState() instanceof TileState && data.isApplicable(block.getBlockData()))
            return data.toBukkitTry(block);
        return false;
    }

    public static TileData getTileState(Block block) {
        if (!INMS.get().hasTile(block.getType()))
            return null;
        return new TileData().fromBukkit(block);
    }

    public static TileData read(DataInputStream in) throws IOException {
        if (!in.markSupported())
            throw new IOException("Mark not supported");
        in.mark(Integer.MAX_VALUE);
        try {
            return new TileData(
                    Material.matchMaterial(in.readUTF()),
                    gson.fromJson(in.readUTF(), KMap.class));
        } catch (Throwable e) {
            in.reset();
            return new LegacyTileData(in);
        } finally {
            in.mark(0);
        }
    }

    public boolean isApplicable(BlockData data) {
        return material != null && data.getMaterial() == material;
    }

    public void toBukkit(Block block) {
        if (material == null) throw new IllegalStateException("Material not set");
        if (block.getType() != material)
            throw new IllegalStateException("Material mismatch: " + block.getType() + " vs " + material);
        INMS.get().deserializeTile(properties, block.getLocation());
    }

    public TileData fromBukkit(Block block) {
        if (material != null && block.getType() != material)
            throw new IllegalStateException("Material mismatch: " + block.getType() + " vs " + material);
        if (material == null) material = block.getType();
        properties = INMS.get().serializeTile(block.getLocation());
        return this;
    }

    public boolean toBukkitTry(Block block) {
        try {
            //noinspection unchecked
            toBukkit(block);
            return true;
        } catch (Throwable e) {
            Iris.reportError(e);
        }

        return false;
    }

    public boolean fromBukkitTry(Block block) {
        try {
            //noinspection unchecked
            fromBukkit(block);
            return true;
        } catch (Throwable e) {
            Iris.reportError(e);

        }

        return false;
    }

    public void toBinary(DataOutputStream out) throws IOException {
        out.writeUTF(material == null ? "" : material.getKey().toString());
        out.writeUTF(gson.toJson(properties));
    }

    @Override
    public TileData clone() {
        var clone = new TileData();
        clone.material = material;
        clone.properties = properties.copy(); //TODO make a deep copy
        return clone;
    }
}
