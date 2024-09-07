package com.volmit.iris.server.packet;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.util.PacketSendListener;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public interface Packet {
    void read(ByteBuf byteBuf) throws IOException;
    void write(ByteBuf byteBuf) throws IOException;

    default Packets<?> getType() {
        return Packets.get(getClass());
    }

    default void send(IrisConnection connection) {
        send(connection, null);
    }

    default void send(IrisConnection connection, PacketSendListener listener) {
        connection.send(this, listener);
    }
}
