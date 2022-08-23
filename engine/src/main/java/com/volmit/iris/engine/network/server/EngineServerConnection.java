package com.volmit.iris.engine.network.server;

import lombok.Getter;

import java.net.Socket;
import java.util.UUID;

@Getter
public class EngineServerConnection extends Thread {
    private final EngineServer server;
    private final Socket socket;
    private final UUID connectionId;

    public EngineServerConnection(EngineServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
        this.connectionId = UUID.randomUUID();
    }
}
