package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.node.IrisServer;
import com.volmit.iris.server.util.PregenHolder;
import com.volmit.iris.util.collection.KMap;
import lombok.extern.java.Log;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Log(topic = "Iris-MasterServer")
public class IrisMasterServer extends IrisServer {
    private static final Object VALUE = new Object();
    private static IrisMasterServer instance;
    private final KMap<UUID, KMap<IrisMasterClient, KMap<UUID, PregenHolder>>> sessions = new KMap<>();
    private final KMap<InetSocketAddress, Object> nodes = new KMap<>();

    public IrisMasterServer(int port, String[] remote) throws InterruptedException {
        super("Iris-MasterServer", port, IrisMasterSession::new);
        if (instance != null && !instance.isRunning())
            close("Server already running");
        instance = this;

        for (var address : remote) {
            try {
                var split = address.split(":");
                if (split.length != 2 || !split[1].matches("\\d+")) {
                    log.warning("Invalid remote server address: " + address);
                    continue;
                }

                addNode(new InetSocketAddress(split[0], Integer.parseInt(split[1])));
            } catch (Throwable e) {
                log.log(Level.WARNING, "Failed to parse address: " + address, e);
            }
        }
    }

    public void addNode(InetSocketAddress address) {
        nodes.put(address, VALUE);
    }

    public void removeNode(InetSocketAddress address) {
        nodes.remove(address);
    }

    public static void close(UUID session) {
        var map = get().sessions.remove(session);
        if (map == null) return;
        map.keySet().forEach(IrisMasterClient::disconnect);
        map.clear();
    }

    public static KMap<IrisMasterClient, KMap<UUID, PregenHolder>> getNodes(IrisMasterSession session) {
        var master = get();
        var uuid = session.getUuid();
        close(uuid);

        master.getLogger().info("Requesting nodes for session " + uuid);
        var map = new KMap<IrisMasterClient, KMap<UUID, PregenHolder>>();
        for (var address : master.nodes.keySet()) {
            try {
                map.put(IrisConnection.connect(address, () -> new IrisMasterClient(session)), new KMap<>());
            } catch (Exception e) {
                master.getLogger().log(Level.WARNING, "Failed to connect to server " + address, e);
                master.removeNode(address);
            }
        }

        master.sessions.put(uuid, map);
        return map;
    }

    @Override
    public void close() throws Exception {
        log.info("Closing!");
        super.close();
        sessions.values()
                .stream()
                .map(KMap::keySet)
                .flatMap(Set::stream)
                .forEach(IrisMasterClient::disconnect);
        sessions.clear();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    private void close(String message) throws IllegalStateException {
        try {
            close();
        } catch (Exception ignored) {}
        throw new IllegalStateException(message);
    }

    public static IrisMasterServer get() {
        if (instance == null)
            throw new IllegalStateException("IrisMasterServer not running");
        return instance;
    }
}
