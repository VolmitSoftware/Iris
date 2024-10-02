package com.volmit.iris.server.node;

import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.execption.RejectedException;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.server.packet.init.EnginePacket;
import com.volmit.iris.server.packet.init.FilePacket;
import com.volmit.iris.server.packet.init.PingPacket;
import com.volmit.iris.server.util.CPSLooper;
import com.volmit.iris.server.util.ConnectionHolder;
import com.volmit.iris.server.util.PacketListener;
import com.volmit.iris.server.packet.work.ChunkPacket;
import com.volmit.iris.server.packet.work.MantleChunkPacket;
import com.volmit.iris.server.packet.work.PregenPacket;
import com.volmit.iris.server.util.PregenHolder;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Getter;

import java.io.File;
import java.util.UUID;

public class IrisSession implements ConnectionHolder, PacketListener {
    private static final MultiBurst burst = new MultiBurst("IrisHeadless", 8);
    private final @Getter IrisConnection connection = new IrisConnection(this);
    private final File base = new File("cache/" + UUID.randomUUID());
    private final KMap<UUID, PregenHolder> chunks = new KMap<>();
    private final KMap<Long, PregenHolder> pregens = new KMap<>();
    private final CPSLooper cpsLooper = new CPSLooper("IrisSession-"+base.getName(), connection);

    private Engine engine;
    private IHeadless headless;

    @Override
    public void onPacket(Packet raw) throws Exception {
        cpsLooper.setNodeCount(1);
        if (raw instanceof FilePacket packet) {
            if (engine != null) throw new RejectedException("Engine already setup");

            packet.write(base);
            Packets.DONE.newPacket()
                    .setId(packet.getId())
                    .send(connection);
        } else if (raw instanceof EnginePacket packet) {
            if (engine != null) throw new RejectedException("Engine already setup");
            engine = packet.getEngine(base);
            headless = INMS.get().createHeadless(engine);
            headless.setSession(this);

            Packets.DONE.newPacket()
                    .setId(packet.getId())
                    .send(connection);
        } else if (raw instanceof MantleChunkPacket packet) {
            if (engine == null) throw new RejectedException("Engine not setup");
            packet.set(engine.getMantle().getMantle());

            var holder = chunks.get(packet.getPregenId());
            if (holder.remove(packet.getX(), packet.getZ())) {
                headless.generateRegion(burst, holder.getX(), holder.getZ(), 20, null)
                        .thenRun(() -> {
                            holder.iterate(chunkPos -> {
                                var resp = Packets.MANTLE_CHUNK.newPacket();
                                resp.setPregenId(holder.getId());
                                resp.read(chunkPos, engine.getMantle().getMantle());
                                connection.send(resp);
                            });
                            chunks.remove(holder.getId());
                        });
            }
        } else if (raw instanceof PregenPacket packet) {
            if (engine == null) throw new RejectedException("Engine not setup");
            var radius = engine.getMantle().getRadius();

            var holder = new PregenHolder(packet, radius, true, null);
            var request = Packets.MANTLE_CHUNK_REQUEST.newPacket()
                    .setPregenId(packet.getId());
            holder.iterate(request::add);
            var pregen = new PregenHolder(packet, 0, true, null);
            pregens.put(Cache.key(pregen.getX(), pregen.getZ()), pregen);

            chunks.put(packet.getId(), holder);
            connection.send(request);
        } else if (raw instanceof PingPacket packet) {
            packet.setBukkit().send(connection);
        } else throw new RejectedException("Unhandled packet: " + raw.getClass().getSimpleName());
    }

    public void completeChunk(int x, int z, byte[] data) {
        cpsLooper.addChunks(1);
        long id = Cache.key(x >> 5, z >> 5);
        var pregen = pregens.get(id);
        if (pregen.remove(x, z))
            pregens.remove(id);
        connection.send(new ChunkPacket(pregen.getId(), x, z, data));
    }

    @Override
    public void onDisconnect() {
        if (engine != null) {
            engine.close();
            engine = null;
            headless = null;
        }
        cpsLooper.exit();
        IO.delete(base);
    }
}
