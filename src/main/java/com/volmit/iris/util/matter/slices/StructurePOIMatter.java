package com.volmit.iris.util.matter.slices;

import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.MatterStructurePOI;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StructurePOIMatter extends RawMatter<MatterStructurePOI> {

    public StructurePOIMatter() {
        super(1, 1, 1, MatterStructurePOI.class);
    }

    @Override
    public Palette<MatterStructurePOI> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(MatterStructurePOI b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getType());
    }

    @Override
    public MatterStructurePOI readNode(DataInputStream din) throws IOException {
        return MatterStructurePOI.get(din.readUTF());
    }
}
