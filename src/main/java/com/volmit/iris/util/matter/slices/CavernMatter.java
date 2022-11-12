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
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class CavernMatter extends RawMatter<MatterCavern> {
    public static final MatterCavern EMPTY = new MatterCavern(false, "", (byte) 0);
    public static final MatterCavern BASIC = new MatterCavern(true, "", (byte) 0);

    public CavernMatter() {
        this(1, 1, 1);
    }

    public CavernMatter(int width, int height, int depth) {
        super(width, height, depth, MatterCavern.class);
    }

    public static MatterCavern get(String customBiome, int liquid) {
        return new MatterCavern(true, customBiome, (byte) liquid);
    }

    @Override
    public Palette<MatterCavern> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(MatterCavern b, DataOutputStream dos) throws IOException {
        dos.writeBoolean(b.isCavern());
        dos.writeUTF(b.getCustomBiome());
        dos.writeByte(b.getLiquid());
    }

    @Override
    public MatterCavern readNode(DataInputStream din) throws IOException {
        boolean b = din.readBoolean();
        String v = din.readUTF();
        byte l = din.readByte();

        return new MatterCavern(b, v, l);
    }
}
