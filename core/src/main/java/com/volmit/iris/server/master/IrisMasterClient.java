package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.util.ConnectionHolder;
import com.volmit.iris.server.util.PacketListener;
import com.volmit.iris.server.util.PacketSendListener;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class IrisMasterClient implements ConnectionHolder, PacketListener {
    private final @Getter IrisConnection connection = new IrisConnection(this);
    private final IrisMasterSession session;

    IrisMasterClient(IrisMasterSession session) {
        this.session = session;
    }

    protected void send(Packet packet) {
        connection.send(packet);
    }

    protected void send(Packet packet, @Nullable PacketSendListener listener) {
        connection.send(packet, listener);
    }

    @Override
    public void onPacket(Packet packet) throws Exception {
        session.onClientPacket(packet);
    }

    public void disconnect() {
        connection.disconnect();
    }
}
