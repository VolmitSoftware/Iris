package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.server.packet.init.InfoPacket;
import com.volmit.iris.server.packet.init.PingPacket;
import com.volmit.iris.server.util.ConnectionHolder;
import com.volmit.iris.server.util.PacketListener;
import com.volmit.iris.server.util.PacketSendListener;
import com.volmit.iris.util.collection.KList;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class IrisMasterClient implements ConnectionHolder, PacketListener {
    private final @Getter IrisConnection connection = new IrisConnection(this);
    private final IrisMasterSession session;
    private final CompletableFuture<PingPacket> pingResponse = new CompletableFuture<>();
    private final CompletableFuture<Integer> nodeCount = new CompletableFuture<>();

    IrisMasterClient(String version, IrisMasterSession session){
        this.session = session;
        Packets.PING.newPacket()
                .setVersion(version)
                .send(connection);
        try {
            var packet = pingResponse.get();
            if (!packet.getVersion().contains(version))
                throw new IllegalStateException("Remote server version does not match");
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException("Failed to get ping packet", e);
        }
    }

    protected void send(Packet packet) {
        connection.send(packet);
    }

    protected void send(Packet packet, @Nullable PacketSendListener listener) {
        connection.send(packet, listener);
    }

    @Override
    public void onPacket(Packet raw) {
        if (!pingResponse.isDone() && raw instanceof PingPacket packet) {
            pingResponse.complete(packet);
            return;
        }
        if (!nodeCount.isDone() && raw instanceof InfoPacket packet && packet.getNodeCount() > 0) {
            nodeCount.complete(packet.getNodeCount());
            return;
        }
        session.onClientPacket(this, raw);
    }

    public KList<String> getVersions() {
        try {
            return pingResponse.get().getVersion();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public int getNodeCount() {
        try {
            return nodeCount.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        connection.disconnect();
    }
}
