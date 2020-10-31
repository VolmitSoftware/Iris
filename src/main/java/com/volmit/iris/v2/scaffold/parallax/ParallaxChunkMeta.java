package com.volmit.iris.v2.scaffold.parallax;

import com.volmit.iris.v2.scaffold.hunk.io.HunkIOAdapter;
import com.volmit.iris.v2.scaffold.hunk.io.PaletteHunkIOAdapter;
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
            dos.writeBoolean(parallaxChunkMeta.isObjects());

            if(parallaxChunkMeta.isObjects())
            {
                dos.writeByte(parallaxChunkMeta.getMinObject() + Byte.MIN_VALUE);
                dos.writeByte(parallaxChunkMeta.getMaxObject() + Byte.MIN_VALUE);
            }
        }

        @Override
        public ParallaxChunkMeta read(DataInputStream din) throws IOException {
            boolean g = din.readBoolean();
            boolean p = din.readBoolean();
            boolean o = din.readBoolean();
            int min = o ? din.readByte() - Byte.MIN_VALUE : -1;
            int max = o ? din.readByte() - Byte.MIN_VALUE : -1;
            return new ParallaxChunkMeta(g, p, o, min, max);
        }
    };

    private boolean generated;
    private boolean parallaxGenerated;
    private boolean objects;
    private int maxObject = -1;
    private int minObject = -1;

    public ParallaxChunkMeta()
    {
        this(false, false, false, -1, -1);
    }
}
