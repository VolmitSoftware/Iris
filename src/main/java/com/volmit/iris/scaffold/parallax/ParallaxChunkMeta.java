package com.volmit.iris.scaffold.parallax;

import com.google.gson.Gson;
import com.volmit.iris.object.IrisFeaturePositional;
import com.volmit.iris.scaffold.hunk.io.HunkIOAdapter;
import com.volmit.iris.scaffold.hunk.io.PaletteHunkIOAdapter;
import com.volmit.iris.util.CompoundTag;
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
    public static final Function<CompoundTag, HunkIOAdapter<ParallaxChunkMeta>> adapter = (c) -> new PaletteHunkIOAdapter<ParallaxChunkMeta>() {
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

    public ParallaxChunkMeta()
    {
        this(false, false, false, false, false, false, -1, -1, 0, new CopyOnWriteArrayList<>());
    }
}
