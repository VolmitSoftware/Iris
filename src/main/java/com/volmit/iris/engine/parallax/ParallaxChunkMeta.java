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

package com.volmit.iris.engine.parallax;

import com.google.gson.Gson;
import com.volmit.iris.engine.object.IrisFeaturePositional;
import com.volmit.iris.engine.hunk.io.HunkIOAdapter;
import com.volmit.iris.engine.hunk.io.PaletteHunkIOAdapter;
import com.volmit.iris.util.oldnbt.CompoundTag;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

@AllArgsConstructor
@Data
public class ParallaxChunkMeta {
    public static final Function<CompoundTag, HunkIOAdapter<ParallaxChunkMeta>> adapter = (c) -> new PaletteHunkIOAdapter<>() {
        @Override
        public void write(ParallaxChunkMeta parallaxChunkMeta, DataOutputStream dos) throws IOException {
            dos.writeUTF(new Gson().toJson(parallaxChunkMeta));
        }

        @Override
        public ParallaxChunkMeta read(DataInputStream din) throws IOException {
            return new Gson().fromJson(din.readUTF(), ParallaxChunkMeta.class);
        }
    };

    private boolean updates;
    private boolean generated;
    private boolean tilesGenerated;
    private boolean parallaxGenerated;
    private boolean featureGenerated;
    private boolean objects;
    private int maxObject = -1;
    private int minObject = -1;
    private int count;
    private CopyOnWriteArrayList<IrisFeaturePositional> features;

    public ParallaxChunkMeta() {
        this(false, false, false, false, false, false, -1, -1, 0, new CopyOnWriteArrayList<>());
    }
}
