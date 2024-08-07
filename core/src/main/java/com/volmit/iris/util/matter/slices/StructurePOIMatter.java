/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.util.matter.MatterStructurePOI;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StructurePOIMatter extends RawMatter<MatterStructurePOI> {

    public StructurePOIMatter() {
        super(1, 1, 1, MatterStructurePOI.class);
    }

    @Override
    public Palette<MatterStructurePOI> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(MatterStructurePOI b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getType());
    }

    @Override
    public MatterStructurePOI readNode(DataInputStream din) throws IOException {
        return MatterStructurePOI.get(din.readUTF());
    }
}
