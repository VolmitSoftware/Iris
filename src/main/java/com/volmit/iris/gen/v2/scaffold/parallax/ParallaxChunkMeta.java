package com.volmit.iris.gen.v2.scaffold.parallax;

import com.sun.tools.javac.code.Attribute;
import com.volmit.iris.gen.v2.scaffold.hunk.io.HunkIOAdapter;
import com.volmit.iris.gen.v2.scaffold.hunk.io.PaletteHunkIOAdapter;
import com.volmit.iris.util.CompoundTag;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.Function;

@AllArgsConstructor
@Data
public class ParallaxChunkMeta {
    public static final Function<CompoundTag, HunkIOAdapter<ParallaxChunkMeta>> adapter = (c) -> new PaletteHunkIOAdapter<ParallaxChunkMeta>() {
        @Override
        public void write(ParallaxChunkMeta parallaxChunkMeta, DataOutputStream dos) throws IOException {
            dos.writeBoolean(parallaxChunkMeta.isGenerated());
            dos.writeBoolean(parallaxChunkMeta.isParallaxGenerated());
        }

        @Override
        public ParallaxChunkMeta read(DataInputStream din) throws IOException {
            return new ParallaxChunkMeta(din.readBoolean(), din.readBoolean());
        }
    };
    private boolean generated;
    private boolean parallaxGenerated;

    public ParallaxChunkMeta()
    {
        this(false, false);
    }
}
