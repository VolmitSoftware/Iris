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

import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.engine.parallax.ParallaxWorld;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.matter.Sliced;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class TileMatter extends RawMatter<TileState> {
    public TileMatter() {
        this(1, 1, 1);
    }

    public TileMatter(int width, int height, int depth) {
        super(width, height, depth, TileState.class);

    }

    @Override
    public void writeNode(TileState b, DataOutputStream dos) throws IOException {

    }

    @Override
    public TileState readNode(DataInputStream din) throws IOException {
        return null;
    }
}
