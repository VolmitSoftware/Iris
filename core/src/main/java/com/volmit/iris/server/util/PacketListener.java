package com.volmit.iris.server.util;

import com.volmit.iris.server.packet.Packet;

public interface PacketListener {

    void onPacket(Packet packet) throws Exception;

    default void onDisconnect() {}

    default boolean isAccepting() {
        return true;
    }
}
