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

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class MarkerMatter extends RawMatter<MatterMarker> {
    public static final MatterMarker NONE = new MatterMarker("none");
    public static final MatterMarker CAVE_FLOOR = new MatterMarker("cave_floor");
    public static final MatterMarker CAVE_CEILING = new MatterMarker("cave_ceiling");
    private static final KMap<String, MatterMarker> markers = new KMap<>();

    public MarkerMatter() {
        this(1, 1, 1);
    }

    public MarkerMatter(int width, int height, int depth) {
        super(width, height, depth, MatterMarker.class);
    }

    @Override
    public Palette<MatterMarker> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(MatterMarker b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getTag());
    }

    @Override
    public MatterMarker readNode(DataInputStream din) throws IOException {
        return markers.computeIfAbsent(din.readUTF(), MatterMarker::new);
    }
}
