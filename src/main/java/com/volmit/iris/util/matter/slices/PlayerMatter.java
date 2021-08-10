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

import com.volmit.iris.util.data.Varint;
import com.volmit.iris.util.matter.Sliced;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

@Sliced
public class PlayerMatter extends RawMatter<Player> {
    public PlayerMatter() {
        this(1, 1, 1);
    }

    public PlayerMatter(int width, int height, int depth) {
        super(width, height, depth, Player.class);
    }

    @Override
    public void writeNode(Player b, DataOutputStream dos) throws IOException {
        Varint.writeSignedVarLong(b.getUniqueId().getMostSignificantBits(), dos);
        Varint.writeSignedVarLong(b.getUniqueId().getLeastSignificantBits(), dos);
    }

    @Override
    public Player readNode(DataInputStream din) throws IOException {
        UUID id = new UUID(Varint.readSignedVarLong(din), Varint.readSignedVarLong(din));

        return Bukkit.getPlayer(id);
    }
}
