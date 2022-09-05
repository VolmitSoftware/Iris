package com.volmit.iris.engine.network.server;

import com.volmit.iris.engine.Engine;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EngineServer extends Thread {
    private final Engine engine;
    private final ServerSocket server;
    private final Map<UUID, EngineServerConnection> connections;

    public EngineServer(Engine engine, int bindPort) throws IOException {
        this.engine = engine;
        this.connections = new HashMap<>();
        this.server = new ServerSocket(bindPort);
        this.server.setPerformancePreferences(0, 0, 1);
        this.server.setSoTimeout(5000);
    }

    public void run() {
        while(!interrupted()) {
            try {
                Socket socket = server.accept();
                EngineServerConnection connection = new EngineServerConnection(this, socket);
                connections.put(connection.getConnectionId(), connection);
            }
            catch(SocketTimeoutException e) {
                continue;
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void close() throws IOException {
        this.interrupt();
        server.close();
    }
}
