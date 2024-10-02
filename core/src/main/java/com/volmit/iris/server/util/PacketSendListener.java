package com.volmit.iris.server.util;

import com.volmit.iris.server.packet.Packet;

import javax.annotation.Nullable;

public interface PacketSendListener {
    static PacketSendListener thenRun(Runnable runnable) {
        return new PacketSendListener() {
            public void onSuccess() {
                runnable.run();
            }

            @Nullable
            public Packet onFailure() {
                runnable.run();
                return null;
            }
        };
    }

    default void onSuccess() {}

    @Nullable
    default Packet onFailure() { return null; }
}
