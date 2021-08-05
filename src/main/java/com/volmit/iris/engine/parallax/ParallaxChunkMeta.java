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
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.util.hunk.io.HunkIOAdapter;
import com.volmit.iris.util.hunk.io.PaletteHunkIOAdapter;
import com.volmit.iris.util.oldnbt.CompoundTag;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

@AllArgsConstructor
@Data
public class ParallaxChunkMeta {
    private static final Gson gson = new Gson();
    public static final Function<CompoundTag, HunkIOAdapter<ParallaxChunkMeta>> adapter = (c) -> new PaletteHunkIOAdapter<>() {
        @Override
        public void write(ParallaxChunkMeta parallaxChunkMeta, DataOutputStream dos) throws IOException {
            dos.writeBoolean(parallaxChunkMeta.updates);
            dos.writeBoolean(parallaxChunkMeta.generated);
            dos.writeBoolean(parallaxChunkMeta.tilesGenerated);
            dos.writeBoolean(parallaxChunkMeta.parallaxGenerated);
            dos.writeBoolean(parallaxChunkMeta.featureGenerated);
            dos.writeBoolean(parallaxChunkMeta.objects);
            dos.writeInt(parallaxChunkMeta.maxObject);
            dos.writeInt(parallaxChunkMeta.minObject);
            dos.writeInt(parallaxChunkMeta.count);
            dos.writeInt(parallaxChunkMeta.features.size());

            for (IrisFeaturePositional i : parallaxChunkMeta.features) {
                dos.writeUTF(gson.toJson(i));
            }
        }

        @Override
        public ParallaxChunkMeta read(DataInputStream din) throws IOException {
            ParallaxChunkMeta pcm = new ParallaxChunkMeta();
            pcm.setUpdates(din.readBoolean());
            pcm.setGenerated(din.readBoolean());
            pcm.setTilesGenerated(din.readBoolean());
            pcm.setParallaxGenerated(din.readBoolean());
            pcm.setFeatureGenerated(din.readBoolean());
            pcm.setObjects(din.readBoolean());
            pcm.setMaxObject(din.readInt());
            pcm.setMinObject(din.readInt());
            pcm.setCount(din.readInt());
            pcm.setFeatures(newSet());
            int c = din.readInt();

            for (int i = 0; i < c; i++) {
                pcm.getFeatures().add(gson.fromJson(din.readUTF(), IrisFeaturePositional.class));
            }

            return pcm;
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
    private Set<IrisFeaturePositional> features;

    private static Set<IrisFeaturePositional> newSet() {
        return new CopyOnWriteArraySet<>();
    }

    public ParallaxChunkMeta() {
        this(false, false, false, false, false, false, -1, -1, 0, newSet());
    }
}
