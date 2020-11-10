package com.volmit.iris.scaffold.hunk.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class StringHunkIOAdapter extends PaletteHunkIOAdapter<String> {

    @Override
    public void write(String data, DataOutputStream dos) throws IOException {
        dos.writeUTF(data);
    }

    @Override
    public String read(DataInputStream din) throws IOException {
        return din.readUTF();
    }
}
