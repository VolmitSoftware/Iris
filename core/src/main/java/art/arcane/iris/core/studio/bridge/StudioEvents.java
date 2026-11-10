/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.studio.bridge;

import art.arcane.iris.core.gui.GuiMarker;
import art.arcane.iris.engine.framework.Engine;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-sent events to every connected studio: {@code players} when a player moves, {@code world} when the world's
 * engine changes (a hotload), {@code usage} with the server's CPU and heap every second, and a comment now and then so
 * a dead connection is noticed. A studio that stops
 * reading is dropped.
 */
final class StudioEvents {
    private static final byte[] KEEP_ALIVE = ": alive\n\n".getBytes(StandardCharsets.UTF_8);
    static final long POLL_INTERVAL_MILLIS = 250L;
    private static final long KEEP_ALIVE_INTERVAL_MILLIS = 10_000L;
    private static final long USAGE_INTERVAL_MILLIS = 1_000L;

    private final StudioWorld world;
    private final Gson gson;
    private final List<OutputStream> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean listenerJoined = new AtomicBoolean();
    private final AtomicBoolean usageWanted = new AtomicBoolean();
    private final AtomicBoolean hotloaded = new AtomicBoolean();
    private List<GuiMarker> lastPlayers;
    private Engine lastEngine;
    private long lastSentMillis;
    private long lastUsageMillis;

    StudioEvents(StudioWorld world, Gson gson) {
        this.world = world;
        this.gson = gson;
    }

    void listen(OutputStream stream) {
        listeners.add(stream);
        listenerJoined.set(true);
        usageWanted.set(true);
    }

    /** The pack was hotloaded; the engine may be the same, its streams are not. */
    void hotloaded() {
        hotloaded.set(true);
    }

    /** Sends what changed since the last poll; called every {@link #POLL_INTERVAL_MILLIS} from one thread. */
    void poll() {
        if (listeners.isEmpty()) {
            return;
        }
        StudioWorld.Session session = world.session();
        Engine engine = session == null ? null : session.engine();
        if (engine != lastEngine || hotloaded.getAndSet(false)) {
            lastEngine = engine;
            send("world", new WorldChange(engine != null));
        }
        List<GuiMarker> players = session == null || session.overlay() == null ? List.of() : session.overlay().players();
        if (listenerJoined.getAndSet(false) || !Objects.equals(players, lastPlayers)) {
            lastPlayers = players;
            send("players", players);
        }
        long now = System.currentTimeMillis();
        if (usageWanted.getAndSet(false) || now - lastUsageMillis >= USAGE_INTERVAL_MILLIS) {
            lastUsageMillis = now;
            send("usage", ServerUsage.sample());
        }
        if (now - lastSentMillis > KEEP_ALIVE_INTERVAL_MILLIS) {
            sendToAll(KEEP_ALIVE);
        }
    }

    void closeAll() {
        for (OutputStream listener : listeners) {
            closeQuietly(listener);
        }
        listeners.clear();
    }

    private void send(String event, Object data) {
        sendToAll(("event: " + event + "\ndata: " + gson.toJson(data) + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private void sendToAll(byte[] message) {
        lastSentMillis = System.currentTimeMillis();
        for (OutputStream listener : listeners) {
            try {
                listener.write(message);
                listener.flush();
            } catch (IOException gone) {
                listeners.remove(listener);
                closeQuietly(listener);
            }
        }
    }

    private static void closeQuietly(OutputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    /** @param running whether the world has an engine again; the studio redraws either way */
    private record WorldChange(boolean running) {
    }
}
