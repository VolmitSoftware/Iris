package com.volmit.iris.server.packet.init;

import com.volmit.iris.server.util.ByteBufUtil;
import com.volmit.iris.server.packet.Packet;
import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.*;
import java.util.UUID;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class FilePacket implements Packet {
    private UUID id = UUID.randomUUID();
    private String path;
    private long offset;
    private long length;
    private byte[] data;

    @Override
    public void read(ByteBuf byteBuf) throws IOException {
        id = new UUID(byteBuf.readLong(), byteBuf.readLong());
        path = ByteBufUtil.readString(byteBuf);
        offset = byteBuf.readLong();
        length = byteBuf.readLong();
        data = ByteBufUtil.readBytes(byteBuf);
    }

    @Override
    public void write(ByteBuf byteBuf) throws IOException {
        byteBuf.writeLong(id.getMostSignificantBits());
        byteBuf.writeLong(id.getLeastSignificantBits());
        ByteBufUtil.writeString(byteBuf, path);
        byteBuf.writeLong(offset);
        byteBuf.writeLong(length);
        ByteBufUtil.writeBytes(byteBuf, data);
    }

    public void write(File base) throws IOException {
        File f = new File(base, path);
        if (!f.getAbsolutePath().startsWith(base.getAbsolutePath()))
            throw new IOException("Invalid path " + path);
        if (!f.getParentFile().exists() && !f.getParentFile().mkdirs())
            throw new IOException("Failed to create directory " + f.getParentFile());

        try (var raf = new RandomAccessFile(f, "rws")) {
            if (raf.length() < length)
                raf.setLength(length);
            raf.seek(offset);
            raf.write(data);
        }
    }
}
