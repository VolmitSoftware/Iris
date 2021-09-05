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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.matter.MatterFluidBody;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class FluidBodyMatter extends RawMatter<MatterFluidBody> {
    public static MatterFluidBody get(String customBiome, boolean lava) {
        return new MatterFluidBody(true, customBiome, lava);
    }

    public FluidBodyMatter() {
        this(1, 1, 1);
    }

    public FluidBodyMatter(int width, int height, int depth) {
        super(width, height, depth, MatterFluidBody.class);
    }

    @Override
    public void writeNode(MatterFluidBody b, DataOutputStream dos) throws IOException {
        dos.writeBoolean(b.isBody());
        dos.writeBoolean(b.isLava());
        dos.writeUTF(b.getCustomBiome());
    }

    @Override
    public MatterFluidBody readNode(DataInputStream din) throws IOException {
        boolean b = din.readBoolean();
        boolean l = din.readBoolean();
        String v = din.readUTF();

        return new MatterFluidBody(b, v, l);
    }
}
