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

package com.volmit.iris.util.hunk.io;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.tile.TileData;
import org.bukkit.block.TileState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TileDataHunkIOAdapter extends PaletteHunkIOAdapter<TileData<? extends TileState>> {
    @Override
    public void write(TileData<? extends TileState> data, DataOutputStream dos) throws IOException {
        data.toBinary(dos);
    }

    @Override
    public TileData<? extends TileState> read(DataInputStream din) throws IOException {
        try {
            return TileData.read(din);
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            throw new IOException();
        }
    }
}
