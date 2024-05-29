package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.Sliced;
import com.volmit.iris.util.matter.slices.container.JigsawStructuresContainer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class JigsawStructuresMatter extends RawMatter<JigsawStructuresContainer> {
    public JigsawStructuresMatter() {
        this(1, 1, 1);
    }

    public JigsawStructuresMatter(int width, int height, int depth) {
        super(width, height, depth, JigsawStructuresContainer.class);
    }

    @Override
    public Palette<JigsawStructuresContainer> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(JigsawStructuresContainer b, DataOutputStream dos) throws IOException {
        b.write(dos);
    }

    @Override
    public JigsawStructuresContainer readNode(DataInputStream din) throws IOException {
        return new JigsawStructuresContainer(din);
    }
}
