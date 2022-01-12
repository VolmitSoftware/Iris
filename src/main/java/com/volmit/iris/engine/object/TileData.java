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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@SuppressWarnings("ALL")
public interface TileData<T extends TileState> extends Cloneable {

    KList<TileData<? extends TileState>> registry = setup();

    static KList<TileData<? extends TileState>> setup() {
        KList<TileData<? extends TileState>> registry = new KList<>();

        registry.add(new TileSign());
        registry.add(new TileSpawner());
        registry.add(new TileBanner());

        return registry;
    }

    static TileData<? extends TileState> read(DataInputStream s) throws Throwable {
        int id = s.readShort();
        @SuppressWarnings("unchecked") TileData<? extends TileState> d = registry.get(id).getClass().getConstructor().newInstance();
        d.fromBinary(s);
        return d;
    }

    static void setTileState(Block block, TileData<? extends TileState> data) {
        if(data.isApplicable(block.getBlockData())) {
            data.toBukkitTry(block.getState());
        }
    }

    static TileData<? extends TileState> getTileState(Block block) {
        for(TileData<? extends TileState> i : registry) {
            BlockData data = block.getBlockData();

            if(i.isApplicable(data)) {
                try {
                    @SuppressWarnings("unchecked") TileData<? extends TileState> s = i.getClass().getConstructor().newInstance();
                    s.fromBukkitTry(block.getState());
                    return s;
                } catch(Throwable e) {
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    String getTileId();

    boolean isApplicable(BlockData data);

    void toBukkit(T t);

    void fromBukkit(T t);

    default boolean toBukkitTry(BlockState t) {
        try {
            //noinspection unchecked
            toBukkit((T) t);
            return true;
        } catch(Throwable e) {
            Iris.reportError(e);

        }

        return false;
    }

    default boolean fromBukkitTry(BlockState t) {
        try {
            //noinspection unchecked
            fromBukkit((T) t);
            return true;
        } catch(Throwable e) {
            Iris.reportError(e);

        }

        return false;
    }

    TileData<T> clone();

    void toBinary(DataOutputStream out) throws IOException;

    void toNBT(CompoundTag tag);

    void fromBinary(DataInputStream in) throws IOException;
}
