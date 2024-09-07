package com.volmit.iris.server.master;

import com.volmit.iris.server.IrisConnection;
import com.volmit.iris.server.node.IrisServer;
import com.volmit.iris.server.util.PregenHolder;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import lombok.extern.java.Log;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Log(topic = "Iris-MasterServer")
public class IrisMasterServer extends IrisServer {
    private static IrisMasterServer instance;
    private final KMap<UUID, KMap<IrisMasterClient, KMap<UUID, PregenHolder>>> sessions = new KMap<>();
    private final KMap<String, KSet<InetSocketAddress>> nodes = new KMap<>();

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

    private void addNode(InetSocketAddress address) throws InterruptedException {
        var ping = new PingConnection(address);
        try {
            for (String version : ping.getVersion().get())
                addNode(address, version);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void addNode(InetSocketAddress address, String version) {
        nodes.computeIfAbsent(version, v -> new KSet<>()).add(address);
    }

    public void removeNode(InetSocketAddress address) {
        for (var set : nodes.values()) {
            set.remove(address);
        }
    }

    public static void close(UUID session) {
        var map = get().sessions.remove(session);
        if (map == null) return;
        map.keySet().forEach(IrisMasterClient::disconnect);
        map.clear();
    }

    public static KList<String> getVersions() {
        return get().nodes.k();
    }

    public static KMap<IrisMasterClient, KMap<UUID, PregenHolder>> getNodes(String version, IrisMasterSession session) {
        var master = get();
        var uuid = session.getUuid();
        close(uuid);

        master.getLogger().info("Requesting nodes for session " + uuid);
        var map = new KMap<IrisMasterClient, KMap<UUID, PregenHolder>>();
        for (var address : master.nodes.getOrDefault(version, new KSet<>())) {
            try {
                map.put(IrisConnection.connect(address, new IrisMasterClient(version, session)), new KMap<>());
            } catch (Throwable e) {
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
