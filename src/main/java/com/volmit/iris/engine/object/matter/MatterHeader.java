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

package com.volmit.iris.engine.object.matter;

import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.math.M;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data
public class MatterHeader {
    private String author = "anonymous";
    private long createdAt = M.ms();
    private int version = Matter.VERSION;

    public void write(DataOutputStream out) throws IOException
    {
        out.writeUTF(author);
        Varint.writeUnsignedVarLong(createdAt, out);
        Varint.writeUnsignedVarInt(version, out);
    }

    public void read(DataInputStream din) throws IOException
    {
        setAuthor(din.readUTF());
        setCreatedAt(Varint.readUnsignedVarLong(din));
        setVersion(Varint.readUnsignedVarInt(din));
    }
}
