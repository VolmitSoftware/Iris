package com.volmit.iris.scaffold.hunk.io;

import com.volmit.iris.object.tile.TileData;
import org.bukkit.block.TileState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TileDataHunkIOAdapter extends PaletteHunkIOAdapter<TileData<? extends TileState>> {
    @Override
    public void write(TileData<? extends TileState> data, DataOutputStream dos) throws IOException {
        data.toBinary(dos);
    }

    @Override
    public TileData<? extends TileState> read(DataInputStream din) throws IOException {
        try {
            return TileData.read(din);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new IOException();
        }
    }
}
