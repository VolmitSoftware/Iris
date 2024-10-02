package com.volmit.iris.server.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.core.nms.IHeadless;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.execption.RejectedException;
import com.volmit.iris.server.packet.Packet;
import com.volmit.iris.server.packet.Packets;
import com.volmit.iris.server.packet.init.InfoPacket;
import com.volmit.iris.server.packet.init.PingPacket;
import com.volmit.iris.server.packet.work.ChunkPacket;
import com.volmit.iris.server.packet.work.DonePacket;
import com.volmit.iris.server.packet.work.MantleChunkPacket;
import com.volmit.iris.server.util.*;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.parallel.MultiBurst;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

@Log(topic = "CloudPregen")
public class CloudMethod implements PregeneratorMethod, ConnectionHolder, PacketListener {
    private final @Getter IrisConnection connection = new IrisConnection(this);
    private final Engine engine;
    private final IHeadless headless;
    private final KMap<UUID, PregenHolder> holders = new KMap<>();
    private final CompletableFuture<LimitedSemaphore> future = new CompletableFuture<>();
    private final KMap<UUID, CompletableFuture<Object>> locks = new KMap<>();

    public CloudMethod(String address, Engine engine) throws InterruptedException {
        var split = address.split(":");
        if (split.length != 2 || !split[1].matches("\\d+"))
            throw new IllegalArgumentException("Invalid remote server address: " + address);

        IrisConnection.connect(new InetSocketAddress(split[0], Integer.parseInt(split[1])), this);

        this.engine = engine;
        this.headless = INMS.get().createHeadless(engine);
    }

    @Override
    public void init() {
        var studio = engine.isStudio();
        var base = studio ?
                engine.getData().getDataFolder() :
                engine.getWorld().worldFolder();
        var name = engine.getWorld().name();
        var exit = new AtomicBoolean(false);
        var limited = new LimitedSemaphore(IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()));

        var remoteVersion = new CompletableFuture<>();
        var ping = Packets.PING.newPacket()
                .setBukkit();
        locks.put(ping.getId(), remoteVersion);
        ping.send(connection);

        try {
            var o = remoteVersion.get();
            if (!(o instanceof PingPacket packet))
                throw new IllegalStateException("Invalid response from remote server");
            if (!packet.getVersion().contains(ping.getVersion().get(0)))
                throw new IllegalStateException("Remote server version does not match");
        } catch (Throwable e) {
            connection.disconnect();
            throw new IllegalStateException("Failed to connect to remote server", e);
        }


        log.info(name + ": Uploading pack...");
        iterate(engine.getData().getDataFolder(), f -> {
            if (exit.get()) return;

            try {
                limited.acquire();
            } catch (InterruptedException e) {
                exit.set(true);
                return;
            }

            MultiBurst.burst.complete(() -> {
                try {
                    upload(exit, base, f, studio, 8192);
                } finally {
                    limited.release();
                }
            });
        });

        try {
            limited.acquireAll();
        } catch (InterruptedException ignored) {}

        log.info(name + ": Done uploading pack");
        log.info(name + ": Initializing Engine...");
        var future = new CompletableFuture<>();
        var packet = Packets.ENGINE.newPacket()
                .setDimension(engine.getDimension().getLoadKey())
                .setSeed(engine.getWorld().getRawWorldSeed())
                .setRadius(engine.getMantle().getRadius());

        locks.put(packet.getId(), future);
        packet.send(connection);

        try {
            future.get();
        } catch (Throwable ignored) {}
        log.info(name + ": Done initializing Engine");
    }

    private void upload(AtomicBoolean exit, File base, File f, boolean studio, int packetSize) {
        if (exit.get() || (!studio && !f.getAbsolutePath().startsWith(base.getAbsolutePath())))
            return;

        String path = studio ? "iris/pack/" : "";
        path += f.getAbsolutePath().substring(base.getAbsolutePath().length() + 1);

        try (FileInputStream in = new FileInputStream(f)) {
            long offset = 0;
            byte[] data;
            while ((data = in.readNBytes(packetSize)).length > 0 && !exit.get()) {
                var future = new CompletableFuture<>();
                var packet = Packets.FILE.newPacket()
                        .setPath(path)
                        .setOffset(offset)
                        .setLength(f.length())
                        .setData(data);

                locks.put(packet.getId(), future);
                packet.send(connection);
                future.get();

                offset += data.length;
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            Iris.error("Failed to upload " + f);
            e.printStackTrace();
            exit.set(true);
        }
    }

    private void iterate(File file, Consumer<File> consumer) {
        var queue = new ArrayDeque<File>();
        queue.add(file);

        while (!queue.isEmpty()) {
            var f = queue.remove();
            if (f.isFile())
                consumer.accept(f);
            if (f.isDirectory()) {
                var files = f.listFiles();
                if (files == null) continue;
                queue.addAll(Arrays.asList(files));
            }
        }
    }

    @Override
    public void close() {
        close0();
        connection.disconnect();
    }

    @Override
    public void save() {}

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return true;
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        var semaphore = future.join();
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            semaphore.release();
            return;
        }

        var p = Packets.PREGEN.newPacket()
                .setX(x)
                .setZ(z);
        new PregenHolder(p, engine.getMantle().getRadius(), true, listener)
                .put(holders);
        p.send(connection);
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {}

    @Override
    public String getMethod(int x, int z) {
        return "Cloud";
    }

    @Override
    public Mantle getMantle() {
        return engine.getMantle().getMantle();
    }

    @Override
    public World getWorld() {
        return engine.getWorld().realWorld();
    }

    @Override
    public void onPacket(Packet raw) throws Exception {
        if (raw instanceof ChunkPacket packet) {
            headless.addChunk(packet);
            holders.get(packet.getPregenId())
                    .getListener()
                    .onChunkGenerated(packet.getX(), packet.getZ());
        } else if (raw instanceof MantleChunkPacket packet) {
            if (holders.get(packet.getPregenId())
                    .remove(packet.getX(), packet.getZ())) {
                future.join().release();
            }
            packet.set(getMantle());
        } else if (raw instanceof MantleChunkPacket.Request packet) {
            var mantle = getMantle();
            for (var chunk : packet.getPositions()) {
                Packets.MANTLE_CHUNK.newPacket()
                        .setPregenId(packet.getPregenId())
                        .read(chunk, mantle)
                        .send(connection);
            }
        } else if (raw instanceof InfoPacket packet) {
            if (packet.getNodeCount() > 0 && !future.isDone())
                future.complete(new LimitedSemaphore(packet.getNodeCount()));
            //if (packet.getCps() >= 0)
            //    Iris.info("Cloud CPS: " + packet.getCps());
        } else if (raw instanceof DonePacket packet) {
            locks.remove(packet.getId()).complete(null);
        } else if (raw instanceof PingPacket packet) {
            locks.remove(packet.getId()).complete(packet);
        } else if (raw instanceof ErrorPacket packet) {
            packet.log(log, Level.SEVERE);
        } else throw new RejectedException("Unhandled packet: " + raw.getClass().getSimpleName());
    }

    @Override
    public void onDisconnect() {
        try {
            if (!future.isDone())
                future.cancel(false);
        } catch (Throwable ignored) {}
        PregeneratorJob.shutdownInstance();
    }

    private void close0() {
        if (!future.isCancelled()) {
            try {
                future.join().acquireAll();
            } catch (InterruptedException ignored) {}
        }

        try {
            headless.close();
        } catch (IOException e) {
            log.log(Level.SEVERE, "Failed to close headless", e);
        }
    }
}
