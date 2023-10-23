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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.MatterBiomeInject;
import com.volmit.iris.util.matter.Sliced;
import org.bukkit.block.Biome;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class BiomeInjectMatter extends RawMatter<MatterBiomeInject> {
    public BiomeInjectMatter() {
        this(1, 1, 1);
    }

    public BiomeInjectMatter(int width, int height, int depth) {
        super(width, height, depth, MatterBiomeInject.class);
    }

    public static MatterBiomeInject get(Biome biome) {
        return get(false, 0, biome);
    }

    public static MatterBiomeInject get(int customBiome) {
        return get(true, customBiome, null);
    }

    public static MatterBiomeInject get(boolean custom, int customBiome, Biome biome) {
        return new MatterBiomeInject(custom, customBiome, biome);
    }

    @Override
    public Palette<MatterBiomeInject> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(MatterBiomeInject b, DataOutputStream dos) throws IOException {
        dos.writeBoolean(b.isCustom());

        if (b.isCustom()) {
            dos.writeShort(b.getBiomeId());
        } else {
            dos.writeByte(b.getBiome().ordinal());
        }
    }

    @Override
    public MatterBiomeInject readNode(DataInputStream din) throws IOException {
        boolean b = din.readBoolean();
        int id = b ? din.readShort() : 0;
        Biome biome = !b ? Biome.values()[din.readByte()] : Biome.PLAINS;

        return new MatterBiomeInject(b, id, biome);
    }
}
