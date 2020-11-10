package com.volmit.iris.scaffold.hunk.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class BooleanHunkIOAdapter extends PaletteHunkIOAdapter<Boolean> {

    @Override
    public void write(Boolean data, DataOutputStream dos) throws IOException {
        dos.writeBoolean(data);
    }

    @Override
    public Boolean read(DataInputStream din) throws IOException {
        return din.readBoolean();
    }
}
