package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.Sliced;
import com.volmit.iris.util.matter.slices.container.JigsawPieceContainer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class JigsawStructureMatter extends RawMatter<JigsawPieceContainer> {
    public JigsawStructureMatter() {
        this(1,1,1);
    }

    public JigsawStructureMatter(int width, int height, int depth) {
        super(width, height, depth, JigsawPieceContainer.class);
    }

    @Override
    public Palette<JigsawPieceContainer> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(JigsawPieceContainer b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getLoadKey());
    }

    @Override
    public JigsawPieceContainer readNode(DataInputStream din) throws IOException {
        return new JigsawPieceContainer(din.readUTF());
    }
}
