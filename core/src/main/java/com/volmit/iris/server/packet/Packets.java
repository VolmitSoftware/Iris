package com.volmit.iris.server.packet;

import com.volmit.iris.server.packet.init.EnginePacket;
import com.volmit.iris.server.packet.init.FilePacket;
import com.volmit.iris.server.packet.init.InfoPacket;
import com.volmit.iris.server.packet.init.PingPacket;
import com.volmit.iris.server.packet.work.DonePacket;
import com.volmit.iris.server.util.ErrorPacket;
import com.volmit.iris.server.packet.work.ChunkPacket;
import com.volmit.iris.server.packet.work.MantleChunkPacket;
import com.volmit.iris.server.packet.work.PregenPacket;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Packets<T extends Packet> {
    private static final List<Packets<? extends Packet>> REGISTRY;
    private static final Map<Class<? extends Packet>, Packets<? extends Packet>> MAP;

    public static final Packets<ErrorPacket> ERROR;
    public static final Packets<InfoPacket> INFO;
    public static final Packets<PingPacket> PING;
    public static final Packets<FilePacket> FILE;
    public static final Packets<EnginePacket> ENGINE;

    public static final Packets<DonePacket> DONE;
    public static final Packets<PregenPacket> PREGEN;
    public static final Packets<ChunkPacket> CHUNK;
    public static final Packets<MantleChunkPacket> MANTLE_CHUNK;
    public static final Packets<MantleChunkPacket.Request> MANTLE_CHUNK_REQUEST;

    private final Class<T> type;
    private final Supplier<T> factory;
    private int id = -1;

    public int getId() {
        if (id == -1) throw new IllegalStateException("Unknown packet type: " + this);
        return id;
    }

    public T newPacket() {
        return factory.get();
    }

    @NotNull
    public static Packets<? extends Packet> get(int id) {
        return REGISTRY.get(id);
    }

    @NotNull
    public static Packet newPacket(int id) {
        return get(id).newPacket();
    }

    @NotNull
    public static <T extends Packet> Packets<T> get(Class<T> type) {
        var t = MAP.get(type);
        if (t == null) throw new IllegalArgumentException("Unknown packet type: " + type);
        return (Packets<T>) t;
    }

    public static int getId(Class<? extends Packet> type) {
        return get(type).getId();
    }

    static {
        ERROR = new Packets<>(ErrorPacket.class, ErrorPacket::new);
        INFO = new Packets<>(InfoPacket.class, InfoPacket::new);
        PING = new Packets<>(PingPacket.class, PingPacket::new);
        FILE = new Packets<>(FilePacket.class, FilePacket::new);
        ENGINE = new Packets<>(EnginePacket.class, EnginePacket::new);

        DONE = new Packets<>(DonePacket.class, DonePacket::new);
        PREGEN = new Packets<>(PregenPacket.class, PregenPacket::new);
        CHUNK = new Packets<>(ChunkPacket.class, ChunkPacket::new);
        MANTLE_CHUNK = new Packets<>(MantleChunkPacket.class, MantleChunkPacket::new);
        MANTLE_CHUNK_REQUEST = new Packets<>(MantleChunkPacket.Request.class, MantleChunkPacket.Request::new);

        REGISTRY = List.of(ERROR, INFO, PING, FILE, ENGINE, DONE, PREGEN, CHUNK, MANTLE_CHUNK, MANTLE_CHUNK_REQUEST);

        var map = new HashMap<Class<? extends Packet>, Packets<? extends Packet>>();
        for (int i = 0; i < REGISTRY.size(); i++) {
            var entry = REGISTRY.get(i);
            entry.id = i;
            map.put(entry.type, entry);
        }
        MAP = Collections.unmodifiableMap(map);
    }
}
