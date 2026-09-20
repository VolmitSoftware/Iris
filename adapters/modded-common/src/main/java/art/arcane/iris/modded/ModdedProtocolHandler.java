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

package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolChannel;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolWorld;
import art.arcane.iris.platform.protocol.EngineResolver;
import art.arcane.iris.platform.protocol.IrisCursorRequestService;
import art.arcane.iris.platform.protocol.IrisProtocolServer;
import art.arcane.iris.platform.protocol.IrisSession;
import art.arcane.iris.platform.protocol.IrisSessionRegistry;
import art.arcane.iris.platform.protocol.IrisVisionRequestService;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedProtocolHandler {
    private static final long SERVER_CAPABILITIES = IrisProtocol.CAPABILITY_PREGEN
            | IrisProtocol.CAPABILITY_VISION
            | IrisProtocol.CAPABILITY_CURSOR
            | IrisProtocol.CAPABILITY_STUDIO;
    private static final int DIMENSION_SYNC_INTERVAL_TICKS = 5;

    private static final ConcurrentHashMap<String, Engine> SESSION_ENGINES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> SESSION_LEVELS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> SESSION_IDS = new ConcurrentHashMap<>();

    private static volatile NativeProtocolChannel channel;
    private static NativeProtocolServer serverContext;
    private static volatile IrisSessionRegistry registry;
    private static volatile IrisProtocolServer protocolServer;
    private static volatile ModdedProtocolTransport transport;
    private static volatile IrisCursorRequestService cursorRequests;
    private static volatile IrisVisionRequestService visionRequests;
    private static int dimensionSyncTicks;

    private ModdedProtocolHandler() {
    }

    public static void bindChannel(NativeProtocolChannel boundChannel) {
        channel = Objects.requireNonNull(boundChannel, "protocol channel");
    }

    public static void start(NativeProtocolServer server) {
        NativeProtocolChannel boundChannel = channel;
        if (server == null || boundChannel == null) {
            return;
        }
        SESSION_ENGINES.clear();
        SESSION_LEVELS.clear();
        SESSION_IDS.clear();
        dimensionSyncTicks = 0;
        IrisSessionRegistry sessionRegistry = new IrisSessionRegistry();
        ModdedProtocolTransport serverTransport = new ModdedProtocolTransport(server.delivery(boundChannel));
        IrisProtocolServer protocol = new IrisProtocolServer(sessionRegistry, SERVER_CAPABILITIES, brand(), true);
        EngineResolver engineResolver = (String sessionId) -> {
            Engine engine = SESSION_ENGINES.get(sessionId);
            return engine == null || engine.isClosed() ? null : engine;
        };
        protocol.setEngineResolver(engineResolver);
        IrisCursorRequestService cursorService = IrisCursorRequestService.create(engineResolver, sessionRegistry);
        protocol.setCursorInfoHandler(cursorService);
        IrisVisionRequestService visionService = IrisVisionRequestService.create(engineResolver, sessionRegistry);
        protocol.setVisionTileHandler(visionService);
        registry = sessionRegistry;
        transport = serverTransport;
        protocolServer = protocol;
        cursorRequests = cursorService;
        visionRequests = visionService;
        IrisServices.register(IrisProtocolServer.class, protocol);
        serverContext = server;
        server.forEachPlayer(player -> sessionRegistry.register(new IrisSession(sessionId(player), serverTransport)));
    }

    public static void stop() {
        IrisServices.remove(IrisProtocolServer.class);
        IrisSessionRegistry current = registry;
        IrisCursorRequestService cursor = cursorRequests;
        IrisVisionRequestService vision = visionRequests;
        if (current != null) {
            for (IrisSession session : current.all()) {
                current.unregister(session.id());
                if (cursor != null) {
                    cursor.clearSession(session.id());
                }
                if (vision != null) {
                    vision.clearSession(session.id());
                }
            }
        }
        SESSION_ENGINES.clear();
        SESSION_LEVELS.clear();
        SESSION_IDS.clear();
        dimensionSyncTicks = 0;
        serverContext = null;
        registry = null;
        protocolServer = null;
        transport = null;
        cursorRequests = null;
        visionRequests = null;
    }

    public static void onPlayerJoin(NativeProtocolPlayer player) {
        if (player == null) {
            return;
        }
        String sessionId = sessionId(player);
        ModdedStartup.warnPackFailuresTo(player);
        IrisSessionRegistry current = registry;
        ModdedProtocolTransport currentTransport = transport;
        if (current == null || currentTransport == null) {
            return;
        }
        current.register(new IrisSession(sessionId, currentTransport));
    }

    public static void onPlayerDisconnect(NativeProtocolPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.id();
        String sessionId = SESSION_IDS.remove(id);
        if (sessionId == null) {
            sessionId = id.toString();
        }
        ModdedPrimaryWorldRouter.forget(id);
        NativeProtocolServer server = serverContext;
        if (server != null) {
            server.forget(id);
        }
        SESSION_ENGINES.remove(sessionId);
        SESSION_LEVELS.remove(sessionId);
        IrisSessionRegistry current = registry;
        if (current != null) {
            current.unregister(sessionId);
        }
        IrisCursorRequestService cursor = cursorRequests;
        if (cursor != null) {
            cursor.clearSession(sessionId);
        }
        IrisVisionRequestService vision = visionRequests;
        if (vision != null) {
            vision.clearSession(sessionId);
        }
    }

    /**
     * UUID.toString allocates a 36-char string every call; the dimension sync tick runs over every player four
     * times a second, so the id is interned per player on first use and dropped on disconnect.
     */
    private static String sessionId(NativeProtocolPlayer player) {
        return SESSION_IDS.computeIfAbsent(player.id(), UUID::toString);
    }

    public static void onInbound(NativeProtocolPlayer player, byte[] frame) {
        IrisProtocolServer current = protocolServer;
        if (player == null || frame == null || current == null) {
            return;
        }
        String sessionId = sessionId(player);
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            current.onClientFrame(sessionId, frame);
            return;
        }
        scheduler.global(() -> current.onClientFrame(sessionId, frame));
    }

    public static void tickDimensionSync() {
        NativeProtocolServer server = serverContext;
        IrisSessionRegistry current = registry;
        IrisProtocolServer protocol = protocolServer;
        if (server == null || current == null || protocol == null) {
            return;
        }
        dimensionSyncTicks++;
        if (dimensionSyncTicks < DIMENSION_SYNC_INTERVAL_TICKS) {
            return;
        }
        dimensionSyncTicks = 0;
        server.forEachPlayer(player -> syncPlayerDimension(current, protocol, player));
    }

    private static void syncPlayerDimension(IrisSessionRegistry current, IrisProtocolServer protocol, NativeProtocolPlayer player) {
        String sessionId = sessionId(player);
        IrisSession session = current.get(sessionId);
        if (session == null || !session.isReady()) {
            return;
        }
        NativeProtocolWorld level = player.world();
        String levelId = level.id();
        Engine cached = SESSION_ENGINES.get(sessionId);
        if (levelId.equals(SESSION_LEVELS.get(sessionId)) && (cached == null || !cached.isClosed())) {
            return;
        }
        if (syncDimension(protocol, sessionId, level, levelId)) {
            SESSION_LEVELS.put(sessionId, levelId);
        }
    }

    private static boolean syncDimension(IrisProtocolServer protocol, String sessionId, NativeProtocolWorld level, String levelId) {
        IrisModdedChunkGenerator generator = level.generator(IrisModdedChunkGenerator.class);
        // engineIfBound only, never commandEngine: the sync tick runs on the server thread and constructing an
        // engine there stalls the tick for the whole pack load. An unbound generator simply retries next tick.
        Engine engine = generator != null ? resolveEngine(level, generator) : null;
        if (generator != null && engine == null) {
            return false;
        }
        long seed = level.seed();
        if (engine != null) {
            SESSION_ENGINES.put(sessionId, engine);
            protocol.sendDimensionStatus(sessionId, engine.getDimension().getLoadKey(), engine.getData().getDataFolder().getName(),
                    seed, engine.getMinHeight(), engine.getMaxHeight(), true);
            return true;
        }
        SESSION_ENGINES.remove(sessionId);
        protocol.sendDimensionStatus(sessionId, levelId, "", seed, level.minHeight(), level.maxHeight(), false);
        return true;
    }

    private static Engine resolveEngine(NativeProtocolWorld level, IrisModdedChunkGenerator generator) {
        try {
            Engine engine = generator.engineIfBound();
            return engine == null || engine.isClosed() ? null : engine;
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris dimension status engine lookup failed for {}", level.id(), failure);
            return null;
        }
    }

    private static String brand() {
        return "Iris " + ModdedEngineBootstrap.loader().modVersion() + " (" + ModdedEngineBootstrap.loader().platformName() + ")";
    }
}
