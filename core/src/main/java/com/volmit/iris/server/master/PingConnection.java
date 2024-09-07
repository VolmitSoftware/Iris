package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.server.packet.init.PingPacket;
import com.volmit.iris.server.util.ConnectionHolder;
import com.volmit.iris.server.util.PacketListener;
import com.volmit.iris.util.collection.KList;
import lombok.Getter;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

@Getter
public class PingConnection implements ConnectionHolder, PacketListener {
    private final IrisConnection connection = new IrisConnection(this);
    private final CompletableFuture<KList<String>> version = new CompletableFuture<>();

    public PingConnection(InetSocketAddress address) throws InterruptedException {
        IrisConnection.connect(address, this);
        Packets.PING.newPacket().send(connection);
    }

    @Override
    public void onPacket(Packet packet) throws Exception {
        if (packet instanceof PingPacket p) {
            version.complete(p.getVersion());
            connection.disconnect();
        }
    }
}
