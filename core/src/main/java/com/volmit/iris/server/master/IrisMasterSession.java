package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.execption.RejectedException;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.server.packet.init.EnginePacket;
import com.volmit.iris.server.packet.init.FilePacket;
import com.volmit.iris.server.packet.init.InfoPacket;
import com.volmit.iris.server.packet.work.ChunkPacket;
import com.volmit.iris.server.packet.work.MantleChunkPacket;
import com.volmit.iris.server.packet.work.PregenPacket;
import com.volmit.iris.server.util.*;
import com.volmit.iris.util.collection.KMap;
import lombok.Getter;
import lombok.extern.java.Log;

import java.util.Comparator;
import java.util.UUID;
import java.util.logging.Level;

//TODO handle done packet
@Log(topic = "IrisMasterSession")
public class IrisMasterSession implements ConnectionHolder, PacketListener {
    private final @Getter IrisConnection connection = new IrisConnection(this);
    private final @Getter UUID uuid = UUID.randomUUID();
    private final KMap<UUID, IrisMasterClient> map = new KMap<>();
    private final CPSLooper cpsLooper = new CPSLooper("IrisMasterSession-" + uuid, connection);
    private KMap<IrisMasterClient, KMap<UUID, PregenHolder>> clients;
    private int radius = -1;

    @Override
    public void onPacket(Packet raw) throws Exception {
        if (clients == null) {
            clients = IrisMasterServer.getNodes(this);
            cpsLooper.setNodeCount(clients.size());

            var info = Packets.INFO.newPacket();
            info.setNodeCount(clients.size());
            connection.send(info);
        }

        if (raw instanceof FilePacket packet) {
            if (radius != -1)
                throw new RejectedException("Engine already setup");
            clients.keySet().forEach(client -> client.send(packet));
        } else if (raw instanceof EnginePacket packet) {
            if (radius != -1)
                throw new RejectedException("Engine already setup");
            radius = packet.getRadius();
            clients.keySet().forEach(client -> client.send(packet));
        } else if (raw instanceof PregenPacket packet) {
            if (radius == -1)
                throw new RejectedException("Engine not setup");
            var client = pick();
            map.put(packet.getId(), client);
            new PregenHolder(packet, radius, true, null)
                    .put(clients.get(client));

            client.send(packet);
        } else if (raw instanceof MantleChunkPacket packet) {
            var client = map.get(packet.getPregenId());
            client.send(packet);
        }
    }

    protected void onClientPacket(Packet raw) {
        if (raw instanceof ErrorPacket packet) {
            packet.log(log, Level.SEVERE);
        } else if (raw instanceof ChunkPacket) {
            connection.send(raw);
        } else if (raw instanceof MantleChunkPacket packet) {
            var map = clients.get(this.map.get(packet.getPregenId()));
            if (map.get(packet.getPregenId())
                    .remove(packet.getX(), packet.getZ()))
                map.get(packet.getPregenId());

            connection.send(packet);
        } else if (raw instanceof MantleChunkPacket.Request packet) {
            connection.send(packet);
        } else if (raw instanceof InfoPacket packet) {
            int i = packet.getGenerated();
            if (i != -1) cpsLooper.addChunks(i);
        }
    }

    @Override
    public void onDisconnect() {
        IrisMasterServer.close(uuid);
    }

    private IrisMasterClient pick() throws RejectedException {
        return clients.keySet()
                .stream()
                .min(Comparator.comparingInt(c -> clients.get(c).size()))
                .orElseThrow(() -> new RejectedException("No clients available"));
    }
}
